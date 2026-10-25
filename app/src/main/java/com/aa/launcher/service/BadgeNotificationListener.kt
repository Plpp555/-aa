package com.aa.launcher.service

import android.app.Notification
import android.os.Build
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aa.launcher.LauncherApp
import com.aa.launcher.core.ComponentKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationState {
    private val _badges = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** キー: [ComponentKey.packageKey] */
    val badges: StateFlow<Map<String, Int>> = _badges.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    internal fun publish(map: Map<String, Int>) {
        _badges.value = map
    }

    internal fun setConnected(value: Boolean) {
        _connected.value = value
        if (!value) _badges.value = emptyMap()
    }
}

/** 通知数をアプリアイコンのバッジとして表示するための通知リスナー。メディア操作にも使う。 */
class BadgeNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationState.setConnected(true)
        refresh()
        runCatching { LauncherApp.instance.mediaRepository.onListenerConnected() }
    }

    override fun onListenerDisconnected() {
        NotificationState.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        val um = getSystemService(UserManager::class.java)
        val ranking = Ranking()
        val map = HashMap<String, Int>()
        for (sbn in list) {
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            if (sbn.isOngoing) continue
            // 「バッジを表示しない」設定 (通知チャネル) は Android 8.0 以降
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val rankingMap = currentRanking
                if (rankingMap != null && rankingMap.getRanking(sbn.key, ranking) && !ranking.canShowBadge()) continue
            }
            val serial = um.getSerialNumberForUser(sbn.user)
            val key = ComponentKey.packageKey(sbn.packageName, serial)
            val count = if (n.number > 0) n.number else 1
            map[key] = (map[key] ?: 0) + count
        }
        NotificationState.publish(map)
    }
}
