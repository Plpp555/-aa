package com.aa.launcher.ui.common

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aa.launcher.R
import com.aa.launcher.core.IconShapes
import com.aa.launcher.core.IconSpec
import com.aa.launcher.core.RenderStyle
import com.aa.launcher.core.rememberAppIcon
import com.aa.launcher.settings.BadgeStyle
import com.aa.launcher.settings.ClearVariant
import com.aa.launcher.settings.IconStyle
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.theme.LocalIsDark
import com.aa.launcher.ui.theme.LocalLauncherSettings

/** 壁紙の平均色 (Tinted アイコンの自動色などに使う)。 */
val LocalWallpaperColor = compositionLocalOf { 0xFF3A3A3C.toInt() }

@Composable
fun rememberIconSpec(): IconSpec {
    val s = LocalLauncherSettings.current
    val dark = LocalIsDark.current
    val wallpaperColor = LocalWallpaperColor.current
    val style = when (s.iconStyle) {
        IconStyle.AUTOMATIC -> if (dark) RenderStyle.DARK else RenderStyle.DEFAULT
        IconStyle.LIGHT -> RenderStyle.DEFAULT
        IconStyle.DARK -> RenderStyle.DARK
        IconStyle.TINTED -> RenderStyle.TINTED
        IconStyle.CLEAR -> when (s.clearVariant) {
            ClearVariant.AUTOMATIC -> if (dark) RenderStyle.CLEAR_DARK else RenderStyle.CLEAR_LIGHT
            ClearVariant.LIGHT -> RenderStyle.CLEAR_LIGHT
            ClearVariant.DARK -> RenderStyle.CLEAR_DARK
        }
    }
    val tint = if (s.tintFromWallpaper) wallpaperColor or 0xFF000000.toInt() else s.iconTintColor
    val px = with(LocalDensity.current) { 76.dp.roundToPx() }.coerceIn(96, 256)
    return remember(style, s.iconShape, tint, s.iconPack, px) { IconSpec(style, s.iconShape, tint, s.iconPack, px) }
}

/** 「デフォルト」スタイル固定のアイコン仕様 (設定画面の一覧など)。 */
@Composable
fun rememberPlainIconSpec(): IconSpec {
    val s = LocalLauncherSettings.current
    val px = with(LocalDensity.current) { 48.dp.roundToPx() }.coerceIn(64, 192)
    return remember(s.iconShape, s.iconPack, px) { IconSpec(RenderStyle.DEFAULT, s.iconShape, 0, s.iconPack, px) }
}

@Composable
fun AppIconImage(
    appId: String,
    size: Dp,
    modifier: Modifier = Modifier,
    spec: IconSpec = rememberIconSpec(),
    workBadge: Boolean = false,
    allowShadow: Boolean = true,
) {
    val bitmap = rememberAppIcon(appId, spec)
    val shape = IconShapes.composeShape(spec.shape)
    val clear = spec.style == RenderStyle.CLEAR_LIGHT || spec.style == RenderStyle.CLEAR_DARK
    val shadow = allowShadow && LocalLauncherSettings.current.iconShadow
    Box(
        modifier
            .size(size)
            .then(
                if (shadow && !clear) {
                    Modifier.shadow(3.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                } else {
                    Modifier
                },
            )
            .then(if (clear) Modifier.liquidGlass(shape, tint = if (spec.style == RenderStyle.CLEAR_DARK) Color.Black else Color.Unspecified, strength = 0.85f) else Modifier),
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), filterQuality = FilterQuality.Medium)
        } else {
            Box(Modifier.fillMaxSize().clip(shape).background(Color.White.copy(alpha = 0.18f)))
        }
        if (workBadge) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.36f)
                    .shadow(2.dp, CircleShape)
                    .background(Color(0xFFFF9500), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Work, null, tint = Color.White, modifier = Modifier.size(size * 0.22f))
            }
        }
    }
}

@Composable
fun NotificationBadge(count: Int, modifier: Modifier = Modifier) {
    val s = LocalLauncherSettings.current
    if (count <= 0 || s.badgeStyle == BadgeStyle.OFF) return
    val color = Color(s.badgeColor)
    if (s.badgeStyle == BadgeStyle.DOT) {
        Box(modifier.size(14.dp).shadow(1.dp, CircleShape).background(color, CircleShape))
    } else {
        Box(
            modifier
                .height(22.dp)
                .widthIn(min = 22.dp)
                .shadow(1.dp, CircleShape)
                .background(color, CircleShape)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 999) "999+" else count.toString(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** 編集モードでアイコン左上に出る「−」ボタン (iOS 18 以降のスタイル)。 */
@Composable
fun MinusBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalIsDark.current
    Box(
        modifier
            .size(24.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(if (dark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.action_remove), tint = if (dark) Color.White else Color.Black, modifier = Modifier.size(16.dp))
    }
}

/** 押したときに少し縮む (iOS のボタン風)。 */
@Composable
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

fun View.haptic(enabled: Boolean, type: Int = HapticFeedbackConstants.LONG_PRESS) {
    if (enabled) performHapticFeedback(type)
}

@Composable
fun rememberHaptics(): (Int) -> Unit {
    val view = LocalView.current
    val enabled = LocalLauncherSettings.current.haptics
    return remember(view, enabled) { { type: Int -> view.haptic(enabled, type) } }
}

/** iOS 風の検索フィールド (ガラス)。 */
@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onSearch: () -> Unit = {},
    textColor: Color = Color.White,
) {
    Row(
        modifier
            .height(44.dp)
            .liquidGlass(RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = textColor.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = textColor.copy(alpha = 0.6f), fontSize = 17.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = textColor, fontSize = 17.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Cancel,
                contentDescription = stringResource(R.string.action_clear),
                tint = textColor.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

/** iOS のアクションシート (下からせり上がる選択肢)。 */
@Composable
fun ActionSheet(
    visible: Boolean,
    title: String?,
    message: String?,
    actions: List<Triple<String, Boolean, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(10.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlass(RoundedCornerShape(26.dp), strength = 1.6f),
                ) {
                    if (title != null || message != null) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            title?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = sheetSecondary(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                            }
                            message?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = sheetSecondary(), textAlign = TextAlign.Center)
                            }
                        }
                        HorizontalDivider(color = sheetDivider(), thickness = 0.5.dp)
                    }
                    actions.forEachIndexed { index, (label, destructive, onClick) ->
                        if (index > 0) HorizontalDivider(color = sheetDivider(), thickness = 0.5.dp)
                        Text(
                            text = label,
                            color = if (destructive) Color(0xFFFF453A) else MaterialTheme.colorScheme.primary,
                            fontSize = 19.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onClick()
                                }
                                .padding(vertical = 16.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(RoundedCornerShape(26.dp), strength = 1.8f)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
fun sheetSecondary(): Color = if (LocalIsDark.current) Color(0xFFAEAEB2) else Color(0xFF6C6C70)

@Composable
fun sheetDivider(): Color = if (LocalIsDark.current) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)

/** ガラス上の文字色 (ダーク: 白、ライト: 黒)。 */
@Composable
fun onGlass(): Color = if (LocalIsDark.current) Color.White else Color.Black

@Composable
fun onGlassSecondary(): Color = if (LocalIsDark.current) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.55f)
