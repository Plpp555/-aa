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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

    /**
     * 画面は常にこのメモリ上の値を参照し、変更は即座に反映する。
     * ファイルへの保存はまとめて行う (スライダー操作のような連続変更で毎回書き込まないため)。
     */
    private val _settings = MutableStateFlow(initial)
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    val current: LauncherSettings get() = _settings.value

    private var saveJob: Job? = null

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        val before = _settings.value
        _settings.update(transform)
        if (_settings.value == before) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            persist()
        }
    }

    /** 保留中の変更をすぐに保存する (画面を離れるとき)。 */
    fun flush() {
        if (saveJob?.isActive != true) return
        saveJob?.cancel()
        saveJob = scope.launch { persist() }
    }

    private suspend fun persist() {
        val value = _settings.value
        runCatching { dataStore.updateData { value } }
    }

    fun replace(value: LauncherSettings) = update { value }

    fun reset() = update { LauncherSettings(onboardingDone = true) }

    private companion object {
        const val SAVE_DELAY_MS = 300L
    }
}
