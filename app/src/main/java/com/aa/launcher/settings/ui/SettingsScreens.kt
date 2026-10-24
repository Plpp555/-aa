package com.aa.launcher.settings.ui

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aa.launcher.BuildConfig
import com.aa.launcher.LauncherApp
import com.aa.launcher.R
import com.aa.launcher.core.AppLock
import com.aa.launcher.core.BackupManager
import com.aa.launcher.core.BuiltinWallpapers
import com.aa.launcher.core.SquircleShape
import com.aa.launcher.core.SystemActions
import com.aa.launcher.service.NotificationState
import com.aa.launcher.settings.BadgeStyle
import com.aa.launcher.settings.ClearVariant
import com.aa.launcher.settings.ClockFormat
import com.aa.launcher.settings.ControlTile
import com.aa.launcher.settings.DockStyle
import com.aa.launcher.settings.GestureAction
import com.aa.launcher.settings.GestureType
import com.aa.launcher.settings.IconShape
import com.aa.launcher.settings.IconStyle
import com.aa.launcher.settings.OpenAnimation
import com.aa.launcher.settings.PageEffect
import com.aa.launcher.settings.Route
import com.aa.launcher.settings.SearchEngine
import com.aa.launcher.settings.SettingsNav
import com.aa.launcher.settings.ThemeMode
import com.aa.launcher.settings.WallpaperSource
import com.aa.launcher.ui.common.AppIconImage
import com.aa.launcher.ui.common.HueBrush
import com.aa.launcher.ui.common.colorToHsv
import com.aa.launcher.ui.common.hsvColor
import com.aa.launcher.ui.common.rememberPlainIconSpec
import com.aa.launcher.ui.theme.IosColors
import com.aa.launcher.ui.theme.LocalLauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(route: Route, nav: SettingsNav, isRoot: Boolean, onExit: () -> Unit) {
    val title = com.aa.launcher.settings.routeTitle(route)
    SettingsPage(title = title, onBack = if (isRoot) null else nav.pop, backLabel = if (isRoot) null else nav.backLabel) {
        when (route) {
            Route.Root -> RootContent(nav)
            Route.Wallpaper -> WallpaperContent(nav)
            Route.Appearance -> AppearanceContent(nav)
            Route.HomeScreen -> HomeScreenContent(nav)
            Route.AppIcons -> AppIconsContent(nav)
            Route.IconPack -> IconPackContent(nav)
            Route.Dock -> DockContent(nav)
            Route.Folders -> FoldersContent(nav)
            Route.Widgets -> WidgetsContent(nav)
            Route.Search -> SearchContent(nav)
            Route.Gestures -> GesturesContent(nav)
            is Route.GestureChoice -> GestureChoiceContent(nav, route.type)
            is Route.GestureApp -> GestureAppContent(nav, route.type)
            Route.Notifications -> NotificationsContent(nav)
            Route.Privacy -> PrivacyContent(nav)
            Route.Animations -> AnimationsContent(nav)
            Route.ControlCenter -> ControlCenterContent(nav)
            Route.Accessibility -> AccessibilityContent(nav)
            Route.Backup -> BackupContent()
            Route.About -> AboutContent()
        }
    }
}

// ---- ルート ------------------------------------------------------------------

