package com.aa.launcher.settings

import androidx.annotation.StringRes
import com.aa.launcher.R
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(@StringRes val label: Int) {
    SYSTEM(R.string.option_automatic),
    LIGHT(R.string.option_light),
    DARK(R.string.option_dark),
}

/** ホーム画面アイコンの見た目 (iOS 18 以降の「カスタマイズ」に相当)。 */
@Serializable
enum class IconStyle(@StringRes val label: Int) {
    AUTOMATIC(R.string.option_automatic),
    LIGHT(R.string.option_default),
    DARK(R.string.option_dark),
    CLEAR(R.string.option_clear),
    TINTED(R.string.option_tinted),
}

@Serializable
enum class ClearVariant(@StringRes val label: Int) {
    AUTOMATIC(R.string.option_automatic),
    LIGHT(R.string.option_light),
    DARK(R.string.option_dark),
}

@Serializable
enum class IconShape(@StringRes val label: Int) {
    SQUIRCLE(R.string.shape_squircle),
    ROUNDED_SQUARE(R.string.shape_rounded),
    CIRCLE(R.string.shape_circle),
    TEARDROP(R.string.shape_teardrop),
    SQUARE(R.string.shape_square),
    SYSTEM(R.string.shape_system),
}

@Serializable
enum class WallpaperSource(@StringRes val label: Int) {
    BUILTIN(R.string.wallpaper_source_builtin),
    CUSTOM(R.string.wallpaper_source_photo),
    SYSTEM(R.string.wallpaper_source_system),
}

@Serializable
enum class DockStyle(@StringRes val label: Int) {
    GLASS(R.string.dock_style_glass),
    OPAQUE(R.string.dock_style_opaque),
    TRANSPARENT(R.string.dock_style_transparent),
}

@Serializable
enum class BadgeStyle(@StringRes val label: Int) {
    NUMBER(R.string.badge_number),
    DOT(R.string.badge_dot),
    OFF(R.string.option_off),
}

@Serializable
enum class PageEffect(@StringRes val label: Int) {
    SLIDE(R.string.effect_slide),
    DEPTH(R.string.effect_depth),
    CUBE(R.string.effect_cube),
    FADE(R.string.effect_fade),
}

@Serializable
enum class OpenAnimation(@StringRes val label: Int) {
    ZOOM(R.string.anim_zoom),
    REVEAL(R.string.anim_reveal),
    FADE(R.string.anim_fade),
    SYSTEM(R.string.anim_system),
    NONE(R.string.option_none),
}

@Serializable
enum class ClockFormat(@StringRes val label: Int) {
    SYSTEM(R.string.option_automatic),
    H12(R.string.clock_12h),
    H24(R.string.clock_24h),
}

@Serializable
enum class SearchEngine(@StringRes val label: Int, val urlTemplate: String) {
    GOOGLE(R.string.engine_google, "https://www.google.com/search?q=%s"),
    BING(R.string.engine_bing, "https://www.bing.com/search?q=%s"),
    DUCKDUCKGO(R.string.engine_duckduckgo, "https://duckduckgo.com/?q=%s"),
    YAHOO(R.string.engine_yahoo, "https://search.yahoo.com/search?p=%s"),
    YAHOO_JAPAN(R.string.engine_yahoo_japan, "https://search.yahoo.co.jp/search?p=%s"),
    ECOSIA(R.string.engine_ecosia, "https://www.ecosia.org/search?q=%s"),
    BRAVE(R.string.engine_brave, "https://search.brave.com/search?q=%s"),
}

