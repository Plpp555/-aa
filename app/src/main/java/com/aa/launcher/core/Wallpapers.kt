package com.aa.launcher.core

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.aa.launcher.R
import com.aa.launcher.settings.WallpaperSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** 背景ぼかし (Liquid Glass の下地) 用の高速ボックスブラー (3 回でガウス近似)。 */
object BlurUtils {
    fun blur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            boxH(pixels, tmp, w, h, radius)
            boxV(tmp, pixels, w, h, radius)
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            var ra = 0; var rr = 0; var rg = 0; var rb = 0
            val row = y * w
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                ra += p ushr 24; rr += (p shr 16) and 0xFF; rg += (p shr 8) and 0xFF; rb += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = ((ra / div) shl 24) or ((rr / div) shl 16) or ((rg / div) shl 8) or (rb / div)
                val pOut = src[row + (x - r).coerceIn(0, w - 1)]
                val pIn = src[row + (x + r + 1).coerceIn(0, w - 1)]
                ra += (pIn ushr 24) - (pOut ushr 24)
                rr += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                rg += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                rb += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
    }

    private fun boxV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var ra = 0; var rr = 0; var rg = 0; var rb = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                ra += p ushr 24; rr += (p shr 16) and 0xFF; rg += (p shr 8) and 0xFF; rb += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = ((ra / div) shl 24) or ((rr / div) shl 16) or ((rg / div) shl 8) or (rb / div)
                val pOut = src[(y - r).coerceIn(0, h - 1) * w + x]
                val pIn = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                ra += (pIn ushr 24) - (pOut ushr 24)
                rr += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                rg += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                rb += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
    }
}

/** 内蔵の壁紙 (コードで生成するグラデーション)。 */
object BuiltinWallpapers {

    data class Blob(val cx: Float, val cy: Float, val r: Float, val color: Int)

    data class Spec(@StringRes val name: Int, val base: IntArray, val blobs: List<Blob>)

    val all: List<Spec> = listOf(
        Spec(
            R.string.wp_liquid_blue,
            intArrayOf(0xFF0B1E5B.toInt(), 0xFF1D4ED8.toInt(), 0xFF0EA5E9.toInt()),
            listOf(Blob(0.15f, 0.2f, 0.55f, 0xCC38BDF8.toInt()), Blob(0.85f, 0.55f, 0.6f, 0xAA6366F1.toInt()), Blob(0.3f, 0.95f, 0.5f, 0x9922D3EE.toInt())),
        ),
        Spec(
            R.string.wp_sunrise,
            intArrayOf(0xFFFF7A59.toInt(), 0xFFFF4F8B.toInt(), 0xFF7C3AED.toInt()),
            listOf(Blob(0.8f, 0.15f, 0.5f, 0xCCFFD166.toInt()), Blob(0.1f, 0.6f, 0.6f, 0xAAFF5DA2.toInt()), Blob(0.7f, 0.95f, 0.55f, 0x995B21B6.toInt())),
        ),
        Spec(
            R.string.wp_aurora,
            intArrayOf(0xFF042F2E.toInt(), 0xFF0F766E.toInt(), 0xFF4C1D95.toInt()),
            listOf(Blob(0.2f, 0.25f, 0.55f, 0xBB34D399.toInt()), Blob(0.9f, 0.45f, 0.5f, 0xAA22D3EE.toInt()), Blob(0.4f, 0.85f, 0.6f, 0xAAA855F7.toInt())),
        ),
        Spec(
            R.string.wp_bloom,
            intArrayOf(0xFFFDE2E4.toInt(), 0xFFFBCFE8.toInt(), 0xFFE9D5FF.toInt()),
            listOf(Blob(0.25f, 0.3f, 0.5f, 0xCCF472B6.toInt()), Blob(0.8f, 0.6f, 0.55f, 0xAAC084FC.toInt()), Blob(0.2f, 0.9f, 0.45f, 0x99FDA4AF.toInt())),
        ),
        Spec(
            R.string.wp_graphite,
            intArrayOf(0xFF1C1C1E.toInt(), 0xFF2C2C2E.toInt(), 0xFF000000.toInt()),
            listOf(Blob(0.2f, 0.2f, 0.5f, 0x66636366.toInt()), Blob(0.85f, 0.7f, 0.55f, 0x553A3A3C.toInt())),
        ),
        Spec(
            R.string.wp_ocean_night,
            intArrayOf(0xFF020617.toInt(), 0xFF1E1B4B.toInt(), 0xFF0C4A6E.toInt()),
            listOf(Blob(0.75f, 0.2f, 0.45f, 0x993B82F6.toInt()), Blob(0.2f, 0.7f, 0.55f, 0x886366F1.toInt()), Blob(0.6f, 1.0f, 0.5f, 0x7706B6D4.toInt())),
        ),
        Spec(
            R.string.wp_meadow,
            intArrayOf(0xFFD9F99D.toInt(), 0xFF4ADE80.toInt(), 0xFF0F766E.toInt()),
            listOf(Blob(0.8f, 0.2f, 0.5f, 0xBBFDE047.toInt()), Blob(0.15f, 0.5f, 0.55f, 0x9922C55E.toInt()), Blob(0.6f, 0.95f, 0.5f, 0x8814B8A6.toInt())),
        ),
        Spec(
            R.string.wp_ember,
            intArrayOf(0xFF450A0A.toInt(), 0xFFB91C1C.toInt(), 0xFFF59E0B.toInt()),
            listOf(Blob(0.25f, 0.25f, 0.5f, 0xAAF97316.toInt()), Blob(0.85f, 0.6f, 0.55f, 0x99DC2626.toInt()), Blob(0.3f, 0.95f, 0.5f, 0x88FBBF24.toInt())),
        ),
    )

