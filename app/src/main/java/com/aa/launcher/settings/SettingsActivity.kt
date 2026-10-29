package com.aa.launcher.settings

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aa.launcher.R
import com.aa.launcher.core.AppLock
import com.aa.launcher.core.FrameRateController
import com.aa.launcher.core.LocalIconVersion
import androidx.compose.runtime.CompositionLocalProvider
import com.aa.launcher.launcherApp
import com.aa.launcher.settings.ui.SettingsColors
import com.aa.launcher.settings.ui.SettingsScreen
import com.aa.launcher.ui.theme.LauncherTheme
import com.aa.launcher.ui.theme.LocalIsDark

/** 設定画面の遷移先。 */
sealed interface Route {
    data object Root : Route
    data object Wallpaper : Route
    data object Appearance : Route
    data object HomeScreen : Route
    data object AppIcons : Route
    data object IconPack : Route
    data object Dock : Route
    data object Folders : Route
    data object Widgets : Route
    data object Search : Route
    data object Gestures : Route
    data class GestureChoice(val type: GestureType) : Route
    data class GestureApp(val type: GestureType) : Route
    data object Notifications : Route
    data object Privacy : Route
    data object Animations : Route
    data object ControlCenter : Route
    data object Accessibility : Route
    data object Backup : Route
    data object About : Route

    companion object {
        fun fromKey(key: String?): Route? = when (key) {
            "wallpaper" -> Wallpaper
            "appearance" -> Appearance
            "home" -> HomeScreen
            "icons" -> AppIcons
            "dock" -> Dock
            "folders" -> Folders
            "today" -> Widgets
            "search" -> Search
            "gestures" -> Gestures
            "notifications" -> Notifications
            "privacy" -> Privacy
            "animations" -> Animations
            "controlcenter" -> ControlCenter
            "accessibility" -> Accessibility
            "backup" -> Backup
            "about" -> About
            else -> null
        }
    }
}

/** 画面遷移と設定更新の窓口。 */
class SettingsNav(
    val push: (Route) -> Unit,
    val pop: () -> Unit,
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    val backLabel: String?,
)

class SettingsActivity : ComponentActivity() {

    private lateinit var frameRate: FrameRateController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        frameRate = FrameRateController(this)
        val repo = launcherApp.settingsRepository
        val start = Route.fromKey(intent.getStringExtra(EXTRA_ROUTE))
        val needsAuth = repo.current.requireAuthForSettings

        setContent {
            val settings by repo.settings.collectAsStateWithLifecycle()
            val iconVersion by launcherApp.iconRepository.version.collectAsStateWithLifecycle()
            var authed by remember { mutableStateOf(!needsAuth) }
            LaunchedEffect(Unit) {
                if (!authed) AppLock.authenticate(this@SettingsActivity, getString(R.string.auth_settings)) { authed = true }
            }
            LauncherTheme(settings) {
                val dark = LocalIsDark.current
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }
                if (authed) {
                    CompositionLocalProvider(LocalIconVersion provides iconVersion) {
                        SettingsNavHost(start = start, update = repo::update, onExit = { finish() })
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(SettingsColors.background()))
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        frameRate.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        frameRate.reset()
        launcherApp.settingsRepository.flush()
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

@Composable
private fun SettingsNavHost(
    start: Route?,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onExit: () -> Unit,
) {
    val stack = remember { mutableStateListOf<Route>(Route.Root).apply { if (start != null) add(start) } }
    var forward by remember { mutableStateOf(true) }
    val pop: () -> Unit = {
        if (stack.size > 1) {
            forward = false
            stack.removeAt(stack.lastIndex)
        } else {
            onExit()
        }
    }
    BackHandler(onBack = pop)
    val current = stack.last()
    AnimatedContent(
        targetState = current,
        transitionSpec = {
            if (forward) {
                (slideInHorizontally(tween(320)) { it } + fadeIn()) togetherWith (slideOutHorizontally(tween(320)) { -it / 3 } + fadeOut())
            } else {
                (slideInHorizontally(tween(320)) { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally(tween(320)) { it } + fadeOut())
            }
        },
        label = "settingsNav",
    ) { route ->
        val index = stack.indexOf(route)
        val previous = if (index > 0) stack[index - 1] else null
        val nav = SettingsNav(
            push = { r ->
                forward = true
                stack.add(r)
            },
            pop = pop,
            update = update,
            backLabel = previous?.let { routeTitle(it) },
        )
        SettingsScreen(route, nav, isRoot = route == Route.Root, onExit = onExit)
    }
}

@Composable
fun routeTitle(route: Route): String = androidx.compose.ui.res.stringResource(
    when (route) {
        Route.Root -> R.string.settings_title
        Route.Wallpaper -> R.string.settings_wallpaper
        Route.Appearance -> R.string.settings_appearance
        Route.HomeScreen -> R.string.settings_home_screen
        Route.AppIcons -> R.string.settings_app_icons
        Route.IconPack -> R.string.settings_icon_pack
        Route.Dock -> R.string.settings_dock
        Route.Folders -> R.string.settings_folders
        Route.Widgets -> R.string.settings_widgets
        Route.Search -> R.string.settings_search
        Route.Gestures -> R.string.settings_gestures
        is Route.GestureChoice -> route.type.label
        is Route.GestureApp -> R.string.gesture_choose_app
        Route.Notifications -> R.string.settings_notifications
        Route.Privacy -> R.string.settings_privacy
        Route.Animations -> R.string.settings_animations
        Route.ControlCenter -> R.string.settings_control_center
        Route.Accessibility -> R.string.settings_accessibility
        Route.Backup -> R.string.settings_backup
        Route.About -> R.string.settings_about
    },
)
