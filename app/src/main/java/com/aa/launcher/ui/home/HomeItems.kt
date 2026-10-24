package com.aa.launcher.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.aa.launcher.core.AppItem
import com.aa.launcher.core.ComponentKey
import com.aa.launcher.core.FolderItem
import com.aa.launcher.core.HomeItem
import com.aa.launcher.core.SquircleShape
import com.aa.launcher.core.WidgetItem
import com.aa.launcher.ui.common.AppIconImage
import com.aa.launcher.ui.common.MinusBadge
import com.aa.launcher.ui.common.NotificationBadge
import com.aa.launcher.ui.common.rememberIconSpec
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.theme.LabelShadow
import com.aa.launcher.ui.theme.LocalLauncherSettings
import com.aa.launcher.ui.today.WidgetContent
import com.aa.launcher.ui.today.widgetLabel
import kotlin.math.abs
import kotlin.math.min

/** ホーム画面アイテムからの操作の受け口。 */
class ItemCallbacks(
    val onTap: (item: HomeItem, bounds: Rect) -> Unit,
    val onLongPress: (item: HomeItem, bounds: Rect) -> Unit,
    val onDragStart: (item: HomeItem, pointer: Offset, grab: Offset, size: Size, iconSize: Dp, showLabel: Boolean) -> Unit,
    val onRemove: (item: HomeItem) -> Unit,
    val onDown: (Long) -> Unit,
)

fun computeIconSize(cellWidth: Dp, cellHeight: Dp, scale: Float, showLabel: Boolean): Dp {
    val labelSpace = if (showLabel) 22.dp else 0.dp
    val byWidth = cellWidth * 0.64f
    val byHeight = (cellHeight - labelSpace) * 0.78f
    val base = if (byWidth < byHeight) byWidth else byHeight
    val scaled = base * scale
    val max = min(cellWidth.value * 0.9f, (cellHeight - labelSpace).value * 0.95f).dp
    return if (scaled > max) max else scaled
}

@Composable
fun jiggleRotation(seed: String): Float {
    val transition = rememberInfiniteTransition(label = "jiggle")
    val duration = remember(seed) { 115 + abs(seed.hashCode() % 45) }
    val start = remember(seed) { if (seed.hashCode() % 2 == 0) -1.5f else 1.5f }
    val rotation by transition.animateFloat(
        initialValue = start,
        targetValue = -start,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Reverse),
        label = "rotation",
    )
    return rotation
}

/** グリッド上のセル (操作可能)。 */
@Composable
fun ItemCell(
    item: HomeItem,
    width: Dp,
    height: Dp,
    iconSize: Dp,
    showLabel: Boolean,
    ghost: Boolean,
    editMode: Boolean,
    mergeHighlight: Boolean,
    callbacks: ItemCallbacks,
    removable: Boolean = true,
) {
    val settings = LocalLauncherSettings.current
    var cellCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var iconCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val jiggle = editMode && settings.wiggleInEditMode && !settings.reduceMotion && !ghost
    val rotation = if (jiggle) jiggleRotation(item.id) else 0f
    val mergeScale by animateFloatAsState(if (mergeHighlight) 1.12f else 1f, label = "merge")

    fun bounds(): Rect? = (iconCoords ?: cellCoords)?.takeIf { it.isAttached }?.boundsInRoot()

    Box(
        Modifier
            .size(width, height)
            .graphicsLayer {
                alpha = if (ghost) 0f else 1f
                rotationZ = rotation
                scaleX = mergeScale
                scaleY = mergeScale
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { cellCoords = it }
                .launcherItemGestures(
                    key = item.id,
                    editMode = editMode,
                    enabled = !ghost,
                    onDown = callbacks.onDown,
                    onTap = { bounds()?.let { callbacks.onTap(item, it) } },
                    onLongPress = { bounds()?.let { callbacks.onLongPress(item, it) } },
                    onDragStart = { local ->
                        val c = cellCoords
                        if (c != null && c.isAttached) {
                            callbacks.onDragStart(item, c.localToRoot(local), local, c.size.toSize(), iconSize, showLabel)
                        }
                    },
                ),
        ) {
            ItemVisual(
                item = item,
                width = width,
                height = height,
                iconSize = iconSize,
                showLabel = showLabel,
                mergeHighlight = mergeHighlight,
                onIconPositioned = { iconCoords = it },
            )
        }
        if (editMode && removable && !ghost && !settings.lockLayout) {
            val (x, y) = minusOffset(item, width, height, iconSize, showLabel)
            MinusBadge(onClick = { callbacks.onRemove(item) }, modifier = Modifier.offset(x, y))
        }
    }
}

