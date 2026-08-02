package com.yourname.litelinkcompanion

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/** Media Controller (companion): control PC playback via media keys. */
class MediaControllerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_controller)

        WebSocketManager.init(this)

        findViewById<MaterialToolbar>(R.id.mediaToolbar)
            .setNavigationOnClickListener { finish() }

        findViewById<ImageButton>(R.id.mediaPlayPause).setOnClickListener {
            send("media.play_pause"); flash("Play/Pause")
        }
        findViewById<ImageButton>(R.id.mediaNext).setOnClickListener {
            send("media.next"); flash("Next")
        }
        findViewById<ImageButton>(R.id.mediaPrev).setOnClickListener {
            send("media.prev"); flash("Previous")
        }
        findViewById<View>(R.id.mediaVolUp).setOnClickListener {
            send("media.volume_up"); flash("Volume +")
        }
        findViewById<View>(R.id.mediaVolDown).setOnClickListener {
            send("media.volume_down"); flash("Volume −")
        }
        findViewById<View>(R.id.mediaMute).setOnClickListener {
            send("media.mute"); flash("Mute")
        }

        if (!WebSocketManager.isConnected) {
            findViewById<TextView>(R.id.mediaStatus).text = "Not connected to the desktop"
        }
    }

    private fun send(cmd: String) {
        if (!WebSocketManager.isConnected) {
            toast("Not connected")
            return
        }
        WebSocketManager.sendPc(cmd)
    }

    private fun flash(msg: String) {
        findViewById<TextView>(R.id.mediaStatus).text = "Sent: $msg"
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
