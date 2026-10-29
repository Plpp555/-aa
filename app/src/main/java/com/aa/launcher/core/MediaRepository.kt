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

    private var controller: MediaController? = null
    private var registered = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers -> bind(controllers) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            bind(runCatching { sessionManager.getActiveSessions(listenerComponent) }.getOrNull())
        }
    }

    fun onListenerConnected() {
        runCatching {
            if (!registered) {
                sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
                registered = true
            }
            bind(sessionManager.getActiveSessions(listenerComponent))
        }
    }

    /** コントロールセンターを開いたときに最新状態を取り直す。 */
    fun refresh() {
        runCatching { bind(sessionManager.getActiveSessions(listenerComponent)) }
    }

    private fun bind(controllers: List<MediaController>?) {
        val next = controllers?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers?.firstOrNull()
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = next
            next?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        if (c == null || meta == null) {
            _nowPlaying.value = null
            return
        }
        val art: Bitmap? = meta.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        _nowPlaying.value = NowPlaying(
            title = meta.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            art = art?.asImageBitmap(),
            isPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            packageName = c.packageName,
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L),
        )
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
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
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
