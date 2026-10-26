package com.aa.launcher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.aa.launcher.core.WidgetHostManager
import com.aa.launcher.core.WidgetItem

/** ウィジェットや検索画面から使うランチャー操作。 */
class LauncherActions(
    val launchApp: (appId: String, bounds: Rect?) -> Unit,
    val openSpotlight: () -> Unit,
    val editNote: (WidgetItem) -> Unit,
    val widgetMenu: (WidgetItem, Rect) -> Unit,
)

val LocalLauncherActions = compositionLocalOf {
    LauncherActions(launchApp = { _, _ -> }, openSpotlight = {}, editNote = {}, widgetMenu = { _, _ -> })
}

val LocalWidgetHost = staticCompositionLocalOf<WidgetHostManager?> { null }

/**
 * このコンテンツが今表示されているか (ページャーの表示中/隣接して見えているページ)。
 * false のときウィジェットの時計やバッテリー監視などの定期処理を止める。
 */
val LocalContentActive = compositionLocalOf { true }

/**
 * 折りたたみを開いた画面やタブレット (ウィンドウの幅 600dp 以上かつ高さ 480dp 以上) か。
 * このときホームは 2 ページを左右に並べて表示する (1 ページを横に引き伸ばさない)。
 * 配置データは閉じた画面と共通なので、開閉しても並びは変わらない。横向きのスマホ (高さ 480dp 未満) は対象外。
 */
@Composable
fun isTwoPane(): Boolean {
    val c = LocalConfiguration.current
    return c.screenWidthDp >= 600 && c.screenHeightDp >= 480
}

/** 大画面で、今日の表示・App ライブラリ・シートなどを引き伸ばさないための最大幅。スマホの幅では効かない。 */
val MaxPanelWidth = 560.dp