@Composable
private fun RootContent(nav: SettingsNav) {
    val context = LocalContext.current
    val s = LocalLauncherSettings.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val isDefault = remember(tick) { SystemActions.isDefaultLauncher(context) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        tick++
        if (!SystemActions.isDefaultLauncher(context)) SystemActions.openHomeSettings(context)
    }

    SettingsGroup {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(SquircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF5AC8FA), Color(0xFF0A84FF), Color(0xFF5856D6)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.GridView, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), color = SettingsColors.label(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_tagline), color = SettingsColors.secondary(), fontSize = 13.sp)
            }
        }
        if (!isDefault) {
            ButtonRow(stringResource(R.string.set_default_home), onClick = {
                val intent = SystemActions.homeRoleIntent(context)
                if (intent != null) roleLauncher.launch(intent) else SystemActions.openHomeSettings(context)
            })
        } else {
            ValueRow(stringResource(R.string.default_home), stringResource(R.string.status_on))
        }
    }

    SettingsGroup {
        NavRow(stringResource(R.string.settings_wallpaper), Icons.Rounded.Wallpaper, IosColors.Cyan) { nav.push(Route.Wallpaper) }
        NavRow(stringResource(R.string.settings_appearance), Icons.Rounded.LightMode, IosColors.Blue, value = stringResource(s.themeMode.label)) { nav.push(Route.Appearance) }
        NavRow(stringResource(R.string.settings_home_screen), Icons.Rounded.GridView, IosColors.Indigo) { nav.push(Route.HomeScreen) }
        NavRow(stringResource(R.string.settings_app_icons), Icons.Rounded.Apps, IosColors.Pink, value = stringResource(s.iconStyle.label)) { nav.push(Route.AppIcons) }
        NavRow(stringResource(R.string.settings_dock), Icons.Rounded.Dock, IosColors.Green) { nav.push(Route.Dock) }
        NavRow(stringResource(R.string.settings_folders), Icons.Rounded.Folder, IosColors.Orange) { nav.push(Route.Folders) }
    }
    SettingsGroup {
        NavRow(stringResource(R.string.settings_widgets), Icons.Rounded.Widgets, IosColors.Teal) { nav.push(Route.Widgets) }
        NavRow(stringResource(R.string.settings_search), Icons.Rounded.Search, IosColors.Gray) { nav.push(Route.Search) }
        NavRow(stringResource(R.string.settings_control_center), Icons.Rounded.Tune, IosColors.Gray) { nav.push(Route.ControlCenter) }
    }
    SettingsGroup {
        NavRow(stringResource(R.string.settings_gestures), Icons.Rounded.Gesture, IosColors.Purple) { nav.push(Route.Gestures) }
        NavRow(stringResource(R.string.settings_notifications), Icons.Rounded.Notifications, IosColors.Red) { nav.push(Route.Notifications) }
        NavRow(stringResource(R.string.settings_privacy), Icons.Rounded.Lock, IosColors.Blue) { nav.push(Route.Privacy) }
        NavRow(stringResource(R.string.settings_animations), Icons.Rounded.Animation, IosColors.Orange) { nav.push(Route.Animations) }
        NavRow(stringResource(R.string.settings_accessibility), Icons.Rounded.AccessibilityNew, IosColors.Blue) { nav.push(Route.Accessibility) }
    }
    SettingsGroup {
        NavRow(stringResource(R.string.settings_backup), Icons.Rounded.Backup, IosColors.Green) { nav.push(Route.Backup) }
        NavRow(stringResource(R.string.settings_about), Icons.Rounded.Info, IosColors.Gray) { nav.push(Route.About) }
    }
    SettingsFooterText(stringResource(R.string.settings_footer, BuildConfig.VERSION_NAME))
}

// ---- 壁紙 ------------------------------------------------------------------

