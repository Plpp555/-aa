package com.aa.launcher.ui.home

import android.app.Activity
import android.content.pm.ShortcutInfo
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aa.launcher.LauncherApp
import com.aa.launcher.R
import com.aa.launcher.core.AppLock
import com.aa.launcher.core.ComponentKey
import com.aa.launcher.core.LayoutOps
import com.aa.launcher.core.SystemActions
import com.aa.launcher.core.WidgetKind
import com.aa.launcher.core.WidgetSize
import com.aa.launcher.ui.common.AppIconImage
import com.aa.launcher.ui.common.onGlass
import com.aa.launcher.ui.common.rememberPlainIconSpec
import com.aa.launcher.ui.common.sheetDivider
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.today.WidgetContent
import com.aa.launcher.ui.theme.LocalLauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class MenuEntry(
    val label: String,
    val icon: ImageVector? = null,
    val bitmap: ImageBitmap? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** 長押しで出る触覚タッチメニュー (クイックアクション)。 */
@Composable
fun ContextMenuOverlay(vm: LauncherViewModel) {
    val state = vm.contextMenu
    val last = remember { mutableStateOf<ContextMenuState?>(null) }
    if (state != null) last.value = state
    val shown = state ?: last.value
    AnimatedVisibility(visible = state != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.contextMenu = null },
        ) {
            if (shown != null) MenuContent(vm, shown)
        }
    }
}

