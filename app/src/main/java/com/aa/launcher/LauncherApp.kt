package com.aa.launcher

import android.app.Application
import android.content.Context
import com.aa.launcher.core.AppRepository
import com.aa.launcher.core.GridSpec
import com.aa.launcher.core.IconPackManager
import com.aa.launcher.core.IconRepository
import com.aa.launcher.core.LayoutRepository
import com.aa.launcher.core.MediaRepository
import com.aa.launcher.core.UsageTracker
import com.aa.launcher.core.WallpaperRepository
import com.aa.launcher.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LauncherApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var appRepository: AppRepository
        private set
    lateinit var layoutRepository: LayoutRepository
        private set
    lateinit var iconPackManager: IconPackManager
        private set
    lateinit var iconRepository: IconRepository
        private set
    lateinit var wallpaperRepository: WallpaperRepository
        private set
    lateinit var usageTracker: UsageTracker
        private set
    lateinit var mediaRepository: MediaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this, appScope)
        appRepository = AppRepository(this, appScope)
        layoutRepository = LayoutRepository(this, appScope)
        iconPackManager = IconPackManager(this)
        iconRepository = IconRepository(this, appRepository, iconPackManager)
        wallpaperRepository = WallpaperRepository(this, appScope)
        usageTracker = UsageTracker(this)
        mediaRepository = MediaRepository(this)

        val settings = settingsRepository.settings

        // アプリ一覧の変化をホーム画面レイアウトへ反映
        appScope.launch {
            combine(appRepository.apps, appRepository.loaded, settings) { apps, loaded, s -> Triple(apps, loaded, s) }
                .collect { (apps, loaded, s) ->
                    if (!loaded) return@collect
                    layoutRepository.grid = GridSpec(s.gridColumns, s.gridRows, s.dockColumns)
                    if (layoutRepository.current == null) layoutRepository.initialize(apps)
                    layoutRepository.sync(apps, s.addNewAppsToHome, s.hiddenApps)
                }
        }

        // 壁紙
        appScope.launch {
            settings.map { it.wallpaperSource to it.builtinWallpaper }.distinctUntilChanged().collect { (source, index) ->
                wallpaperRepository.refresh(source, index)
            }
        }

        // パッケージ更新・アイコン設定変更でアイコンキャッシュを破棄
        appScope.launch {
            appRepository.changeVersion.collect { iconRepository.invalidate() }
        }
        appScope.launch {
            settings.map { it.iconPack }.distinctUntilChanged().collect { iconRepository.invalidate() }
        }
    }

    companion object {
        lateinit var instance: LauncherApp
            private set
    }
}

val Context.launcherApp: LauncherApp get() = applicationContext as LauncherApp
