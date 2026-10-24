package com.aa.launcher.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aa.launcher.R
import com.aa.launcher.core.GridPacker
import com.aa.launcher.core.GridSpec
import com.aa.launcher.core.HomeItem
import com.aa.launcher.core.HomePage
import com.aa.launcher.settings.DockStyle
import com.aa.launcher.settings.GestureAction
import com.aa.launcher.settings.GestureType
import com.aa.launcher.settings.LauncherSettings
import com.aa.launcher.ui.common.onGlass
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.theme.LocalIsDark
import com.aa.launcher.ui.theme.LocalLauncherSettings
import kotlin.math.abs

@Composable
fun HomeGridPage(
    page: HomePage,
    vm: LauncherViewModel,
    callbacks: ItemCallbacks,
    modifier: Modifier = Modifier,
) {
    val settings = LocalLauncherSettings.current
    val grid = GridSpec(settings.gridColumns, settings.gridRows, settings.dockColumns)
    val items = vm.previewPageItems(page.id, page.items)
    val placements = remember(items, grid) { GridPacker.pack(items, grid).first }
    val density = LocalDensity.current
    val draggingId = vm.drag.payload?.item?.id
    val merge = vm.drag.mergeTarget
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { c ->
                vm.drag.pages[page.id] = PageGeometry(
                    origin = c.positionInRoot(),
                    cellWidth = c.size.width / grid.columns.toFloat(),
                    cellHeight = c.size.height / grid.rows.toFloat(),
                    columns = grid.columns,
                    rows = grid.rows,
                )
            },
    ) {
        val cellW = maxWidth / grid.columns
        val cellH = maxHeight / grid.rows
        val showLabel = settings.effectiveShowLabels
        val iconSize = computeIconSize(cellW, cellH, settings.effectiveIconScale, showLabel)
        for (pl in placements) {
            key(pl.item.id) {
                val target = with(density) { IntOffset((cellW * pl.cell.x).roundToPx(), (cellH * pl.cell.y).roundToPx()) }
                val animated by animateIntOffsetAsState(
                    target,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow * settings.animationSpeed),
                    label = "cell",
                )
                Box(Modifier.offset { animated }) {
                    ItemCell(
                        item = pl.item,
                        width = cellW * pl.cell.w,
                        height = cellH * pl.cell.h,
                        iconSize = iconSize,
                        showLabel = showLabel,
                        ghost = pl.item.id == draggingId,
                        editMode = vm.editMode,
                        mergeHighlight = pl.item.id == merge,
                        callbacks = callbacks,
                    )
                }
            }
        }
    }
}

fun dockHeight(iconSize: Dp, labels: Boolean): Dp = iconSize + 30.dp + if (labels) 18.dp else 0.dp

@Composable
fun Dock(
    items: List<HomeItem>,
    vm: LauncherViewModel,
    callbacks: ItemCallbacks,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val settings = LocalLauncherSettings.current
    val dark = LocalIsDark.current
    val preview = vm.previewDock(items)
    val density = LocalDensity.current
    val shape = RoundedCornerShape(34.dp)
    val bg = when (settings.dockStyle) {
        DockStyle.GLASS -> Modifier.liquidGlass(shape)
        DockStyle.OPAQUE -> Modifier.clip(shape).background(if (dark) Color(0xF01C1C1E) else Color(0xF0F2F2F7))
        DockStyle.TRANSPARENT -> Modifier
    }
    val draggingId = vm.drag.payload?.item?.id
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(dockHeight(iconSize, settings.dockLabels))
            .then(bg)
            .onGloballyPositioned { vm.drag.dockBounds = it.boundsInRoot() },
    ) {
        val n = preview.size.coerceAtLeast(1)
        val slotW = maxWidth / n
        preview.forEachIndexed { index, item ->
            key(item.id) {
                val target = with(density) { IntOffset((slotW * index).roundToPx(), 0) }
                val animated by animateIntOffsetAsState(target, spring(stiffness = Spring.StiffnessMediumLow), label = "dock")
                Box(Modifier.offset { animated }) {
                    ItemCell(
                        item = item,
                        width = slotW,
                        height = maxHeight,
                        iconSize = iconSize,
                        showLabel = settings.dockLabels,
                        ghost = item.id == draggingId,
                        editMode = vm.editMode,
                        mergeHighlight = item.id == vm.drag.mergeTarget,
                        callbacks = callbacks,
                    )
                }
            }
        }
    }
}

