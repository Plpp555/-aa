package com.aa.launcher.ui.today

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import android.widget.TextView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aa.launcher.LauncherApp
import com.aa.launcher.R
import com.aa.launcher.core.SystemActions
import com.aa.launcher.core.WidgetHostManager
import com.aa.launcher.core.RoundedWidgetHostView
import com.aa.launcher.core.WidgetItem
import com.aa.launcher.core.WidgetKind
import com.aa.launcher.core.WidgetSize
import com.aa.launcher.settings.ClockFormat
import com.aa.launcher.ui.common.AppIconImage
import com.aa.launcher.ui.common.onGlass
import com.aa.launcher.ui.common.onGlassSecondary
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.home.LocalApps
import com.aa.launcher.ui.home.LocalContentActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.aa.launcher.ui.home.LocalLauncherActions
import com.aa.launcher.ui.home.LocalWidgetHost
import com.aa.launcher.ui.theme.LocalLauncherSettings
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

val WidgetShape = RoundedCornerShape(24.dp)

@Composable
fun widgetLabel(item: WidgetItem): String {
    if (item.kind == WidgetKind.APP_WIDGET) {
        val host = LocalWidgetHost.current
        val context = LocalContext.current
        val info = remember(item.appWidgetId, host) { host?.info(item.appWidgetId) }
        if (info != null) return remember(info) { info.loadLabel(context.packageManager) ?: "" }
    }
    return stringResource(item.kind.label)
}

@Composable
fun WidgetContent(item: WidgetItem, modifier: Modifier = Modifier, interactive: Boolean = true) {
    when (item.kind) {
        WidgetKind.CLOCK -> DigitalClockWidget(item.size, modifier, interactive)
        WidgetKind.ANALOG_CLOCK -> AnalogClockWidget(modifier, interactive)
        WidgetKind.CALENDAR -> CalendarWidget(item.size, modifier, interactive)
        WidgetKind.BATTERY -> BatteryWidget(item.size, modifier)
        WidgetKind.SUGGESTIONS -> SuggestionsWidget(item.size, modifier, interactive)
        WidgetKind.NOTE -> NoteWidget(item, modifier, interactive)
        WidgetKind.SEARCH -> SearchWidget(modifier, interactive)
        WidgetKind.APP_WIDGET -> HostedAppWidget(item, modifier, interactive)
    }
}

/**
 * 現在時刻 (intervalMs ごとに更新)。
 * 画面が見えている (Activity が STARTED かつページが表示中) ときだけ時を刻み、
 * 他のアプリ使用中や見えていないページでは更新を止める。境界 (分の切り替わりなど) にそろえて更新する。
 */
@Composable
fun rememberNow(intervalMs: Long): Long {
    val active = LocalContentActive.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs, active, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                state.longValue = now
                delay(intervalMs - now % intervalMs + 5)
            }
        }
    }
    return state.longValue
}

@Composable
fun use24Hour(): Boolean {
    val context = LocalContext.current
    return when (LocalLauncherSettings.current.clockFormat) {
        ClockFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        ClockFormat.H12 -> false
        ClockFormat.H24 -> true
    }
}

fun formatTime(millis: Long, is24: Boolean, seconds: Boolean): String {
    val skeleton = (if (is24) "Hm" else "hm") + if (seconds) "s" else ""
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton).replace("a", "").trim()
    return DateFormat.format(pattern, millis).toString()
}

fun formatDate(millis: Long, skeleton: String): String =
    DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), millis).toString()