@Composable
private fun WallpaperContent(nav: SettingsNav) {
    val context = LocalContext.current
    val s = LocalLauncherSettings.current
    val repo = LauncherApp.instance.wallpaperRepository
    val scope = rememberCoroutineScope()
    val previews by produceState<List<ImageBitmap>>(emptyList()) {
        value = withContext(Dispatchers.Default) {
            BuiltinWallpapers.all.indices.map { repo.previewBuiltin(it, 180, 390).asImageBitmap() }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                if (repo.importPhoto(uri)) {
                    nav.update { it.copy(wallpaperSource = WallpaperSource.CUSTOM) }
                    repo.refresh(WallpaperSource.CUSTOM, s.builtinWallpaper, force = true)
                } else {
                    Toast.makeText(context, R.string.error_generic, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    var applying by remember { mutableStateOf(false) }
    fun apply(flags: Int) {
        if (applying) return
        applying = true
        scope.launch {
            val ok = repo.applyToSystem(s.wallpaperSource, s.builtinWallpaper, flags)
            applying = false
            Toast.makeText(context, if (ok) R.string.wallpaper_applied else R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
    }

    SettingsGroup(header = stringResource(R.string.wallpaper_builtin_header)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            previews.forEachIndexed { i, bmp ->
                val selected = s.wallpaperSource == WallpaperSource.BUILTIN && s.builtinWallpaper == i
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(width = 84.dp, height = 180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .then(if (selected) Modifier.border(3.dp, IosColors.Blue, RoundedCornerShape(16.dp)) else Modifier)
                            .clickable { nav.update { it.copy(wallpaperSource = WallpaperSource.BUILTIN, builtinWallpaper = i) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(BuiltinWallpapers.all[i].name), color = SettingsColors.secondary(), fontSize = 12.sp)
                }
            }
        }
    }
    SettingsGroup(footer = stringResource(R.string.wallpaper_system_footer)) {
        CheckRow(
            stringResource(R.string.wallpaper_choose_photo),
            s.wallpaperSource == WallpaperSource.CUSTOM,
            { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
        CheckRow(
            stringResource(R.string.wallpaper_use_system),
            s.wallpaperSource == WallpaperSource.SYSTEM,
            { nav.update { it.copy(wallpaperSource = WallpaperSource.SYSTEM) } },
        )
        ButtonRow(stringResource(R.string.wallpaper_open_system_picker), onClick = {
            SystemActions.start(context, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), context.getString(R.string.settings_wallpaper)))
        })
    }
    SettingsGroup(header = stringResource(R.string.options)) {
        SwitchRow(stringResource(R.string.wallpaper_dim), s.dimWallpaperInDark, { v -> nav.update { it.copy(dimWallpaperInDark = v) } })
        SwitchRow(stringResource(R.string.wallpaper_blur_home), s.blurHomeWallpaper, { v -> nav.update { it.copy(blurHomeWallpaper = v) } })
    }
    val canApply = s.wallpaperSource != WallpaperSource.SYSTEM
    SettingsGroup(header = stringResource(R.string.wallpaper_apply_header), footer = stringResource(R.string.wallpaper_apply_footer)) {
        ButtonRow(stringResource(R.string.wallpaper_apply_both), { apply(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) }, enabled = canApply && !applying)
        ButtonRow(stringResource(R.string.wallpaper_apply_home), { apply(WallpaperManager.FLAG_SYSTEM) }, enabled = canApply && !applying)
        ButtonRow(stringResource(R.string.wallpaper_apply_lock), { apply(WallpaperManager.FLAG_LOCK) }, enabled = canApply && !applying)
    }
}

// ---- 外観 ------------------------------------------------------------------

@Composable
private fun AppearanceContent(nav: SettingsNav) {
    val s = LocalLauncherSettings.current
    ChoiceGroup(
        header = stringResource(R.string.appearance_theme),
        footer = null,
        options = ThemeMode.entries,
        selected = s.themeMode,
        label = { stringResource(it.label) },
        onSelect = { v -> nav.update { it.copy(themeMode = v) } },
    )
    SettingsGroup(header = stringResource(R.string.liquid_glass), footer = stringResource(R.string.liquid_glass_footer)) {
        SliderRow(
            value = s.glassOpacity,
            onChange = { v -> nav.update { it.copy(glassOpacity = v) } },
            startLabel = stringResource(R.string.glass_clear),
            endLabel = stringResource(R.string.glass_tinted),
        )
    }
    SettingsGroup(header = stringResource(R.string.accent_color)) {
        ColorRow(stringResource(R.string.accent_color), IosColors.accents, s.accentColor) { c -> nav.update { it.copy(accentColor = c.toArgb()) } }
    }
    SettingsGroup(footer = stringResource(R.string.status_bar_footer)) {
        SwitchRow(stringResource(R.string.show_status_bar), s.showStatusBar, { v -> nav.update { it.copy(showStatusBar = v) } })
    }
    SettingsGroup {
        NavRow(stringResource(R.string.settings_accessibility), Icons.Rounded.AccessibilityNew, IosColors.Blue) { nav.push(Route.Accessibility) }
    }
}

// ---- ホーム画面 ---------------------------------------------------------------

@Composable
private fun HomeScreenContent(nav: SettingsNav) {
    val s = LocalLauncherSettings.current
    var confirmReset by remember { mutableStateOf(false) }
    SettingsGroup(header = stringResource(R.string.home_layout), footer = stringResource(R.string.home_layout_footer)) {
        StepperRow(stringResource(R.string.home_columns), s.gridColumns, 3..7, { v -> nav.update { it.copy(gridColumns = v) } })
        StepperRow(stringResource(R.string.home_rows), s.gridRows, 4..9, { v -> nav.update { it.copy(gridRows = v) } })
    }
    SettingsGroup(header = stringResource(R.string.home_new_apps)) {
        CheckRow(stringResource(R.string.home_new_apps_home), s.addNewAppsToHome, { nav.update { it.copy(addNewAppsToHome = true) } })
        CheckRow(stringResource(R.string.home_new_apps_library), !s.addNewAppsToHome, { nav.update { it.copy(addNewAppsToHome = false) } })
    }
    SettingsGroup(header = stringResource(R.string.home_pages)) {
        SwitchRow(stringResource(R.string.home_show_today), s.showTodayView, { v -> nav.update { it.copy(showTodayView = v) } })
        SwitchRow(stringResource(R.string.home_show_library), s.showAppLibrary, { v -> nav.update { it.copy(showAppLibrary = v) } })
        SwitchRow(stringResource(R.string.home_show_search), s.showSearchOnHome, { v -> nav.update { it.copy(showSearchOnHome = v) } }, subtitle = stringResource(R.string.home_show_search_sub))
        SwitchRow(stringResource(R.string.home_lock_layout), s.lockLayout, { v -> nav.update { it.copy(lockLayout = v) } }, subtitle = stringResource(R.string.home_lock_layout_sub))
    }
    ChoiceGroup(
        header = stringResource(R.string.home_page_effect),
        footer = null,
        options = PageEffect.entries,
        selected = s.pageEffect,
        label = { stringResource(it.label) },
        onSelect = { v -> nav.update { it.copy(pageEffect = v) } },
    )
    SettingsGroup(header = stringResource(R.string.app_library)) {
        SwitchRow(stringResource(R.string.library_show_suggestions), s.librarySuggestions, { v -> nav.update { it.copy(librarySuggestions = v) } })
        SwitchRow(stringResource(R.string.library_show_recent), s.libraryRecentlyAdded, { v -> nav.update { it.copy(libraryRecentlyAdded = v) } })
        SwitchRow(stringResource(R.string.library_show_badges), s.showBadgesInAppLibrary, { v -> nav.update { it.copy(showBadgesInAppLibrary = v) } })
    }
    SettingsGroup {
        ButtonRow(stringResource(R.string.home_reset_layout), { confirmReset = true }, destructive = true)
    }
    if (confirmReset) {
        IosAlert(
            title = stringResource(R.string.home_reset_layout),
            message = stringResource(R.string.home_reset_layout_message),
            confirmLabel = stringResource(R.string.reset),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                val app = LauncherApp.instance
                app.layoutRepository.reset(app.appRepository.apps.value.filter { it.id !in s.hiddenApps })
            },
            onDismiss = { confirmReset = false },
        )
    }
}

// ---- アプリアイコン -------------------------------------------------------------

@Composable
private fun AppIconsContent(nav: SettingsNav) {
    val s = LocalLauncherSettings.current
    val packs by produceState(emptyList<com.aa.launcher.core.IconPackManager.IconPackInfo>()) {
        value = withContext(Dispatchers.IO) { LauncherApp.instance.iconPackManager.installedPacks() }
    }
    val packLabel = s.iconPack?.let { pkg -> packs.firstOrNull { it.packageName == pkg }?.label ?: pkg } ?: stringResource(R.string.icon_pack_none)

    IconPreview()
    ChoiceGroup(
        header = stringResource(R.string.icons_style),
        footer = stringResource(R.string.icons_style_footer),
        options = IconStyle.entries,
        selected = s.iconStyle,
        label = { stringResource(it.label) },
        onSelect = { v -> nav.update { it.copy(iconStyle = v) } },
    )
    if (s.iconStyle == IconStyle.CLEAR) {
        SettingsGroup(header = stringResource(R.string.icons_clear_variant)) {
            SegmentRow(ClearVariant.entries.map { stringResource(it.label) }, s.clearVariant.ordinal, { i -> nav.update { it.copy(clearVariant = ClearVariant.entries[i]) } })
        }
    }
    if (s.iconStyle == IconStyle.TINTED) {
        val hsv = colorToHsv(s.iconTintColor)
        SettingsGroup(header = stringResource(R.string.icons_tint)) {
            SwitchRow(stringResource(R.string.tint_from_wallpaper), s.tintFromWallpaper, { v -> nav.update { it.copy(tintFromWallpaper = v) } })
            SliderRow(
                value = hsv[0],
                onChange = { h -> nav.update { it.copy(iconTintColor = hsvColor(h, hsv[1].coerceAtLeast(0.4f), hsv[2].coerceAtLeast(0.6f)), tintFromWallpaper = false) } },
                range = 0f..360f,
                title = stringResource(R.string.icons_hue),
                trackBrush = HueBrush,
            )
            SliderRow(
                value = hsv[2],
                onChange = { v -> nav.update { it.copy(iconTintColor = hsvColor(hsv[0], hsv[1].coerceAtLeast(0.4f), v.coerceAtLeast(0.3f))) } },
                title = stringResource(R.string.icons_brightness),
            )
        }
    }
    SettingsGroup(header = stringResource(R.string.icons_size)) {
        SwitchRow(stringResource(R.string.icons_large), s.largeIcons, { v -> nav.update { it.copy(largeIcons = v) } }, subtitle = stringResource(R.string.icons_large_sub))
        SliderRow(
            value = s.iconScale,
            onChange = { v -> nav.update { it.copy(iconScale = v) } },
            range = 0.7f..1.3f,
            title = stringResource(R.string.icons_scale),
            valueLabel = "${(s.iconScale * 100).toInt()}%",
        )
    }
    ChoiceGroup(
        header = stringResource(R.string.icons_shape),
        footer = stringResource(R.string.icons_shape_footer),
        options = IconShape.entries,
        selected = s.iconShape,
        label = { stringResource(it.label) },
        onSelect = { v -> nav.update { it.copy(iconShape = v) } },
    )
    SettingsGroup {
        NavRow(stringResource(R.string.settings_icon_pack), value = packLabel) { nav.push(Route.IconPack) }
    }
    SettingsGroup(header = stringResource(R.string.icons_labels)) {
        SwitchRow(stringResource(R.string.icons_show_labels), s.showLabels, { v -> nav.update { it.copy(showLabels = v) } }, enabled = !s.largeIcons)
        SliderRow(
            value = s.labelScale,
            onChange = { v -> nav.update { it.copy(labelScale = v) } },
            range = 0.8f..1.3f,
            title = stringResource(R.string.icons_label_size),
            valueLabel = "${(s.labelScale * 100).toInt()}%",
        )
        SwitchRow(stringResource(R.string.icons_shadow), s.iconShadow, { v -> nav.update { it.copy(iconShadow = v) } })
    }
}

@Composable
private fun IconPreview() {
    val apps = LauncherApp.instance.appRepository.apps.value
    val sample = remember(apps) { apps.filter { !it.isWorkProfile }.take(4) }
    if (sample.isEmpty()) return
    SettingsGroup {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF0EA5E9), Color(0xFF6366F1))))
                .padding(vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            sample.forEach { AppIconImage(it.id, 58.dp) }
        }
    }
}

@Composable
private fun IconPackContent(nav: SettingsNav) {
    val context = LocalContext.current
    val s = LocalLauncherSettings.current
    val packs by produceState(emptyList<com.aa.launcher.core.IconPackManager.IconPackInfo>()) {
        value = withContext(Dispatchers.IO) { LauncherApp.instance.iconPackManager.installedPacks() }
    }
    SettingsGroup(footer = stringResource(R.string.icon_pack_footer)) {
        CheckRow(stringResource(R.string.icon_pack_none), s.iconPack == null, { nav.update { it.copy(iconPack = null) } })
        packs.forEach { pack ->
            CheckRow(pack.label, s.iconPack == pack.packageName, { nav.update { it.copy(iconPack = pack.packageName) } }, subtitle = pack.packageName)
        }
    }
    SettingsGroup {
        ButtonRow(stringResource(R.string.icon_pack_get), { SystemActions.playStoreSearch(context, "icon pack") })
    }
}

// ---- ドック / フォルダ ---------------------------------------------------------

@Composable
private fun DockContent(nav: SettingsNav) {
    val s = LocalLauncherSettings.current
    SettingsGroup {
        SwitchRow(stringResource(R.string.dock_show), s.showDock, { v -> nav.update { it.copy(showDock = v) } })
        StepperRow(stringResource(R.string.dock_count), s.dockColumns, 3..6, { v -> nav.update { it.copy(dockColumns = v) } })
        SwitchRow(stringResource(R.string.dock_labels), s.dockLabels, { v -> nav.update { it.copy(dockLabels = v) } })
    }
    ChoiceGroup(
        header = stringResource(R.string.dock_style),
        footer = stringResource(R.string.dock_style_footer),
        options = DockStyle.entries,
        selected = s.dockStyle,
        label = { stringResource(it.label) },
        onSelect = { v -> nav.update { it.copy(dockStyle = v) } },
    )
}

@Composable
private fun FoldersContent(nav: SettingsNav) {
    val s = LocalLauncherSettings.current
    SettingsGroup(footer = stringResource(R.string.folders_footer)) {
        SegmentRow(listOf("3", "4"), if (s.folderColumns >= 4) 1 else 0, { i -> nav.update { it.copy(folderColumns = 3 + i) } }, title = stringResource(R.string.folders_columns))
        SwitchRow(stringResource(R.string.folders_blur), s.folderBlur, { v -> nav.update { it.copy(folderBlur = v) } })
    }
}

// ---- ウィジェット / 今日の表示 ----------------------------------------------------

@Composable
private fun WidgetsContent(nav: SettingsNav) {
    val s = LocalLauncherSettings.current
    SettingsGroup(footer = stringResource(R.string.widgets_footer)) {
        SwitchRow(stringResource(R.string.home_show_today), s.showTodayView, { v -> nav.update { it.copy(showTodayView = v) } })
        SwitchRow(stringResource(R.string.today_show_date), s.todayShowDate, { v -> nav.update { it.copy(todayShowDate = v) } })
    }
    ChoiceGroup(
        header = stringResource(R.string.clock_format),
        footer = null,
        options = ClockFormat.entries,
        selected = s.clockFormat,
        label = { stringResource(it.label) },
        onSelect = { v -> nav.update { it.copy(clockFormat = v) } },
    )
    SettingsGroup {
        SwitchRow(stringResource(R.string.clock_seconds), s.clockShowSeconds, { v -> nav.update { it.copy(clockShowSeconds = v) } })
    }
}

// ---- 検索 ------------------------------------------------------------------

@Composable
private fun SearchContent(nav: SettingsNav) {
    val context = LocalContext.current
    val s = LocalLauncherSettings.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        nav.update { it.copy(searchContacts = granted) }
    }
    SettingsGroup(header = stringResource(R.string.search_suggestions_header)) {
        SwitchRow(stringResource(R.string.siri_suggestions), s.showSiriSuggestions, { v -> nav.update { it.copy(showSiriSuggestions = v) } })
        SwitchRow(stringResource(R.string.search_auto_keyboard), s.autoOpenKeyboard, { v -> nav.update { it.copy(autoOpenKeyboard = v) } })
        SwitchRow(stringResource(R.string.search_remember_recent), s.rememberRecentSearches, { v -> nav.update { it.copy(rememberRecentSearches = v) } })
        ButtonRow(stringResource(R.string.search_clear_recent), {
            LauncherApp.instance.usageTracker.clearRecentSearches()
            Toast.makeText(context, R.string.done, Toast.LENGTH_SHORT).show()
        })
    }
    SettingsGroup(header = stringResource(R.string.search_sources), footer = stringResource(R.string.search_sources_footer)) {
        SwitchRow(stringResource(R.string.search_apps), s.searchApps, { v -> nav.update { it.copy(searchApps = v) } })
        SwitchRow(stringResource(R.string.contacts), s.searchContacts, { v ->
            if (v && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                permission.launch(Manifest.permission.READ_CONTACTS)
            } else {
                nav.update { it.copy(searchContacts = v) }
            }
        })
        SwitchRow(stringResource(R.string.settings_title), s.searchSettings, { v -> nav.update { it.copy(searchSettings = v) } })
        SwitchRow(stringResource(R.string.calculator), s.searchCalculator, { v -> nav.update { it.copy(searchCalculator = v) } })
        SwitchRow(stringResource(R.string.search_web), s.searchWeb, { v -> nav.update { it.copy(searchWeb = v) } })
        SwitchRow(stringResource(R.string.search_play_store), s.searchPlayStore, { v -> nav.update { it.copy(searchPlayStore = v) } })
    }
    ChoiceGroup(
        header = stringResource(R.string.search_engine),
        footer = null,
        options = SearchEngine.entries,
        selected = s.searchEngine,
        label = { stringResource(it.label) },
        onSelect = { v -> nav.update { it.copy(searchEngine = v) } },
    )
}

