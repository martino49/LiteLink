package com.yourname.litelinkcompanion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "litelink"
        private const val KEY_HOST = "host"
        private const val KEY_ENABLED = "enabled"
        private const val REQ_POST_NOTIFICATIONS = 100
    }

    private lateinit var ipInput: EditText
    private lateinit var toggle: Switch
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ipInput = findViewById(R.id.ipInput)
        toggle = findViewById(R.id.toggleSwitch)
        statusText = findViewById(R.id.statusText)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        ipInput.setText(prefs.getString(KEY_HOST, "").orEmpty())
        toggle.isChecked = prefs.getBoolean(KEY_ENABLED, false)

        toggle.setOnCheckedChangeListener { _, isChecked ->
            val host = ipInput.text.toString().trim()
            if (isChecked) {
                if (host.isEmpty()) {
                    Toast.makeText(this, "Enter your laptop IP address first", Toast.LENGTH_SHORT).show()
                    toggle.isChecked = false
                    return@setOnCheckedChangeListener
                }
                prefs.edit()
                    .putString(KEY_HOST, host)
                    .putBoolean(KEY_ENABLED, true)
                    .apply()
                statusText.text = "Connecting to $host..."
                ConnectionService.start(this, host)
            } else {
                prefs.edit().putBoolean(KEY_ENABLED, false).apply()
                ConnectionService.stop(this)
                statusText.text = "Disconnected"
            }
        }

        WebSocketManager.addConnectionListener(connectionListener)

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        val enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
        statusText.text = when {
            !enabled -> "Disconnected"
            WebSocketManager.isConnected -> "Connected"
            else -> "Disconnected"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        WebSocketManager.removeConnectionListener(connectionListener)
    }

    private val connectionListener: (Boolean) -> Unit = { connected ->
        runOnUiThread {
            statusText.text = if (connected) "Connected" else "Disconnected"
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_POST_NOTIFICATIONS
                )
            }
        }
    }
}
