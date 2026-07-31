package com.yourname.litelinkcompanion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "litelink"
        private const val KEY_HOST = "host"
        private const val KEY_CODE = "pairing_code"
        private const val KEY_ENABLED = "enabled"
        private const val CODE_LENGTH = 6
        private const val DESKTOP_PORT = 8765
        private const val REQ_PERMISSIONS = 100
    }

    private lateinit var ipInput: EditText
    private lateinit var codeInput: EditText
    private lateinit var toggle: SwitchMaterial
    private lateinit var statusText: TextView
    private var statusDot: View? = null
    private lateinit var connectButton: Button
    private lateinit var scanButton: Button
    private lateinit var discoverButton: Button

    private var discoveryHelper: DiscoveryHelper? = null
    private var scanner: SubnetScanner? = null
    private var foundViaScan = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WebSocketManager.init(this)

        ipInput = findViewById(R.id.ipInput)
        codeInput = findViewById(R.id.codeInput)
        toggle = findViewById(R.id.toggleSwitch)
        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        connectButton = findViewById(R.id.connectButton)
        scanButton = findViewById(R.id.scanButton)
        discoverButton = findViewById(R.id.discoverButton)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        ipInput.setText(prefs.getString(KEY_HOST, "").orEmpty())
        codeInput.setText(prefs.getString(KEY_CODE, "").orEmpty())
        toggle.isChecked = prefs.getBoolean(KEY_ENABLED, false)
        setStatus("Not connected", false)

        connectButton.setOnClickListener { connectNow() }
        scanButton.setOnClickListener { runScan() }
        discoverButton.setOnClickListener { discoverServer() }
        findViewById<Button>(R.id.remoteButton).setOnClickListener {
            startActivity(Intent(this, RemoteControlActivity::class.java))
        }
        findViewById<Button>(R.id.phoneHubButton).setOnClickListener {
            startActivity(Intent(this, PhoneActivity::class.java))
        }

        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                connectNow()
            } else {
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, false).apply()
                ConnectionService.stop(this)
                setStatus("Not connected", false)
            }
        }

        WebSocketManager.addConnectionListener(connectionListener)

        requestPermissions()
        startAutoConnect()
    }

    override fun onResume() {
        super.onResume()
        val enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
        if (enabled && !WebSocketManager.isConnected) {
            startAutoConnect()
        } else if (!enabled) {
            setStatus("Not connected", false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        discoveryHelper?.stop()
        scanner?.stop()
        WebSocketManager.removeConnectionListener(connectionListener)
    }

    private val connectionListener: (Boolean) -> Unit = { connected ->
        runOnUiThread {
            if (connected) {
                setStatus("Connected to ${WebSocketManager.currentHost ?: "desktop"}", true)
            } else {
                setStatus("Disconnected", false)
            }
        }
    }

    private fun setStatus(text: String, ok: Boolean?) {
        statusText.text = text
        val color = when (ok) {
            true -> ContextCompat.getColor(this, R.color.success)
            false -> ContextCompat.getColor(this, R.color.danger)
            null -> ContextCompat.getColor(this, R.color.muted)
        }
        statusDot?.backgroundTintList = ColorStateList.valueOf(color)
    }

    /** Manual connect: validate the 6-digit pairing code, save, and start. */
    private fun connectNow() {
        val host = ipInput.text.toString().trim()
        val code = codeInput.text.toString().trim()
        if (host.isEmpty()) {
            toast("Enter the laptop IP (or tap Scan network)")
            return
        }
        if (code.length != CODE_LENGTH) {
            toast("Pairing code must be $CODE_LENGTH digits")
            return
        }
        saveAndStart(host, code)
    }

    private fun saveAndStart(host: String, code: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_HOST, host)
            .putString(KEY_CODE, code)
            .putBoolean(KEY_ENABLED, true)
            .apply()
        if (!toggle.isChecked) toggle.isChecked = true
        foundViaScan = false
        setStatus("Connecting to $host...", null)
        ConnectionService.start(this, host, code)
    }

    /** Auto-connect on launch to the saved host, with a subnet scan as backup. */
    private fun startAutoConnect() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val host = prefs.getString(KEY_HOST, "").orEmpty()
        val code = prefs.getString(KEY_CODE, "").orEmpty()
        if (host.isBlank() || code.isBlank()) return
        if (WebSocketManager.isConnected) {
            setStatus("Connected", true)
            return
        }
        setStatus("Connecting to $host...", null)
        ConnectionService.start(this, host, code)
        // If still not connected shortly after, scan the subnet to find the laptop.
        statusDot?.postDelayed({
            if (!WebSocketManager.isConnected && scanner == null) runScan()
        }, 5000)
    }

    /** Probes the whole /24 subnet for the desktop and auto-connects to the first hit. */
    private fun runScan() {
        setStatus("Scanning network for the laptop...", null)
        scanner?.stop()
        foundViaScan = false
        scanner = SubnetScanner(
            this,
            port = DESKTOP_PORT,
            onFound = { host ->
                runOnUiThread {
                    if (!WebSocketManager.isConnected && !foundViaScan) {
                        foundViaScan = true
                        ipInput.setText(host)
                        saveAndStart(host, codeInput.text.toString().trim().ifBlank { savedCode() })
                    }
                }
            },
            onDone = {
                runOnUiThread {
                    if (!WebSocketManager.isConnected && !foundViaScan) {
                        setStatus("Not found — check Wi-Fi and desktop", false)
                    }
                    scanner = null
                }
            }
        ).also { it.start() }
    }

    private fun savedCode(): String =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_CODE, "").orEmpty()

    private fun discoverServer() {
        discoveryHelper?.stop()
        setStatus("Searching for desktop (mDNS)...", null)
        discoveryHelper = DiscoveryHelper(this) { host ->
            runOnUiThread {
                ipInput.setText(host)
                setStatus("Found desktop at $host", true)
            }
        }.also { it.start() }
        toast("Searching for LiteLink desktop on this network...")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    /** Request all runtime permissions the 24-feature command set needs. */
    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        for (perm in listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed.add(perm)
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMISSIONS)
        }

        // Notification listener + All-files access are special permissions opened via Settings.
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean("asked_notif_access", false)) {
            prefs.edit().putBoolean("asked_notif_access", true).apply()
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            if (!prefs.getBoolean("asked_manage_storage", false)) {
                prefs.edit().putBoolean("asked_manage_storage", true).apply()
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
    }
}
