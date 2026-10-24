package com.aa.launcher.core

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.MotionEvent
import android.view.View

/**
 * 操作中・アニメーション中だけ端末の最高リフレッシュレートを要求し、静止したらシステム既定に戻す。
 *
 * - 解像度が同じ表示モードのうちリフレッシュレートが最大のものを選ぶ (60/90/120/144Hz など端末依存。固定上限なし)
 * - タッチ開始で即座に引き上げ、指を離してから [releaseDelayMs] 後 (フリングや遷移アニメーションが終わる頃) に解除
 * - 編集モードの揺れのような継続アニメーションの間は [hold] で引き上げたままにする
 * - Android 15 以降はビュー単位のフレームレート要求 (REQUESTED_FRAME_RATE_CATEGORY_HIGH) も併用する
 *
 * 静止中は既定 (可変リフレッシュレート対応端末では低いレート) に戻るため、電池消費は増えない。
 */
class FrameRateController(private val activity: Activity, private val releaseDelayMs: Long = 1500L) {

    private val handler = Handler(Looper.getMainLooper())
    private var boosted = false
    private var touching = false
    private var holds = 0
    private val release = Runnable { if (!touching && holds == 0) apply(false) }

    private val highMode: Display.Mode? by lazy {
        runCatching {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay
            } ?: return@runCatching null
            val current = display.mode
            display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?.takeIf { it.refreshRate > current.refreshRate + 0.5f || display.supportedModes.size > 1 }
        }.getOrNull()
    }

    /** Activity.dispatchTouchEvent から呼ぶ。 */
    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                boostNow()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                scheduleRelease()
            }
        }
    }

    /** プログラムからのアニメーション (ページ移動など) の前に呼ぶ。 */
    fun pulse() {
        boostNow()
        scheduleRelease()
    }

    /** 継続アニメーションの間 true にする (編集モードの揺れなど)。 */
    fun hold(on: Boolean) {
        holds = if (on) holds + 1 else (holds - 1).coerceAtLeast(0)
        if (holds > 0) boostNow() else scheduleRelease()
    }

    /** 画面が見えなくなったら即座に解除する。 */
    fun reset() {
        touching = false
        holds = 0
        handler.removeCallbacks(release)
        apply(false)
    }

    private fun boostNow() {
        handler.removeCallbacks(release)
        apply(true)
    }

    private fun scheduleRelease() {
        handler.removeCallbacks(release)
        handler.postDelayed(release, releaseDelayMs)
    }

    private fun apply(on: Boolean) {
        if (on == boosted) return
        boosted = on
        val mode = highMode
        runCatching {
            val window = activity.window
            val lp = window.attributes
            lp.preferredDisplayModeId = if (on && mode != null) mode.modeId else 0
            lp.preferredRefreshRate = if (on && mode != null) mode.refreshRate else 0f
            window.attributes = lp
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            runCatching {
                val content = activity.window.decorView.findViewById<View>(android.R.id.content)
                content?.requestedFrameRate = if (on) View.REQUESTED_FRAME_RATE_CATEGORY_HIGH else View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            }
        }
    }
}
