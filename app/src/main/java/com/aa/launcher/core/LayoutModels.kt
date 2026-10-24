package com.aa.launcher.core

import androidx.annotation.StringRes
import com.aa.launcher.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
sealed class HomeItem {
    abstract val id: String
}

@Serializable
@SerialName("app")
data class AppItem(override val id: String, val app: String) : HomeItem()

@Serializable
@SerialName("folder")
data class FolderItem(override val id: String, val name: String, val apps: List<String>) : HomeItem()

@Serializable
@SerialName("widget")
data class WidgetItem(
    override val id: String,
    val kind: WidgetKind,
    val size: WidgetSize,
    val appWidgetId: Int = -1,
    val provider: String? = null,
    val config: Map<String, String> = emptyMap(),
) : HomeItem()

@Serializable
enum class WidgetKind(@StringRes val label: Int, @StringRes val description: Int) {
    CLOCK(R.string.widget_clock, R.string.widget_clock_desc),
    ANALOG_CLOCK(R.string.widget_analog_clock, R.string.widget_analog_clock_desc),
    CALENDAR(R.string.widget_calendar, R.string.widget_calendar_desc),
    BATTERY(R.string.widget_battery, R.string.widget_battery_desc),
    SUGGESTIONS(R.string.widget_suggestions, R.string.widget_suggestions_desc),
    NOTE(R.string.widget_note, R.string.widget_note_desc),
    SEARCH(R.string.widget_search, R.string.widget_search_desc),
    APP_WIDGET(R.string.widget_android, R.string.widget_android_desc),
}

@Serializable
enum class WidgetSize(val spanX: Int, val spanY: Int, @StringRes val label: Int) {
    SMALL(2, 2, R.string.size_small),
    MEDIUM(4, 2, R.string.size_medium),
    LARGE(4, 4, R.string.size_large),
}

@Serializable
data class HomePage(
    val id: String = newId(),
    val items: List<HomeItem> = emptyList(),
    val hidden: Boolean = false,
)

@Serializable
data class HomeLayout(
    val pages: List<HomePage> = listOf(HomePage()),
    val dock: List<HomeItem> = emptyList(),
    val today: List<WidgetItem> = emptyList(),
    /** これまでに見たアプリ。「ホーム画面から取り除く」したアプリが再追加されないようにする。 */
    val knownApps: Set<String> = emptySet(),
    val version: Int = 1,
)

data class GridSpec(val columns: Int, val rows: Int, val dockSlots: Int)

data class GridCell(val x: Int, val y: Int, val w: Int, val h: Int)

data class Placement(val item: HomeItem, val cell: GridCell)

/** iOS と同じく、アイテムを左上から順に詰めて配置する。 */
object GridPacker {

    fun span(item: HomeItem, grid: GridSpec): Pair<Int, Int> = when (item) {
        is WidgetItem -> item.size.spanX.coerceAtMost(grid.columns) to item.size.spanY.coerceAtMost(grid.rows)
        else -> 1 to 1
    }

    /** 収まったアイテムの配置と、はみ出したアイテム (順序維持) を返す。 */
    fun pack(items: List<HomeItem>, grid: GridSpec): Pair<List<Placement>, List<HomeItem>> {
        val occupied = Array(grid.rows) { BooleanArray(grid.columns) }
        val placed = ArrayList<Placement>(items.size)
        val overflow = ArrayList<HomeItem>()
        for (item in items) {
            if (overflow.isNotEmpty()) {
                overflow += item
                continue
            }
            val (w, h) = span(item, grid)
            val cell = findSlot(occupied, w, h, grid)
            if (cell == null) {
                overflow += item
            } else {
                for (yy in cell.y until cell.y + h) for (xx in cell.x until cell.x + w) occupied[yy][xx] = true
                placed += Placement(item, cell)
            }
        }
        return placed to overflow
    }

    private fun findSlot(occupied: Array<BooleanArray>, w: Int, h: Int, grid: GridSpec): GridCell? {
        for (y in 0..grid.rows - h) {
            for (x in 0..grid.columns - w) {
                // 2 列幅のウィジェットは偶数列にそろえる (iOS と同じ見た目)
                if (w in 2 until grid.columns && grid.columns % 2 == 0 && x % 2 != 0) continue
                var free = true
                loop@ for (yy in y until y + h) for (xx in x until x + w) {
                    if (occupied[yy][xx]) {
                        free = false
                        break@loop
                    }
                }
                if (free) return GridCell(x, y, w, h)
            }
        }
        return null
    }

    fun fits(items: List<HomeItem>, grid: GridSpec): Boolean = pack(items, grid).second.isEmpty()
}