// ---- ジェスチャー --------------------------------------------------------------

@Composable
private fun GesturesContent(nav: SettingsNav) {
    val context = LocalContext.current
    val s = LocalLauncherSettings.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val accessibility = remember(tick) { SystemActions.isAccessibilityEnabled() }
    val apps = LauncherApp.instance.appRepository.apps.value
    SettingsGroup(footer = stringResource(R.string.gestures_footer)) {
        GestureType.entries.forEach { type ->
            val action = s.gestureAction(type)
            val value = if (action == GestureAction.LAUNCH_APP) {
                apps.firstOrNull { it.id == s.gestureApps[type.name] }?.label ?: stringResource(action.label)
            } else {
                stringResource(action.label)
            }
            NavRow(stringResource(type.label), value = value) { nav.push(Route.GestureChoice(type)) }
        }
    }
    SettingsGroup(footer = stringResource(R.string.accessibility_service_footer)) {
        NavRow(
            stringResource(R.string.accessibility_service),
            value = stringResource(if (accessibility) R.string.status_on else R.string.status_off),
        ) { SystemActions.openAccessibilitySettings(context) }
    }
}

@Composable
private fun GestureChoiceContent(nav: SettingsNav, type: GestureType) {
    val s = LocalLauncherSettings.current
    val current = s.gestureAction(type)
    val options = GestureAction.entries
    SettingsGroup {
        options.forEach { action ->
            CheckRow(stringResource(action.label), action == current, {
                if (action == GestureAction.LAUNCH_APP) {
                    nav.push(Route.GestureApp(type))
                } else {
                    nav.update { it.withGestureAction(type, action) }
                    nav.pop()
                }
            })
        }
    }
}

