package com.aa.launcher.core

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/** ウィジェットの追加先。 */
sealed interface WidgetTarget {
    data object Today : WidgetTarget
    data class HomePageTarget(val pageId: String?) : WidgetTarget
}

/** Android のホーム画面ウィジェット (AppWidget) をホストする。 */
class WidgetHostManager(private val activity: ComponentActivity) {

    private data class Pending(
        val id: Int,
        val provider: AppWidgetProviderInfo,
        val onResult: (Int?) -> Unit,
    )

    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    val host = object : AppWidgetHost(activity.applicationContext, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            RoundedWidgetHostView(context)
    }

    private var pending: Pending? = null

    private val bindLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) configureOrFinish() else finish(false)
        }

    fun startListening() {
        runCatching { host.startListening() }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    fun providers(): List<AppWidgetProviderInfo> {
        val profiles = runCatching { activity.userProfiles() }.getOrDefault(emptyList())
        return profiles.flatMap { runCatching { manager.getInstalledProvidersForProfile(it) }.getOrDefault(emptyList()) }
    }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    fun createView(context: Context, appWidgetId: Int): AppWidgetHostView? {
        val info = info(appWidgetId) ?: return null
        return runCatching { host.createView(context, appWidgetId, info) }.getOrNull()
    }

    /** ウィジェットの追加を開始する。完了時に onResult(appWidgetId) (キャンセル時は null)。 */
    fun beginAdd(provider: AppWidgetProviderInfo, onResult: (Int?) -> Unit) {
        val id = host.allocateAppWidgetId()
        pending = Pending(id, provider, onResult)
        val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, null) }.getOrDefault(false)
        if (bound) {
            configureOrFinish()
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
            runCatching { bindLauncher.launch(intent) }.onFailure { finish(false) }
        }
    }

    private fun configureOrFinish() {
        val p = pending ?: return
        val needsConfig = p.provider.configure != null &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || p.provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL == 0)
        if (needsConfig) {
            runCatching { host.startAppWidgetConfigureActivityForResult(activity, p.id, 0, REQUEST_CONFIGURE, null) }
                .onFailure { finish(true) }
        } else {
            finish(true)
        }
    }

    /** Activity.onActivityResult から呼ぶ。 */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_CONFIGURE) return false
        finish(resultCode == Activity.RESULT_OK)
        return true
    }

    /** 既存ウィジェットの再設定 (iOS の「ウィジェットを編集」)。 */
    fun reconfigure(appWidgetId: Int) {
        val info = info(appWidgetId) ?: return
        if (info.configure == null) return
        runCatching { host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, REQUEST_RECONFIGURE, null) }
    }

    private fun finish(success: Boolean) {
        val p = pending ?: return
        pending = null
        if (success) {
            p.onResult(p.id)
        } else {
            runCatching { host.deleteAppWidgetId(p.id) }
            p.onResult(null)
        }
    }

    fun delete(appWidgetId: Int) {
        if (appWidgetId >= 0) runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    companion object {
        const val HOST_ID = 2027
        private const val REQUEST_CONFIGURE = 4101
        private const val REQUEST_RECONFIGURE = 4102

        fun updateSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
            runCatching {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
            }
        }
    }
}

/** 角丸でクリップし、長押しを検出できるウィジェットビュー。 */
class RoundedWidgetHostView(context: Context) : AppWidgetHostView(context) {

    /** 長押し時に呼ばれる (コンテキストメニュー表示用)。 */
    var onLongPress: (() -> Unit)? = null

    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false

    private val longPressRunnable = Runnable {
        longPressed = true
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        val now = android.os.SystemClock.uptimeMillis()
        val cancel = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_CANCEL, downX, downY, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        onLongPress?.invoke()
    }

    init {
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                val r = 22f * view.resources.displayMetrics.density
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                longPressed = false
                downX = ev.x
                downY = ev.y
                postDelayed(longPressRunnable, android.view.ViewConfiguration.getLongPressTimeout().toLong())
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.hypot(ev.x - downX, ev.y - downY) > touchSlop) removeCallbacks(longPressRunnable)
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> removeCallbacks(longPressRunnable)
        }
        if (longPressed) {
            if (ev.actionMasked == android.view.MotionEvent.ACTION_UP || ev.actionMasked == android.view.MotionEvent.ACTION_CANCEL) longPressed = false
            return true
        }
        val handled = super.dispatchTouchEvent(ev)
        return handled || ev.actionMasked == android.view.MotionEvent.ACTION_DOWN
    }
}
