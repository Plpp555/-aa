package com.aa.launcher.ui.spotlight

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aa.launcher.LauncherApp
import com.aa.launcher.R
import com.aa.launcher.core.AppEntry
import com.aa.launcher.core.Calculator
import com.aa.launcher.core.SearchText
import com.aa.launcher.core.SystemActions
import com.aa.launcher.settings.SettingsActivity
import com.aa.launcher.ui.common.AppIconImage
import com.aa.launcher.ui.common.GlassSearchField
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.home.LauncherViewModel
import com.aa.launcher.ui.home.LocalApps
import com.aa.launcher.ui.theme.LocalIsDark
import com.aa.launcher.ui.theme.LocalLauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 設定項目のショートカット (Spotlight の「設定」結果)。 */
data class SettingShortcut(
    @StringRes val title: Int,
    val keywords: String,
    val icon: ImageVector,
    val color: Color,
    val action: (Context) -> Unit,
)

private fun launcherRoute(route: String): (Context) -> Unit = { ctx ->
    val intent = Intent(ctx, SettingsActivity::class.java)
        .putExtra(SettingsActivity.EXTRA_ROUTE, route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    SystemActions.start(ctx, intent)
}

private fun system(action: String): (Context) -> Unit = { ctx -> SystemActions.start(ctx, Intent(action)) }

val SettingShortcuts = listOf(
    SettingShortcut(R.string.settings_wallpaper, "wallpaper background 壁紙", Icons.Rounded.Wallpaper, Color(0xFF32ADE6), launcherRoute("wallpaper")),
    SettingShortcut(R.string.settings_appearance, "appearance dark light glass theme 外観 ダーク ガラス", Icons.Rounded.LightMode, Color(0xFF0A84FF), launcherRoute("appearance")),
    SettingShortcut(R.string.settings_home_screen, "home screen grid layout ホーム画面 グリッド", Icons.Rounded.GridView, Color(0xFF5856D6), launcherRoute("home")),
    SettingShortcut(R.string.settings_app_icons, "icons style tinted clear icon pack アイコン", Icons.Rounded.Apps, Color(0xFFFF2D55), launcherRoute("icons")),
    SettingShortcut(R.string.settings_dock, "dock ドック", Icons.Rounded.Dock, Color(0xFF34C759), launcherRoute("dock")),
    SettingShortcut(R.string.settings_folders, "folders フォルダ", Icons.Rounded.Folder, Color(0xFFFF9500), launcherRoute("folders")),
    SettingShortcut(R.string.settings_widgets, "widgets today ウィジェット 今日", Icons.Rounded.Widgets, Color(0xFF30B0C7), launcherRoute("today")),
    SettingShortcut(R.string.settings_search, "search spotlight 検索", Icons.Rounded.Search, Color(0xFF8E8E93), launcherRoute("search")),
    SettingShortcut(R.string.settings_gestures, "gestures swipe double tap ジェスチャー", Icons.Rounded.Gesture, Color(0xFFAF52DE), launcherRoute("gestures")),
    SettingShortcut(R.string.settings_notifications, "notifications badges 通知 バッジ", Icons.Rounded.Notifications, Color(0xFFFF3B30), launcherRoute("notifications")),
    SettingShortcut(R.string.settings_privacy, "locked hidden apps face id privacy ロック 非表示", Icons.Rounded.Lock, Color(0xFF0A84FF), launcherRoute("privacy")),
    SettingShortcut(R.string.settings_animations, "animation haptics motion アニメーション 触覚", Icons.Rounded.Animation, Color(0xFFFF9500), launcherRoute("animations")),
    SettingShortcut(R.string.settings_control_center, "control center コントロールセンター", Icons.Rounded.Tune, Color(0xFF8E8E93), launcherRoute("controlcenter")),
    SettingShortcut(R.string.settings_accessibility, "accessibility reduce transparency motion アクセシビリティ", Icons.Rounded.AccessibilityNew, Color(0xFF0A84FF), launcherRoute("accessibility")),
    SettingShortcut(R.string.settings_backup, "backup restore export import バックアップ", Icons.Rounded.Backup, Color(0xFF34C759), launcherRoute("backup")),
    SettingShortcut(R.string.sys_wifi, "wifi wi-fi wireless network ネットワーク", Icons.Rounded.Wifi, Color(0xFF0A84FF), system(Settings.ACTION_WIFI_SETTINGS)),
    SettingShortcut(R.string.sys_bluetooth, "bluetooth", Icons.Rounded.Bluetooth, Color(0xFF0A84FF), system(Settings.ACTION_BLUETOOTH_SETTINGS)),
    SettingShortcut(R.string.sys_display, "display brightness screen 画面 明るさ", Icons.Rounded.LightMode, Color(0xFF0A84FF), system(Settings.ACTION_DISPLAY_SETTINGS)),
    SettingShortcut(R.string.sys_sound, "sound volume ringtone サウンド 音量", Icons.Rounded.Notifications, Color(0xFFFF2D55), system(Settings.ACTION_SOUND_SETTINGS)),
    SettingShortcut(R.string.sys_battery, "battery power saver バッテリー", Icons.Rounded.BatteryFull, Color(0xFF34C759), system(Settings.ACTION_BATTERY_SAVER_SETTINGS)),
    SettingShortcut(R.string.sys_apps, "apps applications アプリ", Icons.Rounded.Apps, Color(0xFF5856D6), system(Settings.ACTION_APPLICATION_SETTINGS)),
    SettingShortcut(R.string.sys_location, "location gps 位置情報", Icons.Rounded.LocationOn, Color(0xFF0A84FF), system(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
    SettingShortcut(R.string.sys_security, "security lock password セキュリティ", Icons.Rounded.Security, Color(0xFF8E8E93), system(Settings.ACTION_SECURITY_SETTINGS)),
    SettingShortcut(R.string.sys_language, "language input 言語", Icons.Rounded.Translate, Color(0xFF8E8E93), system(Settings.ACTION_LOCALE_SETTINGS)),
    SettingShortcut(R.string.sys_storage, "storage space ストレージ", Icons.Rounded.Storage, Color(0xFF8E8E93), system(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
    SettingShortcut(R.string.sys_accessibility, "accessibility talkback ユーザー補助", Icons.Rounded.AccessibilityNew, Color(0xFF0A84FF), system(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
    SettingShortcut(R.string.sys_about, "about phone device info 端末情報", Icons.Rounded.Info, Color(0xFF8E8E93), system(Settings.ACTION_DEVICE_INFO_SETTINGS)),
    SettingShortcut(R.string.sys_settings, "settings system 設定", Icons.Rounded.Settings, Color(0xFF8E8E93), system(Settings.ACTION_SETTINGS)),
)

private data class ContactResult(val id: Long, val lookupKey: String, val name: String)

@Composable
fun SpotlightOverlay(vm: LauncherViewModel, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 8 },
        exit = fadeOut() + slideOutVertically { -it / 8 },
    ) {
        SpotlightContent(vm)
    }
}

@Composable
private fun SpotlightContent(vm: LauncherViewModel) {
    val context = LocalContext.current
    val activity = context as Activity
    val settings = LocalLauncherSettings.current
    val dark = LocalIsDark.current
    val apps = LocalApps.current
    val app = LauncherApp.instance
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val usage by app.usageTracker.version.collectAsState()

    LaunchedEffect(Unit) {
        if (settings.autoOpenKeyboard) runCatching { focus.requestFocus() }
    }

    val normalized = SearchText.normalize(query.trim())
    val visibleApps = remember(apps, settings.hiddenApps) { apps.values.filter { it.id !in settings.hiddenApps } }
    val appResults = remember(visibleApps, normalized, settings.searchApps) {
        if (!settings.searchApps || normalized.isEmpty()) emptyList()
        else visibleApps.map { it to SearchText.score(it.searchLabel, normalized) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<AppEntry, Int>> { it.second }.thenBy { it.first.label })
            .map { it.first }
            .take(12)
    }
    val settingResults = remember(normalized, settings.searchSettings) {
        if (!settings.searchSettings || normalized.length < 2) emptyList()
        else SettingShortcuts.filter { s ->
            SearchText.normalize(context.getString(s.title)).contains(normalized) || SearchText.normalize(s.keywords).contains(normalized)
        }.take(6)
    }
    val calc = remember(query, settings.searchCalculator) { if (settings.searchCalculator) Calculator.evaluate(query) else null }
    var contactsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsGranted = it }
    val contacts by produceState<List<ContactResult>>(emptyList(), normalized, contactsGranted, settings.searchContacts) {
        value = if (settings.searchContacts && contactsGranted && query.trim().length >= 2) queryContacts(context, query.trim()) else emptyList()
    }
    val suggestions = remember(visibleApps, usage) { app.usageTracker.suggestions(visibleApps, 8) }
    val recent = remember(usage) { app.usageTracker.recentSearches() }

    fun close() {
        vm.spotlightOpen = false
    }

    fun launch(entry: AppEntry, bounds: androidx.compose.ui.geometry.Rect?) {
        if (settings.rememberRecentSearches && query.isNotBlank()) app.usageTracker.addRecentSearch(query)
        close()
        vm.launchApp(activity, entry.id, bounds)
    }

    fun webSearch() {
        if (query.isBlank()) return
        if (settings.rememberRecentSearches) app.usageTracker.addRecentSearch(query)
        close()
        SystemActions.webSearch(context, settings.searchEngine, query.trim())
    }

    val textColor = Color.White
    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.25f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search),
                    focusRequester = focus,
                    modifier = Modifier.weight(1f),
                    onSearch = {
                        val first = appResults.firstOrNull()
                        if (first != null) launch(first, null) else webSearch()
                    },
                )
                Text(
                    stringResource(R.string.action_cancel),
                    color = textColor,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(start = 12.dp).clickable { close() },
                )
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (normalized.isEmpty()) {
                    if (settings.showSiriSuggestions && suggestions.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.siri_suggestions)) }
                        item { AppGridCard(suggestions) { e, b -> launch(e, b) } }
                    }
                    if (settings.rememberRecentSearches && recent.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader(stringResource(R.string.recent_searches), Modifier.weight(1f))
                                Text(
                                    stringResource(R.string.action_clear),
                                    color = Color(0xFF0A84FF),
                                    fontSize = 14.sp,
                                    modifier = Modifier.clickable { app.usageTracker.clearRecentSearches() },
                                )
                            }
                        }
                        item {
                            ResultCard {
                                recent.forEach { r ->
                                    ResultRow(Icons.Rounded.History, Color(0xFF8E8E93), r, null) { query = r }
                                }
                            }
                        }
                    }
                } else {
                    if (appResults.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.top_hits)) }
                        item { AppGridCard(appResults.take(8)) { e, b -> launch(e, b) } }
                    }
                    if (calc != null) {
                        item { SectionHeader(stringResource(R.string.calculator)) }
                        item {
                            ResultCard {
                                ResultRow(Icons.Rounded.Calculate, Color(0xFFFF9500), "= $calc", query.trim()) {
                                    val cm = context.getSystemService(ClipboardManager::class.java)
                                    cm.setPrimaryClip(ClipData.newPlainText("result", calc))
                                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    if (settingResults.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.settings_title)) }
                        item {
                            ResultCard {
                                settingResults.forEach { s ->
                                    ResultRow(s.icon, s.color, stringResource(s.title), null) {
                                        close()
                                        s.action(context)
                                    }
                                }
                            }
                        }
                    }
                    if (settings.searchContacts) {
                        if (!contactsGranted) {
                            item {
                                ResultCard {
                                    ResultRow(Icons.Rounded.Person, Color(0xFF8E8E93), stringResource(R.string.allow_contacts), null) {
                                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                }
                            }
                        } else if (contacts.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.contacts)) }
                            item {
                                ResultCard {
                                    contacts.forEach { c ->
                                        ResultRow(Icons.Rounded.Person, Color(0xFF8E8E93), c.name, null) {
                                            close()
                                            val uri = ContactsContract.Contacts.getLookupUri(c.id, c.lookupKey)
                                            SystemActions.start(context, Intent(Intent.ACTION_VIEW, uri))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (settings.searchWeb || settings.searchPlayStore) {
                        item { SectionHeader(stringResource(R.string.search_more)) }
                        item {
                            ResultCard {
                                if (settings.searchWeb) {
                                    ResultRow(
                                        Icons.Rounded.Language,
                                        Color(0xFF0A84FF),
                                        stringResource(R.string.search_web_for, query.trim()),
                                        stringResource(settings.searchEngine.label),
                                    ) { webSearch() }
                                }
                                if (settings.searchPlayStore) {
                                    ResultRow(Icons.Rounded.Shop, Color(0xFF34C759), stringResource(R.string.search_store_for, query.trim()), null) {
                                        close()
                                        SystemActions.playStoreSearch(context, query.trim())
                                    }
                                }
                            }
                        }
                    }
                    if (appResults.isEmpty() && calc == null && settingResults.isEmpty() && contacts.isEmpty() && !settings.searchWeb) {
                        item {
                            Text(stringResource(R.string.no_results), color = textColor.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth().padding(24.dp))
                        }
                    }
                }
            }
        }
    }
}

private suspend fun queryContacts(context: Context, query: String): List<ContactResult> = withContext(Dispatchers.IO) {
    runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            val list = ArrayList<ContactResult>()
            while (c.moveToNext() && list.size < 6) {
                list += ContactResult(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
            }
            list
        }.orEmpty()
    }.getOrDefault(emptyList())
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = modifier.padding(start = 4.dp))
}

@Composable
private fun ResultCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(24.dp), strength = 1.2f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(vertical = 4.dp),
    ) { content() }
}

@Composable
private fun ResultRow(icon: ImageVector, color: Color, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AppGridCard(apps: List<AppEntry>, onLaunch: (AppEntry, androidx.compose.ui.geometry.Rect?) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(24.dp), strength = 1.2f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { entry ->
                    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onLaunch(entry, coords?.boundsInRoot()) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIconImage(entry.id, 56.dp, Modifier.onGloballyPositioned { coords = it }, workBadge = entry.isWorkProfile)
                        Spacer(Modifier.height(4.dp))
                        Text(entry.label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
