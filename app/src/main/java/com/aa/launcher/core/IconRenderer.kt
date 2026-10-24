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
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.aa.launcher.settings.IconShape

/** 実際に描画するスタイル (Automatic などを解決したもの)。 */
enum class RenderStyle { DEFAULT, DARK, TINTED, CLEAR_LIGHT, CLEAR_DARK }

/**
 * Android のアイコンを iOS 風に描画する。
 * - スーパー楕円でマスク
 * - ダーク / 色合い (Tinted) / クリア の各スタイル (iOS 18 / 26 / 27)
 */
object IconRenderer {

    private fun newPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

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
        val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) d.monochrome?.mutate() else null
        when (style) {
            RenderStyle.DEFAULT -> {
                bg?.drawIn(canvas, bounds)
                fg?.drawIn(canvas, bounds)
            }
            RenderStyle.DARK -> {
                drawDarkPlate(canvas, size)
                val bgColor = bg?.let { averageColor(it, bounds) } ?: Color.WHITE
                if (fg != null) {
                    val layer = layerBitmap(fg, bounds, size)
                    if (isMostlyLight(layer)) {
                        // 白いグリフは元の背景色で着色 (iOS のダークアイコン風)
                        val glyphColor = brighten(bgColor)
                        paint.colorFilter = PorterDuffColorFilter(glyphColor, PorterDuff.Mode.SRC_IN)
                        canvas.drawBitmap(layer, 0f, 0f, paint)
                        paint.colorFilter = null
                    } else {
                        canvas.drawBitmap(layer, 0f, 0f, paint)
                    }
                }
            }
            RenderStyle.TINTED -> {
                drawTintPlate(canvas, size, tint)
                if (mono != null) {
                    mono.colorFilter = PorterDuffColorFilter(brighten(tint), PorterDuff.Mode.SRC_IN)
                    mono.drawIn(canvas, bounds)
                    mono.colorFilter = null
                } else if (fg != null) {
                    val layer = layerBitmap(fg, bounds, size)
                    paint.colorFilter = luminanceTint(tint)
                    canvas.drawBitmap(layer, 0f, 0f, paint)
                    paint.colorFilter = null
                }
            }
            RenderStyle.CLEAR_LIGHT, RenderStyle.CLEAR_DARK -> {
                val glyphColor = if (style == RenderStyle.CLEAR_LIGHT) Color.WHITE else 0xFFF2F2F7.toInt()
                if (mono != null) {
                    mono.colorFilter = PorterDuffColorFilter(glyphColor, PorterDuff.Mode.SRC_IN)
                    mono.drawIn(canvas, bounds)
                    mono.colorFilter = null
                } else if (fg != null) {
                    canvas.drawBitmap(luminanceAlpha(layerBitmap(fg, bounds, size), glyphColor), 0f, 0f, paint)
                }
            }
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
                }
                canvas.drawBitmap(full, null, target, paint)
                paint.colorFilter = null
            }
            RenderStyle.TINTED -> {
                drawTintPlate(canvas, size, tint)
                paint.colorFilter = luminanceTint(tint)
                canvas.drawBitmap(full, null, target, paint)
                paint.colorFilter = null
            }
            RenderStyle.CLEAR_LIGHT, RenderStyle.CLEAR_DARK -> {
                canvas.drawBitmap(luminanceAlpha(full, Color.WHITE), null, target, paint)
            }
        }
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

    /** 輝度に応じて tint 色で塗る ColorFilter (iOS の Tinted アイコン)。 */
    private fun luminanceTint(tint: Int): ColorMatrixColorFilter {
        val t = brighten(tint)
        val r = Color.red(t) / 255f
        val g = Color.green(t) / 255f
        val b = Color.blue(t) / 255f
        return ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    r * 0.299f, r * 0.587f, r * 0.114f, 0f, 0f,
                    g * 0.299f, g * 0.587f, g * 0.114f, 0f, 0f,
                    b * 0.299f, b * 0.587f, b * 0.114f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    /** 輝度をアルファに変換した単色グリフを作る (クリアアイコン)。 */
    private fun luminanceAlpha(src: Bitmap, color: Int): Bitmap {
        val out = createBitmap(src.width, src.height)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0f, 0f, 0f, 0f, Color.red(color).toFloat(),
                    0f, 0f, 0f, 0f, Color.green(color).toFloat(),
                    0f, 0f, 0f, 0f, Color.blue(color).toFloat(),
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                ),
            ),
        )
        c.drawBitmap(src, 0f, 0f, p)
        // 元のアルファでマスク
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        c.drawBitmap(src, 0f, 0f, mask)
        return out
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

    private fun isMostlyLight(bmp: Bitmap): Boolean {
        val small = Bitmap.createScaledBitmap(bmp, 16, 16, true)
        var light = 0; var total = 0
        for (x in 0 until 16) for (y in 0 until 16) {
            val px = small.getPixel(x, y)
            if (Color.alpha(px) < 100) continue
            total++
            if (ColorUtils.calculateLuminance(px) > 0.75) light++
        }
        return total > 0 && light >= total * 0.8
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
