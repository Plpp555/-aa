package com.aa.launcher.core

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.aa.launcher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/** LauncherApps 経由でインストール済みアプリ (仕事用プロファイル含む) を管理する。 */
class AppRepository(private val context: Context, private val scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** 更新・追加・削除されたパッケージ名 (そのパッケージのアイコンだけ作り直すため)。 */
    private val _packageChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val packageChanges: SharedFlow<String> = _packageChanges.asSharedFlow()

    /** App ショートカットが変わったパッケージ名 (ショートカットのアイコンだけ破棄する。App アイコンは作り直さない)。 */
    private val _shortcutChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val shortcutChanges: SharedFlow<String> = _shortcutChanges.asSharedFlow()

    private var reloadJob: Job? = null

    private fun changed(vararg packageNames: String) {
        packageNames.forEach { _packageChanges.tryEmit(it) }
        reload()
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed(*packageNames)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed(*packageNames)
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = changed(*packageNames)
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = changed(*packageNames)
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
            _shortcutChanges.tryEmit(packageName)
        }
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun reload() {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            // インストール直後は複数のコールバックが連続するのでまとめる
            if (_loaded.value) delay(150)
            val list = withContext(Dispatchers.IO) { runCatching { queryApps() }.getOrNull() }
            if (list != null) {
                _apps.value = list
                _loaded.value = true
            }
        }
    }

    private fun queryApps(): List<AppEntry> {
        val me = Process.myUserHandle()
        val result = ArrayList<AppEntry>()
        for (user in context.userProfiles()) {
            val serial = userManager.getSerialNumberForUser(user)
            val activities = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            for (info in activities) {
                val ai = info.applicationInfo
                val cn = info.componentName
                val key = ComponentKey(cn.packageName, cn.className, serial)
                val label = info.label?.toString()?.trim().orEmpty().ifEmpty { cn.packageName }
                result += AppEntry(
                    key = key,
                    label = label,
                    user = user,
                    category = AppCategories.categorize(ai),
                    firstInstallTime = info.firstInstallTime,
                    isSystem = ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
                    isWorkProfile = user != me,
                )
            }
        }
        val collator = Collator.getInstance()
        return result.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun userFor(serial: Long): UserHandle? = runCatching { userManager.getUserForSerialNumber(serial) }.getOrNull()

    fun serialFor(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    fun activityInfo(key: ComponentKey): LauncherActivityInfo? {
        val user = userFor(key.userSerial) ?: return null
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(key.componentName)
        return runCatching { launcherApps.resolveActivity(intent, user) }.getOrNull()
    }

    fun launch(key: ComponentKey, bounds: Rect?, options: Bundle?): Boolean {
        val user = userFor(key.userSerial) ?: return false
        return try {
            launcherApps.startMainActivity(key.componentName, user, bounds, options)
            true
        } catch (e: Exception) {
            Toast.makeText(context, R.string.error_launch_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openAppInfo(key: ComponentKey, bounds: Rect? = null) {
        val user = userFor(key.userSerial) ?: return
        runCatching { launcherApps.startAppDetailsActivity(key.componentName, user, bounds, null) }
    }

    fun uninstall(key: ComponentKey) {
        val user = userFor(key.userSerial)
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        if (user != null) intent.putExtra("android.intent.extra.USER", user)
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
    }

    /** App ショートカットは Android 7.1 以降。 */
    fun hasShortcutPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    /** iOS のクイックアクションに相当する App ショートカット (Android 7.1 以降。それより前は常に空)。 */
    fun shortcuts(key: ComponentKey): List<ShortcutInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || !hasShortcutPermission()) return emptyList()
        val user = userFor(key.userSerial) ?: return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(key.packageName)
            .setActivity(key.componentName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        val list = runCatching { launcherApps.getShortcuts(query, user) }.getOrNull().orEmpty()
        return list.filter { it.isEnabled && (it.isDynamic || it.isDeclaredInManifest) }
            .sortedWith(compareBy<ShortcutInfo>({ if (it.isDeclaredInManifest) 0 else 1 }, { it.rank }))
            .distinctBy { it.id }
            .take(4)
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun startShortcut(info: ShortcutInfo, bounds: Rect?) {
        runCatching { launcherApps.startShortcut(info, bounds, null) }.onFailure {
            Toast.makeText(context, R.string.error_launch_failed, Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun shortcutIcon(info: ShortcutInfo): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi) }.getOrNull()
}

/**
 * 仕事用プロファイルを含むユーザー一覧。
 * LauncherApps.getProfiles は Android 8.0 以降なので、それより前は UserManager.getUserProfiles (同じ内容) を使う。
 */
fun Context.userProfiles(): List<UserHandle> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        getSystemService(LauncherApps::class.java).profiles
    } else {
        getSystemService(UserManager::class.java).userProfiles
    }
