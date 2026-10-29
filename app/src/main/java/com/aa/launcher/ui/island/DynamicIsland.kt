package com.aa.launcher.ui.island

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aa.launcher.BuildConfig
import com.aa.launcher.LauncherApp
import com.aa.launcher.R
import com.aa.launcher.core.DeviceInfo
import com.aa.launcher.core.MediaRepository
import com.aa.launcher.core.NowPlaying
import com.aa.launcher.ui.common.haptic
import com.aa.launcher.ui.common.onGlass
import com.aa.launcher.ui.common.onGlassSecondary
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.today.BatteryInfo
import com.aa.launcher.ui.today.rememberBattery
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAG = "LiquidIsland"

/** 島に出すもの。再生中の音楽 > 充電中 > なし (黒い島だけ) の順。 */
private enum class IslandMode { IDLE, CHARGING, MUSIC }

/** 島を置く位置 (px)。カメラ穴 (画面の切り欠き) があればその中心、なければ画面上部の中央。 */
private data class IslandAnchor(
    val centerX: Float,
    val top: Float,
    val height: Float,
    val idleWidth: Float,
    val hole: Rect?,
)

private val ChargingGreen = Color(0xFF34C759)

/**
 * Dynamic Island (Island 版のみ)。ホーム画面の上部、カメラ穴を中心に黒い島を置き、充電中と再生中の音楽を表示する。
 * - タップ: 再生中の音楽があれば Liquid Glass のポップアップ (曲名・進み具合・操作) を開く。それ以外は「ぽよん」と弾むだけ。
 * - スマホの縦向きだけ。タブレット・折りたたみ端末・横向きでは出さない。
 * - 静止中は何も描き直さない。動くのはタップ・表示の切り替え・再生中の波形 (ホーム表示中のみ) だけ。
 */
