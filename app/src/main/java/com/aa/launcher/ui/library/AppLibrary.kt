package com.aa.launcher.ui.library

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aa.launcher.LauncherApp
import com.aa.launcher.R
import com.aa.launcher.core.AppCategory
import com.aa.launcher.core.AppEntry
import com.aa.launcher.core.AppLock
import com.aa.launcher.core.SearchText
import com.aa.launcher.ui.common.AppIconImage
import com.aa.launcher.ui.common.GlassSearchField
import com.aa.launcher.ui.common.NotificationBadge
import com.aa.launcher.ui.common.haptic
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.home.ContextMenuState
import com.aa.launcher.ui.home.LauncherViewModel
import com.aa.launcher.ui.home.LocalApps
import com.aa.launcher.ui.home.LocalBadges
import com.aa.launcher.ui.home.MenuTarget
import com.aa.launcher.ui.home.launcherItemGestures
import com.aa.launcher.ui.theme.LabelShadow
import com.aa.launcher.ui.theme.LocalLauncherSettings
import kotlinx.coroutines.launch

private data class LibraryGroup(val title: String, val apps: List<AppEntry>, val locked: Boolean = false, val bigOnly: Boolean = false)

/** App ライブラリ (ホーム画面の右端): カテゴリ別の自動フォルダと一覧/検索。 */
@Composable
fun AppLibraryPage(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(vm.libraryListMode) {
        if (vm.libraryListMode) runCatching { focus.requestFocus() } else {
            query = ""
            focusManager.clearFocus()
        }
    }
    Column(modifier.padding(top = statusTop + 10.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                GlassSearchField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (!vm.libraryListMode) vm.libraryListMode = true
                    },
                    placeholder = stringResource(R.string.app_library),
                    focusRequester = focus,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!vm.libraryListMode) {
                    // タップで一覧モードへ
                    Box(Modifier.matchParentSize().clip(RoundedCornerShape(22.dp)).clickable { vm.libraryListMode = true })
                }
            }
            if (vm.libraryListMode) {
                Text(
                    stringResource(R.string.action_cancel),
                    color = Color.White,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(start = 12.dp).clickable { vm.libraryListMode = false },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (vm.libraryListMode) LibraryList(vm, query, Modifier.weight(1f)) else LibraryGrid(vm, Modifier.weight(1f))
    }
}

@Composable
private fun rememberGroups(vm: LauncherViewModel): List<LibraryGroup> {
    val apps = LocalApps.current
    val settings = LocalLauncherSettings.current
    val usage by LauncherApp.instance.usageTracker.version.collectAsState()
    val suggestionsTitle = stringResource(R.string.library_suggestions)
    val recentTitle = stringResource(R.string.library_recently_added)
    val hiddenTitle = stringResource(R.string.library_hidden)
    val categoryTitles = AppCategory.entries.associateWith { stringResource(it.label) }
    return remember(apps, settings.hiddenApps, settings.librarySuggestions, settings.libraryRecentlyAdded, usage) {
        val visible = apps.values.filter { it.id !in settings.hiddenApps }
        buildList {
            if (settings.librarySuggestions && visible.isNotEmpty()) {
                add(LibraryGroup(suggestionsTitle, LauncherApp.instance.usageTracker.suggestions(visible, 4), bigOnly = true))
            }
            if (settings.libraryRecentlyAdded && visible.isNotEmpty()) {
                add(LibraryGroup(recentTitle, visible.filter { !it.isSystem }.ifEmpty { visible }.sortedByDescending { it.firstInstallTime }.take(8)))
            }
            AppCategory.entries.forEach { cat ->
                val list = visible.filter { it.category == cat }
                if (list.isNotEmpty()) add(LibraryGroup(categoryTitles.getValue(cat), list))
            }
            val hidden = apps.values.filter { it.id in settings.hiddenApps }
            if (hidden.isNotEmpty()) add(LibraryGroup(hiddenTitle, hidden, locked = settings.requireAuthForHidden))
        }
    }
}

@Composable
private fun LibraryGrid(vm: LauncherViewModel, modifier: Modifier) {
    val groups = rememberGroups(vm)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val context = LocalContext.current
    val activity = context as Activity
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = navBottom + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(groups, key = { it.title }) { group ->
            CategoryBox(
                vm = vm,
                group = group,
                onOpenFolder = { bounds ->
                    val open = { vm.openVirtualFolder(group.title, group.apps.map { it.id }, bounds) }
                    if (group.locked) AppLock.authenticate(activity, context.getString(R.string.auth_view_hidden)) { open() } else open()
                },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun CategoryBox(vm: LauncherViewModel, group: LibraryGroup, onOpenFolder: (Rect?) -> Unit) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .liquidGlass(RoundedCornerShape(30.dp), strength = 0.9f)
                .onGloballyPositioned { coords = it }
                .padding(12.dp),
        ) {
            val cell = (maxWidth - 12.dp) / 2
            if (group.locked) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).clickable { onOpenFolder(coords?.boundsInRoot()) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(cell * 0.6f))
                }
            } else {
                val big = if (group.bigOnly || group.apps.size <= 4) group.apps.take(4) else group.apps.take(3)
                big.forEachIndexed { i, entry ->
                    val x = if (i % 2 == 0) 0.dp else cell + 12.dp
                    val y = if (i < 2) 0.dp else cell + 12.dp
                    LibraryIcon(vm, entry, cell, Modifier.offset(x, y))
                }
                if (!group.bigOnly && group.apps.size > 4) {
                    val mini = (cell - 6.dp) / 2
                    Box(
                        Modifier
                            .offset(cell + 12.dp, cell + 12.dp)
                            .size(cell)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenFolder(coords?.boundsInRoot()) },
                    ) {
                        group.apps.drop(3).take(4).forEachIndexed { i, entry ->
                            AppIconImage(
                                entry.id,
                                mini,
                                Modifier.offset(if (i % 2 == 0) 0.dp else mini + 6.dp, if (i < 2) 0.dp else mini + 6.dp),
                                allowShadow = false,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            group.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = LabelShadow),
        )
    }
}