@Composable
private fun WidgetCard(modifier: Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .liquidGlass(WidgetShape)
            .then(if (onClick != null) Modifier.clip(WidgetShape).clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
private fun DigitalClockWidget(size: WidgetSize, modifier: Modifier, interactive: Boolean) {
    val context = LocalContext.current
    val s = LocalLauncherSettings.current
    val now = rememberNow(if (s.clockShowSeconds) 1000L else 60_000L)
    val is24 = use24Hour()
    WidgetCard(modifier, onClick = if (interactive) ({ SystemActions.openAlarms(context); Unit }) else null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(formatDate(now, "EEEE"), color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                formatTime(now, is24, s.clockShowSeconds),
                color = onGlass(),
                fontSize = when (size) {
                    WidgetSize.SMALL -> if (s.clockShowSeconds) 30.sp else 40.sp
                    WidgetSize.MEDIUM -> 54.sp
                    WidgetSize.LARGE -> 72.sp
                },
                fontWeight = FontWeight.Light,
                maxLines = 1,
            )
            Text(formatDate(now, "MMMMd"), color = onGlassSecondary(), fontSize = 14.sp, maxLines = 1)
            if (size == WidgetSize.LARGE) {
                Spacer(Modifier.height(12.dp))
                MonthGrid(now, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun AnalogClockWidget(modifier: Modifier, interactive: Boolean) {
    val context = LocalContext.current
    val s = LocalLauncherSettings.current
    val now = rememberNow(if (s.clockShowSeconds) 1000L else 60_000L)
    val face = onGlass()
    val accent = Color(0xFFFF9F0A)
    WidgetCard(modifier, onClick = if (interactive) ({ SystemActions.openAlarms(context); Unit }) else null) {
        Canvas(Modifier.fillMaxSize()) {
            val r = min(size.width, size.height) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            for (i in 0 until 60) {
                val major = i % 5 == 0
                val angle = Math.toRadians(i * 6.0 - 90)
                val outer = r * 0.96f
                val inner = if (major) r * 0.82f else r * 0.9f
                drawLine(
                    color = face.copy(alpha = if (major) 0.9f else 0.35f),
                    start = Offset(c.x + inner * cos(angle).toFloat(), c.y + inner * sin(angle).toFloat()),
                    end = Offset(c.x + outer * cos(angle).toFloat(), c.y + outer * sin(angle).toFloat()),
                    strokeWidth = if (major) 2.5.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val sec = cal.get(Calendar.SECOND)
            val minute = cal.get(Calendar.MINUTE) + sec / 60f
            val hour = cal.get(Calendar.HOUR) + minute / 60f
            rotate(hour * 30f, c) {
                drawLine(face, c, Offset(c.x, c.y - r * 0.5f), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            }
            rotate(minute * 6f, c) {
                drawLine(face, c, Offset(c.x, c.y - r * 0.75f), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
            }
            if (s.clockShowSeconds) {
                rotate(sec * 6f, c) {
                    drawLine(accent, Offset(c.x, c.y + r * 0.15f), Offset(c.x, c.y - r * 0.85f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            drawCircle(accent, radius = 4.dp.toPx(), center = c)
        }
    }
}

@Composable
private fun CalendarWidget(size: WidgetSize, modifier: Modifier, interactive: Boolean) {
    val context = LocalContext.current
    val now = rememberNow(60_000L)
    val open = {
        SystemActions.start(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
        Unit
    }
    WidgetCard(modifier, onClick = if (interactive) open else null) {
        when (size) {
            WidgetSize.SMALL -> Column(Modifier.fillMaxSize()) {
                Text(formatDate(now, "EEEE").uppercase(), color = Color(0xFFFF453A), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(formatDate(now, "d"), color = onGlass(), fontSize = 46.sp, fontWeight = FontWeight.Light, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.widget_no_events), color = onGlassSecondary(), fontSize = 12.sp, maxLines = 2)
            }
            WidgetSize.MEDIUM -> Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.42f).fillMaxHeight()) {
                    Text(formatDate(now, "EEEE").uppercase(), color = Color(0xFFFF453A), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(formatDate(now, "d"), color = onGlass(), fontSize = 44.sp, fontWeight = FontWeight.Light, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.widget_no_events), color = onGlassSecondary(), fontSize = 12.sp, maxLines = 2)
                }
                MonthGrid(now, Modifier.weight(0.58f).fillMaxHeight())
            }
            WidgetSize.LARGE -> Column(Modifier.fillMaxSize()) {
                Text(formatDate(now, "MMMM yyyy"), color = Color(0xFFFF453A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                MonthGrid(now, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun MonthGrid(now: Long, modifier: Modifier) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDow = cal.firstDayOfWeek
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val offset = (cal.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
    val weekdayNames = remember(firstDow) {
        val c = Calendar.getInstance()
        (0 until 7).map { i ->
            c.set(Calendar.DAY_OF_WEEK, (firstDow - 1 + i) % 7 + 1)
            DateFormat.format("EEEEE", c).toString()
        }
    }
    val weeks = (offset + days + 6) / 7
    Column(modifier, verticalArrangement = Arrangement.SpaceEvenly) {
        Row(Modifier.fillMaxWidth()) {
            weekdayNames.forEach { Text(it, color = onGlassSecondary(), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val day = w * 7 + d - offset + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            val isToday = day == today
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .then(if (isToday) Modifier.liquidGlass(CircleShape, tint = Color(0xFFFF453A), strength = 2f) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(day.toString(), color = if (isToday) Color.White else onGlass(), fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal data class BatteryInfo(val level: Int, val charging: Boolean)

@Composable
internal fun rememberBattery(): BatteryInfo {
    val context = LocalContext.current
    var info by remember { mutableStateOf(BatteryInfo(100, false)) }
    val active = LocalContentActive.current
    // 表示中だけ受信する (他のアプリ使用中や見えていないページでは登録しない)
    LifecycleStartEffect(context, active) {
        if (!active) return@LifecycleStartEffect onStopOrDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (level >= 0) {
                    info = BatteryInfo(
                        level = level * 100 / scale.coerceAtLeast(1),
                        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                    )
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let { receiver.onReceive(context, it) }
        onStopOrDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return info
}

@Composable
private fun BatteryWidget(size: WidgetSize, modifier: Modifier) {
    val battery = rememberBattery()
    val color = when {
        battery.charging -> Color(0xFF34C759)
        battery.level <= 20 -> Color(0xFFFF453A)
        else -> Color(0xFF34C759)
    }
    WidgetCard(modifier) {
        if (size == WidgetSize.SMALL) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BatteryRing(battery, color, Modifier.fillMaxSize(0.82f))
            }
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                BatteryRing(battery, color, Modifier.fillMaxHeight(0.85f).aspectRatio(1f))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.widget_this_device), color = onGlassSecondary(), fontSize = 13.sp)
                    Text("${battery.level}%", color = onGlass(), fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
                    if (battery.charging) Text(stringResource(R.string.widget_charging), color = color, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun BatteryRing(battery: BatteryInfo, color: Color, modifier: Modifier) {
    val track = onGlass().copy(alpha = 0.15f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // ウィジェットの枠は端末によって正方形でないので、リングを枠の中央に描く (中のマークと % は中央揃え)
            val d = size.minDimension
            val stroke = d * 0.1f
            val arcSize = androidx.compose.ui.geometry.Size(d - stroke, d - stroke)
            val topLeft = Offset((size.width - d) / 2f + stroke / 2, (size.height - d) / 2f + stroke / 2)
            drawArc(track, 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * battery.level / 100f, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (battery.charging) Icons.Rounded.Bolt else Icons.Rounded.PhoneAndroid, null, tint = onGlass(), modifier = Modifier.size(22.dp))
            Text("${battery.level}%", color = onGlass(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SuggestionsWidget(size: WidgetSize, modifier: Modifier, interactive: Boolean) {
    val app = LauncherApp.instance
    val apps = LocalApps.current
    val s = LocalLauncherSettings.current
    val version by app.usageTracker.version.collectAsState()
    val count = when (size) {
        WidgetSize.SMALL -> 4
        WidgetSize.MEDIUM -> 8
        WidgetSize.LARGE -> 16
    }
    val suggestions = remember(apps, version, count, s.hiddenApps) {
        app.usageTracker.suggestions(apps.values.filter { it.id !in s.hiddenApps }, count)
    }
    val actions = LocalLauncherActions.current
    val columns = if (size == WidgetSize.SMALL) 2 else 4
    WidgetCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rows = (count + columns - 1) / columns
            val cellW = maxWidth / columns
            val cellH = maxHeight / rows
            val icon = if (cellW < cellH) cellW * 0.78f else cellH * 0.62f
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                suggestions.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { entry ->
                            var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(cellW)) {
                                AppIconImage(
                                    entry.id,
                                    icon,
                                    Modifier
                                        .onGloballyPositioned { coords = it }
                                        .then(
                                            if (interactive) {
                                                Modifier.clickable { actions.launchApp(entry.id, coords?.boundsInRoot()) }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                                if (size != WidgetSize.SMALL) {
                                    Text(entry.label, color = onGlass(), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteWidget(item: WidgetItem, modifier: Modifier, interactive: Boolean) {
    val actions = LocalLauncherActions.current
    val text = item.config["text"].orEmpty()
    Box(
        modifier
            .liquidGlass(WidgetShape, tint = Color(0xFFFFD60A), strength = 1.1f)
            .then(if (interactive) Modifier.clip(WidgetShape).clickable { actions.editNote(item) } else Modifier)
            .padding(14.dp),
    ) {
        Column {
            Text(stringResource(R.string.widget_note), color = Color(0xFFFF9F0A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text.ifEmpty { stringResource(R.string.widget_note_placeholder) },
                color = if (text.isEmpty()) onGlassSecondary() else onGlass(),
                fontSize = 15.sp,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchWidget(modifier: Modifier, interactive: Boolean) {
    val actions = LocalLauncherActions.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .liquidGlass(RoundedCornerShape(24.dp))
                .then(if (interactive) Modifier.clip(RoundedCornerShape(24.dp)).clickable { actions.openSpotlight() } else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = onGlass(), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.search), color = onGlassSecondary(), fontSize = 17.sp)
        }
    }
}

@Composable
private fun HostedAppWidget(item: WidgetItem, modifier: Modifier, interactive: Boolean) {
    val host = LocalWidgetHost.current
    val actions = LocalLauncherActions.current
    val density = LocalDensity.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(modifier.clip(WidgetShape).onGloballyPositioned { coords = it }) {
        if (host == null || host.info(item.appWidgetId) == null) {
            Box(Modifier.fillMaxSize().liquidGlass(WidgetShape), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.widget_unavailable), color = onGlassSecondary(), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    host.createView(ctx, item.appWidgetId) ?: TextView(ctx)
                },
                update = { view ->
                    if (view is RoundedWidgetHostView) {
                        view.onLongPress = { coords?.takeIf { it.isAttached }?.let { actions.widgetMenu(item, it.boundsInRoot()) } }
                        coords?.let { c ->
                            val w = with(density) { c.size.width.toDp().value.toInt() }
                            val h = with(density) { c.size.height.toDp().value.toInt() }
                            if (w > 0 && h > 0) WidgetHostManager.updateSize(view, w, h)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!interactive) {
            // 編集モード中はウィジェットへのタッチを止め、ドラッグできるようにする
            Box(Modifier.fillMaxSize().pointerInput(Unit) { })
        }
    }
}