@Composable
fun DynamicIsland(haptics: Boolean, reduceMotion: Boolean) {
    if (!BuildConfig.DYNAMIC_ISLAND) return
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val foldable = remember { DeviceInfo.isFoldable(context) }
    // タブレット (最小幅 600dp 以上) と折りたたみ端末 (ヒンジセンサーあり) では動かさない。横向きはカメラが横に来るので出さない
    val supported = !foldable && config.smallestScreenWidthDp < 600 && config.orientation != Configuration.ORIENTATION_LANDSCAPE
    if (BuildConfig.DEBUG) {
        LaunchedEffect(supported) {
            if (!supported) Log.d(TAG, "disabled foldable=$foldable sw=${config.smallestScreenWidthDp} orientation=${config.orientation}")
        }
    }
    if (supported) IslandHost(haptics, reduceMotion)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IslandHost(haptics: Boolean, reduceMotion: Boolean) {
    val view = LocalView.current
    val density = LocalDensity.current
    val media = LauncherApp.instance.mediaRepository
    val nowPlaying by media.nowPlaying.collectAsStateWithLifecycle()
    val battery = rememberBattery()
    val mode = when {
        nowPlaying?.isPlaying == true -> IslandMode.MUSIC
        battery.charging -> IslandMode.CHARGING
        else -> IslandMode.IDLE
    }
    // ポップアップを閉じるアニメーション中も内容を出しておくため、最後の曲を覚えておく
    val lastPlaying = remember { arrayOfNulls<NowPlaying>(1) }
    if (nowPlaying != null) lastPlaying[0] = nowPlaying
    val accent = remember(nowPlaying?.art) { accentOf(nowPlaying?.art) }

    // カメラ穴の位置は、切り欠きの余白が変わったとき (回転・切り欠きの変更) だけ取り直す
    val cutout = WindowInsets.displayCutout
    val cutTop = cutout.getTop(density)
    val cutLeft = cutout.getLeft(density, LayoutDirection.Ltr)
    val cutRight = cutout.getRight(density, LayoutDirection.Ltr)
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.getTop(density)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = constraints.maxWidth
        val anchor = remember(cutTop, cutLeft, cutRight, statusTop, screenWidth) {
            computeAnchor(view, screenWidth, statusTop, density)
        }
        val margin = with(density) { 8.dp.toPx() }
        // 画面からはみ出さないように左端を決める (カメラ穴が端に寄っている端末向け)
        fun leftFor(w: Float) = (anchor.centerX - w / 2f).coerceIn(margin, max(margin, screenWidth - margin - w))

        val activeWidth = min(anchor.idleWidth + with(density) { 96.dp.toPx() }, screenWidth - 2 * margin)
        val widthAnim = animateFloatAsState(
            targetValue = if (mode == IslandMode.IDLE) anchor.idleWidth else activeWidth,
            animationSpec = spring(dampingRatio = if (reduceMotion) 1f else 0.62f, stiffness = 380f),
            label = "islandWidth",
        )

        // 「ぽよん」: 押すと少し縮み、離すと勢いよく戻って数回揺れて止まる (縦横で揺れの速さを変えて弾力を出す)
        val squishX = remember { Animatable(1f) }
        val squishY = remember { Animatable(1f) }
        val scope = rememberCoroutineScope()
        val motion by rememberUpdatedState(reduceMotion)
        val press: () -> Unit = {
            if (!motion) {
                scope.launch { squishX.animateTo(0.94f, tween(110)) }
                scope.launch { squishY.animateTo(0.9f, tween(110)) }
            }
        }
        val poyon: (Float) -> Unit = { strength ->
            if (motion) {
                scope.launch { squishX.snapTo(1f) }
                scope.launch { squishY.snapTo(1f) }
            } else {
                scope.launch { squishX.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 480f), initialVelocity = 2.4f * strength) }
                scope.launch { squishY.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = 620f), initialVelocity = 3f * strength) }
            }
        }

        // 表示が切り替わったとき (充電器をつないだ・再生が始まった) も軽く弾ませる
        val shownMode = remember { arrayOf(mode) }
        LaunchedEffect(mode) {
            if (BuildConfig.DEBUG) Log.d(TAG, "mode=$mode")
            if (shownMode[0] != mode) {
                shownMode[0] = mode
                poyon(0.5f)
            }
        }
        if (BuildConfig.DEBUG) {
            LaunchedEffect(anchor) {
                val left = leftFor(anchor.idleWidth)
                Log.d(
                    TAG,
                    "placed hole=${anchor.hole?.toShortString()} rect=${left.roundToInt()},${anchor.top.roundToInt()}," +
                        "${(left + anchor.idleWidth).roundToInt()},${(anchor.top + anchor.height).roundToInt()} screenWidth=$screenWidth",
                )
            }
        }

        var popupOpen by remember { mutableStateOf(false) }
        LaunchedEffect(nowPlaying == null) { if (nowPlaying == null) popupOpen = false }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { popupOpen = false }
        BackHandler(enabled = popupOpen) { popupOpen = false }

        val currentMode by rememberUpdatedState(mode)
        val currentHaptics by rememberUpdatedState(haptics)

        // ポップアップの外をタップしたら閉じる
        if (popupOpen) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { popupOpen = false } })
        }

        val islandHeight = with(density) { anchor.height.toDp() }
        Box(
            Modifier
                // 幅のアニメーションは配置と大きさの計算だけで反映する (中身を作り直さない)
                .offset { IntOffset(leftFor(widthAnim.value).roundToInt(), anchor.top.roundToInt()) }
                .layout { measurable, _ ->
                    val w = widthAnim.value.roundToInt()
                    val h = anchor.height.roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    scaleX = squishX.value
                    scaleY = squishY.value
                }
                .clip(CircleShape)
                .background(Color.Black)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        press()
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            view.haptic(currentHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                            if (currentMode == IslandMode.MUSIC) popupOpen = true
                        }
                        poyon(1f)
                    }
                },
        ) {
            IslandContent(mode, nowPlaying, battery, accent, islandHeight)
        }

        val popupWidth = min(screenWidth - with(density) { 24.dp.toPx() }, with(density) { 400.dp.toPx() })
        val popupLeft = (anchor.centerX - popupWidth / 2f).coerceIn(with(density) { 12.dp.toPx() }, max(0f, screenWidth - popupWidth - with(density) { 12.dp.toPx() }))
        val pivot = TransformOrigin(((anchor.centerX - popupLeft) / popupWidth).coerceIn(0f, 1f), 0f)
        val shown = nowPlaying ?: lastPlaying[0]
        AnimatedVisibility(
            visible = popupOpen && shown != null,
            modifier = Modifier
                .offset { IntOffset(popupLeft.roundToInt(), anchor.top.roundToInt()) }
                .width(with(density) { popupWidth.toDp() }),
            // 島から広がって出てくる (iOS の島を長押ししたときと同じ動き)
            enter = if (reduceMotion) {
                fadeIn()
            } else {
                scaleIn(spring(dampingRatio = 0.7f, stiffness = 420f), initialScale = 0.35f, transformOrigin = pivot) + fadeIn(tween(160))
            },
            exit = if (reduceMotion) {
                fadeOut()
            } else {
                scaleOut(tween(200), targetScale = 0.35f, transformOrigin = pivot) + fadeOut(tween(180))
            },
            label = "islandPopup",
        ) {
            shown?.let {
                NowPlayingPanel(it, accent, media) {
                    popupOpen = false
                    media.openPlayer()
                }
            }
        }
    }
}