@Composable
private fun GestureAppContent(nav: SettingsNav, type: GestureType) {
    val s = LocalLauncherSettings.current
    val apps = LauncherApp.instance.appRepository.apps.value.filter { it.id !in s.hiddenApps }
    val spec = rememberPlainIconSpec()
    SettingsGroup(dividerInset = 62.dp) {
        apps.forEach { entry ->
            CheckRow(
                entry.label,
                s.gestureApps[type.name] == entry.id && s.gestureAction(type) == GestureAction.LAUNCH_APP,
                {
                    nav.update { it.withGestureAction(type, GestureAction.LAUNCH_APP).copy(gestureApps = it.gestureApps + (type.name to entry.id)) }
                    nav.pop()
                },
                leading = { AppIconImage(entry.id, 32.dp, spec = spec, allowShadow = false) },
            )
        }
    }
}

// ---- 通知 ------------------------------------------------------------------

@Composable
private fun NotificationsContent(nav: SettingsNav) {
    val context = LocalContext.current
    val s = LocalLauncherSettings.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val granted = remember(tick) { SystemActions.isNotificationAccessGranted(context) }
    val connected by NotificationState.connected.collectAsState()
    SettingsGroup(footer = stringResource(R.string.notification_access_footer)) {
        NavRow(
            stringResource(R.string.notification_access),
            value = stringResource(if (granted) R.string.status_on else R.string.status_off),
            subtitle = if (granted && !connected) stringResource(R.string.notification_access_waiting) else null,
        ) { SystemActions.openNotificationAccess(context) }
    }
    ChoiceGroup(
        header = stringResource(R.string.badge_style),
        footer = null,
        options = BadgeStyle.entries,
        selected = s.badgeStyle,
        label = { stringResource(it.label) },
        onSelect = { v -> nav.update { it.copy(badgeStyle = v) } },
    )
    SettingsGroup {
        ColorRow(
            stringResource(R.string.badge_color),
            listOf(IosColors.Red, IosColors.Orange, IosColors.Blue, IosColors.Green, IosColors.Purple, IosColors.Pink, IosColors.Gray),
            s.badgeColor,
        ) { c -> nav.update { it.copy(badgeColor = c.toArgb()) } }
    }
}

