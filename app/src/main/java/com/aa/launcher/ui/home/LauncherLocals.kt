package com.aa.launcher.ui.home

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aa.launcher.core.DeviceInfo
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
 * 折りたたみスマホを開いた画面か (ウィンドウの幅 600dp 以上・高さ 480dp 以上で、折りたたみ端末または画面がほぼ正方形)。
 * このときホームは 2 ページを左右に並べて表示する (1 ページを横に引き伸ばさない)。
 * タブレット (16:10 などの横長) は iPadOS と同じく 1 ページのまま。閉じた画面・横向きのスマホも対象外。
 * 配置データは閉じた画面と共通なので、開閉しても並びは変わらない。
 */
@Composable
fun isTwoPane(): Boolean {
    val c = LocalConfiguration.current
    val context = LocalContext.current
    val foldable = remember { DeviceInfo.isFoldable(context) }
    val w = c.screenWidthDp
    val h = c.screenHeightDp
    if (w < 600 || h < 480) return false
    // 開いた折りたたみの内側の画面はほぼ正方形 (縦横比 1.0〜1.25 程度)。ヒンジセンサーのない端末もこれで判定する
    val ratio = maxOf(w, h).toFloat() / minOf(w, h)
    return foldable || ratio <= 1.3f
}

/**
 * Island 版で Dynamic Island を表示中か。このときシステムのステータスバーを隠す
 * (ステータスバーの領域のタッチはシステムが受け取るので、隠さないと島をタップできない)。時刻と電池は島の左右に自前で描く。
 */
val LocalIslandActive = compositionLocalOf { false }

/**
 * ホーム画面の上端に空けるステータスバーの領域。Island 版はシステムのステータスバーを隠して自前で描くので、
 * 隠していても同じ高さを空ける (標準版は従来どおり、設定で隠すと詰める)。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun launcherStatusBars(): WindowInsets =
    if (LocalIslandActive.current) WindowInsets.statusBarsIgnoringVisibility else WindowInsets.statusBars

/** 大画面で、今日の表示・App ライブラリ・シートなどを引き伸ばさないための最大幅。スマホの幅では効かない。 */
val MaxPanelWidth = 560.dp
