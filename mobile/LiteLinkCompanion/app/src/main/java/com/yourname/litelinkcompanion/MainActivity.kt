package com.yourname.litelinkcompanion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Connect gate (Screen 3): discovery-first. The phone finds PCs on the network
 * (UDP beacon + mDNS + subnet scan), the user taps one, enters the 6-digit
 * code, and is taken to the Dashboard on connect.
 */
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

    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var discoveredList: RecyclerView
    private lateinit var discoveredEmpty: TextView
    private lateinit var recentsContainer: LinearLayout
    private lateinit var discoveredAdapter: ConnectDeviceAdapter

    private val found = ConcurrentHashMap<String, Map<String, Any>>()
    private var broadcastListener: BroadcastListener? = null
    private var discoveryHelper: DiscoveryHelper? = null
    private var scanner: SubnetScanner? = null
    private var navigatedToHub = false

    private val prefs get() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WebSocketManager.init(this)

        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        discoveredList = findViewById(R.id.discoveredList)
        discoveredEmpty = findViewById(R.id.discoveredEmpty)
        recentsContainer = findViewById(R.id.recentsContainer)

        discoveredList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        discoveredAdapter = ConnectDeviceAdapter { info -> promptCode(info) }
        discoveredList.adapter = discoveredAdapter

        findViewById<View>(R.id.scanButton).setOnClickListener { startDiscovery() }
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.featuresFab)
            .setOnClickListener { showFeaturesSheet() }
        findViewById<View>(R.id.pullHandle).setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_UP) {
                exploreHub()
                true
            } else false
        }

        WebSocketManager.addConnectionListener(connectionListener)

        requestPermissions()
        // Gate: if we're already connected, go straight to the Dashboard.
        if (WebSocketManager.isConnected) {
            goToHub()
        } else {
            startDiscovery()
            renderRecents()
        }
    }

    override fun onResume() {
        super.onResume()
        if (WebSocketManager.isConnected) {
            goToHub()
        } else if (!startedDiscovery) {
            startDiscovery()
            renderRecents()
        }
    }

    private var startedDiscovery = false

    override fun onDestroy() {
        super.onDestroy()
        broadcastListener?.stop()
        discoveryHelper?.stop()
        scanner?.stop()
        WebSocketManager.removeConnectionListener(connectionListener)
    }

    private val connectionListener: (Boolean) -> Unit = { connected ->
        runOnUiThread {
            if (connected) {
                setStatus("Connected to ${WebSocketManager.currentHost ?: "desktop"}", true)
                goToHub()
            } else {
                setStatus("Searching for your PC on this network…", null)
            }
        }
    }

    private fun setStatus(text: String, ok: Boolean?) {
        statusText.text = text
        statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, when (ok) {
                true -> R.color.success
                false -> R.color.danger
                null -> R.color.muted
            })
        )
    }

    /** Auto-navigate to the Dashboard once connected. */
    private fun goToHub() {
        if (navigatedToHub) return
        navigatedToHub = true
        startActivity(Intent(this, PhoneActivity::class.java))
        finish()
    }

    /** Pull-tab: dismiss the gate and explore the app without connecting. */
    private fun exploreHub() {
        if (navigatedToHub) return
        navigatedToHub = true
        startActivity(Intent(this, PhoneActivity::class.java))
        finish()
    }

    // ------------------------------------------------------------ discovery
    private fun startDiscovery() {
        startedDiscovery = true
        found.clear()
        setStatus("Searching for your PC on this network…", null)
        renderDiscovered()

        broadcastListener = BroadcastListener(this) { info -> addFound(info) }
        broadcastListener?.start()

        discoveryHelper = DiscoveryHelper(this) { host ->
            addFound(mapOf("name" to "LiteLink PC", "ip" to host, "port" to DESKTOP_PORT, "code" to "", "id" to ""))
        }
        discoveryHelper?.start()

        scanner = SubnetScanner(this, onFound = { host ->
            addFound(mapOf("name" to "LiteLink PC", "ip" to host, "port" to DESKTOP_PORT, "code" to "", "id" to ""))
        })
        scanner?.start()
    }

    private fun addFound(info: Map<String, Any>) {
        val ip = (info["ip"] as? String) ?: return
        val id = (info["id"] as? String).orEmpty()
        val key = if (id.isNotEmpty()) id else ip
        if (found.putIfAbsent(key, info) == null) {
            runOnUiThread {
                renderDiscovered()
                // match a recent by id to refresh its host (IP may have changed)
                updateRecentHost(id, ip)
            }
        }
    }

    private fun renderDiscovered() {
        val items = found.values.sortedBy { it["name"]?.toString() }
        discoveredEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        discoveredList.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        discoveredAdapter.submit(items)
    }

    // ------------------------------------------------------------ recents
    private fun recents(): List<JSONObject> {
        val raw = prefs.getString("recent_devices", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRecents(items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString("recent_devices", arr.toString()).apply()
    }

    private fun addRecent(id: String, name: String, host: String) {
        val items = recents().filterNot { it.optString("id") == id }.toMutableList()
        items.add(0, JSONObject().apply {
            put("id", id)
            put("name", name)
            put("host", host)
            put("time", System.currentTimeMillis())
        })
        saveRecents(items.take(5))
        renderRecents()
    }

    private fun updateRecentHost(id: String, host: String) {
        if (id.isEmpty()) return
        val items = recents()
        var changed = false
        items.forEach {
            if (it.optString("id") == id && it.optString("host") != host) {
                it.put("host", host); changed = true
            }
        }
        if (changed) saveRecents(items)
    }

    private fun renderRecents() {
        recentsContainer.removeAllViews()
        val items = recents()
        if (items.isEmpty()) return
        items.forEach { r ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_recent_device, recentsContainer, false)
            row.findViewById<TextView>(R.id.recentName).text = r.optString("name", "PC")
            row.findViewById<TextView>(R.id.recentHost).text = r.optString("host")
            row.setOnClickListener { promptCode(mapOf("name" to r.optString("name"), "ip" to r.optString("host"), "port" to DESKTOP_PORT, "code" to "", "id" to r.optString("id"))) }
            recentsContainer.addView(row)
        }
    }

    // ------------------------------------------------------------ connect
    /** Popup for the 6-digit code — never remembered. */
    private fun promptCode(info: Map<String, Any>) {
        val host = info["ip"]?.toString() ?: return
        val id = (info["id"] as? String).orEmpty()
        val name = info["name"]?.toString() ?: "PC"
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "6-digit code from the desktop"
            setMaxLines(1)
        }
        AlertDialog.Builder(this)
            .setTitle("Connect to $name")
            .setMessage("Enter the 6-digit pairing code shown on the desktop.")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val code = input.text.toString().trim()
                if (code.length != CODE_LENGTH) {
                    toast("Code must be $CODE_LENGTH digits")
                    return@setPositiveButton
                }
                doPair(host, code, id, name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doPair(host: String, code: String, id: String, name: String) {
        // Host is stored for auto-reconnect; the CODE is NOT remembered.
        prefs.edit()
            .putString(KEY_HOST, host)
            .putString(KEY_CODE, code)
            .putBoolean(KEY_ENABLED, true)
            .apply()
        if (id.isNotEmpty()) addRecent(id, name, host)
        setStatus("Connecting to $host…", null)
        ConnectionService.start(this, host, code)
        toast("Connecting…")
    }

    /** Floating menu with every feature screen (moved to the Dashboard via the FAB). */
    private fun showFeaturesSheet() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        sheet.setContentView(R.layout.sheet_features)

        val launch = { activity: Class<*> -> sheet.dismiss(); startActivity(Intent(this, activity)) }
        sheet.findViewById<View>(R.id.sheetPhoneHub)?.setOnClickListener {
            sheet.dismiss(); startActivity(Intent(this, PhoneActivity::class.java))
        }
        sheet.findViewById<View>(R.id.sheetBattery)?.setOnClickListener { launch(BatteryHealthActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetMpesa)?.setOnClickListener { launch(MpesaActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetSignal)?.setOnClickListener { launch(SignalLoggerActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetArchive)?.setOnClickListener { launch(ArchiveActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetSyncFolders)?.setOnClickListener { launch(SyncFoldersActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetDebloat)?.setOnClickListener { launch(DebloatActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetMaintenance)?.setOnClickListener { launch(MaintenanceActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetApk)?.setOnClickListener { launch(ApkInstallerActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetUssd)?.setOnClickListener { launch(UssdActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetCamera)?.setOnClickListener { launch(CameraRemoteActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetMedia)?.setOnClickListener { launch(MediaControllerActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetPower)?.setOnClickListener { launch(PcPowerActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetPcFiles)?.setOnClickListener { launch(PcFilesActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetMirror)?.setOnClickListener {
            sheet.dismiss()
            if (WebSocketManager.isConnected) {
                WebSocketManager.sendPc("mirror.start")
                android.widget.Toast.makeText(this, "Starting mirror on PC…", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                launch(RemoteControlActivity::class.java)
            }
        }
        sheet.findViewById<View>(R.id.sheetPairing)?.setOnClickListener { launch(PairingActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetLogs)?.setOnClickListener { launch(LogViewerActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetFilter)?.setOnClickListener { launch(NotificationFilterActivity::class.java) }
        sheet.findViewById<View>(R.id.sheetAbout)?.setOnClickListener { launch(AboutActivity::class.java) }

        sheet.show()
    }

    /** Request all runtime permissions the feature set needs. */
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

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

/** Horizontal cards for discovered PCs — ~2 visible at a time, scrollable. */
private class ConnectDeviceAdapter(
    private val onClick: (Map<String, Any>) -> Unit
) : RecyclerView.Adapter<ConnectDeviceViewHolder>() {

    private val items = mutableListOf<Map<String, Any>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConnectDeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_connect_device, parent, false)
        return ConnectDeviceViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ConnectDeviceViewHolder, position: Int) {
        val info = items[position]
        holder.name.text = info["name"]?.toString() ?: "PC"
        holder.ip.text = info["ip"]?.toString() ?: ""
        holder.itemView.setOnClickListener { onClick(info) }
    }

    fun submit(newItems: List<Map<String, Any>>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}

private class ConnectDeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val name: TextView = view.findViewById(R.id.deviceName)
    val ip: TextView = view.findViewById(R.id.deviceIp)
}
