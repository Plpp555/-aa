package com.aa.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aa.launcher.R
import com.aa.launcher.core.GridPacker
import com.aa.launcher.core.GridSpec
import com.aa.launcher.core.HomePage
import com.aa.launcher.core.SquircleShape
import com.aa.launcher.core.WidgetItem
import com.aa.launcher.settings.ClearVariant
import com.aa.launcher.settings.IconStyle
import com.aa.launcher.settings.ThemeMode
import com.aa.launcher.ui.common.AppIconImage
import com.aa.launcher.ui.common.HueBrush
import com.aa.launcher.ui.common.IosSegmented
import com.aa.launcher.ui.common.IosSlider
import com.aa.launcher.ui.common.colorToHsv
import com.aa.launcher.ui.common.hsvColor
import com.aa.launcher.ui.common.onGlass
import com.aa.launcher.ui.common.onGlassSecondary
import com.aa.launcher.ui.common.rememberIconSpec
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.theme.LocalIsDark
import com.aa.launcher.ui.theme.LocalLauncherSettings
import androidx.compose.foundation.layout.windowInsetsPadding

/** iOS 18/26/27 の「カスタマイズ」シート: アイコンサイズ・スタイル・色合い・Liquid Glass。 */
@Composable
fun CustomizeSheet(vm: LauncherViewModel) {
    val visible = vm.customizeOpen
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = MutableInteractionSource(), indication = null) { vm.customizeOpen = false },
        )
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            CustomizeContent(vm)
        }
    }
}

@Composable
private fun CustomizeContent(vm: LauncherViewModel) {
    val s = LocalLauncherSettings.current
    val dark = LocalIsDark.current
    Column(
        Modifier
            .navigationBarsPadding()
            .padding(10.dp)
            .widthIn(max = MaxPanelWidth)
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(36.dp), strength = 1.5f)
            .clickable(interactionSource = MutableInteractionSource(), indication = null) { }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.customize), color = onGlass(), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // ライト/ダーク切り替え (iOS の太陽アイコン)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(onGlass().copy(alpha = 0.1f))
                    .clickable {
                        vm.updateSettings { it.copy(themeMode = if (dark) ThemeMode.LIGHT else ThemeMode.DARK) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (dark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, null, tint = onGlass(), modifier = Modifier.size(20.dp))
            }
        }

        // サイズ (小 / 大)
        IosSegmented(
            options = listOf(stringResource(R.string.size_small), stringResource(R.string.size_large)),
            selected = if (s.largeIcons) 1 else 0,
            onSelect = { i -> vm.updateSettings { it.copy(largeIcons = i == 1) } },
        )

        // スタイル
        val styles = IconStyle.entries
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            styles.forEach { style ->
                StyleChip(style, selected = s.iconStyle == style) { vm.updateSettings { it.copy(iconStyle = style) } }
            }
        }

        if (s.iconStyle == IconStyle.TINTED) {
            val hsv = colorToHsv(s.iconTintColor)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IosSlider(
                    value = hsv[0],
                    onValueChange = { h -> vm.updateSettings { it.copy(iconTintColor = hsvColor(h, hsv[1].coerceAtLeast(0.4f), hsv[2].coerceAtLeast(0.6f)), tintFromWallpaper = false) } },
                    valueRange = 0f..360f,
                    trackBrush = HueBrush,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (s.tintFromWallpaper) Color(0xFF0A84FF) else onGlass().copy(alpha = 0.12f))
                        .clickable { vm.updateSettings { it.copy(tintFromWallpaper = !it.tintFromWallpaper) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Colorize, stringResource(R.string.tint_from_wallpaper), tint = if (s.tintFromWallpaper) Color.White else onGlass(), modifier = Modifier.size(18.dp))
                }
            }
            IosSlider(
                value = hsv[2],
                onValueChange = { v -> vm.updateSettings { it.copy(iconTintColor = hsvColor(hsv[0], hsv[1].coerceAtLeast(0.4f), v.coerceAtLeast(0.3f))) } },
                trackBrush = Brush.horizontalGradient(listOf(Color(hsvColor(hsv[0], hsv[1], 0.3f)), Color(hsvColor(hsv[0], hsv[1], 1f)))),
            )
        }
        if (s.iconStyle == IconStyle.CLEAR) {
            IosSegmented(
                options = ClearVariant.entries.map { stringResource(it.label) },
                selected = s.clearVariant.ordinal,
                onSelect = { i -> vm.updateSettings { it.copy(clearVariant = ClearVariant.entries[i]) } },
            )
        }

        // Liquid Glass (iOS 27): クリア ↔ 色付き
        Column {
            Text(stringResource(R.string.liquid_glass), color = onGlass(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.glass_clear), color = onGlassSecondary(), fontSize = 13.sp)
                IosSlider(
                    value = s.glassOpacity,
                    onValueChange = { v -> vm.updateSettings { it.copy(glassOpacity = v) } },
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                Text(stringResource(R.string.glass_tinted), color = onGlassSecondary(), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StyleChip(style: IconStyle, selected: Boolean, onClick: () -> Unit) {
    val preview: Brush = when (style) {
        IconStyle.AUTOMATIC -> Brush.linearGradient(listOf(Color.White, Color(0xFF1C1C1E)))
        IconStyle.LIGHT -> Brush.linearGradient(listOf(Color(0xFF34C759), Color(0xFF0A84FF)))
        IconStyle.DARK -> Brush.linearGradient(listOf(Color(0xFF3A3A3C), Color.Black))
        IconStyle.CLEAR -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.15f)))
        IconStyle.TINTED -> Brush.linearGradient(listOf(Color(0xFFFFB340), Color(0xFF3A2A10)))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(46.dp)
                .clip(SquircleShape)
                .background(preview)
                .then(if (selected) Modifier.border(2.5.dp, Color(0xFF0A84FF), SquircleShape) else Modifier),
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(style.label), color = onGlass(), fontSize = 11.sp, maxLines = 1)
    }
}

