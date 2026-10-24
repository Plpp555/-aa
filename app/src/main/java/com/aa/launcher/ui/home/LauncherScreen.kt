package com.aa.launcher.ui.home

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aa.launcher.R
import com.aa.launcher.core.AppItem
import com.aa.launcher.core.FolderItem
import com.aa.launcher.core.HomeItem
import com.aa.launcher.core.HomeLayout
import com.aa.launcher.core.HomePage
import com.aa.launcher.core.LayoutOps
import com.aa.launcher.core.LocalIconVersion
import com.aa.launcher.core.WallpaperState
import com.aa.launcher.core.WidgetItem
import com.aa.launcher.core.WidgetTarget
import com.aa.launcher.settings.GestureAction
import com.aa.launcher.settings.GestureType
import com.aa.launcher.settings.LauncherSettings
import com.aa.launcher.settings.PageEffect
import com.aa.launcher.ui.common.ActionSheet
import com.aa.launcher.ui.common.LocalWallpaperColor
import com.aa.launcher.ui.common.haptic
import com.aa.launcher.ui.common.onGlass
import com.aa.launcher.ui.controlcenter.ControlCenterOverlay
import com.aa.launcher.ui.glass.GlassParams
import com.aa.launcher.ui.glass.LocalBackdrop
import com.aa.launcher.ui.glass.LocalGlassParams
import com.aa.launcher.ui.glass.LocalScreenSize
import com.aa.launcher.ui.glass.blurCompat
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.library.AppLibraryPage
import com.aa.launcher.ui.onboarding.OnboardingOverlay
import com.aa.launcher.ui.spotlight.SpotlightOverlay
import com.aa.launcher.ui.theme.LauncherTheme
import com.aa.launcher.ui.theme.LocalIsDark
import com.aa.launcher.ui.today.NoteEditorDialog
import com.aa.launcher.ui.today.TodayPage
import com.aa.launcher.ui.today.WidgetPickerSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun LauncherScreen(vm: LauncherViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val apps by vm.appsById.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val iconVersion by vm.app.iconRepository.version.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LauncherTheme(settings) {
        val dark = LocalIsDark.current
        var screen by remember { mutableStateOf(IntSize(1, 1)) }
        val glass = GlassParams(
            dark = dark,
            opacity = settings.glassOpacity,
            reduceTransparency = settings.reduceTransparency,
            increaseContrast = settings.increaseContrast,
            wallpaperTint = Color(wallpaper.averageColor),
        )
        CompositionLocalProvider(
            LocalBackdrop provides wallpaper.backdrop,
            LocalScreenSize provides screen,
            LocalGlassParams provides glass,
            LocalWallpaperColor provides wallpaper.averageColor,
            LocalApps provides apps,
            LocalBadges provides badges,
            LocalIconVersion provides iconVersion,
        ) {
            BackHandler(enabled = true) { vm.onBack() }
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        screen = it
                        vm.drag.screenWidth = it.width.toFloat()
                        vm.drag.edgeWidth = 28f * context.resources.displayMetrics.density
                    }
                    .dragTracking(vm) { vm.endDrag(context) },
            ) {
                WallpaperLayer(wallpaper, settings, dark)
                val blur by animateDpAsState(overlayBlur(vm, settings), label = "contentBlur")
                Box(Modifier.fillMaxSize().blurCompat(blur)) {
                    layout?.let { HomeContent(vm, it, settings) }
                }
                Overlays(vm, settings)
                DragLayer(vm)
            }
        }
    }
}

private fun overlayBlur(vm: LauncherViewModel, s: LauncherSettings) = when {
    s.reduceTransparency -> 0.dp
    vm.openFolder != null -> if (s.folderBlur) 24.dp else 0.dp
    vm.controlCenterOpen -> 28.dp
    vm.spotlightOpen -> 22.dp
    vm.contextMenu != null -> 16.dp
    vm.widgetPicker != null || vm.customizeOpen || vm.editPagesOpen -> 12.dp
    else -> 0.dp
}

