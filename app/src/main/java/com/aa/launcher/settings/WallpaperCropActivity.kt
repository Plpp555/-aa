package com.aa.launcher.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aa.launcher.R
import com.aa.launcher.core.WallpaperCropMath
import com.aa.launcher.launcherApp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 写真壁紙の位置と大きさを決める画面 (各社 OS の壁紙設定と同じ)。ホーム画面と同じく全画面で表示し、
 * ピンチで拡大・縮小、ドラッグで移動する。見えている範囲がそのまま壁紙になる。
 * 結果は中心の位置と拡大率 ([WallpaperCrop]) で保存するので、折りたたみの開閉や回転で縦横比が変わっても同じ部分が使われる。
 */
class WallpaperCropActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val pending = intent.getBooleanExtra(EXTRA_PENDING, false)
        val app = launcherApp
        val initial = if (pending) WallpaperCrop() else app.settingsRepository.settings.value.wallpaperCrop
        setContent {
            val scope = rememberCoroutineScope()
            WallpaperCropScreen(
                pending = pending,
                initial = initial,
                onCancel = {
                    if (pending) app.wallpaperRepository.discardPendingPhoto()
                    finish()
                },
                onSet = { crop ->
                    scope.launch {
                        if (app.wallpaperRepository.commitPendingPhoto()) {
                            app.settingsRepository.update { it.copy(wallpaperSource = WallpaperSource.CUSTOM, wallpaperCrop = crop) }
                            val s = app.settingsRepository.settings.value
                            app.wallpaperRepository.refresh(s.wallpaperSource, s.builtinWallpaper, s.wallpaperCrop)
                        }
                        finish()
                    }
                },
            )
        }
    }

    override fun onStop() {
        super.onStop()
        launcherApp.settingsRepository.flush()
    }

    companion object {
        private const val EXTRA_PENDING = "pending"

        /** [pending] が true なら選んだばかりの写真 (位置は中央から)、false なら今の写真壁紙の位置を調整する。 */
        fun intent(context: Context, pending: Boolean): Intent =
            Intent(context, WallpaperCropActivity::class.java).putExtra(EXTRA_PENDING, pending)
    }
}

@Composable
private fun WallpaperCropScreen(pending: Boolean, initial: WallpaperCrop, onCancel: () -> Unit, onSet: (WallpaperCrop) -> Unit) {
    val app = LocalContext.current.launcherApp
    val photo by produceState<Bitmap?>(null, pending) {
        value = app.wallpaperRepository.loadPhotoForEditor(pending, maxSide = 2560)
    }
    var crop by remember { mutableStateOf(initial) }
    val currentCrop by rememberUpdatedState(crop)
    BackHandler(onBack = onCancel)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val bmp = photo
        if (bmp != null) {
            val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(bmp) {
                        detectTransformGestures { centroid, pan, zoomChange, _ ->
                            val w = size.width
                            val h = size.height
                            if (w <= 0 || h <= 0) return@detectTransformGestures
                            val c0 = currentCrop
                            val r0 = WallpaperCropMath.rect(bmp.width, bmp.height, w, h, c0)
                            val scale0 = r0.width() / w
                            val zoom = (c0.zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                            val scale1 = r0.width() / w * c0.zoom / zoom
                            // 指の下の写真の位置が指に付いてくるように (移動と、指の位置を中心にした拡大・縮小)
                            val px = r0.left + (centroid.x - pan.x) * scale0
                            val py = r0.top + (centroid.y - pan.y) * scale0
                            val left = px - centroid.x * scale1
                            val top = py - centroid.y * scale1
                            val wanted = WallpaperCrop(
                                cx = (left + w * scale1 / 2f) / bmp.width,
                                cy = (top + h * scale1 / 2f) / bmp.height,
                                zoom = zoom,
                            )
                            // 写真の外が見えないように寄せた位置を保存する
                            val r1 = WallpaperCropMath.rect(bmp.width, bmp.height, w, h, wanted)
                            crop = WallpaperCrop(r1.centerX() / bmp.width, r1.centerY() / bmp.height, zoom)
                        }
                    },
            ) {
                val w = size.width.roundToInt().coerceAtLeast(1)
                val h = size.height.roundToInt().coerceAtLeast(1)
                val r = WallpaperCropMath.rect(bmp.width, bmp.height, w, h, crop)
                val src = Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
                drawIntoCanvas { it.nativeCanvas.drawBitmap(bmp, src, RectF(0f, 0f, size.width, size.height), paint) }
            }
        }

        val shadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f))
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 20.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.wallpaper_crop_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, style = shadow)
            Text(stringResource(R.string.wallpaper_crop_hint), color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, style = shadow)
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CropButton(stringResource(R.string.action_cancel), Modifier.weight(1f), onClick = onCancel)
            CropButton(stringResource(R.string.wallpaper_crop_set), Modifier.weight(1f), emphasized = true) {
                if (photo != null) onSet(crop)
            }
        }
    }
}

@Composable
private fun CropButton(label: String, modifier: Modifier, emphasized: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(if (emphasized) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (emphasized) Color.Black else Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

private const val MAX_ZOOM = 6f
