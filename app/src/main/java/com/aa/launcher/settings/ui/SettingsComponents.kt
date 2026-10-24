package com.aa.launcher.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aa.launcher.ui.common.IosSegmented
import com.aa.launcher.ui.common.IosSlider
import com.aa.launcher.ui.common.IosSwitch
import com.aa.launcher.ui.theme.LocalIsDark

/** iOS の設定アプリの配色。 */
object SettingsColors {
    @Composable
    fun background() = if (LocalIsDark.current) Color.Black else Color(0xFFF2F2F7)

    @Composable
    fun cell() = if (LocalIsDark.current) Color(0xFF1C1C1E) else Color.White

    @Composable
    fun separator() = if (LocalIsDark.current) Color(0xFF38383A) else Color(0xFFC6C6C8)

    @Composable
    fun label() = if (LocalIsDark.current) Color.White else Color.Black

    @Composable
    fun secondary() = if (LocalIsDark.current) Color(0xFF98989F) else Color(0xFF8A8A8E)

    @Composable
    fun bar() = if (LocalIsDark.current) Color(0xF01C1C1E) else Color(0xF0F9F9F9)
}

@Composable
fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    backLabel: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val collapsed = scroll.value > with(density) { 40.dp.toPx() }
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().background(SettingsColors.background())) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(top = 44.dp)
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
        ) {
            Text(
                title,
                color = SettingsColors.label(),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally)) {
                content()
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(if (collapsed) SettingsColors.bar() else Color.Transparent)
                .statusBarsPadding()
                .height(44.dp),
        ) {
            if (onBack != null) {
                Row(
                    Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ArrowBackIosNew, null, tint = accent, modifier = Modifier.size(20.dp))
                    if (backLabel != null) {
                        Text(backLabel, color = accent, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                    }
                }
            }
            if (collapsed) {
                Text(
                    title,
                    color = SettingsColors.label(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 100.dp),
                )
            }
        }
    }
}

private class DividerPositions {
    var ys: List<Int> = emptyList()
}

/** 子要素の間に iOS 風の区切り線を自動で入れる縦並び。 */
@Composable
private fun DividedColumn(color: Color, startInset: Dp, content: @Composable () -> Unit) {
    val positions = remember { DividerPositions() }
    Layout(
        content = content,
        modifier = Modifier.drawWithContent {
            drawContent()
            val inset = startInset.toPx()
            val stroke = 0.5.dp.toPx()
            positions.ys.forEach { y ->
                drawLine(color, Offset(inset, y.toFloat()), Offset(size.width, y.toFloat()), stroke)
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)) }
        val ys = ArrayList<Int>()
        var y = 0
        val visible = placeables.filter { it.height > 0 }
        visible.forEachIndexed { i, p ->
            y += p.height
            if (i < visible.size - 1) ys += y
        }
        positions.ys = ys
        layout(constraints.maxWidth, y) {
            var yy = 0
            placeables.forEach { p ->
                p.placeRelative(0, yy)
                yy += p.height
            }
        }
    }
}

