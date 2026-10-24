package com.aa.launcher.ui.home

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aa.launcher.LauncherApp
import com.aa.launcher.R
import com.aa.launcher.core.AppEntry
import com.aa.launcher.core.AppItem
import com.aa.launcher.core.AppLock
import com.aa.launcher.core.ComponentKey
import com.aa.launcher.core.FolderItem
import com.aa.launcher.core.GridPacker
import com.aa.launcher.core.GridSpec
import com.aa.launcher.core.HomeItem
import com.aa.launcher.core.HomeLayout
import com.aa.launcher.core.LayoutOps
import com.aa.launcher.core.SystemActions
import com.aa.launcher.core.WidgetHostManager
import com.aa.launcher.core.WidgetItem
import com.aa.launcher.core.WidgetKind
import com.aa.launcher.core.WidgetSize
import com.aa.launcher.core.WidgetTarget
import com.aa.launcher.core.newId
import com.aa.launcher.service.NotificationState
import com.aa.launcher.settings.GestureAction
import com.aa.launcher.settings.GestureType
import com.aa.launcher.settings.LauncherSettings
import com.aa.launcher.settings.OpenAnimation
import com.aa.launcher.settings.SettingsActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val app = application as LauncherApp
    private val layoutRepo = app.layoutRepository

    val settings: StateFlow<LauncherSettings> = app.settingsRepository.settings
    val layout: StateFlow<HomeLayout?> = layoutRepo.layout
    val apps: StateFlow<List<AppEntry>> = app.appRepository.apps
    val appsById: StateFlow<Map<String, AppEntry>> =
        apps.map { list -> list.associateBy { it.id } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val wallpaper = app.wallpaperRepository.state
    val badges = NotificationState.badges
    val usageVersion = app.usageTracker.version

    val drag = DragState()

    var editMode by mutableStateOf(false)
        private set
    var todayEditMode by mutableStateOf(false)
    var openFolder by mutableStateOf<OpenFolder?>(null)
    var contextMenu by mutableStateOf<ContextMenuState?>(null)
    var spotlightOpen by mutableStateOf(false)
    var controlCenterOpen by mutableStateOf(false)
    var widgetPicker by mutableStateOf<WidgetTarget?>(null)
    var actionSheet by mutableStateOf<ActionSheetState?>(null)
    var customizeOpen by mutableStateOf(false)
    var editPagesOpen by mutableStateOf(false)
    var noteEditing by mutableStateOf<WidgetItem?>(null)
    var libraryListMode by mutableStateOf(false)

    /** Activity が設定する (AppWidget の追加に必要)。 */
    var widgetHost: WidgetHostManager? = null

    private val _events = MutableSharedFlow<LauncherEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LauncherEvent> = _events.asSharedFlow()

    private var targetJob: Job? = null
    private var mergeJob: Job? = null

    val grid: GridSpec get() = settings.value.let { GridSpec(it.gridColumns, it.gridRows, it.dockColumns) }

    val hasOverlay: Boolean
        get() = openFolder != null || contextMenu != null || spotlightOpen || controlCenterOpen ||
            widgetPicker != null || actionSheet != null || customizeOpen || editPagesOpen || noteEditing != null

    /** 開いているオーバーレイを閉じる。何か閉じたら true。 */
    fun closeOverlays(): Boolean {
        val had = hasOverlay
        openFolder = null
        contextMenu = null
        spotlightOpen = false
        controlCenterOpen = false
        widgetPicker = null
        actionSheet = null
        customizeOpen = false
        editPagesOpen = false
        noteEditing = null
        return had
    }

    fun onBack() {
        when {
            drag.active -> cancelDrag()
            actionSheet != null -> actionSheet = null
            contextMenu != null -> contextMenu = null
            noteEditing != null -> noteEditing = null
            widgetPicker != null -> widgetPicker = null
            customizeOpen -> customizeOpen = false
            editPagesOpen -> editPagesOpen = false
            openFolder != null -> openFolder = null
            spotlightOpen -> spotlightOpen = false
            controlCenterOpen -> controlCenterOpen = false
            libraryListMode -> libraryListMode = false
            todayEditMode -> todayEditMode = false
            editMode -> exitEditMode()
        }
    }

    fun onHomeIntent(alreadyOnHome: Boolean, context: Context) {
        val closed = closeOverlays()
        libraryListMode = false
        if (todayEditMode) todayEditMode = false
        if (editMode) {
            exitEditMode()
            return
        }
        if (!alreadyOnHome || closed) return
        performGesture(GestureType.HOME_BUTTON, context)
    }

    /** 他のアプリへ移ったとき。iOS と同様に編集モードも終え、揺れアニメーションを止める。 */
    fun onStop() {
        contextMenu = null
        spotlightOpen = false
        controlCenterOpen = false
        if (drag.active) cancelDrag()
        if (editMode) exitEditMode()
        todayEditMode = false
    }

    // ---- 編集モード ----------------------------------------------------------

    fun enterEditMode() {
        if (settings.value.lockLayout) return
        contextMenu = null
        editMode = true
    }

    fun exitEditMode() {
        editMode = false
        customizeOpen = false
        editPagesOpen = false
    }

    // ---- ジェスチャー ---------------------------------------------------------

    fun performGesture(type: GestureType, context: Context) {
        val s = settings.value
        when (s.gestureAction(type)) {
            GestureAction.NONE -> Unit
            GestureAction.SPOTLIGHT -> spotlightOpen = true
            GestureAction.NOTIFICATIONS -> SystemActions.expandNotifications(context)
            GestureAction.QUICK_SETTINGS -> SystemActions.expandQuickSettings(context)
            GestureAction.CONTROL_CENTER -> if (s.controlCenterEnabled) controlCenterOpen = true else SystemActions.expandQuickSettings(context)
            GestureAction.APP_LIBRARY -> _events.tryEmit(LauncherEvent.ScrollToLibrary)
            GestureAction.TODAY_VIEW -> _events.tryEmit(LauncherEvent.ScrollToToday)
            GestureAction.FIRST_PAGE -> _events.tryEmit(LauncherEvent.ScrollToFirstHome)
            GestureAction.EDIT_MODE -> enterEditMode()
            GestureAction.LOCK_SCREEN -> SystemActions.lockScreen(context)
            GestureAction.RECENTS -> SystemActions.openRecents(context)
            GestureAction.LAUNCHER_SETTINGS -> openSettings(context)
            GestureAction.LAUNCH_APP -> {
                val appId = s.gestureApps[type.name] ?: return
                val activity = context as? Activity ?: return
                launchApp(activity, appId, null)
            }
        }
    }

    fun openSettings(context: Context, route: String? = null) {
        val intent = Intent(context, SettingsActivity::class.java)
        if (route != null) {
            // 既存の設定タスクがあっても指定した画面から開き直す
            intent.putExtra(SettingsActivity.EXTRA_ROUTE, route)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        SystemActions.start(context, intent)
    }

    // ---- アプリ起動 -----------------------------------------------------------

    fun launchApp(activity: Activity, appId: String, bounds: Rect?) {
        val s = settings.value
        val key = ComponentKey.parse(appId) ?: return
        val run = {
            val rect = bounds?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
            if (app.appRepository.launch(key, rect, launchOptions(activity, rect, s))) app.usageTracker.recordLaunch(appId)
        }
        if (appId in s.lockedApps || appId in s.hiddenApps) {
            val label = appsById.value[appId]?.label ?: ""
            AppLock.authenticate(activity, activity.getString(R.string.auth_open_app, label)) { run() }
        } else {
            run()
        }
    }

    private fun launchOptions(activity: Activity, rect: android.graphics.Rect?, s: LauncherSettings): Bundle? {
        val anim = if (s.reduceMotion && s.openAnimation != OpenAnimation.NONE) OpenAnimation.FADE else s.openAnimation
        val view = activity.window.decorView
        return when (anim) {
            OpenAnimation.ZOOM -> rect?.let { ActivityOptions.makeScaleUpAnimation(view, it.left, it.top, it.width(), it.height()).toBundle() }
            OpenAnimation.REVEAL -> rect?.let { ActivityOptions.makeClipRevealAnimation(view, it.left, it.top, it.width(), it.height()).toBundle() }
            OpenAnimation.FADE -> ActivityOptions.makeCustomAnimation(activity, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
            OpenAnimation.NONE -> ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle()
            OpenAnimation.SYSTEM -> null
        }
    }

    // ---- フォルダ -------------------------------------------------------------

    fun openFolder(folder: FolderItem, origin: Rect?, focusTitle: Boolean = false) {
        contextMenu = null
        openFolder = OpenFolder(folder.id, folder.name, folder.apps, origin, focusTitle)
    }

    fun openVirtualFolder(title: String, apps: List<String>, origin: Rect?) {
        openFolder = OpenFolder(null, title, apps, origin)
    }

    fun renameFolder(folderId: String, name: String) {
        layoutRepo.update { l ->
            val f = LayoutOps.findFolder(l, folderId) ?: return@update l
            LayoutOps.replaceItem(l, folderId, f.copy(name = name.trim().ifEmpty { f.name }))
        }
    }

    // ---- 削除・非表示・ロック ---------------------------------------------------

    fun removeItem(itemId: String) {
        val item = layout.value?.let { LayoutOps.findItem(it, itemId) }
        if (item is WidgetItem && item.kind == WidgetKind.APP_WIDGET) widgetHost?.delete(item.appWidgetId)
        layoutRepo.update { LayoutOps.removeItem(it, itemId) }
    }

    fun removeAppFromFolder(folderId: String, appId: String) {
        layoutRepo.update { LayoutOps.removeFromFolder(it, folderId, appId) }
    }

    fun addAppToHome(appId: String) {
        layoutRepo.update { l ->
            if (appId in LayoutOps.allAppKeys(l)) l else LayoutOps.appendApp(l, appId, grid)
        }
    }

    fun toggleLocked(appId: String) {
        app.settingsRepository.update { s ->
            s.copy(lockedApps = if (appId in s.lockedApps) s.lockedApps - appId else s.lockedApps + appId)
        }
    }

    fun hideApp(appId: String) {
        app.settingsRepository.update { s -> s.copy(hiddenApps = s.hiddenApps + appId, lockedApps = s.lockedApps - appId) }
        layoutRepo.update { LayoutOps.removeAppKey(it, appId) }
    }

    fun unhideApp(appId: String) {
        app.settingsRepository.update { s -> s.copy(hiddenApps = s.hiddenApps - appId) }
    }

    fun uninstall(appId: String) {
        ComponentKey.parse(appId)?.let { app.appRepository.uninstall(it) }
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = app.settingsRepository.update(transform)

    // ---- ウィジェット ---------------------------------------------------------

    fun addWidget(item: WidgetItem, target: WidgetTarget) {
        layoutRepo.update { l ->
            when (target) {
                WidgetTarget.Today -> l.copy(today = listOf(item) + l.today)
                is WidgetTarget.HomePageTarget -> LayoutOps.insertIntoPage(l, target.pageId, 0, item)
            }
        }
    }

    fun addBuiltinWidget(kind: WidgetKind, size: WidgetSize, target: WidgetTarget) {
        addWidget(WidgetItem(newId(), kind, size), target)
        widgetPicker = null
    }

    fun addAppWidget(provider: android.appwidget.AppWidgetProviderInfo, size: WidgetSize, target: WidgetTarget) {
        val host = widgetHost ?: return
        widgetPicker = null
        host.beginAdd(provider) { id ->
            if (id != null) {
                addWidget(
                    WidgetItem(newId(), WidgetKind.APP_WIDGET, size, appWidgetId = id, provider = provider.provider.flattenToString()),
                    target,
                )
            }
        }
    }

    fun updateWidget(item: WidgetItem) {
        layoutRepo.update { LayoutOps.replaceItem(it, item.id, item) }
    }

    fun moveTodayWidget(id: String, delta: Int) {
        layoutRepo.update { l ->
            val list = l.today.toMutableList()
            val i = list.indexOfFirst { it.id == id }
            val j = i + delta
            if (i < 0 || j !in list.indices) return@update l
            val item = list.removeAt(i)
            list.add(j, item)
            l.copy(today = list)
        }
    }

    // ---- ページ ---------------------------------------------------------------

    fun setPageHidden(pageId: String, hidden: Boolean) {
        layoutRepo.update { l ->
            val visible = l.pages.count { !it.hidden }
            if (hidden && visible <= 1) return@update l
            l.copy(pages = l.pages.map { if (it.id == pageId) it.copy(hidden = hidden) else it })
        }
    }

    fun deletePage(pageId: String) {
        layoutRepo.update { l ->
            val page = l.pages.firstOrNull { it.id == pageId } ?: return@update l
            page.items.filterIsInstance<WidgetItem>().forEach { if (it.kind == WidgetKind.APP_WIDGET) widgetHost?.delete(it.appWidgetId) }
            l.copy(pages = l.pages.filter { it.id != pageId })
        }
    }

    // ---- ドラッグ & ドロップ -----------------------------------------------------

    /** アイテム上でポインタが押された時刻 (背景ジェスチャーとの重複防止)。 */
    var lastItemDownUptime: Long = -1L

    fun startDrag(item: HomeItem, origin: DragOrigin, pointer: Offset, grabOffset: Offset, size: Size, iconSize: Dp, showLabel: Boolean) {
        if (settings.value.lockLayout) return
        contextMenu = null
        if (!editMode) editMode = true
        if (origin !is DragOrigin.Folder) openFolder = null
        drag.reset()
        drag.visualIconSize = iconSize
        drag.visualShowLabel = showLabel
        // 指を動かすまでは元の位置を空けたままにする (隣のアイコンが詰めてこないように)
        val initial = initialTarget(item, origin)
        drag.target = initial
        drag.pendingTarget = initial
        drag.payload = DragPayload(item, origin)
        drag.pointer = pointer
        drag.grabOffset = grabOffset
        drag.itemSize = size
        dragMove(pointer)
    }

    private fun initialTarget(item: HomeItem, origin: DragOrigin): DropTarget? {
        val l = layout.value ?: return null
        return when (origin) {
            is DragOrigin.Page -> l.pages.firstOrNull { it.id == origin.pageId }?.items
                ?.indexOfFirst { it.id == item.id }?.takeIf { it >= 0 }
                ?.let { DropTarget.Page(origin.pageId, it) }
            DragOrigin.Dock -> l.dock.indexOfFirst { it.id == item.id }.takeIf { it >= 0 }?.let { DropTarget.Dock(it) }
            is DragOrigin.Folder -> {
                val app = (item as? AppItem)?.app ?: return null
                LayoutOps.findFolder(l, origin.folderId)?.apps?.indexOf(app)?.takeIf { it >= 0 }
                    ?.let { DropTarget.Folder(origin.folderId, it) }
            }
        }
    }

    fun cancelDrag() {
        targetJob?.cancel()
        mergeJob?.cancel()
        drag.reset()
    }

    /** 表示用: ドラッグ中のアイテムをターゲット位置に差し込んだページの並び。 */
    fun previewPageItems(pageId: String, items: List<HomeItem>): List<HomeItem> {
        val p = drag.payload ?: return items
        val base = items.filter { it.id != p.item.id }
        val t = drag.target
        return if (t is DropTarget.Page && t.pageId == pageId) {
            base.toMutableList().apply { add(t.index.coerceIn(0, size), p.item) }
        } else {
            base
        }
    }

    fun previewDock(items: List<HomeItem>): List<HomeItem> {
        val p = drag.payload ?: return items
        val base = items.filter { it.id != p.item.id }
        val t = drag.target
        return if (t is DropTarget.Dock && base.size < grid.dockSlots) {
            base.toMutableList().apply { add(t.index.coerceIn(0, size), p.item) }
        } else {
            base
        }
    }

    fun previewFolder(folderId: String, apps: List<String>): List<String> {
        val p = drag.payload ?: return apps
        val dragged = (p.item as? AppItem)?.app ?: return apps
        val base = apps.filter { it != dragged }
        val t = drag.target
        return if (t is DropTarget.Folder && t.folderId == folderId) {
            base.toMutableList().apply { add(t.index.coerceIn(0, size), dragged) }
        } else {
            base
        }
    }

    fun dragMove(pos: Offset) {
        val d = drag
        val p = d.payload ?: return
        d.pointer = pos
        val l = layout.value ?: return
        val s = settings.value
        val g = grid

        // フォルダ内での並べ替え / フォルダ外へ出す
        val folder = openFolder
        if (folder?.folderId != null && p.origin is DragOrigin.Folder) {
            if (d.folderBounds.inflate(d.edgeWidth * 0.4f).contains(pos)) {
                val geo = d.folderGeometry
                val index = if (geo != null) {
                    val col = floor((pos.x - geo.origin.x) / geo.cellWidth).toInt().coerceIn(0, geo.columns - 1)
                    val row = floor((pos.y - geo.origin.y) / geo.cellHeight).toInt().coerceAtLeast(0)
                    (geo.firstIndex + row * geo.columns + col).coerceIn(0, geo.count)
                } else {
                    0
                }
                setTarget(DropTarget.Folder(folder.folderId, index), immediate = false)
                setMergeCandidate(null)
                d.edge = 0
                return
            } else {
                openFolder = null
            }
        }

        // ドック
        if (s.showDock && p.item !is WidgetItem && d.dockBounds.contains(pos)) {
            d.edge = 0
            val dockItems = l.dock.filter { it.id != p.item.id }
            val n = dockItems.size
            if (n > 0) {
                val slotW = d.dockBounds.width / n
                val i = floor((pos.x - d.dockBounds.left) / slotW).toInt().coerceIn(0, n - 1)
                val center = d.dockBounds.left + slotW * (i + 0.5f)
                val target = dockItems[i]
                val canMerge = p.item is AppItem && (target is AppItem || target is FolderItem)
                if (canMerge && abs(pos.x - center) < slotW * 0.22f) {
                    setMergeCandidate(target.id)
                    return
                }
            }
            setMergeCandidate(null)
            if (n >= g.dockSlots) {
                setTarget(null, immediate = true)
            } else {
                val slotW = d.dockBounds.width / (n + 1)
                val index = floor((pos.x - d.dockBounds.left) / slotW).toInt().coerceIn(0, n)
                setTarget(DropTarget.Dock(index), immediate = false)
            }
            return
        }

        // ホーム画面のページ
        val pageId = d.currentPageId
        val geo = pageId?.let { d.pages[it] }
        if (pageId == null || geo == null) {
            setMergeCandidate(null)
            d.edge = 0
            return
        }
        d.edge = when {
            pos.x < d.edgeWidth -> -1
            pos.x > d.screenWidth - d.edgeWidth -> 1
            else -> 0
        }
        val pageItems = l.pages.firstOrNull { it.id == pageId }?.items.orEmpty()
        val base = pageItems.filter { it.id != p.item.id }
        val preview = previewPageItems(pageId, pageItems)
        val placements = GridPacker.pack(preview, g).first
        val local = pos - geo.origin
        val probe = if (p.item is WidgetItem) {
            pos - d.grabOffset - geo.origin + Offset(geo.cellWidth / 2f, geo.cellHeight / 2f)
        } else {
            local
        }
        val cx = floor(probe.x / geo.cellWidth).toInt().coerceIn(0, g.columns - 1)
        val cy = floor(probe.y / geo.cellHeight).toInt().coerceIn(0, g.rows - 1)
        val hovered = placements.firstOrNull { pl ->
            cx >= pl.cell.x && cx < pl.cell.x + pl.cell.w && cy >= pl.cell.y && cy < pl.cell.y + pl.cell.h
        }
        if (hovered != null && hovered.item.id == p.item.id) {
            // 自分自身の上: 現状維持
            setMergeCandidate(null)
            return
        }
        if (hovered != null) {
            val centerX = (hovered.cell.x + hovered.cell.w / 2f) * geo.cellWidth
            val centerY = (hovered.cell.y + hovered.cell.h / 2f) * geo.cellHeight
            val canMerge = p.item is AppItem && (hovered.item is AppItem || hovered.item is FolderItem)
            val near = abs(local.x - centerX) < geo.cellWidth * 0.26f && abs(local.y - centerY) < geo.cellHeight * 0.26f
            if (canMerge && near) {
                setMergeCandidate(hovered.item.id)
                return
            }
            setMergeCandidate(null)
            val baseIndex = base.indexOf(hovered.item)
            val after = local.x > centerX
            setTarget(DropTarget.Page(pageId, if (after) baseIndex + 1 else baseIndex), immediate = false)
        } else {
            setMergeCandidate(null)
            // 空きセル: 読み順でそれより前にあるアイテム数
            val basePlacements = GridPacker.pack(base, g).first
            val before = basePlacements.count { pl -> pl.cell.y < cy || (pl.cell.y == cy && pl.cell.x < cx) }
            setTarget(DropTarget.Page(pageId, before), immediate = false)
        }
    }

    private fun setTarget(target: DropTarget?, immediate: Boolean) {
        if (target == drag.pendingTarget && !immediate) return
        drag.pendingTarget = target
        targetJob?.cancel()
        if (immediate || drag.target == null) {
            drag.target = target
        } else {
            targetJob = viewModelScope.launch {
                delay(110)
                drag.target = target
            }
        }
    }

    private fun setMergeCandidate(id: String?) {
        if (id == drag.pendingMerge) return
        drag.pendingMerge = id
        mergeJob?.cancel()
        if (id == null) {
            drag.mergeTarget = null
        } else {
            mergeJob = viewModelScope.launch {
                delay(420)
                drag.mergeTarget = id
            }
        }
    }

    fun endDrag(context: Context) {
        val d = drag
        val p = d.payload ?: return
        targetJob?.cancel()
        mergeJob?.cancel()
        val target = d.pendingTarget ?: d.target
        val merge = d.mergeTarget
        val folderName = defaultFolderName(context, merge)
        layoutRepo.update { l ->
            val out = when (val o = p.origin) {
                is DragOrigin.Folder -> LayoutOps.removeFromFolder(l, o.folderId, (p.item as? AppItem)?.app ?: return@update l)
                else -> LayoutOps.removeItem(l, p.item.id)
            }
            when {
                merge != null -> LayoutOps.mergeInto(out, p.item, merge, folderName) ?: l
                target is DropTarget.Page -> LayoutOps.insertIntoPage(out, target.pageId, target.index, p.item)
                target is DropTarget.Dock -> {
                    if (out.dock.size >= grid.dockSlots) l else LayoutOps.insertIntoDock(out, target.index, p.item)
                }
                target is DropTarget.Folder -> {
                    val appId = (p.item as? AppItem)?.app ?: return@update l
                    LayoutOps.insertIntoFolder(out, target.folderId, appId, target.index)
                }
                else -> l
            }
        }
        if (merge != null) {
            // 新しくできたフォルダは iOS と同じく開いた状態にする
            val appId = (p.item as? AppItem)?.app
            val lay = layout.value
            val folder = lay?.let { LayoutOps.findItem(it, merge) as? FolderItem }
                ?: lay?.let { (it.pages.flatMap { page -> page.items } + it.dock).filterIsInstance<FolderItem>().firstOrNull { f -> appId in f.apps } }
            if (folder != null && openFolder == null) openFolder = OpenFolder(folder.id, folder.name, folder.apps, null)
        }
        d.reset()
    }

    private fun defaultFolderName(context: Context, targetId: String?): String {
        val l = layout.value ?: return context.getString(R.string.folder_default_name)
        val target = targetId?.let { LayoutOps.findItem(l, it) }
        val appId = (target as? AppItem)?.app ?: (drag.payload?.item as? AppItem)?.app
        val entry = appId?.let { appsById.value[it] } ?: return context.getString(R.string.folder_default_name)
        return context.getString(entry.category.label)
    }

    // ---- その他 ---------------------------------------------------------------

    fun visibleApps(): List<AppEntry> {
        val hidden = settings.value.hiddenApps
        return apps.value.filter { it.id !in hidden }
    }

    fun suggestions(count: Int): List<AppEntry> = app.usageTracker.suggestions(visibleApps(), count)
}
