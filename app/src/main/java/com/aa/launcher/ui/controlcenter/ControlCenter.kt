package com.aa.launcher.ui.controlcenter

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aa.launcher.LauncherApp
import com.aa.launcher.R
import com.aa.launcher.core.DoNotDisturb
import com.aa.launcher.core.Flashlight
import com.aa.launcher.core.SystemActions
import com.aa.launcher.core.SystemSettingsAccess
import com.aa.launcher.service.NotificationState
import com.aa.launcher.settings.ControlTile
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.home.LauncherViewModel
import com.aa.launcher.ui.theme.LocalLauncherSettings
import com.aa.launcher.ui.home.launcherStatusBars
import androidx.compose.foundation.layout.windowInsetsPadding

private val TileShape = RoundedCornerShape(26.dp)
private val ActiveBlue = Color(0xFF0A84FF)

/** iOS 風コントロールセンター (右上から下へスワイプ)。 */
@Composable
fun ControlCenterOverlay(vm: LauncherViewModel, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 5 },
        exit = fadeOut() + slideOutVertically { -it / 5 },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.controlCenterOpen = false }
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { change, dy ->
                            total += dy
                            if (total < -80f) vm.controlCenterOpen = false
                            change.consume()
                        },
                    )
                },
            // 大画面では右上のパネルにする (スマホの幅では全面)
            contentAlignment = Alignment.TopEnd,
        ) {
            ControlCenterContent(vm)
        }
    }
}

