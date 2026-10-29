package com.aa.launcher.core

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aa.launcher.service.BadgeNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val isPlaying: Boolean,
    val packageName: String,
    /** 曲の長さ (ミリ秒)。不明なら 0。 */
    val durationMs: Long = 0L,
)

/** コントロールセンターの「再生中」タイルと Dynamic Island 用。通知アクセスが必要。 */
class MediaRepository(private val context: Context) {

    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, BadgeNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** 表示・操作の対象 (再生中のセッション。なければ直前に選んでいたもの)。 */
    private var controller: MediaController? = null
    /** 状態の変化を受け取っている全セッション。 */
    private var controllers: List<MediaController> = emptyList()
    private var registered = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> bind(list) }

    // すべてのセッションに同じコールバックを付け、どれかの状態が変わったら対象を選び直す。
    // 一時停止していたアプリが再生を始めてもセッションの一覧は変わらない (OnActiveSessionsChangedListener は呼ばれない) ため、
    // 1 つのセッションだけを見ていると、別のアプリの再生に気付けなかった
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = select()
        override fun onMetadataChanged(metadata: MediaMetadata?) = select()
        override fun onSessionDestroyed() = refresh()
    }

    /**
     * 再生中のメディアの監視を始める。通知アクセスが許可されていれば、通知リスナーの接続を待たずに始める
     * (アプリの更新後などにリスナーが再接続されない端末があるため)。アプリの起動時・ホームの表示時・リスナーの接続時に呼ぶ。
     */
    fun start() {
        if (registered) {
            refresh()
            return
        }
        if (!SystemActions.isNotificationAccessGranted(context)) return
        runCatching {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            registered = true
            bind(sessionManager.getActiveSessions(listenerComponent))
        }
    }

    fun onListenerConnected() = start()

    /** コントロールセンターを開いたときなどに最新状態を取り直す。 */
    fun refresh() {
        runCatching { bind(sessionManager.getActiveSessions(listenerComponent)) }
    }

    private fun bind(list: List<MediaController>?) {
        val next = list.orEmpty()
        val old = controllers
        old.filter { o -> next.none { it.sessionToken == o.sessionToken } }.forEach { runCatching { it.unregisterCallback(callback) } }
        // 同じセッションは前の MediaController を使い続ける (コールバックを付け直さない)
        controllers = next.map { n -> old.firstOrNull { it.sessionToken == n.sessionToken } ?: n.also { runCatching { it.registerCallback(callback, handler) } } }
        select()
    }

    private fun select() {
        val list = controllers
        controller = list.firstOrNull { it.playbackState.isPlaying() }
            ?: controller?.let { c -> list.firstOrNull { it.sessionToken == c.sessionToken } }
            ?: list.firstOrNull()
        publish()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val playing = c?.playbackState.isPlaying()
        // 曲の情報がなくても再生中なら出す (曲名はアプリの名前)
        if (c == null || (meta == null && !playing)) {
            _nowPlaying.value = null
            return
        }
        val art: Bitmap? = meta?.let {
            it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotEmpty() }
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotEmpty() }
            ?: appLabel(c.packageName)
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotEmpty() }
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotEmpty() }
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        _nowPlaying.value = NowPlaying(
            title = title,
            artist = artist,
            art = art?.asImageBitmap(),
            isPlaying = playing,
            packageName = c.packageName,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
        )
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault("")

    /** 再生中とみなす状態 (読み込み中・早送り/巻き戻し中も含む)。 */
    private fun PlaybackState?.isPlaying(): Boolean = when (this?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        -> true
        else -> false
    }

    /** 今の再生位置 (ミリ秒)。再生中は最後に位置が更新されてからの経過時間を足す。 */
    fun positionMs(): Long {
        val state = controller?.playbackState ?: return 0L
        var position = state.position
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0L) {
            position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        return position.coerceAtLeast(0L)
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState.isPlaying()) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    fun openPlayer() {
        val pkg = controller?.packageName ?: return
        val intent = controller?.sessionActivity
        if (intent != null) {
            runCatching { intent.send() }
        } else {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}