/** ページを編集 (表示/非表示・削除)。 */
@Composable
fun EditPagesOverlay(vm: LauncherViewModel) {
    val visible = vm.editPagesOpen
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        val layout = vm.layout.value
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(interactionSource = MutableInteractionSource(), indication = null) { vm.editPagesOpen = false }
                .windowInsetsPadding(launcherStatusBars())
                .navigationBarsPadding(),
        ) {
            if (layout != null) {
                Column(Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.edit_pages),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.done),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .liquidGlass(CircleShape, tint = Color(0xFF0A84FF), strength = 1.4f)
                                .clip(CircleShape)
                                .clickable { vm.editPagesOpen = false }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(layout.pages, key = { it.id }) { page ->
                            PageThumbnail(
                                page = page,
                                onToggle = { vm.setPageHidden(page.id, !page.hidden) },
                                onDelete = { vm.deletePage(page.id) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.edit_pages_hint),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageThumbnail(page: HomePage, onToggle: () -> Unit, onDelete: () -> Unit) {
    val s = LocalLauncherSettings.current
    val grid = GridSpec(s.gridColumns, s.gridRows, s.dockColumns)
    val spec = rememberIconSpec()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.56f)
                    .liquidGlass(RoundedCornerShape(22.dp))
                    .clickable(onClick = onToggle)
                    .padding(10.dp),
            ) {
                val cellW = maxWidth / grid.columns
                val cellH = maxHeight / grid.rows
                val icon: Dp = if (cellW < cellH) cellW * 0.7f else cellH * 0.7f
                GridPacker.pack(page.items, grid).first.forEach { pl ->
                    val x = cellW * pl.cell.x
                    val y = cellH * pl.cell.y
                    when (val item = pl.item) {
                        is com.aa.launcher.core.AppItem -> AppIconImage(
                            item.app,
                            icon,
                            Modifier.offset(x + (cellW - icon) / 2, y + (cellH - icon) / 2),
                            spec = spec,
                            allowShadow = false,
                        )
                        is com.aa.launcher.core.FolderItem -> Box(
                            Modifier
                                .offset(x + (cellW - icon) / 2, y + (cellH - icon) / 2)
                                .size(icon)
                                .liquidGlass(SquircleShape),
                        )
                        is WidgetItem -> Box(
                            Modifier
                                .offset(x + 2.dp, y + 2.dp)
                                .size(cellW * pl.cell.w - 4.dp, cellH * pl.cell.h - 4.dp)
                                .liquidGlass(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8E8E93))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Remove, stringResource(R.string.action_remove), tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (!page.hidden) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.25f))
                .border(1.5.dp, Color.White, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (!page.hidden) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
