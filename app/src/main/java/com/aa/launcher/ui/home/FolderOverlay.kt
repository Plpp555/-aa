package com.aa.launcher.ui.home

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aa.launcher.core.AppItem
import com.aa.launcher.core.LayoutOps
import com.aa.launcher.core.newId
import com.aa.launcher.ui.common.haptic
import com.aa.launcher.ui.glass.LocalScreenSize
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.theme.LabelShadow
import com.aa.launcher.ui.theme.LocalLauncherSettings

private const val FOLDER_ROWS = 3

/** フォルダを開いた状態 (iOS と同じく背景をぼかし、3x3 のページで表示)。 */
@Composable
fun FolderOverlay(vm: LauncherViewModel) {
    val open = vm.openFolder
    val last = remember { mutableStateOf<OpenFolder?>(null) }
    if (open != null) last.value = open
    val shown = open ?: last.value
    val screen = LocalScreenSize.current
    val origin = shown?.origin
    val transformOrigin = if (origin != null && screen.width > 1) {
        TransformOrigin(origin.center.x / screen.width, origin.center.y / screen.height)
    } else {
        TransformOrigin.Center
    }

    AnimatedVisibility(visible = open != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.openFolder = null },
        )
    }
    AnimatedVisibility(
        visible = open != null,
        enter = fadeIn() + scaleIn(initialScale = 0.25f, transformOrigin = transformOrigin),
        exit = fadeOut() + scaleOut(targetScale = 0.25f, transformOrigin = transformOrigin),
    ) {
        if (shown != null) FolderContent(vm, shown)
    }
}

@Composable
private fun FolderContent(vm: LauncherViewModel, folder: OpenFolder) {
    val settings = LocalLauncherSettings.current
    val layout = vm.layout.value
    val real = folder.folderId?.let { id -> layout?.let { LayoutOps.findFolder(it, id) } }
    val apps = if (real != null) vm.previewFolder(real.id, real.apps) else folder.apps
    val title = real?.name ?: folder.title
    val columns = settings.folderColumns.coerceIn(3, 4)
    val perPage = columns * FOLDER_ROWS
    val pageCount = ((apps.size + perPage - 1) / perPage).coerceAtLeast(1)
    val pagerState = rememberPagerState { pageCount }
    val context = LocalContext.current
    val activity = context as Activity
    val view = LocalView.current
    val editable = real != null && !settings.lockLayout

    val callbacks = remember(vm, real?.id) {
        ItemCallbacks(
            onTap = { item, bounds ->
                val app = (item as? AppItem)?.app
                if (app != null && !vm.editMode) {
                    vm.openFolder = null
                    vm.launchApp(activity, app, bounds)
                }
            },
            onLongPress = longPress@{ item, bounds ->
                val app = (item as? AppItem)?.app ?: return@longPress
                view.haptic(settings.haptics, HapticFeedbackConstants.LONG_PRESS)
                vm.contextMenu = ContextMenuState(
                    if (real != null) MenuTarget.FolderApp(real.id, app) else MenuTarget.LibraryApp(app),
                    bounds,
                )
            },
            onDragStart = { item, pointer, grab, size, iconSize, showLabel ->
                if (real != null && editable) {
                    view.haptic(settings.haptics, HapticFeedbackConstants.LONG_PRESS)
                    vm.startDrag(item, DragOrigin.Folder(real.id), pointer, grab, size, iconSize, showLabel)
                }
            },
            onRemove = { item ->
                val app = (item as? AppItem)?.app
                if (app != null && real != null) vm.removeAppFromFolder(real.id, app)
            },
            onDown = { vm.lastItemDownUptime = it },
        )
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FolderTitle(
                title = title,
                editable = editable && (vm.editMode || folder.focusTitle),
                focus = folder.focusTitle,
                onRename = { name -> real?.let { vm.renameFolder(it.id, name) } },
            )
            Spacer(Modifier.height(18.dp))
            BoxWithConstraints(
                Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth(0.84f)
                    .aspectRatio(columns / FOLDER_ROWS.toFloat() * 0.92f)
                    .liquidGlass(RoundedCornerShape(38.dp), strength = 1.1f)
                    .onGloballyPositioned { vm.drag.folderBounds = it.boundsInRoot() }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                val padding = 14.dp
                val cellW = (maxWidth - padding * 2) / columns
                val cellH = (maxHeight - padding * 2 - 14.dp) / FOLDER_ROWS
                val iconSize = computeIconSize(cellW, cellH, settings.effectiveIconScale, true)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    userScrollEnabled = !vm.drag.active,
                ) { page ->
                    val slice = apps.drop(page * perPage).take(perPage)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned {
                                if (page == pagerState.currentPage && real != null) {
                                    val density = it.size.width / columns.toFloat()
                                    vm.drag.folderGeometry = FolderGeometry(
                                        origin = it.positionInRoot(),
                                        cellWidth = density,
                                        cellHeight = (it.size.height - 14 * context.resources.displayMetrics.density) / FOLDER_ROWS,
                                        columns = columns,
                                        firstIndex = page * perPage,
                                        count = (apps.size).coerceAtLeast(0),
                                    )
                                }
                            },
                    ) {
                        slice.forEachIndexed { i, appId ->
                            key(appId) {
                                val col = i % columns
                                val row = i / columns
                                val target = with(LocalDensity.current) { IntOffset((cellW * col).roundToPx(), (cellH * row).roundToPx()) }
                                val animated by animateIntOffsetAsState(target, spring(), label = "folderCell")
                                val item = remember(appId) { AppItem(id = "folder:$appId:${newId()}", app = appId) }
                                Box(Modifier.offset { animated }) {
                                    ItemCell(
                                        item = item,
                                        width = cellW,
                                        height = cellH,
                                        iconSize = iconSize,
                                        showLabel = true,
                                        ghost = (vm.drag.payload?.item as? AppItem)?.app == appId,
                                        editMode = vm.editMode && editable,
                                        mergeHighlight = false,
                                        callbacks = callbacks,
                                        removable = real != null,
                                    )
                                }
                            }
                        }
                    }
                }
                if (pageCount > 1) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        repeat(pageCount) { i ->
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (i == pagerState.currentPage) 0.95f else 0.35f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTitle(title: String, editable: Boolean, focus: Boolean, onRename: (String) -> Unit) {
    val style = TextStyle(
        color = Color.White,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        shadow = LabelShadow,
    )
    if (!editable) {
        androidx.compose.material3.Text(title, style = style, modifier = Modifier.padding(horizontal = 24.dp))
        return
    }
    var text by remember(title) { mutableStateOf(title) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) runCatching { focusRequester.requestFocus() } }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onRename(text) }),
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .liquidGlass(RoundedCornerShape(18.dp), strength = 0.8f)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .focusRequester(focusRequester)
            .onGloballyPositioned { },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) { inner() }
        },
    )
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(600)
        if (text.isNotBlank() && text != title) onRename(text)
    }
}
