package com.aa.launcher.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aa.launcher.R
import com.aa.launcher.core.SystemActions
import com.aa.launcher.ui.common.onGlass
import com.aa.launcher.ui.common.onGlassSecondary
import com.aa.launcher.ui.glass.liquidGlass
import com.aa.launcher.ui.home.LauncherViewModel

/** 初回起動時の案内 (iOS の「こんにちは」風)。 */
@Composable
fun OnboardingOverlay(vm: LauncherViewModel) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val isDefault = remember(tick) { SystemActions.isDefaultLauncher(context) }
    val hasNotifications = remember(tick) { SystemActions.isNotificationAccessGranted(context) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        tick++
        if (!SystemActions.isDefaultLauncher(context)) SystemActions.openHomeSettings(context)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(36.dp), strength = 1.8f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.onboarding_hello),
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFFAF52DE), Color(0xFFFF2D55), Color(0xFFFF9500))),
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.onboarding_subtitle), color = onGlassSecondary(), fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Step(Icons.Rounded.Home, stringResource(R.string.onboarding_default_title), stringResource(R.string.onboarding_default_desc), isDefault) {
                val intent = SystemActions.homeRoleIntent(context)
                if (intent != null) roleLauncher.launch(intent) else SystemActions.openHomeSettings(context)
            }
            Step(Icons.Rounded.Notifications, stringResource(R.string.onboarding_badges_title), stringResource(R.string.onboarding_badges_desc), hasNotifications) {
                SystemActions.openNotificationAccess(context)
            }
            Step(Icons.Rounded.Wallpaper, stringResource(R.string.onboarding_wallpaper_title), stringResource(R.string.onboarding_wallpaper_desc), false) {
                vm.openSettings(context, "wallpaper")
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.onboarding_start),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(Color(0xFF0A84FF))
                    .clickable { vm.updateSettings { it.copy(onboardingDone = true) } }
                    .padding(vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, description: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !done, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF0A84FF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = onGlass(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = onGlassSecondary(), fontSize = 13.sp)
        }
        if (done) Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(24.dp))
    }
}
