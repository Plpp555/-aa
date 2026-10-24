package com.aa.launcher.core

import android.content.Context
import android.net.Uri
import com.aa.launcher.LauncherApp
import com.aa.launcher.settings.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class LauncherBackup(
    val format: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val settings: LauncherSettings,
    val layout: HomeLayout?,
)

/** 設定とホーム画面レイアウトの書き出し・読み込み。 */
object BackupManager {

    suspend fun export(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val app = LauncherApp.instance
        val backup = LauncherBackup(settings = app.settingsRepository.current, layout = app.layoutRepository.current)
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(LauncherJson.encodeToString(LauncherBackup.serializer(), backup).encodeToByteArray())
            } ?: return@withContext false
            true
        }.getOrDefault(false)
    }

    suspend fun import(context: Context, uri: Uri): Boolean {
        val backup = withContext(Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching null
                LauncherJson.decodeFromString(LauncherBackup.serializer(), text)
            }.getOrNull()
        } ?: return false
        val app = LauncherApp.instance
        app.settingsRepository.replace(backup.settings.copy(onboardingDone = true))
        backup.layout?.let { layout ->
            // Android ウィジェットの ID は端末ごとに異なるので取り込まない
            fun strip(items: List<HomeItem>) = items.filter { !(it is WidgetItem && it.kind == WidgetKind.APP_WIDGET) }
            val cleaned = layout.copy(
                pages = layout.pages.map { it.copy(items = strip(it.items)) },
                today = layout.today.filter { it.kind != WidgetKind.APP_WIDGET },
            )
            app.layoutRepository.replaceAll(cleaned)
            app.layoutRepository.sync(app.appRepository.apps.value, false, backup.settings.hiddenApps)
        }
        return true
    }
}
