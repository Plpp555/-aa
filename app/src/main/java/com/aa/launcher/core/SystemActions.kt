package com.aa.launcher.core

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import com.aa.launcher.R
import com.aa.launcher.service.BadgeNotificationListener
import com.aa.launcher.service.LauncherAccessibilityService
import com.aa.launcher.settings.SearchEngine
import java.net.URLEncoder

/** システムの画面やパネルを開く・操作するユーティリティ。 */
object SystemActions {

    fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_no_app, Toast.LENGTH_SHORT).show()
        false
    } catch (e: SecurityException) {
        Toast.makeText(context, R.string.error_generic, Toast.LENGTH_SHORT).show()
        false
    }

    // ---- ステータスバー -------------------------------------------------------

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun statusBar(context: Context, method: String): Boolean = runCatching {
        val service = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod(method).invoke(service)
        true
    }.getOrDefault(false)

    fun expandNotifications(context: Context) {
        if (!statusBar(context, "expandNotificationsPanel")) {
            LauncherAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }
    }

    fun expandQuickSettings(context: Context) {
        if (!statusBar(context, "expandSettingsPanel")) {
            LauncherAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        }
    }

    // ---- アクセシビリティ経由の操作 ---------------------------------------------

    fun isAccessibilityEnabled(): Boolean = LauncherAccessibilityService.instance != null

    fun lockScreen(context: Context) {
        // ユーザー補助からの画面ロックは Android 9 以降
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(context, R.string.toast_lock_screen_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        val service = LauncherAccessibilityService.instance
        if (service != null) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            Toast.makeText(context, R.string.toast_enable_accessibility, Toast.LENGTH_LONG).show()
            openAccessibilitySettings(context)
        }
    }

    fun openRecents(context: Context) {
        val service = LauncherAccessibilityService.instance
        if (service != null) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        } else {
            Toast.makeText(context, R.string.toast_enable_accessibility, Toast.LENGTH_LONG).show()
            openAccessibilitySettings(context)
        }
    }

    fun openAccessibilitySettings(context: Context) = start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    // ---- 既定のホームアプリ -----------------------------------------------------

    fun isDefaultLauncher(context: Context): Boolean {
        // RoleManager は Android 10 以降。それより前は HOME を解決した結果で判定する
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val role = context.getSystemService(RoleManager::class.java)
            if (role != null && role.isRoleAvailable(RoleManager.ROLE_HOME)) return role.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == context.packageName
    }

    /** 既定のホームアプリにする要求 (Android 10 以降)。null のときはホーム設定画面を開く。 */
    fun homeRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val role = context.getSystemService(RoleManager::class.java) ?: return null
        return if (role.isRoleAvailable(RoleManager.ROLE_HOME) && !role.isRoleHeld(RoleManager.ROLE_HOME)) {
            role.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            null
        }
    }

    fun openHomeSettings(context: Context) = start(context, Intent(Settings.ACTION_HOME_SETTINGS))

    // ---- 通知アクセス ---------------------------------------------------------

    fun isNotificationAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun openNotificationAccess(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, BadgeNotificationListener::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        if (!start(context, intent)) start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    // ---- パネル / 設定画面 -----------------------------------------------------

    /** 設定パネル (Android 10 以降) を開く。それより前は通常の設定画面を開く。 */
    fun openPanel(context: Context, action: String, fallback: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            start(context, Intent(fallback))
            return
        }
        val ok = runCatching {
            context.startActivity(Intent(action).apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }.isSuccess
        if (!ok) start(context, Intent(fallback))
    }

    fun openWifi(context: Context) = openPanel(context, Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
    fun openInternet(context: Context) = openPanel(context, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIRELESS_SETTINGS)
    fun openBluetooth(context: Context) = start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    fun openAirplane(context: Context) = start(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
    fun openLocation(context: Context) = start(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    fun openDisplay(context: Context) = start(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))
    fun openSystemSettings(context: Context) = start(context, Intent(Settings.ACTION_SETTINGS))
    fun openTimer(context: Context) = start(context, Intent(AlarmClock.ACTION_SHOW_TIMERS))
    fun openAlarms(context: Context) = start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
    fun openCamera(context: Context) = start(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    fun openCalculator(context: Context) =
        start(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))

    fun openWriteSettings(context: Context) =
        start(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))

    fun openDndAccess(context: Context) = start(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    // ---- 検索 ---------------------------------------------------------------

    fun webSearch(context: Context, engine: SearchEngine, query: String) {
        val url = engine.urlTemplate.format(URLEncoder.encode(query, "UTF-8"))
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun playStoreSearch(context: Context, query: String) {
        val q = URLEncoder.encode(query, "UTF-8")
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q&c=apps"))
        val ok = runCatching { context.startActivity(market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!ok) start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q&c=apps")))
    }

    fun shareApp(context: Context, packageName: String, label: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, label)
            .putExtra(Intent.EXTRA_TEXT, "$label\nhttps://play.google.com/store/apps/details?id=$packageName")
        start(context, Intent.createChooser(send, context.getString(R.string.menu_share_app)))
    }
}

/** 懐中電灯 (トーチ) の制御。 */
class Flashlight(context: Context) {
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val cameraId: String? = runCatching {
        cameraManager.cameraIdList.firstOrNull { id ->
            val c = cameraManager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    val available: Boolean get() = cameraId != null
    var isOn: Boolean = false
        private set
    private var listener: ((Boolean) -> Unit)? = null

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) {
                isOn = enabled
                listener?.invoke(enabled)
            }
        }
    }

    fun observe(onChange: (Boolean) -> Unit) {
        listener = onChange
        runCatching { cameraManager.registerTorchCallback(callback, android.os.Handler(android.os.Looper.getMainLooper())) }
    }

    fun stop() {
        listener = null
        runCatching { cameraManager.unregisterTorchCallback(callback) }
    }

    fun toggle() {
        val id = cameraId ?: return
        runCatching { cameraManager.setTorchMode(id, !isOn) }
    }
}

/** おやすみモード (通知のポリシーアクセスが必要)。 */
object DoNotDisturb {
    fun hasAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun isOn(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    fun toggle(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            SystemActions.openDndAccess(context)
            return false
        }
        val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return true
    }
}

/** 画面の明るさ・自動回転 (WRITE_SETTINGS が必要)。 */
object SystemSettingsAccess {
    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    fun brightness(context: Context): Float =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f

    fun setBrightness(context: Context, value: Float) {
        if (!canWrite(context)) return
        runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (value * 255).toInt().coerceIn(1, 255))
        }
    }

    fun isRotationLocked(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0

    fun toggleRotationLock(context: Context): Boolean {
        if (!canWrite(context)) {
            SystemActions.openWriteSettings(context)
            return false
        }
        val locked = isRotationLocked(context)
        runCatching { Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 1 else 0) }
        return true
    }
}

