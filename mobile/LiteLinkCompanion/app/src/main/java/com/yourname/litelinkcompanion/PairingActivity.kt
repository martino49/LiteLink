package com.yourname.litelinkcompanion

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Pairing & Devices (Screen 15): show current device, paired desktops, pair a new one. */
class PairingActivity : AppCompatActivity() {

    private lateinit var adapter: PairedAdapter
    private lateinit var emptyView: TextView
    private lateinit var discoveryStatus: TextView
    private lateinit var discoveredContainer: LinearLayout

    private val prefs get() = getSharedPreferences("litelink", Context.MODE_PRIVATE)
    private val found = ConcurrentHashMap<String, Map<String, Any>>()
    private var broadcastListener: BroadcastListener? = null
    private var discoveryHelper: DiscoveryHelper? = null
    private var scanner: SubnetScanner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.pairToolbar)
            .setNavigationOnClickListener { finish() }

        findViewById<TextView>(R.id.currentDeviceId).text = "Device ID: ${deviceId()}"
        findViewById<TextView>(R.id.currentDeviceName).text = "Device: ${Build.MODEL}"
        findViewById<TextView>(R.id.currentDeviceOs).text =
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        findViewById<TextView>(R.id.currentDeviceBuild).text = "Build ${Build.DISPLAY}"

        emptyView = findViewById(R.id.pairEmpty)
        val list = findViewById<RecyclerView>(R.id.pairedList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = PairedAdapter(this)
        list.adapter = adapter
        render()

        discoveryStatus = findViewById(R.id.discoveryStatus)
        discoveredContainer = findViewById(R.id.discoveredContainer)
        findViewById<View>(R.id.rescanButton).setOnClickListener { startDiscovery() }

        findViewById<View>(R.id.pairButton).setOnClickListener { pairNow() }
        findViewById<View>(R.id.showQrButton).setOnClickListener { showQr() }
        findViewById<View>(R.id.scanQrButton).setOnClickListener {
            toast("QR scanning needs the desktop LiteLink app; pair with the code instead")
        }
    }

    override fun onResume() {
        super.onResume()
        render()
        startDiscovery()
    }

    override fun onPause() {
        super.onPause()
        stopDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDiscovery()
    }

    // ------------------------------------------------------------ discovery
    private fun startDiscovery() {
        found.clear()
        discoveryStatus.text = "Searching for your PC…"
        renderFound()

        broadcastListener = BroadcastListener(this) { info ->
            addFound(info)
        }
        broadcastListener?.start()

        discoveryHelper = DiscoveryHelper(this) { host ->
            addFound(mapOf("name" to "LiteLink PC", "ip" to host, "port" to 8765, "code" to ""))
        }
        discoveryHelper?.start()

        scanner = SubnetScanner(this, onFound = { host ->
            addFound(mapOf("name" to "LiteLink PC", "ip" to host, "port" to 8765, "code" to ""))
        })
        scanner?.start()
    }

    private fun stopDiscovery() {
        broadcastListener?.stop()
        broadcastListener = null
        discoveryHelper?.stop()
        discoveryHelper = null
        scanner?.stop()
        scanner = null
    }

    private fun addFound(info: Map<String, Any>) {
        val ip = (info["ip"] as? String) ?: return
        if (found.putIfAbsent(ip, info) == null) {
            runOnUiThread { renderFound() }
        }
    }

    private fun renderFound() {
        discoveredContainer.removeAllViews()
        if (found.isEmpty()) {
            discoveryStatus.text = "Searching for your PC…"
            return
        }
        discoveryStatus.text = "Found ${found.size} PC(s) — tap Connect to pair."
        found.values.sortedBy { it["name"]?.toString() }.forEach { info ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_discovered_pc, discoveredContainer, false)
            row.findViewById<TextView>(R.id.discoveredName).text = info["name"]?.toString() ?: "PC"
            row.findViewById<TextView>(R.id.discoveredIp).text =
                "${info["ip"]} : ${info["port"]}"
            row.findViewById<View>(R.id.discoveredConnect).setOnClickListener {
                connectToFound(info)
            }
            discoveredContainer.addView(row)
        }
    }

    private fun connectToFound(info: Map<String, Any>) {
        val host = info["ip"]?.toString() ?: return
        val code = info["code"]?.toString().orEmpty()
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.pairHostInput).setText(host)
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.pairCodeInput).setText(code)
        if (code.length == 6) {
            doPair(host, code)
        } else {
            pairNow()
        }
    }

    private fun render() {
        val items = pairedDevices()
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        adapter.submit(items)
    }

    private fun pairedDevices(): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        val raw = prefs.getString("paired_devices", null)
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) out.add(arr.getJSONObject(i))
            } catch (e: Exception) { }
        }
        return out
    }

    private fun savePaired(items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString("paired_devices", arr.toString()).apply()
    }

    private fun pairNow() {
        val host = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.pairHostInput)
            .text.toString().trim()
        val code = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.pairCodeInput)
            .text.toString().trim()
        if (host.isEmpty()) {
            toast("Enter the desktop IP or hostname")
            return
        }
        if (code.length != 6) {
            toast("Pairing code must be 6 digits")
            return
        }
        doPair(host, code)
    }

    private fun doPair(host: String, code: String) {
        prefs.edit()
            .putString("host", host)
            .putString("pairing_code", code)
            .putBoolean("enabled", true)
            .apply()

        val items = pairedDevices().filterNot { it.optString("host") == host }.toMutableList()
        items.add(0, JSONObject().apply {
            put("name", "Desktop")
            put("host", host)
            put("paired_at", System.currentTimeMillis())
        })
        savePaired(items)
        render()

        ConnectionService.start(this, host, code)
        toast("Pairing with $host...")
    }

    fun removeDevice(host: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove $host?")
            .setMessage("This desktop will no longer be paired.")
            .setPositiveButton("Remove") { _, _ ->
                savePaired(pairedDevices().filterNot { it.optString("host") == host })
                render()
                toast("Removed $host")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQr() {
        val code = prefs.getString("pairing_code", "123456").orEmpty()
        AlertDialog.Builder(this)
            .setTitle("Pairing code")
            .setMessage("Enter this code on your desktop to pair:\n\n$code\n\nQR scanning needs the desktop app — type the code instead.")
            .setPositiveButton("OK", null)
            .setNegativeButton("Copy") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("pairing", code))
                toast("Copied")
            }
            .show()
    }

    private fun deviceId(): String {
        val key = "device_id"
        prefs.getString(key, null)?.let { return it }
        val id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeLast(6)?.uppercase()?.chunked(3)?.joinToString("-") ?: "UNKNOWN"
        prefs.edit().putString(key, id).apply()
        return id
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

private class PairedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val name: TextView = view.findViewById(R.id.pairedName)
    val host: TextView = view.findViewById(R.id.pairedHost)
    val date: TextView = view.findViewById(R.id.pairedDate)
    val remove: ImageButton = view.findViewById(R.id.pairedRemove)
}

private class PairedAdapter(
    private val activity: PairingActivity
) : RecyclerView.Adapter<PairedViewHolder>() {

    private val items = mutableListOf<JSONObject>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PairedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_paired_device, parent, false)
        return PairedViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: PairedViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.optString("name")
        holder.host.text = item.optString("host")
        val fmt = SimpleDateFormat("MM-dd yyyy", Locale.US)
        holder.date.text = "Paired ${fmt.format(Date(item.optLong("paired_at")))}"
        holder.remove.setOnClickListener { activity.removeDevice(item.optString("host")) }
    }

    fun submit(newItems: List<JSONObject>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
