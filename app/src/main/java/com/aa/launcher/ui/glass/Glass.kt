package com.aa.launcher.ui.glass

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.aa.launcher.core.Backdrop
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Liquid Glass の表示パラメータ。iOS 27 の「クリア ↔ 色合い」スライダーを opacity で表す。 */
data class GlassParams(
    val dark: Boolean = false,
    val opacity: Float = 0.3f,
    val reduceTransparency: Boolean = false,
    val increaseContrast: Boolean = false,
    val wallpaperTint: Color = Color.Unspecified,
)

val LocalBackdrop = compositionLocalOf<Backdrop?> { null }
val LocalScreenSize = compositionLocalOf { IntSize(1, 1) }
val LocalGlassParams = compositionLocalOf { GlassParams() }

/** この strength 以上は文字を載せるパネル (メニュー・シートなど)。読みやすさのため最低限の不透明度を保つ。 */
private const val TEXT_PANEL_STRENGTH = 1.4f

fun Outline.toPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}

/**
 * スライダー値 → フィルの効き具合。既定値 (0.3) では従来と同じ見た目になり、
 * 0 に近づくほど急速に透明になるよう、べき乗カーブで割り当てる。
 */
private fun opacityCurve(opacity: Float): Float = opacity.coerceIn(0f, 1f).pow(0.745f)

/** 強ぼかし背景の混ぜ具合。既定値以上では従来どおり強いぼかし、クリア側では弱いぼかしだけになる。 */
private fun heavyBlurMix(opacity: Float, strength: Float): Float =
    if (strength >= TEXT_PANEL_STRENGTH) 1f else (opacity / 0.25f).coerceIn(0f, 1f)

