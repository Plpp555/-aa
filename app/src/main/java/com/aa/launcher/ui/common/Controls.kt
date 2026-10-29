package com.aa.launcher.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aa.launcher.ui.theme.IosColors
import com.aa.launcher.ui.theme.LocalIsDark

/** iOS 風スイッチ。 */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dark = LocalIsDark.current
    val track by animateColorAsState(
        when {
            checked -> IosColors.Green
            dark -> Color(0xFF39393D)
            else -> Color(0xFFE9E9EA)
        },
        label = "track",
    )
    val offset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "thumb")
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(if (enabled) track else track.copy(alpha = 0.5f))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = offset, y = 2.dp)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** iOS 風スライダー。trackBrush を渡すと色相スライダーなどにも使える。 */
@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    trackBrush: Brush? = null,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    onValueChangeFinished: () -> Unit = {},
) {
    val dark = LocalIsDark.current
    val inactive = if (dark) Color(0xFF3A3A3C) else Color(0xFFE3E3E8)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentFinished by rememberUpdatedState(onValueChangeFinished)
    val range = valueRange.endInclusive - valueRange.start
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().height(32.dp)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val thumbPx = with(density) { 28.dp.toPx() }
        var dragFraction by remember { mutableFloatStateOf(fraction) }
        fun emit(x: Float) {
            var f = ((x - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f)
            if (steps > 0) {
                val n = steps + 1
                f = Math.round(f * n) / n.toFloat()
            }
            dragFraction = f
            currentOnChange(valueRange.start + f * range)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(widthPx, steps) {
                    detectTapGestures { emit(it.x); currentFinished() }
                }
                .pointerInput(widthPx, steps) {
                    detectDragGestures(
                        onDragStart = { emit(it.x) },
                        onDragEnd = { currentFinished() },
                        onDragCancel = { currentFinished() },
                    ) { change, _ ->
                        change.consume()
                        emit(change.position.x)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val trackModifier = Modifier
                .fillMaxWidth()
                .height(if (trackBrush != null) 8.dp else 4.dp)
                .clip(CircleShape)
            if (trackBrush != null) {
                Box(trackModifier.background(trackBrush))
            } else {
                Box(trackModifier.background(inactive))
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(activeColor),
                )
            }
            val thumbOffset = with(density) { (fraction * (widthPx - thumbPx)).toDp() }
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(28.dp)
                    .shadow(3.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.06f), CircleShape),
            )
        }
    }
}

/** iOS 風セグメンテッドコントロール。 */
@Composable
fun IosSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalIsDark.current
    val bg = if (dark) Color(0xFF2C2C2E) else Color(0xFFE3E3E8)
    val thumb = if (dark) Color(0xFF636366) else Color.White
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(bg)
            .padding(2.dp),
    ) {
        val count = options.size.coerceAtLeast(1)
        val segW = maxWidth / count
        val offset by animateDpAsState(segW * selected.coerceIn(0, count - 1), label = "segment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segW)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(15.dp))
                .background(thumb, RoundedCornerShape(15.dp)),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .width(segW)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(15.dp))
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (dark) Color.White else Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/** 色相スライダー用の虹色ブラシ。 */
val HueBrush: Brush = Brush.horizontalGradient(
    listOf(
        Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759),
        Color(0xFF00C7BE), Color(0xFF0A84FF), Color(0xFF5856D6), Color(0xFFAF52DE), Color(0xFFFF2D55), Color(0xFFFF3B30),
    ),
)

fun hsvColor(hue: Float, saturation: Float, value: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)))

fun colorToHsv(color: Int): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
