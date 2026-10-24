package com.aa.launcher.core

import android.content.Context
import android.content.pm.ShortcutInfo
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aa.launcher.LauncherApp
import com.aa.launcher.settings.IconShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class IconSpec(
    val style: RenderStyle,
    val shape: IconShape,
    val tint: Int,
    val pack: String?,
    val sizePx: Int,
)

/** アイコンの描画結果をキャッシュする。 */
class IconRepository(private val context: Context, private val apps: AppRepository, private val packs: IconPackManager) {

    private val cache = object : LruCache<String, ImageBitmap>(maxCacheKb()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height * 4) / 1024
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(4)

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun key(id: String, spec: IconSpec) = "$id|${spec.style}|${spec.shape}|${spec.tint}|${spec.pack}|${spec.sizePx}"

    fun peek(appId: String, spec: IconSpec): ImageBitmap? = cache.get(key(appId, spec))

    suspend fun load(appId: String, spec: IconSpec): ImageBitmap? = withContext(dispatcher) {
        val k = key(appId, spec)
        cache.get(k)?.let { return@withContext it }
        val component = ComponentKey.parse(appId) ?: return@withContext null
        var fromPack = false
        val drawable = spec.pack?.let { packs.iconFor(it, component.componentName) }?.also { fromPack = true }
            ?: apps.activityInfo(component)?.getIcon(context.resources.displayMetrics.densityDpi)
            ?: runCatching { context.packageManager.getApplicationIcon(component.packageName) }.getOrNull()
            ?: return@withContext null
        val bitmap = runCatching {
            IconRenderer.render(drawable.mutate(), spec.style, spec.shape, spec.sizePx, spec.tint, fromPack).asImageBitmap()
        }.getOrNull() ?: return@withContext null
        cache.put(k, bitmap)
        bitmap
    }

    suspend fun loadShortcut(info: ShortcutInfo, spec: IconSpec): ImageBitmap? = withContext(dispatcher) {
        val k = key("shortcut:${info.`package`}/${info.id}", spec)
        cache.get(k)?.let { return@withContext it }
        val drawable = apps.shortcutIcon(info) ?: return@withContext null
        val bitmap = runCatching {
            IconRenderer.render(drawable.mutate(), spec.style, spec.shape, spec.sizePx, spec.tint, false).asImageBitmap()
        }.getOrNull() ?: return@withContext null
        cache.put(k, bitmap)
        bitmap
    }

    fun invalidate() {
        cache.evictAll()
        packs.clear()
        _version.value++
    }

    private companion object {
        fun maxCacheKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceIn(8 * 1024, 64 * 1024)
    }
}

@Composable
fun rememberAppIcon(appId: String, spec: IconSpec): ImageBitmap? {
    val repo = LauncherApp.instance.iconRepository
    val version by repo.version.collectAsState()
    val state = produceState(initialValue = repo.peek(appId, spec), appId, spec, version) {
        value = repo.peek(appId, spec) ?: repo.load(appId, spec)
    }
    return state.value
}