// ---- ロック / 非表示 -----------------------------------------------------------

@Composable
private fun PrivacyContent(nav: SettingsNav) {
    val context = LocalContext.current
    val activity = context as Activity
    val s = LocalLauncherSettings.current
    var tab by remember { mutableIntStateOf(0) }
    val apps = LauncherApp.instance.appRepository.apps.value
    val spec = rememberPlainIconSpec()
    val authTitle = stringResource(R.string.auth_change_lock)

    SettingsGroup(footer = stringResource(R.string.privacy_footer)) {
        SwitchRow(stringResource(R.string.privacy_auth_hidden), s.requireAuthForHidden, { v ->
            AppLock.authenticate(activity, authTitle) { nav.update { it.copy(requireAuthForHidden = v) } }
        })
        SwitchRow(stringResource(R.string.privacy_auth_settings), s.requireAuthForSettings, { v ->
            AppLock.authenticate(activity, authTitle) { nav.update { it.copy(requireAuthForSettings = v) } }
        })
    }
    SettingsGroup(dividerInset = 62.dp) {
        SegmentRow(listOf(stringResource(R.string.privacy_locked), stringResource(R.string.privacy_hidden)), tab, { tab = it })
        apps.forEach { entry ->
            val checked = if (tab == 0) entry.id in s.lockedApps else entry.id in s.hiddenApps
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconImage(entry.id, 32.dp, spec = spec, allowShadow = false)
                Spacer(Modifier.width(14.dp))
                Text(entry.label, color = SettingsColors.label(), fontSize = 17.sp, modifier = Modifier.weight(1f))
                com.aa.launcher.ui.common.IosSwitch(checked, { v ->
                    AppLock.authenticate(activity, authTitle) {
                        nav.update { cur ->
                            if (tab == 0) {
                                cur.copy(lockedApps = if (v) cur.lockedApps + entry.id else cur.lockedApps - entry.id)
                            } else {
                                cur.copy(hiddenApps = if (v) cur.hiddenApps + entry.id else cur.hiddenApps - entry.id)
                            }
                        }
                    }
                })
            }
        }
    }
}