@Composable
private fun LibraryIcon(vm: LauncherViewModel, entry: AppEntry, size: Dp, modifier: Modifier) {
    val context = LocalContext.current
    val activity = context as Activity
    val view = LocalView.current
    val settings = LocalLauncherSettings.current
    val badges = LocalBadges.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier
            .size(size)
            .onGloballyPositioned { coords = it }
            .launcherItemGestures(
                key = entry.id,
                editMode = false,
                onTap = { vm.launchApp(activity, entry.id, coords?.boundsInRoot()) },
                onLongPress = {
                    view.haptic(settings.haptics, HapticFeedbackConstants.LONG_PRESS)
                    coords?.let { vm.contextMenu = ContextMenuState(MenuTarget.LibraryApp(entry.id), it.boundsInRoot()) }
                },
                onDragStart = {},
            ),
    ) {
        AppIconImage(entry.id, size, workBadge = entry.isWorkProfile)
        if (settings.showBadgesInAppLibrary) {
            NotificationBadge(badges[entry.key.packageKey] ?: 0, Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp))
        }
    }
}

@Composable
private fun LibraryList(vm: LauncherViewModel, query: String, modifier: Modifier) {
    val apps = LocalApps.current
    val settings = LocalLauncherSettings.current
    val context = LocalContext.current
    val activity = context as Activity
    val view = LocalView.current
    val visible = remember(apps, settings.hiddenApps) { apps.values.filter { it.id !in settings.hiddenApps }.sortedBy { SearchText.normalize(it.label) } }
    val normalizedQuery = SearchText.normalize(query.trim())
    val filtered = remember(visible, normalizedQuery) {
        if (normalizedQuery.isEmpty()) visible
        else visible.map { it to SearchText.score(it.searchLabel, normalizedQuery) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }
    val sections = remember(filtered, normalizedQuery) {
        if (normalizedQuery.isNotEmpty()) {
            listOf("" to filtered)
        } else {
            filtered.groupBy { SearchText.sectionOf(it.label) }.toList().sortedBy { SearchText.sectionOrder(it.first) }
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sectionIndex = remember(sections) {
        var index = 0
        sections.map { (title, list) -> (title to index).also { index += 1 + list.size } }
    }
    Box(modifier.fillMaxWidth().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .liquidGlass(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), strength = 1.3f),
            contentPadding = PaddingValues(top = 8.dp, bottom = navBottom + 24.dp, end = 18.dp),
        ) {
            sections.forEach { (title, list) ->
                item(key = "h:$title") {
                    if (title.isNotEmpty()) {
                        Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp))
                    }
                }
                items(list, key = { "a:${it.id}" }) { entry ->
                    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords = it }
                            .launcherItemGestures(
                                key = entry.id,
                                editMode = false,
                                onTap = {
                                    if (query.isNotBlank()) LauncherApp.instance.usageTracker.addRecentSearch(query)
                                    vm.launchApp(activity, entry.id, coords?.boundsInRoot())
                                },
                                onLongPress = {
                                    view.haptic(settings.haptics, HapticFeedbackConstants.LONG_PRESS)
                                    coords?.let { vm.contextMenu = ContextMenuState(MenuTarget.LibraryApp(entry.id), it.boundsInRoot()) }
                                },
                                onDragStart = {},
                            )
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconImage(entry.id, 42.dp, workBadge = entry.isWorkProfile, allowShadow = false)
                        Spacer(Modifier.width(14.dp))
                        Text(entry.label, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (normalizedQuery.isEmpty() && sections.size > 1) {
            // 右端の索引 (なぞってジャンプ)
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .fillMaxHeight(0.85f)
                    .width(20.dp)
                    .pointerInput(sectionIndex) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            fun jump(y: Float) {
                                val i = ((y / size.height) * sectionIndex.size).toInt().coerceIn(0, sectionIndex.size - 1)
                                scope.launch { listState.scrollToItem(sectionIndex[i].second) }
                                view.haptic(settings.haptics, HapticFeedbackConstants.CLOCK_TICK)
                            }
                            jump(down.position.y)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                jump(change.position.y)
                                change.consume()
                            }
                        }
                    },
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                sectionIndex.forEach { (title, _) ->
                    Text(title, color = Color(0xFF0A84FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