@Serializable
enum class GestureAction(@StringRes val label: Int) {
    NONE(R.string.option_none),
    SPOTLIGHT(R.string.gesture_spotlight),
    NOTIFICATIONS(R.string.gesture_notifications),
    QUICK_SETTINGS(R.string.gesture_quick_settings),
    CONTROL_CENTER(R.string.gesture_control_center),
    APP_LIBRARY(R.string.gesture_app_library),
    TODAY_VIEW(R.string.gesture_today_view),
    FIRST_PAGE(R.string.gesture_first_page),
    EDIT_MODE(R.string.gesture_edit_mode),
    LOCK_SCREEN(R.string.gesture_lock_screen),
    RECENTS(R.string.gesture_recents),
    LAUNCHER_SETTINGS(R.string.gesture_launcher_settings),
    LAUNCH_APP(R.string.gesture_launch_app),
}

/** ジェスチャーの種類。LAUNCH_APP 用のアプリ割り当てのキーにも使う。 */
@Serializable
enum class GestureType(@StringRes val label: Int) {
    SWIPE_DOWN(R.string.gesture_swipe_down),
    SWIPE_DOWN_RIGHT(R.string.gesture_swipe_down_right),
    SWIPE_UP(R.string.gesture_swipe_up),
    DOUBLE_TAP(R.string.gesture_double_tap),
    HOME_BUTTON(R.string.gesture_home_button),
}

@Serializable
enum class ControlTile(@StringRes val label: Int) {
    CONNECTIVITY(R.string.cc_connectivity),
    MEDIA(R.string.cc_media),
    BRIGHTNESS(R.string.cc_brightness),
    VOLUME(R.string.cc_volume),
    FLASHLIGHT(R.string.cc_flashlight),
    DO_NOT_DISTURB(R.string.cc_dnd),
    ROTATION_LOCK(R.string.cc_rotation),
    TIMER(R.string.cc_timer),
    ALARM(R.string.cc_alarm),
    CALCULATOR(R.string.cc_calculator),
    CAMERA(R.string.cc_camera),
    SCREEN_LOCK(R.string.cc_screen_lock),
    LOCATION(R.string.cc_location),
    DARK_MODE(R.string.cc_dark_mode),
    SETTINGS(R.string.cc_settings),
}

val DefaultControlTiles = listOf(
    ControlTile.CONNECTIVITY,
    ControlTile.MEDIA,
    ControlTile.ROTATION_LOCK,
    ControlTile.DO_NOT_DISTURB,
    ControlTile.BRIGHTNESS,
    ControlTile.VOLUME,
    ControlTile.FLASHLIGHT,
    ControlTile.TIMER,
    ControlTile.CALCULATOR,
    ControlTile.CAMERA,
)

/**
 * ランチャーの全設定。DataStore に JSON として保存される。
 * 項目を追加するときは必ずデフォルト値を付けること (古い JSON との互換性のため)。
 */
