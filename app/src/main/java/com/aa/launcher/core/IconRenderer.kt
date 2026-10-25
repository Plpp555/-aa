package com.aa.launcher.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.aa.launcher.settings.IconShape
import kotlin.math.sqrt

/** 実際に描画するスタイル (Automatic などを解決したもの)。 */
enum class RenderStyle { DEFAULT, DARK, TINTED, CLEAR_LIGHT, CLEAR_DARK }

/**
 * Android のアイコンを iOS 風に描画する。
 * - スーパー楕円でマスク
 * - ダーク / 色合い (Tinted) / クリア の各スタイル (iOS 18 / 26 / 27)
 *
 * 色合い・クリア (とダークの一部) は、アルファに形と陰影を持つ単色マスクを作って塗る。
 * 1. モノクロアイコン (Android 13+) があればそれを使う
 * 2. 前景レイヤーが記号や絵柄なら、その **輝度** を白の濃さにする ([layerMask])。
 *    絵柄のアイコンも細部まで見分けられる (iOS のクリアと同じ見え方)。
 *    暗い記号は反転、全体に暗いものは持ち上げるので消えない
 * 3. 前景が空・全面の場合はアイコン全体から作る ([opaqueMask])。単色の背景に記号が載ったものは
 *    背景色との色差で記号を抜き出し、絵柄のものは 2 と同じく輝度で描く
 *
 * 注意: 前景レイヤーの「形」を単色で塗りつぶすと、絵柄のアイコンが白い塊になって見分けられなくなる (dd32fda まではこの問題があった)。
 */
object IconRenderer {

    private fun newPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** 前景レイヤーを「記号」とみなせる覆い率の範囲 (全面の絵柄や空のレイヤーは除く)。 */
    private const val MIN_GLYPH_COVERAGE = 0.02f
    private const val MAX_GLYPH_COVERAGE = 0.75f

    fun render(drawable: Drawable, style: RenderStyle, shape: IconShape, size: Int, tint: Int, fromIconPack: Boolean): Bitmap {
        val out = createBitmap(size, size)
        val canvas = Canvas(out)
        val clip = if (fromIconPack) null else IconShapes.path(shape, size.toFloat())
        val save = canvas.save()
        clip?.let { canvas.clipPath(it) }
        // アダプティブアイコンは Android 8.0 以降。それより前のアイコンはすべてレガシー扱い
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable && !fromIconPack) {
            drawAdaptive(canvas, drawable, style, size, tint)
        } else {
            drawLegacy(canvas, drawable, style, size, tint, clipped = clip != null)
        }
        canvas.restoreToCount(save)
        return out
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun drawAdaptive(canvas: Canvas, d: AdaptiveIconDrawable, style: RenderStyle, size: Int, tint: Int) {
        // アダプティブアイコンのレイヤーは 108dp で、表示領域は中央 72dp (2/3)
        val inset = size / 4
        val bounds = Rect(-inset, -inset, size + inset, size + inset)
        val paint = newPaint()
        val bg = d.background
        val fg = d.foreground
        if (style == RenderStyle.DEFAULT) {
            bg?.drawIn(canvas, bounds)
            fg?.drawIn(canvas, bounds)
            return
        }

        val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) d.monochrome?.mutate() else null
        val monoLayer = mono?.let { layerBitmap(it, bounds, size) }?.takeIf { toneStats(it).coverage >= MIN_GLYPH_COVERAGE / 2 }
        val fgLayer = fg?.let { layerBitmap(it, bounds, size) }
        val fgTone = fgLayer?.let { toneStats(it) }
        val bgColor = bg?.let { averageColor(it, bounds) }
        // 単色マスクは必要になったときだけ作る
        val mask by lazy {
            monoLayer ?: if (fgLayer != null && fgTone != null && fgTone.coverage in MIN_GLYPH_COVERAGE..MAX_GLYPH_COVERAGE) {
                layerMask(fgLayer, fgTone, allowInvert = true)
            } else {
                // 前景が空 (絵柄が背景レイヤー側) か全面: アイコン全体から作る
                val full = createBitmap(size, size)
                val c = Canvas(full)
                bg?.drawIn(c, bounds)
                fg?.drawIn(c, bounds)
                opaqueMask(full)
            }
        }