/** ドラッグ開始後はルートでポインタを追跡する (アイテムが再配置されても途切れない)。 */
private fun Modifier.dragTracking(vm: LauncherViewModel, onEnd: () -> Unit): Modifier = pointerInput(vm) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (!vm.drag.active) continue
            val change = event.changes.firstOrNull() ?: continue
            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                onEnd()
            } else {
                vm.dragMove(change.position)
            }
            event.changes.forEach { it.consume() }
        }
    }
}

@Composable
private fun WallpaperLayer(state: WallpaperState, settings: LauncherSettings, dark: Boolean) {
    val image = if (settings.blurHomeWallpaper) state.backdrop?.heavy ?: state.image else state.image
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
    if (dark && settings.dimWallpaperInDark) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
    }
}

private fun buildPages(layout: HomeLayout, settings: LauncherSettings, dragging: Boolean): List<PageModel> = buildList {
    if (settings.showTodayView) add(PageModel.Today)
    layout.pages.filter { !it.hidden }.forEach { add(PageModel.Home(it)) }
    if (dragging) add(PageModel.Home(HomePage(id = EXTRA_PAGE_ID), isExtra = true))
    if (settings.showAppLibrary) add(PageModel.Library)
}

@Composable
private fun HomeContent(vm: LauncherViewModel, layout: HomeLayout, settings: LauncherSettings) {
    val context = LocalContext.current
    val activity = context as Activity
    val view = androidx.compose.ui.platform.LocalView.current
    val density = LocalDensity.current
    val pages = remember(layout, settings.showTodayView, settings.showAppLibrary, vm.drag.active) {
        buildPages(layout, settings, vm.drag.active)
    }
    val pagesState = rememberUpdatedState(pages)
    val firstHome = pages.indexOfFirst { it is PageModel.Home }.coerceAtLeast(0)
    val lastHome = pages.indexOfLast { it is PageModel.Home && !it.isExtra }.coerceAtLeast(firstHome)
    val pagerState = rememberPagerState(initialPage = firstHome) { pagesState.value.size }
    val scope = rememberCoroutineScope()

    // 現在のページ (ドラッグの当たり判定用)
    SideEffect {
        vm.drag.currentPageId = (pages.getOrNull(pagerState.currentPage) as? PageModel.Home)?.page?.id
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            val list = pagesState.value
            val target = when (event) {
                LauncherEvent.ScrollToFirstHome -> list.indexOfFirst { it is PageModel.Home }
                LauncherEvent.ScrollToToday -> list.indexOfFirst { it is PageModel.Today }
                LauncherEvent.ScrollToLibrary -> list.indexOfFirst { it is PageModel.Library }
                is LauncherEvent.ScrollBy -> (pagerState.currentPage + event.delta).coerceIn(0, list.size - 1)
            }
            if (target >= 0) {
                if (settings.reduceMotion) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
            }
        }
    }

    // ドラッグ中に画面端で待つとページ移動
    val edge = vm.drag.edge
    LaunchedEffect(edge, vm.drag.active) {
        if (edge == 0 || !vm.drag.active) return@LaunchedEffect
        delay(600)
        while (true) {
            val list = pagesState.value
            val next = pagerState.currentPage + edge
            val ok = next in list.indices && list[next] is PageModel.Home
            if (ok) pagerState.animateScrollToPage(next)
            delay(900)
        }
    }

    val callbacks = remember(vm, activity) {
        ItemCallbacks(
            onTap = { item, bounds ->
                when (item) {
                    is AppItem -> if (!vm.editMode) vm.launchApp(activity, item.app, bounds)
                    is FolderItem -> vm.openFolder(item, bounds)
                    is WidgetItem -> Unit
                }
            },
            onLongPress = { item, bounds ->
                view.haptic(vm.settings.value.haptics, HapticFeedbackConstants.LONG_PRESS)
                val l = vm.layout.value
                val inDock = l != null && LayoutOps.locate(l, item.id) is LayoutOps.Location.Dock
                val target = when (item) {
                    is AppItem -> MenuTarget.HomeApp(item, inDock)
                    is FolderItem -> MenuTarget.Folder(item)
                    is WidgetItem -> MenuTarget.Widget(item, inToday = false)
                }
                vm.contextMenu = ContextMenuState(target, bounds)
            },
            onDragStart = dragStart@{ item, pointer, grab, size, iconSize, showLabel ->
                val l = vm.layout.value ?: return@dragStart
                val origin = when (val loc = LayoutOps.locate(l, item.id)) {
                    is LayoutOps.Location.Dock -> DragOrigin.Dock
                    is LayoutOps.Location.Page -> DragOrigin.Page(l.pages[loc.page].id)
                    null -> return@dragStart
                }
                view.haptic(vm.settings.value.haptics, HapticFeedbackConstants.LONG_PRESS)
                vm.startDrag(item, origin, pointer, grab, size, iconSize, showLabel)
            },
            onRemove = { item -> vm.actionSheet = removeSheet(context, vm, item) },
            onDown = { vm.lastItemDownUptime = it },
        )
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val gridWidth = maxWidth - 24.dp
        val cellW = gridWidth / settings.gridColumns
        val dockIconSize = computeIconSize(cellW, cellW * 1.25f, settings.effectiveIconScale, false)
            .let { if (it > 72.dp) 72.dp else it }
        val dockH = if (settings.showDock) dockHeight(dockIconSize, settings.dockLabels) else 0.dp
        val indicatorH = 40.dp
        val bottomReserved = navBottom + 8.dp + dockH + indicatorH

        val onHome = pages.getOrNull(pagerState.currentPage) is PageModel.Home
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .homeVerticalSwipe(
                    enabled = onHome && !vm.drag.active && !vm.hasOverlay,
                    settings = settings,
                    screenWidth = { vm.drag.screenWidth },
                    screenHeight = { with(density) { maxHeight.toPx() } },
                    onGesture = { vm.performGesture(it, context) },
                ),
            beyondViewportPageCount = 1,
            userScrollEnabled = !vm.drag.active,
            key = { index -> pagesState.value.getOrNull(index)?.key ?: index },
        ) { index ->
            val page = pages.getOrNull(index) ?: return@HorizontalPager
            // 見えているページ (スクロール中は隣のページも) だけ定期処理を動かす
            val active by remember(index) {
                derivedStateOf { abs(pagerState.currentPage + pagerState.currentPageOffsetFraction - index) < 1f }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .pageEffect(pagerState, index, settings.pageEffect),
            ) {
                CompositionLocalProvider(LocalContentActive provides active) {
                when (page) {
                    PageModel.Today -> TodayPage(vm, Modifier.fillMaxSize())
                    PageModel.Library -> AppLibraryPage(vm, Modifier.fillMaxSize())
                    is PageModel.Home -> Box(
                        Modifier
                            .fillMaxSize()
                            .homeBackgroundGestures(
                                vm = vm,
                                doubleTapEnabled = settings.doubleTapAction != GestureAction.NONE,
                                onTap = { if (vm.editMode) vm.exitEditMode() },
                                onLongPress = {
                                    view.haptic(settings.haptics)
                                    vm.enterEditMode()
                                },
                                onDoubleTap = { vm.performGesture(GestureType.DOUBLE_TAP, context) },
                            ),
                    ) {
                        HomeGridPage(
                            page = page.page,
                            vm = vm,
                            callbacks = callbacks,
                            modifier = Modifier.padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = statusTop + if (vm.editMode) 52.dp else 16.dp,
                                bottom = bottomReserved,
                            ),
                        )
                    }
                }
                }
            }
        }

        // ドックとインジケータ (Today / App ライブラリではフェードアウト)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = navBottom + 8.dp)
                .graphicsLayer {
                    val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    val a = when {
                        pos < firstHome -> 1f - (firstHome - pos)
                        pos > lastHome && !vm.drag.active -> 1f - (pos - lastHome)
                        else -> 1f
                    }.coerceIn(0f, 1f)
                    alpha = a
                    translationY = (1f - a) * 120.dp.toPx()
                },
        ) {
            val homeIndices = pages.indices.filter { pages[it] is PageModel.Home }
            val currentHome = homeIndices.indexOf(pagerState.currentPage).coerceAtLeast(0)
            PageIndicator(
                pageCount = homeIndices.size,
                current = currentHome,
                showSearch = settings.showSearchOnHome && !vm.editMode,
                onSearch = { vm.spotlightOpen = true },
                onDots = { if (vm.editMode) vm.editPagesOpen = true },
                onSelect = { i -> homeIndices.getOrNull(i)?.let { scope.launch { pagerState.animateScrollToPage(it) } } },
            )
            if (settings.showDock) {
                Dock(items = layout.dock, vm = vm, callbacks = callbacks, iconSize = dockIconSize)
            }
        }

        // 編集モードの上部バー
        AnimatedVisibility(
            visible = vm.editMode,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            EditModeBar(
                onAddWidget = {
                    val page = pages.getOrNull(pagerState.currentPage) as? PageModel.Home
                    vm.widgetPicker = WidgetTarget.HomePageTarget(page?.page?.id?.takeIf { it != EXTRA_PAGE_ID })
                },
                onCustomize = { vm.customizeOpen = true },
                onEditPages = { vm.editPagesOpen = true },
                onDone = { vm.exitEditMode() },
            )
        }
    }
}