/** ドックの上の「検索」ボタン、またはページインジケータ。 */
@Composable
fun PageIndicator(
    pageCount: Int,
    current: Int,
    showSearch: Boolean,
    onSearch: () -> Unit,
    onDots: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        if (showSearch) {
            Row(
                Modifier
                    .height(32.dp)
                    .liquidGlass(CircleShape, strength = 0.9f)
                    .clip(CircleShape)
                    .clickable(onClick = onSearch)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = onGlass(), modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(5.dp))
                Text(stringResource(R.string.search), color = onGlass(), fontSize = 14.sp)
            }
        } else if (pageCount > 1) {
            Row(
                Modifier
                    .height(26.dp)
                    .liquidGlass(CircleShape, strength = 0.7f)
                    .clip(CircleShape)
                    .clickable(onClick = onDots)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pageCount) { i ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (i == current) onGlass() else onGlass().copy(alpha = 0.35f))
                            .clickable { onSelect(i) },
                    )
                }
            }
        }
    }
}

/** ホーム画面の空き領域のジェスチャー: 長押し=編集、タップ=編集終了、ダブルタップ=設定の動作。 */
fun Modifier.homeBackgroundGestures(
    vm: LauncherViewModel,
    doubleTapEnabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
): Modifier = pointerInput(doubleTapEnabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (vm.lastItemDownUptime == down.uptimeMillis || down.isConsumed) return@awaitEachGesture
        var cancelled = false
        val up = withTimeoutOrNull(520L) {
            waitForUpOrCancellation().also { if (it == null) cancelled = true }
        }
        when {
            up != null -> {
                if (doubleTapEnabled) {
                    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown(requireUnconsumed = false) }
                    if (second != null) {
                        onDoubleTap()
                        waitForUpOrCancellation()
                    } else {
                        onTap()
                    }
                } else {
                    onTap()
                }
            }
            cancelled -> Unit
            else -> {
                if (!vm.drag.active && vm.contextMenu == null) onLongPress()
                waitForUpOrCancellation()
            }
        }
    }
}

/** ホーム画面の縦スワイプ (下: Spotlight / 右上から下: コントロールセンター / 上: App ライブラリ等)。 */
fun Modifier.homeVerticalSwipe(
    enabled: Boolean,
    settings: LauncherSettings,
    screenWidth: () -> Float,
    screenHeight: () -> Float,
    onGesture: (GestureType) -> Unit,
): Modifier = if (!enabled) this else pointerInput(settings.swipeDownAction, settings.swipeUpAction, settings.swipeDownRightAction) {
    var start = Offset.Zero
    var total = 0f
    var fired = false
    val threshold = 56.dp.toPx()
    detectVerticalDragGestures(
        onDragStart = {
            start = it
            total = 0f
            fired = false
        },
        onDragEnd = { fired = false },
        onDragCancel = { fired = false },
        onVerticalDrag = { change, dy ->
            total += dy
            if (!fired && abs(total) > threshold) {
                fired = true
                val type = if (total > 0) {
                    val fromTopRight = start.y < screenHeight() * 0.18f && start.x > screenWidth() * 0.6f
                    if (fromTopRight && settings.swipeDownRightAction != GestureAction.NONE) GestureType.SWIPE_DOWN_RIGHT else GestureType.SWIPE_DOWN
                } else {
                    GestureType.SWIPE_UP
                }
                onGesture(type)
            }
            change.consume()
        },
    )
}