fun glassFill(params: GlassParams, tint: Color = Color.Unspecified, strength: Float = 1f): Color {
    if (params.reduceTransparency) return if (params.dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val base = if (params.dark) Color.Black else Color.White
    val minA = if (params.dark) 0.06f else 0.04f
    val maxA = if (params.dark) 0.78f else 0.82f
    var alpha = lerp(minA, maxA, opacityCurve(params.opacity)) * strength
    if (strength >= TEXT_PANEL_STRENGTH) alpha = alpha.coerceAtLeast(if (params.dark) 0.45f else 0.40f)
    val c = when {
        tint.isSpecified -> lerp(base, tint, 0.45f)
        params.wallpaperTint.isSpecified && params.opacity > 0.5f -> lerp(base, params.wallpaperTint, 0.12f)
        else -> base
    }
    return c.copy(alpha = alpha.coerceIn(0f, 0.95f))
}

/**
 * Liquid Glass 風の素材を背景に描く。
 * - 背後の壁紙を写し込む (クリア寄り: 弱ぼかし / 色合い寄り: 強ぼかし を連続的に混ぜる)
 * - 透明度スライダーに応じた白/黒のフィル、上部のハイライト、縁の光 (スペキュラーリム)
 *
 * 性能面:
 * - Modifier.Node 実装で、位置が変わったときだけ再描画する (静止中は一切再計算しない)
 * - パスやブラシはサイズが変わるまでキャッシュする
 * - ガラス自身と中身をそれぞれ独立したレイヤーにし、中身 (時計の文字など) の更新でガラスを描き直さず、
 *   ガラスの位置更新 (スクロール中) で中身を描き直さない
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = Color.Unspecified,
    strength: Float = 1f,
    rim: Boolean = true,
): Modifier {
    val element = LiquidGlassElement(
        shape = shape,
        tint = tint,
        strength = strength,
        rim = rim,
        backdrop = LocalBackdrop.current,
        screen = LocalScreenSize.current,
        params = LocalGlassParams.current,
    )
    return this
        .graphicsLayer()
        .then(element)
        .graphicsLayer()
}

private data class LiquidGlassElement(
    val shape: Shape,
    val tint: Color,
    val strength: Float,
    val rim: Boolean,
    val backdrop: Backdrop?,
    val screen: IntSize,
    val params: GlassParams,
) : ModifierNodeElement<LiquidGlassNode>() {
    override fun create() = LiquidGlassNode(shape, tint, strength, rim, backdrop, screen, params)

    override fun update(node: LiquidGlassNode) = node.update(shape, tint, strength, rim, backdrop, screen, params)

    override fun InspectorInfo.inspectableProperties() {
        name = "liquidGlass"
    }
}

private class LiquidGlassNode(
    var shape: Shape,
    var tint: Color,
    var strength: Float,
    var rim: Boolean,
    var backdrop: Backdrop?,
    var screen: IntSize,
    var params: GlassParams,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var origin = Offset.Zero

    // サイズ・方向・パラメータが変わるまで使い回すキャッシュ
    private var cacheSize = Size.Unspecified
    private var cacheDirection: LayoutDirection? = null
    private var cacheDensity = 0f
    private var cacheValid = false
    private var path = Path()
    private var fill = Color.Transparent
    private var rimBrush: Brush? = null
    private var rimWidth = 1f
    private var sheen: Brush? = null
    private var innerGlow: Brush? = null
    private var innerGlowWidth = 0f
    private var heavyMix = 1f

    fun update(
        shape: Shape,
        tint: Color,
        strength: Float,
        rim: Boolean,
        backdrop: Backdrop?,
        screen: IntSize,
        params: GlassParams,
    ) {
        this.shape = shape
        this.tint = tint
        this.strength = strength
        this.rim = rim
        this.backdrop = backdrop
        this.screen = screen
        this.params = params
        cacheValid = false
        invalidateDraw()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val p = coordinates.positionInRoot()
        if (p != origin) {
            origin = p
            // 背景を写し込むときだけ位置に依存する
            if (backdrop != null && !params.reduceTransparency) invalidateDraw()
        }
    }

    private fun ContentDrawScope.ensureCache() {
        if (cacheValid && cacheSize == size && cacheDirection == layoutDirection && cacheDensity == density) return
        cacheSize = size
        cacheDirection = layoutDirection
        cacheDensity = density
        cacheValid = true

        path = shape.createOutline(size, layoutDirection, this).toPath()
        fill = glassFill(params, tint, strength)
        heavyMix = heavyBlurMix(params.opacity, strength)
        val p = params
        // 既定値 (0.3) で従来と同じ強さ、クリア側では控えめにする
        val emphasis = 0.4f + 0.6f * (p.opacity / 0.3f).coerceIn(0f, 1f)
        rimWidth = (if (p.increaseContrast) 1.5.dp else 1.dp).toPx()
        rimBrush = if (p.increaseContrast) {
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.45f)))
        } else {
            Brush.linearGradient(
                0f to Color.White.copy(alpha = if (p.dark) 0.38f else 0.75f),
                0.45f to Color.White.copy(alpha = 0.06f),
                1f to Color.White.copy(alpha = if (p.dark) 0.18f else 0.35f),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
        }
        sheen = if (p.reduceTransparency) {
            null
        } else {
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = (if (p.dark) 0.07f else 0.16f) * emphasis), Color.Transparent),
                endY = size.height * 0.55f,
            )
        }
        // ガラスの厚みを感じさせる内側の光 (透明にしても輪郭が分かるように、クリア側でやや強める)
        val clearness = 1f - (p.opacity / 0.3f).coerceIn(0f, 1f)
        innerGlowWidth = 6.dp.toPx()
        innerGlow = if (p.reduceTransparency) {
            null
        } else {
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.10f + 0.08f * clearness),
                0.5f to Color.Transparent,
                1f to Color.White.copy(alpha = 0.04f + 0.06f * clearness),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
        }
    }

    /**
     * 背景画像を画面に置く位置と大きさ (この要素の座標)。壁紙 (ContentScale.Crop) と同じく縦横比を保って画面を覆い、中央に合わせる。
     * 画面の縦横比が壁紙を作ったときと違っても (折りたたみの開閉直後など)、透かしが背後の壁紙とずれない。
     */
    private fun coverPlacement(image: ImageBitmap): Pair<IntOffset, IntSize> {
        val sw = screen.width.toFloat()
        val sh = screen.height.toFloat()
        val scale = max(sw / image.width, sh / image.height)
        val w = image.width * scale
        val h = image.height * scale
        val left = (sw - w) / 2f - origin.x
        val top = (sh - h) / 2f - origin.y
        return IntOffset(left.roundToInt(), top.roundToInt()) to IntSize(w.roundToInt(), h.roundToInt())
    }

    override fun ContentDrawScope.draw() {
        ensureCache()
        val bd = backdrop
        val reduce = params.reduceTransparency
        clipPath(path) {
            if (bd != null && !reduce) {
                if (heavyMix < 0.999f) {
                    val (dst, dstSize) = coverPlacement(bd.light)
                    drawImage(
                        image = bd.light,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bd.light.width, bd.light.height),
                        dstOffset = dst,
                        dstSize = dstSize,
                        filterQuality = FilterQuality.Medium,
                    )
                }
                if (heavyMix > 0.001f) {
                    val (dst, dstSize) = coverPlacement(bd.heavy)
                    drawImage(
                        image = bd.heavy,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bd.heavy.width, bd.heavy.height),
                        dstOffset = dst,
                        dstSize = dstSize,
                        alpha = heavyMix,
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
            drawRect(fill)
            sheen?.let { drawRect(it) }
            innerGlow?.let { drawPath(path, it, style = Stroke(width = innerGlowWidth)) }
        }
        if (rim) rimBrush?.let { drawPath(path, it, style = Stroke(width = rimWidth)) }
        drawContent()
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
