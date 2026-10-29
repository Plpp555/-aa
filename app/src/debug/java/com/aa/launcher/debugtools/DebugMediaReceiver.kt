package com.aa.launcher.debugtools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock

/**
 * スモークテスト用 (debug ビルドのみ): 擬似の「再生中のメディア」を作る。エミュレータには音楽アプリがないため。
 * `am broadcast -n <パッケージ>/com.aa.launcher.debugtools.DebugMediaReceiver --es cmd <コマンド>`
 * - prepare: 一時停止中のセッションを 2 つ作る (テスト用の曲と、後から作る「別のアプリ」。別のアプリが一覧の先頭になる)
 * - play: テスト用の曲を再生にする (prepare 済みならセッションの一覧は変わらず、再生状態だけが変わる。実機で表示されなかった状況)
 * - stop: すべて片付ける
 */
class DebugMediaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra("cmd")) {
            "prepare" -> FakeMediaSession.prepare(context.applicationContext)
            "play" -> FakeMediaSession.play(context.applicationContext)
            "stop" -> FakeMediaSession.stop()
        }
    }
}

private object FakeMediaSession {
    private var session: MediaSession? = null
    private var other: MediaSession? = null
    private var position = 42_000L
    private var startedAt = 0L
    private var playing = false

    fun prepare(context: Context) {
        start(context, play = false)
        // 一時停止中の別のアプリ (後から有効にしたので一覧の先頭になる)
        val o = other ?: MediaSession(context, "LiquidSmokeOther").also { other = it }
        o.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Other Paused App").build())
        o.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0L, 0f).build())
        o.isActive = true
    }

    fun play(context: Context) {
        val s = session
        if (s == null) start(context, play = true) else setPlaying(s, true)
    }

    private fun start(context: Context, play: Boolean) {
        val s = session ?: MediaSession(context, "LiquidSmokeTest").also { session = it }
        @Suppress("DEPRECATION")
        s.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        s.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = setPlaying(s, true)
            override fun onPause() = setPlaying(s, false)
        })
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Liquid Test Song")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Smoke Test")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 215_000L)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, artwork())
                .build(),
        )
        setPlaying(s, play)
        s.isActive = true
    }

    fun stop() {
        listOfNotNull(session, other).forEach {
            it.isActive = false
            it.release()
        }
        session = null
        other = null
        playing = false
    }

    private fun setPlaying(s: MediaSession, play: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (playing) position += now - startedAt
        playing = play
        startedAt = now
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(if (play) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, position, if (play) 1f else 0f, now)
                .build(),
        )
    }

    private fun artwork(): Bitmap {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 256f, 256f, 0xFFFF2D55.toInt(), 0xFF5E5CE6.toInt(), Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, 256f, 256f, paint)
        return bitmap
    }
}
