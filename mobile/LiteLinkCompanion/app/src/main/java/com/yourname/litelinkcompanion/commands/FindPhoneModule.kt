package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

/** Rings the phone loudly even on silent/vibrate for a fixed duration. */
object FindPhoneModule {

    private const val RING_MS = 30000L
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null
    private var lastVolume = -1
    private var lastStreamVolume = -1

    fun register(app: Context) {
        CommandCenter.register("phone.ring") { ctx, id, params ->
            val durationMs = params.optLong("duration_ms", RING_MS).coerceIn(3000, 60000)
            startRing(app, durationMs)
            ctx.reply(id, JSONObject().put("status", "ringing").put("duration_ms", durationMs))
        }
        CommandCenter.register("phone.stop_ring") { ctx, id, _ ->
            stopRing()
            ctx.reply(id, JSONObject().put("status", "stopped"))
        }
    }

    fun startRing(app: Context, durationMs: Long = RING_MS) {
        stopRing()
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        lastVolume = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        lastStreamVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, lastVolume, 0)

        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            val p = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_ALARM)
                setDataSource(app, uri)
                isLooping = true
                setOnErrorListener { _, _, _ -> false }
                prepare()
                start()
            }
            player = p
        } catch (e: Exception) {
            player = null
        }
        stopRunnable = Runnable { stopRing() }
        handler.postDelayed(stopRunnable!!, durationMs)
    }

    fun stopRing() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
        }
        player = null
    }
}
