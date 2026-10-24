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
 * ダーク・色合い・クリアでは、App がモノクロアイコン (Android 13+) を持っていればそれを使い、
 * なければ [glyphMask] でアイコンから記号部分を抽出する。抽出は「背景色との色差」と
 * 「前景レイヤーの形」を組み合わせるので、黒い記号・背景レイヤー側に描かれた絵柄・
 * カラフルなロゴでも消えない (以前は輝度だけを使っていたため、暗い記号の App が消えていた)。
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
        if (drawable is AdaptiveIconDrawable && !fromIconPack) {
            drawAdaptive(canvas, drawable, style, size, tint)
        } else {
            drawLegacy(canvas, drawable, style, size, tint, clipped = clip != null)
        }
        canvas.restoreToCount(save)
        return out
    }

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
        val monoLayer = mono?.let { layerBitmap(it, bounds, size) }?.takeIf { stats(it).coverage >= MIN_GLYPH_COVERAGE / 2 }
        val fgLayer = fg?.let { layerBitmap(it, bounds, size) }
        val bgColor = bg?.let { averageColor(it, bounds) }
        // 記号の抽出は必要になったときだけ (モノクロアイコンがあれば不要)
        val glyph by lazy {
            val full = createBitmap(size, size)
            val c = Canvas(full)
            bg?.drawIn(c, bounds)
            fg?.drawIn(c, bounds)
            glyphMask(full, fgLayer, reference = null)
        }

        when (style) {
            RenderStyle.DARK -> {
                drawDarkPlate(canvas, size)
                val accent = brighten(bgColor ?: Color.WHITE)
                when {
                    monoLayer != null -> drawColored(canvas, monoLayer, accent, paint)
                    fgLayer != null -> {
                        val s = stats(fgLayer)
                        when {
                            // 前景が暗い・空・全面の絵柄で記号が分からない → 抽出した記号を明るい色で
                            s.coverage < MIN_GLYPH_COVERAGE || s.meanLuminance < 0.3f -> drawColored(canvas, glyph, accent, paint)
                            // 白い記号は元の背景色で着色 (iOS のダークアイコン風)
                            s.coverage <= MAX_GLYPH_COVERAGE && s.lightRatio >= 0.8f -> drawColored(canvas, fgLayer, accent, paint)
                            else -> canvas.drawBitmap(fgLayer, 0f, 0f, paint)
                        }
                    }
                    else -> drawColored(canvas, glyph, accent, paint)
                }
            }
            RenderStyle.TINTED -> {
                drawTintPlate(canvas, size, tint)
                drawColored(canvas, monoLayer ?: glyph, brighten(tint), paint)
            }
            RenderStyle.CLEAR_LIGHT, RenderStyle.CLEAR_DARK -> {
                val glyphColor = if (style == RenderStyle.CLEAR_LIGHT) Color.WHITE else 0xFFF2F2F7.toInt()
                drawColored(canvas, monoLayer ?: glyph, glyphColor, paint)
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
                } else if (stats(full).meanLuminance < 0.3f) {
                    // 透明背景の暗いロゴは暗い下地で見えなくなるので、記号として明るく描く
                    drawColored(canvas, glyphMask(full, null, reference = null), 0xFFE5E5EA.toInt(), paint, target)
                } else {
                    canvas.drawBitmap(full, null, target, paint)
                }
            }
            RenderStyle.TINTED -> {
                drawTintPlate(canvas, size, tint)
                drawColored(canvas, glyphMask(full, null, reference = null), brighten(tint), paint, target)
            }
            RenderStyle.CLEAR_LIGHT, RenderStyle.CLEAR_DARK -> {
                val glyphColor = if (style == RenderStyle.CLEAR_LIGHT) Color.WHITE else 0xFFF2F2F7.toInt()
                drawColored(canvas, glyphMask(full, null, reference = null), glyphColor, paint, target)
            }
        }
    }

    /** アルファで形を持つビットマップを単色で塗る。 */
    private fun drawColored(canvas: Canvas, src: Bitmap, color: Int, paint: Paint, target: RectF? = null) {
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        if (target == null) canvas.drawBitmap(src, 0f, 0f, paint) else canvas.drawBitmap(src, null, target, paint)
        paint.colorFilter = null
    }

    private class LayerStats(val coverage: Float, val meanLuminance: Float, val lightRatio: Float)

    /** 不透明部分の覆い率・平均輝度・明るい画素の割合。 */
    private fun stats(bmp: Bitmap): LayerStats {
        val small = Bitmap.createScaledBitmap(bmp, 24, 24, true)
        var opaque = 0
        var light = 0
        var lumSum = 0.0
        for (x in 0 until 24) for (y in 0 until 24) {
            val px = small.getPixel(x, y)
            if (Color.alpha(px) < 100) continue
            opaque++
            val l = ColorUtils.calculateLuminance(px or 0xFF000000.toInt())
            lumSum += l
            if (l > 0.75) light++
        }
        if (opaque == 0) return LayerStats(0f, 0f, 0f)
        return LayerStats(opaque / 576f, (lumSum / opaque).toFloat(), light.toFloat() / opaque)
    }

    /**
     * アイコンから白いグリフ (アルファに形を持つ) を作る。
     * - 背景色 (縁の画素の中央値) との色差を画素ごとに求め、98 パーセンタイルで正規化する
     * - 前景レイヤーが記号らしい覆い率なら、その形も重ねる
     * - 背景が透明なアイコンは、形 (アルファ) に明るさの陰影を付けたものにする
     * - どれでも空に近ければ、アイコンの形そのものを半透明で使う (絶対に消えない)
     */
    private fun glyphMask(full: Bitmap, fgLayer: Bitmap?, reference: Int?): Bitmap {
        val w = full.width
        val h = full.height
        val n = w * h
        val px = IntArray(n)
        full.getPixels(px, 0, w, 0, 0, w, h)
        val fgPx = fgLayer?.let { layer -> IntArray(n).also { layer.getPixels(it, 0, w, 0, 0, w, h) } }

        val ref = reference ?: edgeColor(px, w, h)
        val refTransparent = Color.alpha(ref) < 128
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
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val d = if (refTransparent) {
                a * (0.6f + 0.4f * ((0.299f * r + 0.587f * g + 0.114f * b) / 255f))
            } else {
                val dr = (r - rr).toFloat()
                val dg = (g - rg).toFloat()
                val db = (b - rb).toFloat()
                a * (sqrt(0.30f * dr * dr + 0.59f * dg * dg + 0.11f * db * db) / 255f)
            }
            diff[i] = d
            histogram[(d * 100f).toInt().coerceIn(0, 100)]++
            counted++
        }
        // 98 パーセンタイル (一部の強いハイライトに引っ張られないように)
        var p98 = 1f
        if (counted > 0) {
            var acc = 0
            val limit = counted * 0.98f
            for (k in 0..100) {
                acc += histogram[k]
                if (acc >= limit) {
                    p98 = k / 100f
                    break
                }
            }
        }
        val norm = p98.coerceAtLeast(0.12f)

        var fgCoverage = 0
        if (fgPx != null) for (c in fgPx) if (Color.alpha(c) > 128) fgCoverage++
        val useFg = fgPx != null && fgCoverage >= n * MIN_GLYPH_COVERAGE && fgCoverage <= n * MAX_GLYPH_COVERAGE

        val out = IntArray(n)
        var total = 0f
        for (i in 0 until n) {
            var a = (diff[i] / norm).coerceIn(0f, 1f)
            if (useFg) a = maxOf(a, Color.alpha(fgPx!![i]) / 255f * 0.9f)
            total += a
            out[i] = ((a * 255f).toInt() shl 24) or 0x00FFFFFF
        }
        if (total < n * 0.01f) {
            // 単色のアイコンなど: 形そのものを使う
            for (i in 0 until n) out[i] = (((Color.alpha(px[i]) * 0.55f).toInt()) shl 24) or 0x00FFFFFF
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