/** 島の位置と大きさを決める。カメラ穴は Android 9 以降で取得できる (それより前・穴のない端末は画面上部の中央)。 */
private fun computeAnchor(view: View, screenWidth: Int, statusTop: Int, density: Density): IslandAnchor {
    with(density) {
        val rects = ViewCompat.getRootWindowInsets(view)?.displayCutout?.boundingRects.orEmpty()
        // 画面の上端にある切り欠きのうち、横方向の中央に一番近いもの
        val hole = rects
            .filter { it.width() > 0 && it.height() > 0 && it.top < 120.dp.toPx() }
            .minByOrNull { abs(it.exactCenterX() - screenWidth / 2f) }
        val baseHeight = 34.dp.toPx()
        val baseWidth = 120.dp.toPx()
        if (hole == null) {
            val top = ((statusTop - baseHeight) / 2f).coerceAtLeast(6.dp.toPx())
            return IslandAnchor(screenWidth / 2f, top, baseHeight, baseWidth, null)
        }
        // カメラ穴を囲む一回り大きい島にする。上端に付いた切り欠き (ノッチ) は上端から伸ばす
        val height = max(baseHeight, hole.height() + 10.dp.toPx())
        val top = if (hole.top <= 0) 0f else (hole.exactCenterY() - height / 2f).coerceAtLeast(2.dp.toPx())
        val idleWidth = max(baseWidth, hole.width() + 24.dp.toPx())
        return IslandAnchor(hole.exactCenterX(), top, height, idleWidth, Rect(hole))
    }
}