// ---- アニメーション ------------------------------------------------------------

@Composable
private fun AnimationsContent(nav: SettingsNav) {
    val s = LocalLauncherSettings.current
    SettingsGroup(header = stringResource(R.string.anim_speed)) {
        SliderRow(
            value = s.animationSpeed,
            onChange = { v -> nav.update { it.copy(animationSpeed = (v * 4).toInt() / 4f) } },
            range = 0.5f..2f,
            title = stringResource(R.string.anim_speed),
            valueLabel = "×%.2f".format(Locale.US, s.animationSpeed),
            startLabel = stringResource(R.string.anim_slow),
            endLabel = stringResource(R.string.anim_fast),
        )
    }
    ChoiceGroup(
        header = stringResource(R.string.anim_open),
        footer = null,
        options = OpenAnimation.entries,
        selected = s.openAnimation,
        label = { stringResource(it.label) },
        onSelect = { v -> nav.update { it.copy(openAnimation = v) } },
    )
    SettingsGroup {
        SwitchRow(stringResource(R.string.anim_wiggle), s.wiggleInEditMode, { v -> nav.update { it.copy(wiggleInEditMode = v) } })
        SwitchRow(stringResource(R.string.anim_haptics), s.haptics, { v -> nav.update { it.copy(haptics = v) } })
        SwitchRow(stringResource(R.string.reduce_motion), s.reduceMotion, { v -> nav.update { it.copy(reduceMotion = v) } })
    }
}

// ---- コントロールセンター --------------------------------------------------------