@Composable
private fun MenuContent(vm: LauncherViewModel, state: ContextMenuState) {
    val bounds = state.bounds
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val entries = rememberEntries(vm, state)

    // 持ち上がったアイテムのプレビュー
    val w = with(density) { bounds.width.toDp() }
    val h = with(density) { bounds.height.toDp() }
    Box(
        Modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(w, h),
    ) {
        when (val t = state.target) {
            is MenuTarget.HomeApp -> AppIconImage(t.item.app, w)
            is MenuTarget.FolderApp -> AppIconImage(t.appId, w)
            is MenuTarget.LibraryApp -> AppIconImage(t.appId, w)
            is MenuTarget.Folder -> FolderIcon(t.item, w)
            is MenuTarget.Widget -> WidgetContent(t.item, Modifier.fillMaxSize(), interactive = false)
        }
    }

    val above = remember(bounds) { mutableStateOf(false) }
    Layout(
        content = {
            AnimatedVisibility(
                visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                enter = fadeIn() + scaleIn(initialScale = 0.6f, transformOrigin = TransformOrigin(0.5f, if (above.value) 1f else 0f)),
                exit = fadeOut() + scaleOut(),
            ) {
                MenuPanel(entries)
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val margin = 16.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val top = statusTop.roundToPx() + margin
        val bottom = constraints.maxHeight - navBottom.roundToPx() - margin
        val fitsBelow = bounds.bottom + gap + placeable.height <= bottom
        above.value = !fitsBelow
        val y = if (fitsBelow) {
            bounds.bottom.roundToInt() + gap
        } else {
            (bounds.top.roundToInt() - gap - placeable.height).coerceAtLeast(top)
        }
        val x = if (bounds.center.x < constraints.maxWidth / 2f) {
            bounds.left.roundToInt().coerceIn(margin, (constraints.maxWidth - margin - placeable.width).coerceAtLeast(margin))
        } else {
            (bounds.right.roundToInt() - placeable.width).coerceIn(margin, (constraints.maxWidth - margin - placeable.width).coerceAtLeast(margin))
        }
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
    }
}

@Composable
private fun MenuPanel(entries: List<MenuEntry?>) {
    Column(
        Modifier
            .width(260.dp)
            .liquidGlass(RoundedCornerShape(24.dp), strength = 1.7f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(vertical = 4.dp),
    ) {
        entries.forEachIndexed { i, entry ->
            if (entry == null) {
                if (i > 0) HorizontalDivider(thickness = 6.dp, color = sheetDivider().copy(alpha = 0.08f))
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = entry.onClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = if (entry.destructive) Color(0xFFFF453A) else onGlass()
                    Text(
                        entry.label,
                        color = color,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    when {
                        entry.bitmap != null -> Image(entry.bitmap, null, Modifier.size(22.dp))
                        entry.icon != null -> Icon(entry.icon, null, tint = color, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberEntries(vm: LauncherViewModel, state: ContextMenuState): List<MenuEntry?> {
    val context = LocalContext.current
    val activity = context as Activity
    val settings = LocalLauncherSettings.current
    val app = LauncherApp.instance
    val target = state.target
    val appId = when (target) {
        is MenuTarget.HomeApp -> target.item.app
        is MenuTarget.FolderApp -> target.appId
        is MenuTarget.LibraryApp -> target.appId
        else -> null
    }
    val spec = rememberPlainIconSpec()
    val shortcuts by produceState<List<Pair<ShortcutInfo, ImageBitmap?>>>(emptyList(), appId) {
        val key = appId?.let { ComponentKey.parse(it) } ?: return@produceState
        // App ショートカットは Android 7.1 以降
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            value = withContext(Dispatchers.IO) {
                app.appRepository.shortcuts(key).map { it to app.iconRepository.loadShortcut(it, spec) }
            }
        }
    }
    val close = { vm.contextMenu = null }
    val strEdit = stringResource(R.string.menu_edit_home)
    val strShare = stringResource(R.string.menu_share_app)
    val strInfo = stringResource(R.string.menu_app_info)
    val strRemoveApp = stringResource(R.string.menu_remove_app)
    val strDeleteApp = stringResource(R.string.delete_app)
    val strLock = stringResource(R.string.menu_require_auth)
    val strUnlock = stringResource(R.string.menu_dont_require_auth)
    val strHide = stringResource(R.string.menu_hide_and_require)
    val strUnhide = stringResource(R.string.menu_unhide)
    val strAddHome = stringResource(R.string.menu_add_to_home)
    val strRemoveFolderApp = stringResource(R.string.menu_remove_from_folder)
    val strRename = stringResource(R.string.menu_rename)
    val strRemoveFolder = stringResource(R.string.menu_remove_folder)
    val strEditWidget = stringResource(R.string.menu_edit_widget)
    val strRemoveWidget = stringResource(R.string.menu_remove_widget)
    val sizeLabels = WidgetSize.entries.associateWith { stringResource(it.label) }
    val authTitle = stringResource(R.string.auth_change_lock)

    return buildList {
        if (appId != null) {
            val label = vm.appsById.value[appId]?.label.orEmpty()
            val key = ComponentKey.parse(appId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                shortcuts.forEach { (info, bmp) ->
                    add(MenuEntry(info.shortLabel?.toString() ?: info.longLabel?.toString().orEmpty(), bitmap = bmp) {
                        close()
                        app.appRepository.startShortcut(info, null)
                    })
                }
            }
            if (shortcuts.isNotEmpty()) add(null)
            if (target !is MenuTarget.LibraryApp && !settings.lockLayout) {
                add(MenuEntry(strEdit, Icons.Rounded.Apps) { vm.enterEditMode() })
            }
            if (target is MenuTarget.LibraryApp && vm.layout.value?.let { appId in LayoutOps.allAppKeys(it) } == false && appId !in settings.hiddenApps) {
                add(MenuEntry(strAddHome, Icons.Rounded.AddCircle) { close(); vm.addAppToHome(appId) })
            }
            if (key != null) {
                add(MenuEntry(strShare, Icons.Rounded.Share) { close(); SystemActions.shareApp(context, key.packageName, label) })
            }
            val locked = appId in settings.lockedApps
            if (appId !in settings.hiddenApps) {
                add(
                    MenuEntry(if (locked) strUnlock else strLock, if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock) {
                        close()
                        if (locked) AppLock.authenticate(activity, authTitle) { vm.toggleLocked(appId) } else vm.toggleLocked(appId)
                    },
                )
                add(MenuEntry(strHide, Icons.Rounded.VisibilityOff) {
                    close()
                    AppLock.authenticate(activity, authTitle) { vm.hideApp(appId) }
                })
            } else {
                add(MenuEntry(strUnhide, Icons.Rounded.VisibilityOff) {
                    close()
                    AppLock.authenticate(activity, authTitle) { vm.unhideApp(appId) }
                })
            }
            if (key != null) {
                add(MenuEntry(strInfo, Icons.Rounded.Info) { close(); app.appRepository.openAppInfo(key) })
            }
            when (target) {
                is MenuTarget.HomeApp -> add(MenuEntry(strRemoveApp, Icons.Rounded.RemoveCircle, destructive = true) {
                    close()
                    vm.actionSheet = ActionSheetState(
                        title = context.getString(R.string.remove_app_title, label),
                        message = context.getString(R.string.remove_app_message),
                        actions = listOf(
                            SheetAction(strDeleteApp, destructive = true) { vm.uninstall(appId) },
                            SheetAction(context.getString(R.string.remove_from_home)) { vm.removeItem(target.item.id) },
                        ),
                    )
                })
                is MenuTarget.FolderApp -> {
                    add(MenuEntry(strRemoveFolderApp, Icons.Rounded.RemoveCircle) { close(); vm.removeAppFromFolder(target.folderId, appId) })
                    add(MenuEntry(strDeleteApp, Icons.Rounded.Delete, destructive = true) { close(); vm.uninstall(appId) })
                }
                else -> add(MenuEntry(strDeleteApp, Icons.Rounded.Delete, destructive = true) { close(); vm.uninstall(appId) })
            }
        }
        when (target) {
            is MenuTarget.Folder -> {
                add(MenuEntry(strRename, Icons.Rounded.Edit) { vm.openFolder(target.item, state.bounds, focusTitle = true) })
                if (!settings.lockLayout) add(MenuEntry(strEdit, Icons.Rounded.Apps) { vm.enterEditMode() })
                add(MenuEntry(strRemoveFolder, Icons.Rounded.RemoveCircle, destructive = true) {
                    close()
                    vm.actionSheet = ActionSheetState(
                        title = context.getString(R.string.remove_folder_title, target.item.name),
                        message = context.getString(R.string.remove_folder_message),
                        actions = listOf(SheetAction(context.getString(R.string.remove_from_home), destructive = true) { vm.removeItem(target.item.id) }),
                    )
                })
            }
            is MenuTarget.Widget -> {
                val item = target.item
                val canEdit = item.kind == WidgetKind.NOTE ||
                    (item.kind == WidgetKind.APP_WIDGET && vm.widgetHost?.info(item.appWidgetId)?.configure != null)
                if (canEdit) {
                    add(MenuEntry(strEditWidget, Icons.Rounded.Edit) {
                        close()
                        if (item.kind == WidgetKind.NOTE) vm.noteEditing = item else vm.widgetHost?.reconfigure(item.appWidgetId)
                    })
                }
                WidgetSize.entries.filter { it != item.size }.forEach { size ->
                    add(MenuEntry(sizeLabels.getValue(size), Icons.Rounded.AspectRatio) {
                        close()
                        vm.updateWidget(item.copy(size = size))
                    })
                }
                if (!target.inToday && !settings.lockLayout) add(MenuEntry(strEdit, Icons.Rounded.Widgets) { vm.enterEditMode() })
                add(MenuEntry(strRemoveWidget, Icons.Rounded.RemoveCircle, destructive = true) {
                    close()
                    vm.removeItem(item.id)
                })
            }
            else -> Unit
        }
    }
}