@Composable
private fun IslandContent(mode: IslandMode, nowPlaying: NowPlaying?, battery: BatteryInfo, accent: Color, height: Dp) {
    // 文字の大きさは島の高さに合わせる (端末の文字サイズ設定で島からはみ出さない)
    val textSize = with(LocalDensity.current) { (height * 0.4f).toSp() }
    Crossfade(mode, animationSpec = tween(220), label = "islandContent") { m ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = height * 0.24f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (m) {
                IslandMode.IDLE -> Unit
                IslandMode.CHARGING -> {
                    Icon(Icons.Rounded.Bolt, null, tint = ChargingGreen, modifier = Modifier.size(height * 0.6f))
                    Text("${battery.level}%", color = ChargingGreen, fontSize = textSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                IslandMode.MUSIC -> {
                    Artwork(nowPlaying?.art, height * 0.62f, height * 0.18f)
                    Waveform(accent, Modifier.size(height * 0.66f, height * 0.46f))
                }
            }
        }
    }
}

/** 再生中の曲のポップアップ (Liquid Glass)。アートワーク・曲名・進み具合・前へ/再生/次へ。 */
@Composable
private fun NowPlayingPanel(np: NowPlaying, accent: Color, media: MediaRepository, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(36.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(shape, strength = 1.5f)
            .clip(shape)
            // パネルの空いている所をタップしても閉じない (外側だけで閉じる)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(np.art, 56.dp, 14.dp, Modifier.clickable(onClick = onOpen))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(
                    np.title.ifEmpty { stringResource(R.string.island_unknown_title) },
                    color = onGlass(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (np.artist.isNotEmpty()) {
                    Text(np.artist, color = onGlassSecondary(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            if (np.isPlaying) {
                Waveform(accent, Modifier.size(28.dp, 22.dp))
            } else {
                Canvas(Modifier.size(28.dp, 22.dp)) { drawBars(accent, null) }
            }
        }
        if (np.durationMs > 0L) {
            Spacer(Modifier.height(16.dp))
            Progress(np, media)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            PanelButton(Icons.Rounded.SkipPrevious, stringResource(R.string.island_previous), 36.dp) { media.previous() }
            if (np.isPlaying) {
                PanelButton(Icons.Rounded.Pause, stringResource(R.string.island_pause), 46.dp) { media.playPause() }
            } else {
                PanelButton(Icons.Rounded.PlayArrow, stringResource(R.string.island_play), 46.dp) { media.playPause() }
            }
            PanelButton(Icons.Rounded.SkipNext, stringResource(R.string.island_next), 36.dp) { media.next() }
        }
    }
}

@Composable
private fun Progress(np: NowPlaying, media: MediaRepository) {
    var position by remember { mutableLongStateOf(media.positionMs()) }
    // ポップアップを開いている間だけ、秒の境目ごとに進める (一時停止中は更新しない)
    LaunchedEffect(np.isPlaying, np.title, np.durationMs) {
        position = media.positionMs()
        while (np.isPlaying) {
            delay((1000L - position % 1000L).coerceAtLeast(50L))
            position = media.positionMs()
        }
    }
    val shown = position.coerceIn(0L, np.durationMs)
    val track = onGlass()
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(track.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxWidth(shown.toFloat() / np.durationMs).fillMaxHeight().background(track.copy(alpha = 0.85f)))
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(shown), color = onGlassSecondary(), fontSize = 11.sp)
        Text("-" + formatTime(np.durationMs - shown), color = onGlassSecondary(), fontSize = 11.sp)
    }
}

@Composable
private fun PanelButton(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit) {
    Icon(
        icon,
        description,
        tint = onGlass(),
        modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick).padding(size * 0.12f),
    )
}

@Composable
private fun Artwork(art: ImageBitmap?, size: Dp, corner: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(corner)
    if (art != null) {
        Image(art, null, modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.size(size).clip(shape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/** 再生中の波形。位相は描画の段階でだけ読むので、動いている間も再構成やレイアウトは起きない。 */
@Composable
private fun Waveform(color: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "wavePhase")
    Canvas(modifier) { drawBars(color, phase.value) }
}

// 各バーの速さ (1 周期の中の山の数、整数なので継ぎ目なく繰り返す) とずれ
private val BarSpeeds = intArrayOf(2, 3, 1, 3, 2)
private val BarOffsets = floatArrayOf(0f, 0.3f, 0.55f, 0.15f, 0.7f)

/** [phase] が null なら止まった低い波形。 */
private fun DrawScope.drawBars(color: Color, phase: Float?) {
    val count = BarSpeeds.size
    val stroke = size.width / (count * 2 - 1)
    for (i in 0 until count) {
        val level = if (phase == null) 0.22f else 0.3f + 0.7f * abs(sin((phase * BarSpeeds[i] + BarOffsets[i]) * 2f * PI.toFloat()))
        val h = (size.height - stroke) * level
        val x = stroke * (i * 2) + stroke / 2f
        val cy = size.height / 2f
        drawLine(color, Offset(x, cy - h / 2f), Offset(x, cy + h / 2f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** アートワークの平均色を波形の色にする (iOS と同じ)。黒い島の上で見えるよう明るさを持ち上げる。 */
private fun accentOf(art: ImageBitmap?): Color {
    art ?: return Color.White
    return runCatching {
        var bitmap = art.asAndroidBitmap()
        // HARDWARE ビットマップは画素を読めないので、通常の形式に写してから縮める
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(pixel, hsv)
        hsv[1] = min(hsv[1], 0.75f)
        hsv[2] = max(hsv[2], 0.85f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }.getOrDefault(Color.White)
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60L, total % 60L)
}
