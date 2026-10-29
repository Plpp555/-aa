package com.aa.launcher.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
                // スクロール中は毎フレーム呼ばれるので、変化したときだけ保存する
                val origin = c.positionInRoot()
                val old = vm.drag.pages[page.id]
                val cellW = c.size.width / grid.columns.toFloat()
                val cellH = c.size.height / grid.rows.toFloat()
                if (old == null || old.origin != origin || old.cellWidth != cellW || old.cellHeight != cellH ||
                    old.columns != grid.columns || old.rows != grid.rows
                ) {
                    vm.drag.pages[page.id] = PageGeometry(origin, cellW, cellH, grid.columns, grid.rows)
                }
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
        val slotH = maxHeight
        preview.forEachIndexed { index, item ->
            key(item.id) {
                val target = with(density) { IntOffset((slotW * index).roundToPx(), 0) }
                val animated by animateIntOffsetAsState(target, spring(stiffness = Spring.StiffnessMediumLow), label = "dock")
                Box(Modifier.offset { animated }) {
                    ItemCell(
                        item = item,
                        width = slotW,
                        height = slotH,
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

/**
 * ホーム画面の縦スワイプ (下: Spotlight / 右上から下: コントロールセンター / 上: App ライブラリ等)。
 *
 * [enabled] は指を置いたときに評価する。条件によってこの修飾子を付け外ししないこと:
 * 祖先の pointerInput が外れると、Compose はその下にある子孫 (アイコン) の進行中のジェスチャーも取り消すため、
 * 長押しメニューが出た後に指を動かしてもドラッグへ移行できなくなる。
 */
fun Modifier.homeVerticalSwipe(
    enabled: () -> Boolean,
    settings: LauncherSettings,
    screenWidth: () -> Float,
    screenHeight: () -> Float,
    onGesture: (GestureType) -> Unit,
): Modifier = pointerInput(settings.swipeDownAction, settings.swipeUpAction, settings.swipeDownRightAction) {
    val threshold = 56.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // 無効な間 (他のページ・オーバーレイ表示中・ドラッグ中) はイベントを消費せずに見送る
        if (!enabled()) return@awaitEachGesture
        var total = 0f
        val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            total = over
        } ?: return@awaitEachGesture
        var fired = false
        fun fireIfNeeded() {
            if (fired || abs(total) <= threshold || !enabled()) return
            fired = true
            val type = if (total > 0) {
                val start = down.position
                val fromTopRight = start.y < screenHeight() * 0.18f && start.x > screenWidth() * 0.6f
                if (fromTopRight && settings.swipeDownRightAction != GestureAction.NONE) GestureType.SWIPE_DOWN_RIGHT else GestureType.SWIPE_DOWN
            } else {
                GestureType.SWIPE_UP
            }
            onGesture(type)
        }
        fireIfNeeded()
        verticalDrag(drag.id) { change ->
            total += change.positionChange().y
            change.consume()
            fireIfNeeded()
        }
    }
}