    fun render(index: Int, width: Int, height: Int): Bitmap {
        val spec = all[index.coerceIn(all.indices)]
        val bmp = createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        p.shader = LinearGradient(0f, 0f, width * 0.35f, height.toFloat(), spec.base, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        val m = max(width, height).toFloat()
        for (b in spec.blobs) {
            p.shader = RadialGradient(b.cx * width, b.cy * height, b.r * m, intArrayOf(b.color, b.color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        }
        // うっすらとした光の帯 (Liquid Glass の映り込み風)
        p.shader = LinearGradient(0f, height * 0.25f, width.toFloat(), height * 0.55f, intArrayOf(0x00FFFFFF, 0x22FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        return bmp
    }
}

/**
 * Liquid Glass の下地にする壁紙 (画面サイズにスケールして描く)。
 * - [light]: 画面の 1/2 解像度・弱いぼかし。クリア寄りのときに背景がはっきり、かつ粗くならずに見える
 * - [heavy]: 画面の 1/4 解像度・強いぼかし。色合い寄りのとき、および文字を載せるパネルで使う
 * どちらも GPU 上の HARDWARE ビットマップ (Java ヒープを消費しない)。
 */
data class Backdrop(val light: ImageBitmap, val heavy: ImageBitmap)

data class WallpaperState(
    /** ランチャー自身が描く壁紙。null の場合はシステム壁紙をウィンドウ越しに表示する。 */
    val image: ImageBitmap? = null,
    /** ガラス素材用のぼかし済み背景。読めない場合 (Android 13+ のシステム壁紙など) は null。 */
    val backdrop: Backdrop? = null,
    val averageColor: Int = 0xFF3A3A3C.toInt(),
    val isDark: Boolean = true,
)

class WallpaperRepository(private val context: Context, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(WallpaperState())
    val state: StateFlow<WallpaperState> = _state.asStateFlow()

    private val customFile get() = File(context.filesDir, "wallpaper_custom.jpg")
    private var lastKey: String? = null
    private var job: Job? = null
    private var currentSource = WallpaperSource.BUILTIN
    private var currentBuiltin = 0

    init {
        // システム壁紙の変更を検知する。リスナーは Android 8.1 以降、それより前は壁紙変更のブロードキャスト
        val onChanged = { if (currentSource == WallpaperSource.SYSTEM) refresh(currentSource, currentBuiltin, force = true) }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                WallpaperManager.getInstance(context).addOnColorsChangedListener({ _, _ -> onChanged() }, Handler(Looper.getMainLooper()))
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(
                    object : BroadcastReceiver() {
                        override fun onReceive(c: Context?, intent: Intent?) = onChanged()
                    },
                    IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
                )
            }
        }
    }

    fun refresh(source: WallpaperSource, builtin: Int, force: Boolean = false) {
        currentSource = source
        currentBuiltin = builtin
        val key = "$source:$builtin:${customFile.lastModified()}"
        if (!force && key == lastKey) return
        lastKey = key
        job?.cancel()
        job = scope.launch {
            val st = withContext(Dispatchers.Default) { runCatching { build(source, builtin) }.getOrDefault(WallpaperState()) }
            _state.value = st
        }
    }

    fun screenSize(): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = context.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
        // 縦向き基準
        return minOf(w, h) to maxOf(w, h)
    }

    private fun build(source: WallpaperSource, builtin: Int): WallpaperState {
        val (w, h) = screenSize()
        return when (source) {
            WallpaperSource.BUILTIN -> fromBitmap(BuiltinWallpapers.render(builtin, w / 2, h / 2), showImage = true)
            WallpaperSource.CUSTOM -> {
                val bmp = decodeFile(customFile, w, h)
                if (bmp != null) fromBitmap(bmp, showImage = true) else fromBitmap(BuiltinWallpapers.render(builtin, w / 2, h / 2), showImage = true)
            }
            WallpaperSource.SYSTEM -> {
                val wm = WallpaperManager.getInstance(context)
                // Android 13 以降は権限がないと読めないことが多い。その場合はぼかし無しのガラスになる。
                val drawable = runCatching { wm.drawable }.getOrNull()
                val bmp = drawable?.let { d -> runCatching { centerCrop(d.toBitmap(), w / 2, h / 2) }.getOrNull() }
                // 壁紙の色 (WallpaperColors) は Android 8.1 以降
                val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    runCatching { wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb() }.getOrNull()
                } else {
                    null
                } ?: 0xFF3A3A3C.toInt()
                if (bmp != null) {
                    fromBitmap(bmp, showImage = false)
                } else {
                    WallpaperState(image = null, backdrop = null, averageColor = primary, isDark = ColorUtils.calculateLuminance(primary) < 0.5)
                }
            }
        }
    }

    private fun fromBitmap(bmp: Bitmap, showImage: Boolean): WallpaperState {
        val (w, h) = screenSize()
        // 弱ぼかし: 画面の 1/2 解像度。クリア寄りでぼかしが弱くても拡大時に粗さが出ない
        val light = BlurUtils.blur(bmp.scale((w / 2).coerceAtLeast(8), (h / 2).coerceAtLeast(8)), 3)
        // 強ぼかし: 1/4 解像度で十分 (従来の 1/6・半径 10 と同等の効き具合で、より滑らか)
        val heavySource = bmp.scale((w / 4).coerceAtLeast(8), (h / 4).coerceAtLeast(8))
        val heavy = BlurUtils.blur(heavySource, 15)
        val avg = average(heavySource)
        return WallpaperState(
            image = if (showImage) bmp.toGpu().asImageBitmap() else null,
            backdrop = Backdrop(light.toGpu().asImageBitmap(), heavy.toGpu().asImageBitmap()),
            averageColor = avg,
            isDark = ColorUtils.calculateLuminance(avg) < 0.55,
        )
    }

    /** 描画専用のビットマップを GPU メモリへ移す (Java ヒープの節約とテクスチャ転送の削減)。HARDWARE は Android 8.0 以降。 */
    private fun Bitmap.toGpu(): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) runCatching { copy(Bitmap.Config.HARDWARE, false) }.getOrNull() ?: this else this