/** ページめくり効果 (スライド / 奥行き / キューブ / フェード)。 */
private fun Modifier.pageEffect(state: PagerState, index: Int, effect: PageEffect): Modifier =
    // スライドでも各ページを独立したレイヤーにして、スクロール中はページの再描画なしに位置だけ動かす
    if (effect == PageEffect.SLIDE) graphicsLayer() else graphicsLayer {
        val position = -((state.currentPage - index) + state.currentPageOffsetFraction)
        when (effect) {
            PageEffect.DEPTH -> {
                if (position > 0f && position <= 1f) {
                    alpha = 1f - position
                    translationX = size.width * -position
                    val scale = 0.75f + 0.25f * (1f - abs(position))
                    scaleX = scale
                    scaleY = scale
                }
            }
            PageEffect.CUBE -> {
                cameraDistance = 12f * density
                transformOrigin = TransformOrigin(if (position < 0f) 1f else 0f, 0.5f)
                rotationY = 80f * position
            }
            PageEffect.FADE -> {
                alpha = (1f - abs(position)).coerceIn(0f, 1f)
                translationX = size.width * -position
            }
            PageEffect.SLIDE -> Unit
        }
        Unit
    }

@Composable
private fun EditModeBar(onAddWidget: () -> Unit, onCustomize: () -> Unit, onEditPages: () -> Unit, onDone: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .liquidGlass(CircleShape)
                .clip(CircleShape)
                .clickable { menu = !menu }
                .padding(horizontal = 18.dp, vertical = 9.dp),
        ) {
            Text(stringResource(R.string.edit), color = onGlass(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .liquidGlass(CircleShape, tint = Color(0xFF0A84FF), strength = 1.4f)
                .clip(CircleShape)
                .clickable(onClick = onDone)
                .padding(horizontal = 18.dp, vertical = 9.dp),
        ) {
            Text(stringResource(R.string.done), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        AnimatedVisibility(
            visible = menu,
            enter = fadeIn() + scaleIn(transformOrigin = TransformOrigin(0f, 0f)),
            exit = fadeOut() + scaleOut(transformOrigin = TransformOrigin(0f, 0f)),
            modifier = Modifier.offset(y = 48.dp),
        ) {
            Column(
                Modifier
                    .width(230.dp)
                    .liquidGlass(RoundedCornerShape(22.dp), strength = 1.6f)
                    .padding(vertical = 6.dp),
            ) {
                EditMenuRow(Icons.Rounded.Add, stringResource(R.string.add_widget)) { menu = false; onAddWidget() }
                EditMenuRow(Icons.Rounded.Palette, stringResource(R.string.customize)) { menu = false; onCustomize() }
                EditMenuRow(Icons.Rounded.ViewCarousel, stringResource(R.string.edit_pages)) { menu = false; onEditPages() }
            }
        }
    }
}

@Composable
private fun EditMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = onGlass(), fontSize = 16.sp)
        Icon(icon, null, tint = onGlass(), modifier = Modifier.size(20.dp))
    }
}

