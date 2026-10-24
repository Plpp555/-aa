package com.aa.launcher.ui.home

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
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