        when (style) {
            RenderStyle.DARK -> {
                drawDarkPlate(canvas, size)
                val accent = brighten(bgColor ?: Color.WHITE)
                when {
                    monoLayer != null -> drawColored(canvas, monoLayer, accent, paint)
                    fgLayer != null && fgTone != null -> when {
                        // 前景が暗い・空で暗い下地に埋もれる → 単色マスクを明るい色で
                        fgTone.coverage < MIN_GLYPH_COVERAGE || fgTone.meanLuminance < 0.3f -> drawColored(canvas, mask, accent, paint)
                        // 白い記号は元の背景色で着色 (iOS のダークアイコン風)
                        fgTone.coverage <= MAX_GLYPH_COVERAGE && fgTone.lightRatio >= 0.8f -> drawColored(canvas, fgLayer, accent, paint)
                        else -> canvas.drawBitmap(fgLayer, 0f, 0f, paint)
                    }
                    else -> drawColored(canvas, mask, accent, paint)
                }
            }
            RenderStyle.TINTED -> {
                drawTintPlate(canvas, size, tint)
                drawColored(canvas, mask, brighten(tint), paint)
            }
            RenderStyle.CLEAR_LIGHT, RenderStyle.CLEAR_DARK -> {
                val glyphColor = if (style == RenderStyle.CLEAR_LIGHT) Color.WHITE else 0xFFF2F2F7.toInt()
                drawColored(canvas, mask, glyphColor, paint)
            }
            RenderStyle.DEFAULT -> Unit
        }
    }

    private fun drawLegacy(canvas: Canvas, d: Drawable, style: RenderStyle, size: Int, tint: Int, clipped: Boolean) {
        val paint = newPaint()
        val full = createBitmap(size, size)
        d.drawIn(Canvas(full), Rect(0, 0, size, size))
        val fullBleed = !clipped || isFullBleed(full)
        val target = if (fullBleed) RectF(0f, 0f, size.toFloat(), size.toFloat()) else {
            val pad = size * 0.16f
            RectF(pad, pad, size - pad, size - pad)
        }
        // 全面のアイコンは全体から、透明な背景のロゴは形と輝度からマスクを作る
        val mask by lazy { if (fullBleed) opaqueMask(full) else layerMask(full, toneStats(full), allowInvert = true) }
        when (style) {
            RenderStyle.DEFAULT -> {
                if (!fullBleed) canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(full, null, target, paint)
            }
            RenderStyle.DARK -> {
                drawDarkPlate(canvas, size)
                if (fullBleed) {
                    paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.62f, 0.62f, 0.62f, 1f) })
                    canvas.drawBitmap(full, null, target, paint)
                    paint.colorFilter = null
                } else if (toneStats(full).meanLuminance < 0.3f) {
                    // 透明背景の暗いロゴは暗い下地で見えなくなるので、記号として明るく描く
                    drawColored(canvas, mask, 0xFFE5E5EA.toInt(), paint, target)
                } else {
                    canvas.drawBitmap(full, null, target, paint)
                }
            }
            RenderStyle.TINTED -> {
                drawTintPlate(canvas, size, tint)
                drawColored(canvas, mask, brighten(tint), paint, target)
            }
            RenderStyle.CLEAR_LIGHT, RenderStyle.CLEAR_DARK -> {
                val glyphColor = if (style == RenderStyle.CLEAR_LIGHT) Color.WHITE else 0xFFF2F2F7.toInt()
                drawColored(canvas, mask, glyphColor, paint, target)
            }
        }
    }

    /** アルファで形を持つビットマップを単色で塗る。 */
    private fun drawColored(canvas: Canvas, src: Bitmap, color: Int, paint: Paint, target: RectF? = null) {
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        if (target == null) canvas.drawBitmap(src, 0f, 0f, paint) else canvas.drawBitmap(src, null, target, paint)
        paint.colorFilter = null
    }

    /**
     * 不透明部分 (アルファ 100 以上) の統計。
     * coverage: 覆い率 / meanLuminance: 平均輝度 / lightRatio: 明るい (> 0.75) 画素の割合 /
     * midRatio: 中間調 (0.15〜0.7) の画素の割合 (絵柄ほど大きい) / p98Luminance: 輝度の 98 パーセンタイル
     */
    private class ToneStats(
        val coverage: Float,
        val meanLuminance: Float,
        val lightRatio: Float,
        val midRatio: Float,
        val p98Luminance: Float,
    )

    private fun luminance(c: Int): Float = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

    private fun toneStats(bmp: Bitmap): ToneStats {
        val s = 48
        val small = Bitmap.createScaledBitmap(bmp, s, s, true)
        val px = IntArray(s * s)
        small.getPixels(px, 0, s, 0, 0, s, s)
        val histogram = IntArray(51)
        var opaque = 0
        var light = 0
        var mid = 0
        var lumSum = 0.0
        for (c in px) {
            if (Color.alpha(c) < 100) continue
            opaque++
            val l = luminance(c)
            lumSum += l
            if (l > 0.75f) light++
            if (l > 0.15f && l < 0.7f) mid++
            histogram[(l * 50f).toInt().coerceIn(0, 50)]++
        }
        if (opaque == 0) return ToneStats(0f, 0f, 0f, 0f, 0f)
        var p98 = 1f
        var acc = 0
        for (k in 0..50) {
            acc += histogram[k]
            if (acc >= opaque * 0.98f) {
                p98 = k / 50f
                break
            }
        }
        return ToneStats(
            coverage = opaque / (s * s).toFloat(),
            meanLuminance = (lumSum / opaque).toFloat(),
            lightRatio = light.toFloat() / opaque,
            midRatio = mid.toFloat() / opaque,
            p98Luminance = p98,
        )
    }

    /**
     * 形 (アルファ) を持つ画像から、輝度を白の濃さにした単色マスクを作る (iOS のクリアアイコンの見え方)。
     * - 通常: 明るいところほど濃い白。絵柄の陰影や線がそのまま残る (以前の輝度描画と同じ)
     * - 全体に暗いもの: 最も明るい部分が白になるよう持ち上げ、暗い部分にも薄い下限を付ける
     * - 暗い記号 (中間調が少ない): 反転して記号を白くする ([allowInvert] のとき。絵柄は反転しない)
     */
    private fun layerMask(src: Bitmap, tone: ToneStats, allowInvert: Boolean): Bitmap {
        val w = src.width
        val h = src.height
        val n = w * h
        val px = IntArray(n)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val invert = allowInvert && tone.meanLuminance < 0.25f && tone.midRatio < 0.25f
        val gain = 1f / tone.p98Luminance.coerceIn(0.5f, 1f)
        val floor = ((0.45f - tone.meanLuminance) / 0.2f).coerceIn(0f, 1f) * 0.3f
        val out = IntArray(n)
        var total = 0f
        for (i in 0 until n) {
            val c = px[i]
            val a = Color.alpha(c) / 255f
            if (a <= 0.004f) continue
            val v = (luminance(c) * gain).coerceAtMost(1f)
            val m = a * if (invert) 1f - 0.7f * v else floor + (1f - floor) * v
            total += m
            out[i] = ((m * 255f).toInt().coerceIn(0, 255) shl 24) or 0x00FFFFFF
        }
        return ensureVisible(out, px, w, h, total)
    }

    /**
     * 不透明なアイコン全体から単色マスクを作る。
     * 背景色 (縁の中央値) との色差を求め、単色の背景に記号が載ったもの (背景と同じ色が多く、中間の色差が少ない) なら
     * 色差で記号を抜き出す。写真や絵柄 (グラデーション背景を含む) は [layerMask] と同じく輝度で描く。
     */
    private fun opaqueMask(full: Bitmap): Bitmap {
        val w = full.width
        val h = full.height
        val n = w * h
        val px = IntArray(n)
        full.getPixels(px, 0, w, 0, 0, w, h)
        val ref = edgeColor(px, w, h)
        if (Color.alpha(ref) < 200) return layerMask(full, toneStats(full), allowInvert = true)
        val rr = Color.red(ref)
        val rg = Color.green(ref)
        val rb = Color.blue(ref)

        val diff = FloatArray(n)
        val histogram = IntArray(101)
        var counted = 0
        for (i in 0 until n) {
            val c = px[i]
            val a = Color.alpha(c) / 255f
            if (a <= 0.01f) continue
            val dr = (Color.red(c) - rr).toFloat()
            val dg = (Color.green(c) - rg).toFloat()
            val db = (Color.blue(c) - rb).toFloat()
            val d = a * (sqrt(0.30f * dr * dr + 0.59f * dg * dg + 0.11f * db * db) / 255f)
            diff[i] = d
            histogram[(d * 100f).toInt().coerceIn(0, 100)]++
            counted++
        }
        if (counted == 0) return layerMask(full, toneStats(full), allowInvert = true)
        // 98 パーセンタイルで正規化 (一部の強いハイライトに引っ張られないように)
        var p98 = 1f
        var acc = 0
        for (k in 0..100) {
            acc += histogram[k]
            if (acc >= counted * 0.98f) {
                p98 = k / 100f
                break
            }
        }
        val norm = p98.coerceAtLeast(0.12f)
        var background = 0
        var middle = 0
        for (i in 0 until n) {
            if (Color.alpha(px[i]) <= 2) continue
            val dn = diff[i] / norm
            if (dn < 0.15f) background++ else if (dn > 0.25f && dn < 0.75f) middle++
        }
        val glyphOnPlain = background >= counted * 0.3f && middle <= counted * 0.2f
        if (!glyphOnPlain) return layerMask(full, toneStats(full), allowInvert = false)

        val out = IntArray(n)
        var total = 0f
        for (i in 0 until n) {
            val a = (diff[i] / norm).coerceIn(0f, 1f)
            total += a
            out[i] = ((a * 255f).toInt() shl 24) or 0x00FFFFFF
        }
        return ensureVisible(out, px, w, h, total)
    }

    /** マスクがほとんど空なら、アイコンの形そのものを半透明で使う (どのアイコンも消えないように)。 */
    private fun ensureVisible(out: IntArray, src: IntArray, w: Int, h: Int, total: Float): Bitmap {
        if (total < w * h * 0.01f) {
            for (i in out.indices) out[i] = (((Color.alpha(src[i]) * 0.55f).toInt()) shl 24) or 0x00FFFFFF
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** 縁 (外周 3〜9%) の画素の中央値。背景色の推定に使う。 */
    private fun edgeColor(px: IntArray, w: Int, h: Int): Int {
        val a = ArrayList<Int>()
        val r = ArrayList<Int>()
        val g = ArrayList<Int>()
        val b = ArrayList<Int>()
        val lo = (w * 0.03f).toInt()
        val hi = (w * 0.09f).toInt().coerceAtLeast(lo + 1)
        val step = (w / 48).coerceAtLeast(1)
        fun sample(x: Int, y: Int) {
            val c = px[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
            a += Color.alpha(c); r += Color.red(c); g += Color.green(c); b += Color.blue(c)
        }
        for (t in lo until hi) {
            var i = 0
            while (i < w) {
                sample(i, t); sample(i, h - 1 - t); sample(t, i); sample(w - 1 - t, i)
                i += step
            }
        }
        fun median(list: ArrayList<Int>) = list.sorted()[list.size / 2]
        return Color.argb(median(a), median(r), median(g), median(b))
    }

    private fun Drawable.drawIn(canvas: Canvas, bounds: Rect) {
        val old = copyBounds()
        setBounds(bounds)
        draw(canvas)
        setBounds(old)
    }

    private fun layerBitmap(layer: Drawable, bounds: Rect, size: Int): Bitmap {
        val bmp = createBitmap(size, size)
        layer.drawIn(Canvas(bmp), bounds)
        return bmp
    }

    private fun drawDarkPlate(canvas: Canvas, size: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), 0xFF3A3A3C.toInt(), 0xFF0E0E10.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
    }

    private fun drawTintPlate(canvas: Canvas, size: Int, tint: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val top = ColorUtils.blendARGB(tint, Color.BLACK, 0.78f)
        val bottom = ColorUtils.blendARGB(tint, Color.BLACK, 0.93f)
        p.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
    }

    private fun averageColor(d: Drawable, bounds: Rect): Int {
        val bmp = createBitmap(8, 8)
        val c = Canvas(bmp)
        c.scale(8f / bounds.width(), 8f / bounds.height())
        c.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
        d.drawIn(c, bounds)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (x in 0 until 8) for (y in 0 until 8) {
            val px = bmp.getPixel(x, y)
            if (Color.alpha(px) < 128) continue
            r += Color.red(px); g += Color.green(px); b += Color.blue(px); n++
        }
        if (n == 0L) return Color.WHITE
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun isFullBleed(bmp: Bitmap): Boolean {
        val s = bmp.width
        val inset = (s * 0.08f).toInt().coerceAtLeast(1)
        val points = listOf(inset to inset, s - inset to inset, inset to s - inset, s - inset to s - inset)
        return points.all { (x, y) -> Color.alpha(bmp.getPixel(x.coerceIn(0, s - 1), y.coerceIn(0, s - 1))) > 200 }
    }

    private fun brighten(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceIn(0.55f, 0.8f)
        hsl[1] = hsl[1].coerceAtLeast(0.35f)
        return ColorUtils.HSLToColor(hsl)
    }
}