private fun minusOffset(item: HomeItem, width: Dp, height: Dp, iconSize: Dp, showLabel: Boolean): Pair<Dp, Dp> {
    if (item is WidgetItem) return 2.dp to 2.dp
    val contentHeight = iconSize + if (showLabel) 22.dp else 0.dp
    val top = (height - contentHeight) / 2
    val left = (width - iconSize) / 2
    val x = left - 8.dp
    val y = top - 8.dp
    return (if (x < 0.dp) 0.dp else x) to (if (y < 0.dp) 0.dp else y)
}

/** アイテムの見た目のみ (ドラッグ中の表示やメニューでも使う)。 */
@Composable
fun ItemVisual(
    item: HomeItem,
    width: Dp,
    height: Dp,
    iconSize: Dp,
    showLabel: Boolean,
    mergeHighlight: Boolean = false,
    interactiveWidget: Boolean = true,
    onIconPositioned: (LayoutCoordinates) -> Unit = {},
) {
    when (item) {
        is AppItem -> AppVisual(item.app, iconSize, showLabel, mergeHighlight, onIconPositioned)
        is FolderItem -> FolderVisual(item, iconSize, showLabel, mergeHighlight, onIconPositioned)
        is WidgetItem -> WidgetVisual(item, width, height, showLabel, interactiveWidget)
    }
}

@Composable
fun HomeLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.White) {
    val s = LocalLauncherSettings.current
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = (12f * s.labelScale).sp,
            shadow = if (color == Color.White) LabelShadow else null,
            textAlign = TextAlign.Center,
            fontWeight = if (s.boldText) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun AppVisual(
    appId: String,
    iconSize: Dp,
    showLabel: Boolean,
    mergeHighlight: Boolean = false,
    onIconPositioned: (LayoutCoordinates) -> Unit = {},
    labelColor: Color = Color.White,
) {
    val apps = LocalApps.current
    val badges = LocalBadges.current
    val entry = apps[appId]
    val badge = ComponentKey.parse(appId)?.let { badges[it.packageKey] } ?: 0
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(iconSize)) {
            if (mergeHighlight) {
                Box(Modifier.size(iconSize).graphicsLayer { scaleX = 1.22f; scaleY = 1.22f }.liquidGlass(SquircleShape))
            }
            AppIconImage(
                appId = appId,
                size = iconSize,
                modifier = Modifier.onGloballyPositioned(onIconPositioned),
                workBadge = entry?.isWorkProfile == true,
            )
            NotificationBadge(badge, Modifier.align(Alignment.TopEnd).offset(7.dp, (-7).dp))
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            HomeLabel(entry?.label.orEmpty(), Modifier.width(iconSize * 1.45f).padding(horizontal = 2.dp), labelColor)
        }
    }
}

@Composable
fun FolderIcon(folder: FolderItem, iconSize: Dp, modifier: Modifier = Modifier) {
    val spec = rememberIconSpec()
    val pad = iconSize * 0.13f
    val gap = iconSize * 0.07f
    val mini = (iconSize - pad * 2 - gap * 2) / 3
    Box(modifier.size(iconSize).liquidGlass(SquircleShape, strength = 0.9f)) {
        folder.apps.take(9).forEachIndexed { i, appId ->
            val col = i % 3
            val row = i / 3
            AppIconImage(
                appId = appId,
                size = mini,
                spec = spec,
                allowShadow = false,
                modifier = Modifier.offset(pad + (mini + gap) * col, pad + (mini + gap) * row),
            )
        }
    }
}

@Composable
fun FolderVisual(
    folder: FolderItem,
    iconSize: Dp,
    showLabel: Boolean,
    mergeHighlight: Boolean = false,
    onIconPositioned: (LayoutCoordinates) -> Unit = {},
) {
    val badges = LocalBadges.current
    val badge = folder.apps.sumOf { id -> ComponentKey.parse(id)?.let { badges[it.packageKey] } ?: 0 }
    val scale by animateFloatAsState(if (mergeHighlight) 1.1f else 1f, label = "folderMerge")
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(iconSize).graphicsLayer { scaleX = scale; scaleY = scale }) {
            FolderIcon(folder, iconSize, Modifier.onGloballyPositioned(onIconPositioned))
            NotificationBadge(badge, Modifier.align(Alignment.TopEnd).offset(7.dp, (-7).dp))
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            HomeLabel(folder.name, Modifier.width(iconSize * 1.45f).padding(horizontal = 2.dp))
        }
    }
}

@Composable
fun WidgetVisual(item: WidgetItem, width: Dp, height: Dp, showLabel: Boolean, interactive: Boolean) {
    val labelSpace = if (showLabel) 20.dp else 0.dp
    Column(Modifier.size(width, height), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = if (showLabel) 2.dp else 8.dp),
        ) {
            WidgetContent(item = item, modifier = Modifier.fillMaxSize(), interactive = interactive)
        }
        if (showLabel) {
            Box(Modifier.height(labelSpace), contentAlignment = Alignment.TopCenter) {
                HomeLabel(widgetLabel(item))
            }
        }
    }
}

fun IntSize.isPositive() = width > 0 && height > 0
