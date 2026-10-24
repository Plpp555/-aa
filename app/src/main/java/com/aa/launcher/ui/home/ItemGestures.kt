package com.aa.launcher.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

private const val RESULT_CANCEL = 0
private const val RESULT_TAP = 1
private const val RESULT_DRAG = 2
private const val RESULT_LONG = 3

/**
 * ホーム画面アイテムのジェスチャー (iOS と同じ挙動):
 * - タップ: 起動 / フォルダを開く
 * - 長押し: コンテキストメニュー (触覚タッチ)。そのまま指を動かすと編集モードでドラッグ開始
 * - 編集モード中: 指を動かすと即ドラッグ
 * ドラッグ開始後のポインタ追跡はルートのハンドラが引き継ぐ。
 */
fun Modifier.launcherItemGestures(
    key: Any?,
    editMode: Boolean,
    enabled: Boolean = true,
    onDown: (Long) -> Unit = {},
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit,
): Modifier = if (!enabled) this else pointerInput(key, editMode) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        onDown(down.uptimeMillis)
        val slop = viewConfiguration.touchSlop
        val timeout = if (editMode) 220L else viewConfiguration.longPressTimeoutMillis.coerceAtMost(420L)
        var last = down.position
        val result = withTimeoutOrNull(timeout) {
            var r = RESULT_CANCEL
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                last = change.position
                if (change.changedToUp()) {
                    r = RESULT_TAP
                    change.consume()
                    break
                }
                if (change.isConsumed) break
                if ((change.position - down.position).getDistance() > slop) {
                    if (editMode) {
                        change.consume()
                        r = RESULT_DRAG
                    }
                    break
                }
            }
            r
        } ?: RESULT_LONG

        when (result) {
            RESULT_TAP -> onTap()
            RESULT_DRAG -> onDragStart(last)
            RESULT_LONG -> {
                if (editMode) {
                    onDragStart(last)
                } else {
                    onLongPress()
                    // メニュー表示後も指を追跡し、動かしたらドラッグへ移行。
                    // Initial パスで消費して、子要素 (ウィジェット内のボタン等) がクリックしないようにする。
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            break
                        }
                        if ((change.position - down.position).getDistance() > slop * 1.5f) {
                            change.consume()
                            onDragStart(change.position)
                            break
                        }
                        change.consume()
                    }
                }
            }
        }
    }
}
