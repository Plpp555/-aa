package com.aa.launcher.core

import android.graphics.Path
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.aa.launcher.settings.IconShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

object IconShapes {

    /** iOS の連続曲率コーナーに近いスーパー楕円 (n = 5)。 */
    fun superellipse(width: Float, height: Float, n: Double = 5.0, steps: Int = 120): Path {
        val path = Path()
        val cx = width / 2f
        val cy = height / 2f
        for (i in 0..steps) {
            val t = 2.0 * Math.PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = cx + cx * (sign(c) * abs(c).pow(2.0 / n)).toFloat()
            val y = cy + cy * (sign(s) * abs(s).pow(2.0 / n)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** アイコン描画用のクリップパス。null の場合はマスクしない。 */
    fun path(shape: IconShape, size: Float): Path? = when (shape) {
        IconShape.SQUIRCLE -> superellipse(size, size)
        IconShape.CIRCLE -> Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) }
        IconShape.ROUNDED_SQUARE -> Path().apply { addRoundRect(0f, 0f, size, size, size * 0.24f, size * 0.24f, Path.Direction.CW) }
        IconShape.SQUARE -> Path().apply { addRoundRect(0f, 0f, size, size, size * 0.08f, size * 0.08f, Path.Direction.CW) }
        IconShape.TEARDROP -> Path().apply {
            val r = size / 2f
            val small = size * 0.12f
            addRoundRect(0f, 0f, size, size, floatArrayOf(r, r, r, r, small, small, r, r), Path.Direction.CW)
        }
        IconShape.SYSTEM -> null
    }

    fun composeShape(shape: IconShape): Shape = when (shape) {
        IconShape.SQUIRCLE, IconShape.SYSTEM -> SquircleShape
        IconShape.CIRCLE -> RoundedCornerShape(50)
        IconShape.ROUNDED_SQUARE -> RoundedCornerShape(24)
        IconShape.SQUARE -> RoundedCornerShape(8)
        IconShape.TEARDROP -> RoundedCornerShape(50, 50, 12, 50)
    }
}

/** Compose 用のスーパー楕円シェイプ (フォルダ・ガラス背景用)。 */
object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(IconShapes.superellipse(size.width, size.height).asComposePath())
}
