package com.yourname.litelinkcompanion

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.appbar.MaterialToolbar

/** PC Power (companion): shutdown/restart/sleep/lock the desktop. */
class PcPowerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pc_power)

        WebSocketManager.init(this)

        findViewById<MaterialToolbar>(R.id.powerToolbar)
            .setNavigationOnClickListener { finish() }

        findViewById<View>(R.id.powerShutdown).setOnClickListener {
            confirm("Shutdown", "Turn off the PC?") { send("power.shutdown") }
        }
        findViewById<View>(R.id.powerRestart).setOnClickListener {
            confirm("Restart", "Restart the PC?") { send("power.restart") }
        }
        findViewById<View>(R.id.powerSleep).setOnClickListener {
            send("power.sleep")
        }
        findViewById<View>(R.id.powerLock).setOnClickListener {
            send("power.lock")
        }

        if (!WebSocketManager.isConnected) {
            findViewById<TextView>(R.id.powerStatus).text = "Not connected to the desktop"
        }
    }

    private fun confirm(title: String, msg: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(title) { _, _ -> action() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun send(cmd: String) {
        if (!WebSocketManager.isConnected) {
            toast("Not connected")
            return
        }
        WebSocketManager.sendPc(cmd)
        findViewById<TextView>(R.id.powerStatus).text = "Sent: $cmd"
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
