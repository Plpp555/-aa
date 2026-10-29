package com.aa.launcher.core

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import android.content.pm.ShortcutInfo
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
            IconRenderer.render(drawable.mutate(), spec.style, spec.shape, spec.sizePx, spec.tint, fromPack).toGpu().asImageBitmap()
        }.getOrNull() ?: return@withContext null
        cache.put(k, bitmap)
        bitmap
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    suspend fun loadShortcut(info: ShortcutInfo, spec: IconSpec): ImageBitmap? = withContext(dispatcher) {
        val k = key("shortcut:${info.`package`}/${info.id}", spec)
        cache.get(k)?.let { return@withContext it }
        val drawable = apps.shortcutIcon(info) ?: return@withContext null
        val bitmap = runCatching {
            IconRenderer.render(drawable.mutate(), spec.style, spec.shape, spec.sizePx, spec.tint, false).toGpu().asImageBitmap()
        }.getOrNull() ?: return@withContext null
        cache.put(k, bitmap)
        bitmap
    }

    /**
     * 描画済みアイコンを GPU 上の HARDWARE ビットマップへ移す。
     * Java ヒープを使わず、描画のたびのテクスチャ転送も不要になる (表示品質は同じ)。
     * HARDWARE は Android 8.0 以降。それより前は通常のビットマップのまま使う。
     */
    private fun Bitmap.toGpu(): Bitmap {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return this
        val hw = runCatching { copy(Bitmap.Config.HARDWARE, false) }.getOrNull() ?: return this
        recycle()
        return hw
    }

    /** すべて作り直す (アイコンパックの変更など)。 */
    fun invalidate() {
        cache.evictAll()
        packs.clear()
        _version.value++
    }

    /** 指定パッケージのアイコンだけ作り直す (更新・インストール時)。他のアイコンはキャッシュを使い続ける。 */
    fun invalidatePackage(packageName: String) {
        val prefixes = listOf("$packageName/", "shortcut:$packageName/")
        val stale = cache.snapshot().keys.filter { key -> prefixes.any { key.startsWith(it) } }
        if (stale.isEmpty()) return
        stale.forEach { cache.remove(it) }
        _version.value++
    }

    /** App ショートカットのアイコンだけ破棄する (次にメニューを開いたときに読み直す)。 */
    fun invalidateShortcuts(packageName: String) {
        val prefix = "shortcut:$packageName/"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }

    /** メモリ逼迫時に呼ぶ。表示中のアイコンは Compose 側が保持しているので、キャッシュだけ縮める。 */
    fun trimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> cache.evictAll()
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> cache.trimToSize(cache.maxSize() / 2)
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> cache.trimToSize(cache.maxSize() / 2)
        }
    }

    private companion object {
        fun maxCacheKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceIn(8 * 1024, 64 * 1024)
    }
}

/**
 * アイコンキャッシュの世代。画面のルートで 1 回だけ購読して配る
 * (アイコンごとに Flow を購読しないようにするため)。
 */
val LocalIconVersion = compositionLocalOf { 0 }

@Composable
fun rememberAppIcon(appId: String, spec: IconSpec): ImageBitmap? {
    val repo = LauncherApp.instance.iconRepository
    val version = LocalIconVersion.current
    // 初回はキャッシュから同期的に取得 (毎回の再コンポーズで探し直さない)。
    // キーが変わっても読み込み完了までは前の画像を表示し続けるので、ちらつかない。
    val state = remember(appId) { mutableStateOf(repo.peek(appId, spec)) }
    LaunchedEffect(appId, spec, version) {
        state.value = repo.peek(appId, spec) ?: repo.load(appId, spec)
    }
    return state.value
}