    private fun average(bmp: Bitmap): Int {
        val tiny = bmp.scale(1, 1)
        return tiny.getPixel(0, 0) or 0xFF000000.toInt()
    }

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (w / scale).roundToInt().coerceAtMost(src.width)
        val sh = (h / scale).roundToInt().coerceAtMost(src.height)
        val left = (src.width - sw) / 2
        val top = (src.height - sh) / 2
        val out = createBitmap(w, h)
        Canvas(out).drawBitmap(src, Rect(left, top, left + sw, top + sh), RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun decodeFile(file: File, w: Int, h: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return centerCrop(bmp, w, h)
    }

    /** 写真を壁紙として取り込む。 */
    suspend fun importPhoto(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val (w, h) = screenSize()
            val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeWithImageDecoder(uri, w, h) else decodeLegacy(uri, w, h) ?: return@runCatching false
            val cropped = centerCrop(decoded, w, h)
            customFile.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(uri: Uri, w: Int, h: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val s = max(w.toFloat() / info.size.width, h.toFloat() / info.size.height).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * s).roundToInt().coerceAtLeast(1), (info.size.height * s).roundToInt().coerceAtLeast(1))
        }
    }

    /** Android 8.1 以前: BitmapFactory で縮小して読み、EXIF の向きを反映する (ImageDecoder は自動で反映する)。 */
    private fun decodeLegacy(uri: Uri, w: Int, h: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val swap = orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270
        val srcW = if (swap) bounds.outHeight else bounds.outWidth
        val srcH = if (swap) bounds.outWidth else bounds.outHeight
        var sample = 1
        while (srcW / (sample * 2) >= w && srcH / (sample * 2) >= h) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
    }

    /** 現在のランチャー壁紙をシステム壁紙 (ホーム/ロック) にも設定する。 */
    suspend fun applyToSystem(source: WallpaperSource, builtin: Int, flags: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val (w, h) = screenSize()
            val bmp = when (source) {
                WallpaperSource.CUSTOM -> decodeFile(customFile, w, h) ?: BuiltinWallpapers.render(builtin, w, h)
                else -> BuiltinWallpapers.render(builtin, w, h)
            }
            val manager = WallpaperManager.getInstance(context)
            // ホーム/ロックを分けて設定できるのは Android 7.0 以降 (6.0 は 1 枚の壁紙が両方に使われる)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) manager.setBitmap(bmp, null, true, flags) else manager.setBitmap(bmp)
            true
        }.getOrDefault(false)
    }

    fun hasCustomPhoto(): Boolean = customFile.exists()

    fun previewBuiltin(index: Int, w: Int, h: Int): Bitmap = BuiltinWallpapers.render(index, w, h)

    companion object {
        fun isLight(color: Int) = ColorUtils.calculateLuminance(color) > 0.6 || Color.alpha(color) < 80
    }
}
