package com.aa.launcher.ui.glass

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/** Liquid Glass の表示パラメータ。iOS 27 の「クリア ↔ 色付き」スライダーを opacity で表す。 */
data class GlassParams(
    val dark: Boolean = false,
    val opacity: Float = 0.3f,
    val reduceTransparency: Boolean = false,
    val increaseContrast: Boolean = false,
    val wallpaperTint: Color = Color.Unspecified,
)

/** 画面全体サイズにスケールして描くぼかし済み壁紙。 */
val LocalBackdrop = compositionLocalOf<ImageBitmap?> { null }
val LocalScreenSize = compositionLocalOf { IntSize(1, 1) }
val LocalGlassParams = compositionLocalOf { GlassParams() }

fun Outline.toPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}

fun glassFill(params: GlassParams, tint: Color = Color.Unspecified, strength: Float = 1f): Color {
    if (params.reduceTransparency) return if (params.dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val base = if (params.dark) Color.Black else Color.White
    val minA = if (params.dark) 0.14f else 0.16f
    val maxA = if (params.dark) 0.78f else 0.82f
    val alpha = (lerp(minA, maxA, params.opacity) * strength).coerceIn(0f, 0.95f)
    val c = when {
        tint.isSpecified -> lerp(base, tint, 0.45f)
        params.wallpaperTint.isSpecified && params.opacity > 0.5f -> lerp(base, params.wallpaperTint, 0.12f)
        else -> base
    }
    return c.copy(alpha = alpha)
}

/**
 * Liquid Glass 風の素材を背景に描く。
 * - 背後の壁紙をぼかして写し込む (ランチャーが壁紙を保持している場合)
 * - 透明度スライダーに応じた白/黒のフィル
 * - 上部のハイライトと、光の当たる縁 (スペキュラーリム)
 */
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = Color.Unspecified,
    strength: Float = 1f,
    rim: Boolean = true,
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val screen = LocalScreenSize.current
    val params = LocalGlassParams.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawWithCache {
            val path = shape.createOutline(size, layoutDirection, this).toPath()
            val fill = glassFill(params, tint, strength)
            val rimWidth = (if (params.increaseContrast) 1.5.dp else 1.dp).toPx()
            val rimBrush = if (params.increaseContrast) {
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.45f)))
            } else {
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = if (params.dark) 0.38f else 0.75f),
                    0.45f to Color.White.copy(alpha = 0.06f),
                    1f to Color.White.copy(alpha = if (params.dark) 0.18f else 0.35f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            }
            val sheen = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = if (params.dark) 0.07f else 0.16f), Color.Transparent),
                endY = size.height * 0.55f,
            )
            onDrawBehind {
                clipPath(path) {
                    if (backdrop != null && !params.reduceTransparency) {
                        drawImage(
                            image = backdrop,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(backdrop.width, backdrop.height),
                            dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
                            dstSize = screen,
                            filterQuality = FilterQuality.Low,
                        )
                    }
                    drawRect(fill)
                    if (!params.reduceTransparency) drawRect(sheen)
                }
                if (rim) drawPath(path, rimBrush, style = Stroke(width = rimWidth))
            }
        }
}

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.Unspecified,
    strength: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.liquidGlass(shape, tint, strength), content = content)
}

/** Android 12 以降でのみ有効なコンテンツぼかし。 */
fun Modifier.blurCompat(radius: Dp): Modifier =
    if (radius > 0.5.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this