@Composable
private fun ControlCenterContent(vm: LauncherViewModel) {
    val context = LocalContext.current
    val settings = LocalLauncherSettings.current
    val tiles = settings.controlTiles
    val media = LauncherApp.instance.mediaRepository
    LaunchedEffect(Unit) { media.refresh() }

    BoxWithConstraints(
        Modifier
            .fillMaxHeight()
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .windowInsetsPadding(launcherStatusBars())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        val gap = 14.dp
        val unit = (maxWidth - gap * 3) / 4
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            // 大きいタイル (2x2): 通信 / 再生中
            val big = tiles.filter { it == ControlTile.CONNECTIVITY || it == ControlTile.MEDIA }
            if (big.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    big.forEach { tile ->
                        Box(Modifier.size(unit * 2 + gap)) {
                            when (tile) {
                                ControlTile.CONNECTIVITY -> ConnectivityTile(context)
                                else -> MediaTile(vm)
                            }
                        }
                    }
                }
            }
            // スライダー (縦長 1x2) と小タイル
            val sliders = tiles.filter { it == ControlTile.BRIGHTNESS || it == ControlTile.VOLUME }
            val small = tiles.filter { it !in big && it !in sliders }
            val leftColumns = (4 - sliders.size).coerceAtLeast(1)
            val firstRowSmall = if (sliders.isEmpty()) emptyList() else small.take(leftColumns * 2)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    firstRowSmall.chunked(leftColumns).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            pair.forEach { SmallTile(it, unit, vm) }
                        }
                    }
                }
                sliders.forEach { tile ->
                    if (tile == ControlTile.BRIGHTNESS) BrightnessSlider(unit, unit * 2 + gap) else VolumeSlider(unit, unit * 2 + gap)
                }
            }
            small.drop(firstRowSmall.size).chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { SmallTile(it, unit, vm) }
                }
            }
            Text(
                stringResource(R.string.cc_hint),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ConnectivityTile(context: Context) {
    Column(
        Modifier
            .fillMaxSize()
            .liquidGlass(TileShape, strength = 1.2f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundToggle(Icons.Rounded.AirplanemodeActive, Color(0xFFFF9500), false) { SystemActions.openAirplane(context) }
            RoundToggle(Icons.Rounded.SignalCellularAlt, Color(0xFF34C759), true) { SystemActions.openInternet(context) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundToggle(Icons.Rounded.Wifi, ActiveBlue, true) { SystemActions.openWifi(context) }
            RoundToggle(Icons.Rounded.Bluetooth, ActiveBlue, true) { SystemActions.openBluetooth(context) }
        }
    }
}

@Composable
private fun RoundToggle(icon: ImageVector, activeColor: Color, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (active) activeColor else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun MediaTile(vm: LauncherViewModel) {
    val context = LocalContext.current
    val media = LauncherApp.instance.mediaRepository
    val nowPlaying by media.nowPlaying.collectAsState()
    val connected by NotificationState.connected.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .liquidGlass(TileShape, strength = 1.2f)
            .clip(TileShape)
            .clickable {
                if (!connected) {
                    vm.controlCenterOpen = false
                    SystemActions.openNotificationAccess(context)
                } else {
                    media.openPlayer()
                }
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = nowPlaying?.art
            if (art != null) {
                Image(art, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White)
                }
            }
        }
        Column {
            Text(
                nowPlaying?.title?.ifEmpty { null } ?: stringResource(if (connected) R.string.cc_not_playing else R.string.cc_media_permission),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            nowPlaying?.artist?.takeIf { it.isNotEmpty() }?.let {
                Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MediaButton(Icons.Rounded.SkipPrevious) { media.previous() }
            MediaButton(if (nowPlaying?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow) { media.playPause() }
            MediaButton(Icons.Rounded.SkipNext) { media.next() }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, onClick: () -> Unit) {
    Icon(
        icon,
        null,
        tint = Color.White,
        modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick).padding(4.dp),
    )
}

@Composable
private fun SmallTile(tile: ControlTile, size: Dp, vm: LauncherViewModel) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val flashlight = remember { Flashlight(context) }
    var torchOn by remember { mutableStateOf(false) }
    if (tile == ControlTile.FLASHLIGHT) {
        DisposableEffect(Unit) {
            flashlight.observe { torchOn = it }
            onDispose { flashlight.stop() }
        }
    }
    val (icon, active, color) = when (tile) {
        ControlTile.FLASHLIGHT -> Triple(if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, torchOn, Color.White)
        ControlTile.DO_NOT_DISTURB -> Triple(Icons.Rounded.DoNotDisturbOn, remember(refresh) { DoNotDisturb.isOn(context) }, Color(0xFF5E5CE6))
        ControlTile.ROTATION_LOCK -> {
            val locked = remember(refresh) { SystemSettingsAccess.isRotationLocked(context) }
            Triple(if (locked) Icons.Rounded.ScreenLockRotation else Icons.Rounded.ScreenRotation, locked, Color(0xFFFF453A))
        }
        ControlTile.TIMER -> Triple(Icons.Rounded.Timer, false, Color.White)
        ControlTile.ALARM -> Triple(Icons.Rounded.Alarm, false, Color.White)
        ControlTile.CALCULATOR -> Triple(Icons.Rounded.Calculate, false, Color.White)
        ControlTile.CAMERA -> Triple(Icons.Rounded.PhotoCamera, false, Color.White)
        ControlTile.SCREEN_LOCK -> Triple(Icons.Rounded.Lock, false, Color.White)
        ControlTile.LOCATION -> Triple(Icons.Rounded.LocationOn, false, Color.White)
        ControlTile.DARK_MODE -> Triple(Icons.Rounded.DarkMode, false, Color.White)
        ControlTile.SETTINGS -> Triple(Icons.Rounded.Settings, false, Color.White)
        else -> Triple(Icons.Rounded.Settings, false, Color.White)
    }
    val bg = if (active) color else Color.Unspecified
    Box(
        Modifier
            .size(size)
            .then(if (active) Modifier.clip(TileShape).background(bg) else Modifier.liquidGlass(TileShape, strength = 1.2f))
            .clip(TileShape)
            .clickable {
                val close = { vm.controlCenterOpen = false }
                when (tile) {
                    ControlTile.FLASHLIGHT -> flashlight.toggle()
                    ControlTile.DO_NOT_DISTURB -> if (!DoNotDisturb.toggle(context)) close()
                    ControlTile.ROTATION_LOCK -> if (!SystemSettingsAccess.toggleRotationLock(context)) close()
                    ControlTile.TIMER -> { close(); SystemActions.openTimer(context) }
                    ControlTile.ALARM -> { close(); SystemActions.openAlarms(context) }
                    ControlTile.CALCULATOR -> { close(); SystemActions.openCalculator(context) }
                    ControlTile.CAMERA -> { close(); SystemActions.openCamera(context) }
                    ControlTile.SCREEN_LOCK -> { close(); SystemActions.lockScreen(context) }
                    ControlTile.LOCATION -> { close(); SystemActions.openLocation(context) }
                    ControlTile.DARK_MODE -> { close(); SystemActions.openDisplay(context) }
                    ControlTile.SETTINGS -> { close(); SystemActions.openSystemSettings(context) }
                    else -> Unit
                }
                refresh++
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, stringResource(tile.label), tint = if (active && color == Color.White) Color.Black else Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun VerticalSlider(
    value: Float,
    onChange: (Float) -> Unit,
    icon: ImageVector,
    width: Dp,
    height: Dp,
    enabled: Boolean,
    onDisabledClick: () -> Unit,
) {
    var current by remember { mutableFloatStateOf(value) }
    Box(
        Modifier
            .size(width, height)
            .liquidGlass(RoundedCornerShape(26.dp), strength = 1.2f)
            .clip(RoundedCornerShape(26.dp))
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            current = (current - drag.y / size.height).coerceIn(0f, 1f)
                            onChange(current)
                        }
                    }
                } else {
                    Modifier.clickable(onClick = onDisabledClick)
                },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(current.coerceIn(0.02f, 1f)).background(Color.White))
        Icon(icon, null, tint = if (current > 0.15f) Color(0xFF8E8E93) else Color.White, modifier = Modifier.padding(bottom = 16.dp).size(28.dp))
    }
}

@Composable
private fun BrightnessSlider(width: Dp, height: Dp) {
    val context = LocalContext.current
    val canWrite = remember { SystemSettingsAccess.canWrite(context) }
    VerticalSlider(
        value = remember { SystemSettingsAccess.brightness(context) },
        onChange = { SystemSettingsAccess.setBrightness(context, it) },
        icon = Icons.Rounded.BrightnessHigh,
        width = width,
        height = height,
        enabled = canWrite,
        onDisabledClick = { SystemActions.openWriteSettings(context) },
    )
}

@Composable
private fun VolumeSlider(width: Dp, height: Dp) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    VerticalSlider(
        value = remember { audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat() },
        onChange = { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt(), 0) },
        icon = Icons.AutoMirrored.Rounded.VolumeUp,
        width = width,
        height = height,
        enabled = true,
        onDisabledClick = {},
    )
}