private fun removeSheet(context: android.content.Context, vm: LauncherViewModel, item: HomeItem): ActionSheetState = when (item) {
    is AppItem -> {
        val label = vm.appsById.value[item.app]?.label.orEmpty()
        ActionSheetState(
            title = context.getString(R.string.remove_app_title, label),
            message = context.getString(R.string.remove_app_message),
            actions = listOf(
                SheetAction(context.getString(R.string.delete_app), destructive = true) { vm.uninstall(item.app) },
                SheetAction(context.getString(R.string.remove_from_home)) { vm.removeItem(item.id) },
            ),
        )
    }
    is FolderItem -> ActionSheetState(
        title = context.getString(R.string.remove_folder_title, item.name),
        message = context.getString(R.string.remove_folder_message),
        actions = listOf(SheetAction(context.getString(R.string.remove_from_home), destructive = true) { vm.removeItem(item.id) }),
    )
    is WidgetItem -> ActionSheetState(
        title = context.getString(R.string.remove_widget_title),
        message = context.getString(R.string.remove_widget_message),
        actions = listOf(SheetAction(context.getString(R.string.remove), destructive = true) { vm.removeItem(item.id) }),
    )
}

@Composable
private fun Overlays(vm: LauncherViewModel, settings: LauncherSettings) {
    FolderOverlay(vm)
    ContextMenuOverlay(vm)
    SpotlightOverlay(vm, visible = vm.spotlightOpen)
    ControlCenterOverlay(vm, visible = vm.controlCenterOpen)
    WidgetPickerSheet(vm)
    CustomizeSheet(vm)
    EditPagesOverlay(vm)
    NoteEditorDialog(vm)
    val sheet = vm.actionSheet
    ActionSheet(
        visible = sheet != null,
        title = sheet?.title,
        message = sheet?.message,
        actions = sheet?.actions?.map { Triple(it.label, it.destructive, it.onClick) }.orEmpty(),
        onDismiss = { vm.actionSheet = null },
    )
    if (!settings.onboardingDone) OnboardingOverlay(vm)
}

@Composable
private fun DragLayer(vm: LauncherViewModel) {
    val payload = vm.drag.payload ?: return
    val density = LocalDensity.current
    val size = vm.drag.itemSize
    val topLeft = vm.drag.pointer - vm.drag.grabOffset
    val w = with(density) { size.width.toDp() }
    val h = with(density) { size.height.toDp() }
    Box(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
            .size(w, h)
            .graphicsLayer {
                scaleX = 1.1f
                scaleY = 1.1f
                alpha = 0.96f
                transformOrigin = TransformOrigin(
                    (vm.drag.grabOffset.x / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                    (vm.drag.grabOffset.y / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f),
                )
            },
    ) {
        ItemVisual(
            item = payload.item,
            width = w,
            height = h,
            iconSize = vm.drag.visualIconSize,
            showLabel = false,
            interactiveWidget = false,
        )
    }
}
