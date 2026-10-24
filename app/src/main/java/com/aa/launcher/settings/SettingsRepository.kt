package com.aa.launcher.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.aa.launcher.core.LauncherJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream

private object SettingsSerializer : Serializer<LauncherSettings> {
    override val defaultValue: LauncherSettings = LauncherSettings()

    override suspend fun readFrom(input: InputStream): LauncherSettings = try {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) defaultValue else LauncherJson.decodeFromString(LauncherSettings.serializer(), text)
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot read launcher settings", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Cannot read launcher settings", e)
    }

    override suspend fun writeTo(t: LauncherSettings, output: OutputStream) {
        output.write(LauncherJson.encodeToString(LauncherSettings.serializer(), t).encodeToByteArray())
    }
}

class SettingsRepository(context: Context, private val scope: CoroutineScope) {

    private val dataStore: DataStore<LauncherSettings> = DataStoreFactory.create(
        serializer = SettingsSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { LauncherSettings() },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.dataStoreFile("launcher_settings.json") },
    )

    // 起動直後にデフォルト値でレイアウトが一瞬ずれるのを避けるため、最初の値だけ同期的に読む。
    private val initial: LauncherSettings = runBlocking { runCatching { dataStore.data.first() }.getOrDefault(LauncherSettings()) }

    val settings: StateFlow<LauncherSettings> = dataStore.data.stateIn(scope, SharingStarted.Eagerly, initial)

    val current: LauncherSettings get() = settings.value

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        scope.launch { dataStore.updateData(transform) }
    }

    fun replace(value: LauncherSettings) = update { value }

    fun reset() = update { LauncherSettings(onboardingDone = true) }
}
