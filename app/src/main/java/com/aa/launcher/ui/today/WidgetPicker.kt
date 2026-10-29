package com.aa.launcher.ui.today

import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.aa.launcher.R
import com.aa.launcher.core.WidgetItem
import com.aa.launcher.core.WidgetKind
import com.aa.launcher.core.WidgetSize
import com.aa.launcher.ui.common.onGlass
import com.aa.launcher.ui.common.onGlassSecondary
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.home.LauncherViewModel
import com.aa.launcher.ui.home.MaxPanelWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class ProviderEntry(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val icon: ImageBitmap?,
    val size: WidgetSize,
)

/** ウィジェットギャラリー (内蔵ウィジェット + Android ウィジェット)。 */
@Composable
fun WidgetPickerSheet(vm: LauncherViewModel) {
    val target = vm.widgetPicker
    AnimatedVisibility(target != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.widgetPicker = null },
        )
    }
    AnimatedVisibility(
        target != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            PickerContent(vm)
        }
    }
}

@Composable
private fun PickerContent(vm: LauncherViewModel) {
    val context = LocalContext.current
    val target = vm.widgetPicker
    val host = vm.widgetHost
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val providers by produceState<List<ProviderEntry>>(emptyList(), host) {
        val h = host ?: return@produceState
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val density = context.resources.displayMetrics.density
            h.providers().mapNotNull { info ->
                runCatching {
                    val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrDefault(info.provider.packageName)
                    val icon = runCatching { info.loadIcon(context, context.resources.displayMetrics.densityDpi)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
                    ProviderEntry(info, info.loadLabel(pm) ?: appLabel, appLabel, icon, sizeFor(info, density))
                }.getOrNull()
            }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
        }
    }
    var query by remember { mutableStateOf("") }
    val sizes = remember { mutableStateOf(WidgetKind.entries.associateWith { WidgetSize.SMALL }) }

    Column(
        Modifier
            .widthIn(max = MaxPanelWidth)
            .fillMaxWidth()
            .fillMaxHeight(0.86f)
            .liquidGlass(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp), strength = 1.6f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.widgets), color = onGlass(), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(onGlass().copy(alpha = 0.12f)).clickable { vm.widgetPicker = null },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, stringResource(R.string.action_close), tint = onGlass(), modifier = Modifier.size(18.dp))
            }
        }
        SearchBox(query, { query = it }, Modifier.padding(horizontal = 16.dp))
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = navBottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val builtins = WidgetKind.entries.filter { it != WidgetKind.APP_WIDGET }
            item { SectionTitle(stringResource(R.string.widgets_builtin)) }
            items(builtins, key = { it.name }) { kind ->
                val label = stringResource(kind.label)
                if (query.isNotBlank() && !label.contains(query, ignoreCase = true)) return@items
                val size = sizes.value.getValue(kind)
                BuiltinRow(
                    kind = kind,
                    size = size,
                    onSize = { sizes.value = sizes.value + (kind to it) },
                    onAdd = { if (target != null) vm.addBuiltinWidget(kind, size, target) },
                )
            }
            if (providers.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.widgets_android)) }
                val filtered = providers.filter {
                    query.isBlank() || it.label.contains(query, ignoreCase = true) || it.appLabel.contains(query, ignoreCase = true)
                }
                items(filtered, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { entry ->
                    ProviderRow(entry) { if (target != null) vm.addAppWidget(entry.info, entry.size, target) }
                }
            }
        }
    }
}

private fun sizeFor(info: AppWidgetProviderInfo, density: Float): WidgetSize {
    val (cellsX, cellsY) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0) {
        info.targetCellWidth to info.targetCellHeight
    } else {
        val w = info.minWidth / density
        val h = info.minHeight / density
        ((w + 30) / 70).toInt().coerceAtLeast(1) to ((h + 30) / 70).toInt().coerceAtLeast(1)
    }
    return when {
        cellsX <= 2 && cellsY <= 2 -> WidgetSize.SMALL
        cellsY <= 2 -> WidgetSize.MEDIUM
        else -> WidgetSize.LARGE
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(onGlass().copy(alpha = 0.08f))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(stringResource(R.string.search_widgets), color = onGlassSecondary(), fontSize = 16.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = onGlass(), fontSize = 16.sp),
            cursorBrush = SolidColor(Color(0xFF0A84FF)),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = onGlassSecondary(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun BuiltinRow(kind: WidgetKind, size: WidgetSize, onSize: (WidgetSize) -> Unit, onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(onGlass().copy(alpha = 0.06f))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 96.dp, height = 96.dp)) {
                WidgetContent(WidgetItem("preview-${kind.name}", kind, WidgetSize.SMALL), Modifier.fillMaxSize(), interactive = false)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(kind.label), color = onGlass(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(kind.description), color = onGlassSecondary(), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WidgetSize.entries.forEach { s ->
                val selected = s == size
                Text(
                    stringResource(s.label),
                    color = if (selected) Color.White else onGlass(),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color(0xFF0A84FF) else onGlass().copy(alpha = 0.1f))
                        .clickable { onSize(s) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.add_widget_button),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF0A84FF))
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ProviderRow(entry: ProviderEntry, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(onGlass().copy(alpha = 0.06f))
            .clickable(onClick = onAdd)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.icon != null) {
            Image(entry.icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
        } else {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(onGlass().copy(alpha = 0.1f)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.label, color = onGlass(), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${entry.appLabel} · ${stringResource(entry.size.label)}", color = onGlassSecondary(), fontSize = 12.sp, maxLines = 1)
        }
        Text(
            stringResource(R.string.add_widget_button),
            color = Color(0xFF0A84FF),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** メモウィジェットの編集ダイアログ。 */
@Composable
fun NoteEditorDialog(vm: LauncherViewModel) {
    val item = vm.noteEditing
    AnimatedVisibility(item != null, enter = fadeIn(), exit = fadeOut()) {
        if (item == null) return@AnimatedVisibility
        var text by remember(item.id) { mutableStateOf(item.config["text"].orEmpty()) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(item.id) { runCatching { focus.requestFocus() } }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.noteEditing = null },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .widthIn(max = MaxPanelWidth)
                    .fillMaxWidth()
                    .liquidGlass(RoundedCornerShape(28.dp), tint = Color(0xFFFFD60A), strength = 1.6f)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(18.dp),
            ) {
                Text(stringResource(R.string.widget_note), color = onGlass(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = onGlass(), fontSize = 17.sp),
                    cursorBrush = SolidColor(Color(0xFFFF9F0A)),
                    modifier = Modifier.fillMaxWidth().height(160.dp).focusRequester(focus),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = onGlass(),
                        modifier = Modifier.clip(CircleShape).clickable { vm.noteEditing = null }.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        stringResource(R.string.action_save),
                        color = Color(0xFFFF9F0A),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).clickable {
                            vm.updateWidget(item.copy(config = item.config + ("text" to text)))
                            vm.noteEditing = null
                        }.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
