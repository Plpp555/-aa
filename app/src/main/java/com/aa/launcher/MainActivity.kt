package com.aa.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aa.launcher.core.FrameRateController
import com.aa.launcher.core.WidgetHostManager
import com.aa.launcher.ui.home.ContextMenuState
import com.aa.launcher.ui.home.LauncherActions
import com.aa.launcher.ui.home.LauncherScreen
import com.aa.launcher.ui.home.LauncherViewModel
import com.aa.launcher.ui.home.LocalLauncherActions
import com.aa.launcher.ui.home.LocalWidgetHost
import com.aa.launcher.ui.home.MenuTarget
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** ホームアプリ本体。 */
class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private lateinit var widgetHost: WidgetHostManager
    private lateinit var frameRate: FrameRateController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        widgetHost = WidgetHostManager(this)
        vm.widgetHost = widgetHost
        frameRate = FrameRateController(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false

        setContent {
            val actions = remember {
                LauncherActions(
                    launchApp = { id, bounds -> vm.launchApp(this, id, bounds) },
                    openSpotlight = { vm.spotlightOpen = true },
                    editNote = { vm.noteEditing = it },
                    widgetMenu = { item, bounds ->
                        val inToday = vm.layout.value?.today?.any { it.id == item.id } == true
                        vm.contextMenu = ContextMenuState(MenuTarget.Widget(item, inToday), bounds)
                    },
                )
            }
            CompositionLocalProvider(
                LocalLauncherActions provides actions,
                LocalWidgetHost provides widgetHost,
            ) {
                LauncherScreen(vm)
            }
        }

        // 編集モードの揺れ (継続アニメーション) の間は最高リフレッシュレートを保つ
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                snapshotFlow { vm.editMode || vm.drag.active }
                    .combine(vm.settings) { animating, s -> animating && s.wiggleInEditMode && !s.reduceMotion }
                    .distinctUntilChanged()
                    .collect { frameRate.hold(it) }
            }
        }

        // 壁紙・ステータスバーの表示をウィンドウに反映
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.settings, vm.wallpaper) { s, w -> Triple(s.showStatusBar, w.image == null, w.isDark) }
                    .distinctUntilChanged()
                    .collect { (showStatusBar, showSystemWallpaper, darkWallpaper) ->
                        applyWindow(showStatusBar, showSystemWallpaper, darkWallpaper)
                    }
            }
        }
    }

    private fun applyWindow(showStatusBar: Boolean, showSystemWallpaper: Boolean, darkWallpaper: Boolean) {
        if (showSystemWallpaper) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } else {
            // ランチャーが不透明な壁紙を全面に描くので、ウィンドウ背景とシステム壁紙の合成を省く (重ね塗りの削減)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(null)
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (showStatusBar) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.isAppearanceLightStatusBars = !darkWallpaper
        controller.isAppearanceLightNavigationBars = !darkWallpaper
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
        vm.onStop()
        frameRate.reset()
        launcherApp.settingsRepository.flush()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        frameRate.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        frameRate.pulse()
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            val broughtToFront = intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0
            val alreadyOnHome = hasWindowFocus() && !broughtToFront
            vm.onHomeIntent(alreadyOnHome, this)
        }
    }

    @Deprecated("Needed for AppWidgetHost.startAppWidgetConfigureActivityForResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgetHost.onActivityResult(requestCode, resultCode)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
        if (vm.widgetHost === widgetHost) vm.widgetHost = null
        super.onDestroy()
    }
}
