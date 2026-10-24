package com.aa.launcher.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** ホーム画面レイアウトの保存と変更。変更はメインスレッドから行う。 */
class LayoutRepository(private val context: Context, private val scope: CoroutineScope) {

    private val file = AtomicFile(File(context.filesDir, "home_layout.json"))
    private val _layout = MutableStateFlow<HomeLayout?>(null)
    val layout: StateFlow<HomeLayout?> = _layout.asStateFlow()
    private var saveJob: Job? = null

    var grid: GridSpec = GridSpec(4, 6, 4)
        set(value) {
            if (field == value) return
            field = value
            _layout.value?.let { commit(LayoutOps.normalize(it, value)) }
        }

    val current: HomeLayout? get() = _layout.value

    fun initialize(installed: List<AppEntry>) {
        if (_layout.value != null) return
        val stored = runCatching {
            if (file.baseFile.exists()) {
                LauncherJson.decodeFromString(HomeLayout.serializer(), file.readFully().decodeToString())
            } else {
                null
            }
        }.getOrNull()
        commit(if (stored != null) LayoutOps.normalize(stored, grid) else createDefault(installed))
    }

    fun update(transform: (HomeLayout) -> HomeLayout) {
        val current = _layout.value ?: return
        val next = LayoutOps.normalize(transform(current), grid)
        if (next != current) commit(next)
    }

    fun replaceAll(layout: HomeLayout) = commit(LayoutOps.normalize(layout, grid))

    fun reset(installed: List<AppEntry>) = commit(createDefault(installed))

    /** インストール/アンインストールをレイアウトへ反映する。 */
    fun sync(installed: List<AppEntry>, addNewApps: Boolean, hidden: Set<String>) {
        if (installed.isEmpty()) return
        update { layout ->
            val installedIds = installed.mapTo(HashSet()) { it.id }
            var out = layout
            for (key in LayoutOps.allAppKeys(layout)) {
                if (key !in installedIds || key in hidden) out = LayoutOps.removeAppKey(out, key)
            }
            val newApps = installed.filter { it.id !in layout.knownApps }
            if (addNewApps && layout.knownApps.isNotEmpty()) {
                newApps.filter { it.id !in hidden }.forEach { out = LayoutOps.appendApp(out, it.id, grid) }
            }
            out.copy(knownApps = layout.knownApps.filterTo(HashSet()) { it in installedIds } + newApps.map { it.id })
        }
    }

    private fun commit(layout: HomeLayout) {
        _layout.value = layout
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(250)
            write(layout)
        }
    }

    private fun write(layout: HomeLayout) {
        val bytes = LauncherJson.encodeToString(HomeLayout.serializer(), layout).encodeToByteArray()
        val stream = runCatching { file.startWrite() }.getOrNull() ?: return
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
        }
    }

    private fun createDefault(installed: List<AppEntry>): HomeLayout {
        val dockIds = DefaultApps.dock(context, installed).take(grid.dockSlots)
        val apps = installed.filter { it.id !in dockIds && !it.isWorkProfile }.map { AppItem(newId(), it.id) }
        val widgets = listOf(
            WidgetItem(newId(), WidgetKind.CLOCK, WidgetSize.SMALL),
            WidgetItem(newId(), WidgetKind.CALENDAR, WidgetSize.SMALL),
        )
        val layout = HomeLayout(
            pages = listOf(HomePage(items = widgets + apps)),
            dock = dockIds.map { AppItem(newId(), it) },
            today = listOf(
                WidgetItem(newId(), WidgetKind.SEARCH, WidgetSize.MEDIUM),
                WidgetItem(newId(), WidgetKind.SUGGESTIONS, WidgetSize.MEDIUM),
                WidgetItem(newId(), WidgetKind.BATTERY, WidgetSize.SMALL),
                WidgetItem(newId(), WidgetKind.ANALOG_CLOCK, WidgetSize.SMALL),
                WidgetItem(newId(), WidgetKind.CALENDAR, WidgetSize.MEDIUM),
                WidgetItem(newId(), WidgetKind.NOTE, WidgetSize.MEDIUM),
            ),
            knownApps = installed.mapTo(HashSet()) { it.id },
        )
        return LayoutOps.normalize(layout, grid)
    }
}

/** 初期ドックに置く既定アプリ (電話・メッセージ・ブラウザ・カメラ) を推定する。 */
object DefaultApps {
    fun dock(context: Context, installed: List<AppEntry>): List<String> {
        val pm = context.packageManager
        val packages = LinkedHashSet<String>()
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()?.let(packages::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let(packages::add)
        resolve(pm, Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))?.let(packages::add)
        resolve(pm, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))?.let(packages::add)
        val mine = installed.filter { !it.isWorkProfile }
        val result = packages.mapNotNull { pkg -> mine.firstOrNull { it.key.packageName == pkg }?.id }.toMutableList()
        // 見つからない場合はよく使われるアプリで補う
        val fallbacks = listOf("com.google.android.dialer", "com.google.android.apps.messaging", "com.android.chrome", "com.google.android.GoogleCamera", "com.android.settings")
        for (pkg in fallbacks) {
            if (result.size >= 4) break
            mine.firstOrNull { it.key.packageName == pkg && it.id !in result }?.let { result += it.id }
        }
        return result
    }

    private fun resolve(pm: PackageManager, intent: Intent): String? =
        runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .getOrNull()
            ?.takeIf { it != "android" }
}