@Composable
private fun ControlCenterContent(nav: SettingsNav) {
    val context = LocalContext.current
    val s = LocalLauncherSettings.current
    SettingsGroup(footer = stringResource(R.string.cc_footer)) {
        SwitchRow(stringResource(R.string.cc_enable), s.controlCenterEnabled, { v -> nav.update { it.copy(controlCenterEnabled = v) } })
    }
    SettingsGroup(header = stringResource(R.string.cc_controls)) {
        ControlTile.entries.forEach { tile ->
            SwitchRow(stringResource(tile.label), tile in s.controlTiles, { v ->
                nav.update { cur ->
                    val list = if (v) (cur.controlTiles + tile).distinct() else cur.controlTiles - tile
                    cur.copy(controlTiles = ControlTile.entries.filter { it in list })
                }
            })
        }
    }
    SettingsGroup(footer = stringResource(R.string.cc_permissions_footer)) {
        ButtonRow(stringResource(R.string.cc_grant_write_settings), { SystemActions.openWriteSettings(context) })
        ButtonRow(stringResource(R.string.cc_grant_dnd), { SystemActions.openDndAccess(context) })
        ButtonRow(stringResource(R.string.notification_access), { SystemActions.openNotificationAccess(context) })
    }
}

// ---- アクセシビリティ -----------------------------------------------------------

@Composable
private fun AccessibilityContent(nav: SettingsNav) {
    val s = LocalLauncherSettings.current
    SettingsGroup(footer = stringResource(R.string.reduce_transparency_footer)) {
        SwitchRow(stringResource(R.string.reduce_transparency), s.reduceTransparency, { v -> nav.update { it.copy(reduceTransparency = v) } })
        SwitchRow(stringResource(R.string.increase_contrast), s.increaseContrast, { v -> nav.update { it.copy(increaseContrast = v) } })
    }
    SettingsGroup {
        SwitchRow(stringResource(R.string.bold_text), s.boldText, { v -> nav.update { it.copy(boldText = v) } })
        SwitchRow(stringResource(R.string.reduce_motion), s.reduceMotion, { v -> nav.update { it.copy(reduceMotion = v) } }, subtitle = stringResource(R.string.reduce_motion_sub))
    }
}

// ---- バックアップ ---------------------------------------------------------------

@Composable
private fun BackupContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmSettings by remember { mutableStateOf(false) }
    var confirmUsage by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = BackupManager.export(context, uri)
            Toast.makeText(context, if (ok) R.string.backup_exported else R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val ok = BackupManager.import(context, uri)
            Toast.makeText(context, if (ok) R.string.backup_imported else R.string.backup_import_failed, Toast.LENGTH_SHORT).show()
        }
    }
    SettingsGroup(header = stringResource(R.string.backup_header), footer = stringResource(R.string.backup_footer)) {
        ButtonRow(stringResource(R.string.backup_export), {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            exporter.launch("liquid-launcher-$stamp.json")
        })
        ButtonRow(stringResource(R.string.backup_import), { importer.launch(arrayOf("application/json", "text/plain", "*/*")) })
    }
    SettingsGroup(header = stringResource(R.string.reset)) {
        ButtonRow(stringResource(R.string.reset_settings), { confirmSettings = true }, destructive = true)
        ButtonRow(stringResource(R.string.reset_usage), { confirmUsage = true }, destructive = true)
    }
    if (confirmSettings) {
        IosAlert(
            title = stringResource(R.string.reset_settings),
            message = stringResource(R.string.reset_settings_message),
            confirmLabel = stringResource(R.string.reset),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = { LauncherApp.instance.settingsRepository.reset() },
            onDismiss = { confirmSettings = false },
        )
    }
    if (confirmUsage) {
        IosAlert(
            title = stringResource(R.string.reset_usage),
            message = stringResource(R.string.reset_usage_message),
            confirmLabel = stringResource(R.string.reset),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = { LauncherApp.instance.usageTracker.clearAll() },
            onDismiss = { confirmUsage = false },
        )
    }
}

// ---- 情報 ------------------------------------------------------------------

@Composable
private fun AboutContent() {
    val context = LocalContext.current
    SettingsGroup {
        ValueRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
        ValueRow(stringResource(R.string.about_apps), LauncherApp.instance.appRepository.apps.value.size.toString())
        ValueRow(stringResource(R.string.about_design), stringResource(R.string.about_design_value))
    }
    SettingsGroup {
        NavRow(stringResource(R.string.about_home_settings), Icons.Rounded.Home, IosColors.Gray) { SystemActions.openHomeSettings(context) }
        ButtonRow(stringResource(R.string.about_restart), {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
        }, destructive = true)
    }
    SettingsFooterText(stringResource(R.string.about_disclaimer))
}