@Composable
fun SettingsGroup(
    header: String? = null,
    footer: String? = null,
    dividerInset: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 22.dp)) {
        if (header != null) {
            Text(
                header.uppercase(),
                color = SettingsColors.secondary(),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 7.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SettingsColors.cell()),
        ) {
            DividedColumn(SettingsColors.separator(), dividerInset, content)
        }
        if (footer != null) {
            Text(
                footer,
                color = SettingsColors.secondary(),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

@Composable
fun SettingsIcon(icon: ImageVector, color: Color, size: Dp = 30.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.25f)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
private fun RowBase(
    onClick: (() -> Unit)?,
    icon: ImageVector?,
    iconColor: Color,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingsIcon(icon, iconColor)
            Spacer(Modifier.width(14.dp))
        }
        content()
    }
}

@Composable
private fun TitleBlock(title: String, subtitle: String?, modifier: Modifier, color: Color = SettingsColors.label()) {
    Column(modifier) {
        Text(title, color = color, fontSize = 17.sp)
        if (subtitle != null) Text(subtitle, color = SettingsColors.secondary(), fontSize = 13.sp)
    }
}

@Composable
fun NavRow(
    title: String,
    icon: ImageVector? = null,
    iconColor: Color = Color(0xFF8E8E93),
    value: String? = null,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    RowBase(onClick, icon, iconColor) {
        TitleBlock(title, subtitle, Modifier.weight(1f))
        if (value != null) {
            Text(value, color = SettingsColors.secondary(), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = SettingsColors.secondary().copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconColor: Color = Color(0xFF8E8E93),
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    RowBase({ onChange(!checked) }, icon, iconColor, enabled) {
        TitleBlock(title, subtitle, Modifier.weight(1f), if (enabled) SettingsColors.label() else SettingsColors.secondary())
        Spacer(Modifier.width(8.dp))
        IosSwitch(checked, onChange, enabled = enabled)
    }
}

@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        TitleBlock(title, subtitle, Modifier.weight(1f))
        if (checked) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun <T> ChoiceGroup(
    header: String?,
    footer: String?,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    SettingsGroup(header, footer) {
        options.forEach { option ->
            CheckRow(label(option), option == selected, { onSelect(option) })
        }
    }
}

@Composable
fun SliderRow(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    title: String? = null,
    valueLabel: String? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    startIcon: ImageVector? = null,
    endIcon: ImageVector? = null,
    trackBrush: androidx.compose.ui.graphics.Brush? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (title != null || valueLabel != null) {
            Row {
                Text(title.orEmpty(), color = SettingsColors.label(), fontSize = 17.sp, modifier = Modifier.weight(1f))
                if (valueLabel != null) Text(valueLabel, color = SettingsColors.secondary(), fontSize = 17.sp)
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            startIcon?.let { Icon(it, null, tint = SettingsColors.secondary(), modifier = Modifier.size(18.dp)) }
            startLabel?.let { Text(it, color = SettingsColors.secondary(), fontSize = 13.sp) }
            IosSlider(value, onChange, Modifier.weight(1f).padding(horizontal = 10.dp), range, steps, trackBrush)
            endLabel?.let { Text(it, color = SettingsColors.secondary(), fontSize = 13.sp) }
            endIcon?.let { Icon(it, null, tint = SettingsColors.secondary(), modifier = Modifier.size(24.dp)) }
        }
    }
}

@Composable
fun SegmentRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, title: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (title != null) {
            Text(title, color = SettingsColors.label(), fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
        }
        IosSegmented(options, selected, onSelect)
    }
}

@Composable
fun StepperRow(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit, icon: ImageVector? = null, iconColor: Color = Color(0xFF8E8E93)) {
    val dark = LocalIsDark.current
    RowBase(null, icon, iconColor) {
        Text(title, color = SettingsColors.label(), fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value.toString(), color = SettingsColors.secondary(), fontSize = 17.sp, modifier = Modifier.padding(end = 12.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (dark) Color(0xFF2C2C2E) else Color(0xFFE9E9EB)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(width = 46.dp, height = 32.dp).clickable(enabled = value > range.first) { onChange(value - 1) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Remove, null, tint = if (value > range.first) SettingsColors.label() else SettingsColors.secondary(), modifier = Modifier.size(18.dp))
            }
            Box(Modifier.width(0.5.dp).height(18.dp).background(SettingsColors.separator()))
            Box(
                Modifier.size(width = 46.dp, height = 32.dp).clickable(enabled = value < range.last) { onChange(value + 1) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, null, tint = if (value < range.last) SettingsColors.label() else SettingsColors.secondary(), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun ButtonRow(title: String, onClick: () -> Unit, destructive: Boolean = false, enabled: Boolean = true, icon: ImageVector? = null, iconColor: Color = Color(0xFF8E8E93)) {
    RowBase(onClick, icon, iconColor, enabled) {
        Text(
            title,
            color = when {
                !enabled -> SettingsColors.secondary()
                destructive -> Color(0xFFFF3B30)
                else -> MaterialTheme.colorScheme.primary
            },
            fontSize = 17.sp,
        )
    }
}

@Composable
fun ValueRow(title: String, value: String, icon: ImageVector? = null, iconColor: Color = Color(0xFF8E8E93)) {
    RowBase(null, icon, iconColor) {
        Text(title, color = SettingsColors.label(), fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value, color = SettingsColors.secondary(), fontSize = 17.sp)
    }
}

@Composable
fun ColorRow(title: String, colors: List<Color>, selected: Int, onSelect: (Color) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = SettingsColors.label(), fontSize = 17.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colors.forEach { c ->
                val isSelected = c.toArgb() == selected
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(c)
                        .then(if (isSelected) Modifier.border(3.dp, SettingsColors.label().copy(alpha = 0.6f), CircleShape) else Modifier)
                        .clickable { onSelect(c) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** iOS 風アラート。 */
@Composable
fun IosAlert(
    title: String,
    message: String?,
    confirmLabel: String,
    cancelLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(290.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SettingsColors.cell()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = SettingsColors.label(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                if (message != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(message, color = SettingsColors.label(), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(SettingsColors.separator()))
            Row(Modifier.fillMaxWidth().height(46.dp)) {
                Box(Modifier.weight(1f).fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text(cancelLabel, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
                }
                Box(Modifier.width(0.5.dp).fillMaxSize().background(SettingsColors.separator()))
                Box(
                    Modifier.weight(1f).fillMaxSize().clickable {
                        onConfirm()
                        onDismiss()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        confirmLabel,
                        color = if (destructive) Color(0xFFFF3B30) else MaterialTheme.colorScheme.primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsFooterText(text: String) {
    Text(
        text,
        color = SettingsColors.secondary(),
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp),
    )
}
