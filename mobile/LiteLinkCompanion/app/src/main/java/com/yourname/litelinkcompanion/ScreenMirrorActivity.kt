package com.yourname.litelinkcompanion

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/** Screen Mirror (companion): start/stop the PC mirror + open the touchpad. */
class ScreenMirrorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_mirror)

        WebSocketManager.init(this)

        findViewById<MaterialToolbar>(R.id.mirrorToolbar)
            .setNavigationOnClickListener { finish() }

        findViewById<View>(R.id.mirrorStartButton).setOnClickListener {
            if (!WebSocketManager.isConnected) {
                toast("Not connected to the PC")
                return@setOnClickListener
            }
            WebSocketManager.sendPc("mirror.start")
            findViewById<TextView>(R.id.mirrorStatus).text = "Starting mirror on the PC…"
        }

        findViewById<View>(R.id.mirrorStopButton).setOnClickListener {
            if (WebSocketManager.isConnected) WebSocketManager.sendPc("mirror.stop")
            findViewById<TextView>(R.id.mirrorStatus).text = "Mirror stopped"
        }

        findViewById<View>(R.id.mirrorTouchpadButton).setOnClickListener {
            startActivity(Intent(this, RemoteControlActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.mirrorStatus).text = if (WebSocketManager.isConnected)
            "Connected to ${WebSocketManager.currentHost ?: "PC"}"
        else
            "Not connected"
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