@Serializable
data class LauncherSettings(
    // 外観
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** iOS 27 の Liquid Glass スライダー。0 = クリア、1 = 色付き (不透明)。 */
    val glassOpacity: Float = 0.3f,
    val accentColor: Int = 0xFF0A84FF.toInt(),
    val showStatusBar: Boolean = true,

    // アクセシビリティ
    val reduceTransparency: Boolean = false,
    val increaseContrast: Boolean = false,
    val boldText: Boolean = false,
    val reduceMotion: Boolean = false,

    // 壁紙
    val wallpaperSource: WallpaperSource = WallpaperSource.BUILTIN,
    val builtinWallpaper: Int = 0,
    val dimWallpaperInDark: Boolean = true,
    val blurHomeWallpaper: Boolean = false,

    // ホーム画面
    val gridColumns: Int = 4,
    val gridRows: Int = 6,
    val addNewAppsToHome: Boolean = true,
    val showAppLibrary: Boolean = true,
    val showTodayView: Boolean = true,
    val showSearchOnHome: Boolean = true,
    val lockLayout: Boolean = false,
    val pageEffect: PageEffect = PageEffect.SLIDE,
    val showBadgesInAppLibrary: Boolean = true,

    // アプリアイコン
    val iconStyle: IconStyle = IconStyle.LIGHT,
    val clearVariant: ClearVariant = ClearVariant.AUTOMATIC,
    val iconTintColor: Int = 0xFFFFB340.toInt(),
    val tintFromWallpaper: Boolean = false,
    val largeIcons: Boolean = false,
    val iconScale: Float = 1f,
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconPack: String? = null,
    val showLabels: Boolean = true,
    val labelScale: Float = 1f,
    val iconShadow: Boolean = true,

    // ドック
    val showDock: Boolean = true,
    val dockColumns: Int = 4,
    val dockStyle: DockStyle = DockStyle.GLASS,
    val dockLabels: Boolean = false,

    // フォルダ
    val folderColumns: Int = 3,
    val folderBlur: Boolean = true,

    // App ライブラリ
    val librarySuggestions: Boolean = true,
    val libraryRecentlyAdded: Boolean = true,

    // 今日の表示 / ウィジェット
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val clockShowSeconds: Boolean = false,
    val todayShowDate: Boolean = true,

    // 検索 (Spotlight)
    val searchApps: Boolean = true,
    val searchContacts: Boolean = false,
    val searchSettings: Boolean = true,
    val searchWeb: Boolean = true,
    val searchCalculator: Boolean = true,
    val searchPlayStore: Boolean = true,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val showSiriSuggestions: Boolean = true,
    val autoOpenKeyboard: Boolean = true,
    val rememberRecentSearches: Boolean = true,

    // ジェスチャー
    val swipeDownAction: GestureAction = GestureAction.SPOTLIGHT,
    val swipeDownRightAction: GestureAction = GestureAction.CONTROL_CENTER,
    val swipeUpAction: GestureAction = GestureAction.APP_LIBRARY,
    val doubleTapAction: GestureAction = GestureAction.NONE,
    val homeButtonAction: GestureAction = GestureAction.FIRST_PAGE,
    val gestureApps: Map<String, String> = emptyMap(),

    // 通知バッジ
    val badgeStyle: BadgeStyle = BadgeStyle.NUMBER,
    val badgeColor: Int = 0xFFFF3B30.toInt(),

    // ロック / 非表示 App (iOS 18)
    val lockedApps: Set<String> = emptySet(),
    val hiddenApps: Set<String> = emptySet(),
    val requireAuthForHidden: Boolean = true,
    val requireAuthForSettings: Boolean = false,

    // アニメーションと触覚
    val animationSpeed: Float = 1f,
    val openAnimation: OpenAnimation = OpenAnimation.ZOOM,
    val wiggleInEditMode: Boolean = true,
    val haptics: Boolean = true,

    // コントロールセンター
    val controlCenterEnabled: Boolean = true,
    val controlTiles: List<ControlTile> = DefaultControlTiles,

    // その他
    val onboardingDone: Boolean = false,
) {
    fun gestureAction(type: GestureType): GestureAction = when (type) {
        GestureType.SWIPE_DOWN -> swipeDownAction
        GestureType.SWIPE_DOWN_RIGHT -> swipeDownRightAction
        GestureType.SWIPE_UP -> swipeUpAction
        GestureType.DOUBLE_TAP -> doubleTapAction
        GestureType.HOME_BUTTON -> homeButtonAction
    }

    fun withGestureAction(type: GestureType, action: GestureAction): LauncherSettings = when (type) {
        GestureType.SWIPE_DOWN -> copy(swipeDownAction = action)
        GestureType.SWIPE_DOWN_RIGHT -> copy(swipeDownRightAction = action)
        GestureType.SWIPE_UP -> copy(swipeUpAction = action)
        GestureType.DOUBLE_TAP -> copy(doubleTapAction = action)
        GestureType.HOME_BUTTON -> copy(homeButtonAction = action)
    }

    /** 画面上の実際のアイコン倍率 (「大きい」アイコン設定を含む)。 */
    val effectiveIconScale: Float get() = iconScale * if (largeIcons) 1.18f else 1f

    val effectiveShowLabels: Boolean get() = showLabels && !largeIcons
}
