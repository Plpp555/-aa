package com.aa.launcher.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aa.launcher.R
import com.aa.launcher.core.WidgetItem
import com.aa.launcher.core.WidgetSize
import com.aa.launcher.core.WidgetTarget
import com.aa.launcher.ui.common.MinusBadge
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.home.ContextMenuState
import com.aa.launcher.ui.home.LauncherViewModel
import com.aa.launcher.ui.home.MenuTarget
import com.aa.launcher.ui.home.launcherItemGestures
import com.aa.launcher.ui.theme.LabelShadow
import com.aa.launcher.ui.theme.LocalLauncherSettings

/** 今日の表示 (ホーム画面の左端): 日付とウィジェットの一覧。 */
@Composable
fun TodayPage(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val layout by vm.layout.collectAsStateWithLifecycle()
    val settings = LocalLauncherSettings.current
    val widgets = layout?.today.orEmpty()
    val rows = remember(widgets) { groupRows(widgets) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val editing = vm.todayEditMode
    val now = rememberNow(60_000L)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusTop + 12.dp, bottom = navBottom + 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (settings.todayShowDate) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        formatDate(now, "EEEE"),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(shadow = LabelShadow),
                    )
                    Text(
                        formatDate(now, "MMMMd"),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        style = androidx.compose.ui.text.TextStyle(shadow = LabelShadow),
                    )
                }
            }
        }
        items(rows, key = { row -> row.joinToString { it.id } }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    val weightModifier = if (item.size == WidgetSize.SMALL) Modifier.weight(1f) else Modifier.fillMaxWidth()
                    TodayWidget(vm, item, editing, weightModifier)
                }
                if (row.size == 1 && row[0].size == WidgetSize.SMALL) Spacer(Modifier.weight(1f))
            }
        }
        item(key = "edit") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (editing) {
                    PillButton(stringResource(R.string.add_widget)) { vm.widgetPicker = WidgetTarget.Today }
                    Spacer(Modifier.width(12.dp))
                    PillButton(stringResource(R.string.done)) { vm.todayEditMode = false }
                } else {
                    PillButton(stringResource(R.string.edit)) { vm.todayEditMode = true }
                }
            }
        }
    }
}

private fun groupRows(widgets: List<WidgetItem>): List<List<WidgetItem>> = buildList {
    var pending: WidgetItem? = null
    for (w in widgets) {
        if (w.size == WidgetSize.SMALL) {
            val p = pending
            if (p == null) pending = w else {
                add(listOf(p, w))
                pending = null
            }
        } else {
            pending?.let { add(listOf(it)) }
            pending = null
            add(listOf(w))
        }
    }
    pending?.let { add(listOf(it)) }
}

@Composable
private fun TodayWidget(vm: LauncherViewModel, item: WidgetItem, editing: Boolean, modifier: Modifier) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val sizeModifier = when (item.size) {
        WidgetSize.SMALL -> Modifier.aspectRatio(1f)
        WidgetSize.MEDIUM -> Modifier.height(170.dp)
        WidgetSize.LARGE -> Modifier.height(356.dp)
    }
    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(sizeModifier)
                .onGloballyPositioned { coords = it }
                .launcherItemGestures(
                    key = item.id,
                    editMode = false,
                    onTap = {},
                    onLongPress = {
                        coords?.takeIf { it.isAttached }?.let { c ->
                            vm.contextMenu = ContextMenuState(MenuTarget.Widget(item, inToday = true), c.boundsInRoot())
                        }
                    },
                    onDragStart = {},
                ),
        ) {
            WidgetContent(item, Modifier.fillMaxWidth().then(sizeModifier), interactive = !editing)
        }
        if (editing) {
            MinusBadge(onClick = { vm.removeItem(item.id) }, modifier = Modifier.offset((-6).dp, (-6).dp))
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                ReorderButton(Icons.Rounded.KeyboardArrowUp) { vm.moveTodayWidget(item.id, -1) }
                Spacer(Modifier.height(6.dp))
                ReorderButton(Icons.Rounded.KeyboardArrowDown) { vm.moveTodayWidget(item.id, 1) }
            }
        }
    }
}

@Composable
private fun ReorderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .liquidGlass(CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
