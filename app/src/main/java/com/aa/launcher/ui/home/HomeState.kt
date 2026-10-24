package com.aa.launcher.ui.home

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aa.launcher.core.AppEntry
import com.aa.launcher.core.AppItem
import com.aa.launcher.core.FolderItem
import com.aa.launcher.core.HomeItem
import com.aa.launcher.core.HomePage
import com.aa.launcher.core.WidgetItem

const val EXTRA_PAGE_ID = "__extra_page__"

val LocalApps = compositionLocalOf<Map<String, AppEntry>> { emptyMap() }
val LocalBadges = compositionLocalOf<Map<String, Int>> { emptyMap() }

sealed interface PageModel {
    val key: String

    data object Today : PageModel {
        override val key = "today"
    }

    data class Home(val page: HomePage, val isExtra: Boolean = false) : PageModel {
        override val key: String get() = page.id
    }

    data object Library : PageModel {
        override val key = "library"
    }
}

/** 開いているフォルダ。folderId が null の場合は App ライブラリのカテゴリなど読み取り専用の仮想フォルダ。 */
data class OpenFolder(
    val folderId: String?,
    val title: String,
    val apps: List<String>,
    val origin: Rect?,
    val focusTitle: Boolean = false,
)

sealed interface MenuTarget {
    data class HomeApp(val item: AppItem, val inDock: Boolean) : MenuTarget
    data class FolderApp(val folderId: String, val appId: String) : MenuTarget
    data class Folder(val item: FolderItem) : MenuTarget
    data class Widget(val item: WidgetItem, val inToday: Boolean) : MenuTarget
    data class LibraryApp(val appId: String) : MenuTarget
}

data class ContextMenuState(val target: MenuTarget, val bounds: Rect)

data class SheetAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

data class ActionSheetState(val title: String?, val message: String?, val actions: List<SheetAction>)

sealed interface DragOrigin {
    data class Page(val pageId: String) : DragOrigin
    data object Dock : DragOrigin
    data class Folder(val folderId: String) : DragOrigin
}

data class DragPayload(val item: HomeItem, val origin: DragOrigin)

sealed interface DropTarget {
    data class Page(val pageId: String, val index: Int) : DropTarget
    data class Dock(val index: Int) : DropTarget
    data class Folder(val folderId: String, val index: Int) : DropTarget
}

data class PageGeometry(val origin: Offset, val cellWidth: Float, val cellHeight: Float, val columns: Int, val rows: Int)

data class FolderGeometry(val origin: Offset, val cellWidth: Float, val cellHeight: Float, val columns: Int, val firstIndex: Int, val count: Int)

sealed interface LauncherEvent {
    data object ScrollToFirstHome : LauncherEvent
    data object ScrollToToday : LauncherEvent
    data object ScrollToLibrary : LauncherEvent
    data class ScrollBy(val delta: Int) : LauncherEvent
}

/** ドラッグ & ドロップの状態と、当たり判定用のジオメトリ。 */
class DragState {
    var payload by mutableStateOf<DragPayload?>(null)
        internal set
    var pointer by mutableStateOf(Offset.Zero)
        internal set
    var grabOffset by mutableStateOf(Offset.Zero)
        internal set
    var itemSize by mutableStateOf(Size.Zero)
        internal set
    var target by mutableStateOf<DropTarget?>(null)
        internal set
    var mergeTarget by mutableStateOf<String?>(null)
        internal set
    var edge by mutableIntStateOf(0)
        internal set

    /** ドラッグ中アイテムの見た目 (開始時のセルと同じ大きさで描く)。 */
    var visualIconSize: Dp = 60.dp
    var visualShowLabel: Boolean = true

    internal var pendingTarget: DropTarget? = null
    internal var pendingMerge: String? = null

    val pages = HashMap<String, PageGeometry>()
    var dockBounds: Rect = Rect.Zero
    var folderBounds: Rect = Rect.Zero
    var folderGeometry: FolderGeometry? = null
    var currentPageId: String? = null
    var screenWidth: Float = 0f
    var edgeWidth: Float = 60f

    val active: Boolean get() = payload != null

    internal fun reset() {
        payload = null
        target = null
        mergeTarget = null
        pendingTarget = null
        pendingMerge = null
        edge = 0
    }
}
