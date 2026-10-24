package com.aa.launcher.core

/** HomeLayout を変更する純粋関数群。 */
object LayoutOps {

    sealed interface Location {
        data class Page(val page: Int, val index: Int) : Location
        data class Dock(val index: Int) : Location
    }

    fun appKeysOf(item: HomeItem): List<String> = when (item) {
        is AppItem -> listOf(item.app)
        is FolderItem -> item.apps
        is WidgetItem -> emptyList()
    }

    fun allAppKeys(layout: HomeLayout): Set<String> {
        val set = HashSet<String>()
        layout.pages.forEach { p -> p.items.forEach { set += appKeysOf(it) } }
        layout.dock.forEach { set += appKeysOf(it) }
        return set
    }

    fun allWidgets(layout: HomeLayout): List<WidgetItem> =
        layout.pages.flatMap { it.items.filterIsInstance<WidgetItem>() } + layout.today

    fun locate(layout: HomeLayout, id: String): Location? {
        layout.pages.forEachIndexed { p, page ->
            val i = page.items.indexOfFirst { it.id == id }
            if (i >= 0) return Location.Page(p, i)
        }
        val d = layout.dock.indexOfFirst { it.id == id }
        return if (d >= 0) Location.Dock(d) else null
    }

    fun findItem(layout: HomeLayout, id: String): HomeItem? =
        layout.pages.firstNotNullOfOrNull { p -> p.items.firstOrNull { it.id == id } }
            ?: layout.dock.firstOrNull { it.id == id }
            ?: layout.today.firstOrNull { it.id == id }

    fun findFolder(layout: HomeLayout, id: String): FolderItem? = findItem(layout, id) as? FolderItem

    fun removeItem(layout: HomeLayout, id: String): HomeLayout = layout.copy(
        pages = layout.pages.map { p -> if (p.items.any { it.id == id }) p.copy(items = p.items.filter { it.id != id }) else p },
        dock = layout.dock.filter { it.id != id },
        today = layout.today.filter { it.id != id },
    )

    fun replaceItem(layout: HomeLayout, id: String, replacement: HomeItem): HomeLayout = layout.copy(
        pages = layout.pages.map { p -> p.copy(items = p.items.map { if (it.id == id) replacement else it }) },
        dock = layout.dock.map { if (it.id == id) replacement else it },
        today = layout.today.map { if (it.id == id && replacement is WidgetItem) replacement else it },
    )

    fun insertIntoPage(layout: HomeLayout, pageId: String?, index: Int, item: HomeItem): HomeLayout {
        val pageIndex = layout.pages.indexOfFirst { it.id == pageId }
        if (pageIndex < 0) {
            // 新しいページ
            return layout.copy(pages = layout.pages + HomePage(items = listOf(item)))
        }
        val page = layout.pages[pageIndex]
        val items = page.items.toMutableList().apply { add(index.coerceIn(0, size), item) }
        return layout.copy(pages = layout.pages.toMutableList().apply { set(pageIndex, page.copy(items = items)) })
    }

    fun insertIntoDock(layout: HomeLayout, index: Int, item: HomeItem): HomeLayout =
        layout.copy(dock = layout.dock.toMutableList().apply { add(index.coerceIn(0, size), item) })

    /** アプリをホーム画面・ドック・フォルダのすべてから取り除く。 */
    fun removeAppKey(layout: HomeLayout, appKey: String): HomeLayout {
        fun strip(items: List<HomeItem>): List<HomeItem> = items.mapNotNull { item ->
            when (item) {
                is AppItem -> if (item.app == appKey) null else item
                is FolderItem -> if (appKey in item.apps) item.copy(apps = item.apps.filter { it != appKey }) else item
                is WidgetItem -> item
            }
        }
        return layout.copy(pages = layout.pages.map { it.copy(items = strip(it.items)) }, dock = strip(layout.dock))
    }

    /** 空きのある最後のページにアプリを追加する (なければ新規ページ)。 */
    fun appendApp(layout: HomeLayout, appKey: String, grid: GridSpec): HomeLayout {
        val item = AppItem(newId(), appKey)
        val visible = layout.pages.indices.filter { !layout.pages[it].hidden }
        val last = visible.lastOrNull()
        if (last != null) {
            val page = layout.pages[last]
            if (GridPacker.fits(page.items + item, grid)) {
                return layout.copy(pages = layout.pages.toMutableList().apply { set(last, page.copy(items = page.items + item)) })
            }
        }
        return layout.copy(pages = layout.pages + HomePage(items = listOf(item)))
    }

    fun removeFromFolder(layout: HomeLayout, folderId: String, appKey: String): HomeLayout {
        val folder = findFolder(layout, folderId) ?: return layout
        return replaceItem(layout, folderId, folder.copy(apps = folder.apps.filter { it != appKey }))
    }

    fun insertIntoFolder(layout: HomeLayout, folderId: String, appKey: String, index: Int): HomeLayout {
        val folder = findFolder(layout, folderId) ?: return layout
        val apps = folder.apps.filter { it != appKey }.toMutableList().apply { add(index.coerceIn(0, size), appKey) }
        return replaceItem(layout, folderId, folder.copy(apps = apps))
    }

    /** アプリを別のアプリ/フォルダに重ねてフォルダを作る (または追加する)。 */
    fun mergeInto(layout: HomeLayout, dragged: HomeItem, targetId: String, folderName: String): HomeLayout? {
        val draggedApp = (dragged as? AppItem)?.app ?: return null
        val replacement = when (val target = findItem(layout, targetId)) {
            is FolderItem -> target.copy(apps = target.apps.filter { it != draggedApp } + draggedApp)
            is AppItem -> FolderItem(newId(), folderName, listOf(target.app, draggedApp))
            else -> return null
        }
        return replaceItem(layout, targetId, replacement)
    }

    /**
     * 整合性を保つ: 空フォルダの削除、ドックの容量超過分の移動、
     * ページからはみ出したアイテムを次のページへ送る、空ページの削除。
     */
    fun normalize(layout: HomeLayout, grid: GridSpec): HomeLayout {
        fun clean(item: HomeItem): HomeItem? = if (item is FolderItem && item.apps.isEmpty()) null else item

        var dock = layout.dock.mapNotNull(::clean).filter { it !is WidgetItem }
        var carry: List<HomeItem> = emptyList()
        if (dock.size > grid.dockSlots) {
            carry = dock.drop(grid.dockSlots)
            dock = dock.take(grid.dockSlots)
        }
        val pages = ArrayList<HomePage>()
        for (page in layout.pages) {
            val items = carry + page.items.mapNotNull(::clean)
            val (placed, overflow) = GridPacker.pack(items, grid)
            pages += page.copy(items = placed.map { it.item })
            carry = overflow
        }
        var guard = 0
        while (carry.isNotEmpty() && guard++ < 100) {
            val (placed, overflow) = GridPacker.pack(carry, grid)
            if (placed.isEmpty()) break
            pages += HomePage(items = placed.map { it.item })
            carry = overflow
        }
        val nonEmpty = pages.filter { it.items.isNotEmpty() }
        return layout.copy(
            pages = nonEmpty.ifEmpty { listOf(pages.firstOrNull()?.copy(items = emptyList(), hidden = false) ?: HomePage()) },
            dock = dock,
            today = layout.today,
        )
    }
}