/** iOS 18 の「Face ID を必要にする」相当 (生体認証 / 画面ロック)。 */
object AppLock {
    fun authenticate(activity: Activity, title: String, onSuccess: () -> Unit) {
        val km = activity.getSystemService(KeyguardManager::class.java)
        if (!km.isDeviceSecure) {
            Toast.makeText(activity, R.string.toast_no_screen_lock, Toast.LENGTH_LONG).show()
            onSuccess()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            confirmDeviceCredential(activity, title, onSuccess)
            return
        }
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(activity.getString(R.string.auth_subtitle))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        runCatching {
            builder.build().authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
                },
            )
        }.onFailure {
            Toast.makeText(activity, R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Android 9 以前: 端末の画面ロック確認画面 (PIN / パターン / パスワード、端末によっては指紋も) で認証する。
     * BiometricPrompt で画面ロックを使えるのは Android 10 以降のため。
     */
    @Suppress("DEPRECATION")
    private fun confirmDeviceCredential(activity: Activity, title: String, onSuccess: () -> Unit) {
        val km = activity.getSystemService(KeyguardManager::class.java)
        val intent = km.createConfirmDeviceCredentialIntent(title, activity.getString(R.string.auth_subtitle))
        val registry = (activity as? ComponentActivity)?.activityResultRegistry
        if (intent == null || registry == null) {
            onSuccess()
            return
        }
        lateinit var launcher: ActivityResultLauncher<Intent>
        launcher = registry.register("app_lock_" + System.nanoTime(), ActivityResultContracts.StartActivityForResult()) { result ->
            launcher.unregister()
            if (result.resultCode == Activity.RESULT_OK) onSuccess()
        }
        runCatching { launcher.launch(intent) }.onFailure {
            launcher.unregister()
            Toast.makeText(activity, R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
    }
}
