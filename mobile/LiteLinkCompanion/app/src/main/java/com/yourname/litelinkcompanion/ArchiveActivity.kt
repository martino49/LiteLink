package com.yourname.litelinkcompanion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yourname.litelinkcompanion.commands.LocalExecutor
import com.yourname.litelinkcompanion.commands.SmsArchiveModule
import org.json.JSONObject
import java.io.File

/** SMS & Call Log Archive (Screen 12): browse SMS/calls, backup to desktop, export CSV. */
class ArchiveActivity : AppCompatActivity() {

    private lateinit var adapter: ArchiveAdapter
    private lateinit var emptyView: TextView
    private var tab = "sms"
    private var query = ""
    private var sms = listOf<JSONObject>()
    private var calls = listOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archive)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.archiveToolbar)
            .setNavigationOnClickListener { finish() }

        emptyView = findViewById(R.id.archiveEmpty)
        val list = findViewById<RecyclerView>(R.id.archiveList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = ArchiveAdapter()
        list.adapter = adapter

        findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.archiveTabs)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                tab = when (checkedId) {
                    R.id.archiveTabSms -> "sms"
                    R.id.archiveTabCalls -> "calls"
                    else -> "combined"
                }
                load()
            }

        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.archiveSearchInput)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    render()
                }
            })

        findViewById<View>(R.id.archiveBackupButton).setOnClickListener { backupNow() }
        findViewById<View>(R.id.archiveSendPcButton).setOnClickListener { sendToPc() }
        findViewById<View>(R.id.archiveExportButton).setOnClickListener { exportCsv() }

        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val needSms = tab != "calls"
        val needCalls = tab != "sms"
        val missing = mutableListOf<String>()
        if (needSms && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) missing.add(Manifest.permission.READ_SMS)
        if (needCalls && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) missing.add(Manifest.permission.READ_CALL_LOG)
        if (missing.isNotEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyView.text = "Permission needed to read this data.\nTap to grant."
            emptyView.setOnClickListener {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), 300)
            }
            adapter.submit(emptyList())
            return
        }
        emptyView.setOnClickListener(null)
        sms = if (needSms) SmsArchiveModule.listRecentSms(this, 100) else emptyList()
        calls = if (needCalls) SmsArchiveModule.listRecentCalls(this, 100) else emptyList()
        render()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 300) load()
    }

    private fun render() {
        val shown = buildList {
            when (tab) {
                "sms" -> addAll(sms)
                "calls" -> addAll(calls)
                else -> addAll(sms + calls)
            }
        }.filter { item ->
            val q = query.lowercase()
            q.isEmpty() ||
                item.optString("address").contains(q, ignoreCase = true) ||
                item.optString("person").contains(q, ignoreCase = true) ||
                item.optString("name").contains(q, ignoreCase = true) ||
                item.optString("number").contains(q, ignoreCase = true) ||
                item.optString("body").contains(q, ignoreCase = true)
        }
        emptyView.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        if (shown.isEmpty()) emptyView.text = "Nothing here yet"
        adapter.submit(shown)
    }

    private fun backupNow() {
        val app = applicationContext
        val needSms = tab != "calls"
        val needCalls = tab != "sms"
        if (needSms && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toast("SMS permission needed")
            return
        }
        if (needCalls && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toast("Call log permission needed")
            return
        }
        if (!WebSocketManager.isConnected) {
            toast("Not connected to desktop")
            return
        }
        if (needSms) {
            LocalExecutor.run(app, "sms.backup") { ok, data ->
                runOnUiThread {
                    toast(if (ok) "SMS backed up (${data.optInt("rows")} rows)" else "SMS backup failed")
                }
            }
        }
        if (needCalls) {
            LocalExecutor.run(app, "calls.backup") { ok, data ->
                runOnUiThread {
                    toast(if (ok) "Calls backed up (${data.optInt("rows")} rows)" else "Calls backup failed")
                }
            }
        }
    }

    private fun exportCsv() {
        val shown = adapter.currentItems()
        if (shown.isEmpty()) {
            toast("Nothing to export")
            return
        }
        val sb = StringBuilder()
        if (tab == "calls") {
            sb.append("date,number,name,type,duration\n")
            shown.forEach {
                sb.append("\"").append(it.optString("date")).append("\",")
                    .append("\"").append(it.optString("number")).append("\",")
                    .append("\"").append(it.optString("name")).append("\",")
                    .append(it.optInt("type")).append(",")
                    .append(it.optLong("duration")).append("\n")
            }
        } else {
            sb.append("date,address,person,body\n")
            shown.forEach {
                sb.append("\"").append(it.optString("date")).append("\",")
                    .append("\"").append(it.optString("address")).append("\",")
                    .append("\"").append(it.optString("person")).append("\",")
                    .append("\"").append(it.optString("body").replace("\n", "\\n")).append("\"\n")
            }
        }
        try {
            val dir = File(getExternalFilesDir(null), "archive").apply { mkdirs() }
            val file = File(dir, "archive_${System.currentTimeMillis()}.csv")
            java.io.FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                    this@ArchiveActivity, "$packageName.fileprovider", file
                ))
                putExtra(Intent.EXTRA_TEXT, "Archive export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share archive CSV"))
        } catch (e: Exception) {
            toast("Export failed: ${e.message}")
        }
    }

    /** Push the current SMS/call list to the PC as structured data (feature 80). */
    private fun sendToPc() {
        if (!WebSocketManager.isConnected) {
            toast("Not connected to the PC")
            return
        }
        val shown = adapter.currentItems()
        if (shown.isEmpty()) {
            toast("Nothing to send")
            return
        }
        val arr = org.json.JSONArray()
        shown.forEach { arr.put(it) }
        WebSocketManager.sendPc("messages.push", org.json.JSONObject().apply {
            put("kind", tab) // "sms" or "calls"
            put("items", arr)
        })
        toast("Sent ${shown.size} item(s) to the PC")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

private class ArchiveViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val icon: TextView = view.findViewById(R.id.archiveIcon)
    val title: TextView = view.findViewById(R.id.archiveTitle)
    val body: TextView = view.findViewById(R.id.archiveBody)
    val meta: TextView = view.findViewById(R.id.archiveMeta)
}

private class ArchiveAdapter : RecyclerView.Adapter<ArchiveViewHolder>() {

    private val items = mutableListOf<JSONObject>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArchiveViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_archive, parent, false)
        return ArchiveViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ArchiveViewHolder, position: Int) {
        val item = items[position]
        val isCall = item.has("duration")
        if (isCall) {
            holder.icon.text = when (item.optInt("type")) {
                1 -> "\uD83D\uDCDE"
                2 -> "\uD83D\uDCDE"
                3 -> "\uD83D\uDCDE"
                else -> "\uD83D\uDCDE"
            }
            holder.title.text = item.optString("name").ifBlank { item.optString("number") }
            holder.body.text = when (item.optInt("type")) {
                1 -> "Incoming call"
                2 -> "Outgoing call"
                3 -> "Missed call"
                else -> "Call"
            }
            holder.meta.text = "${item.optString("date")}  ·  ${item.optLong("duration")}s"
        } else {
            holder.icon.text = "\uD83D\uDCE9"
            holder.title.text = item.optString("person").ifBlank { item.optString("address") }
            holder.body.text = item.optString("body")
            holder.meta.text = item.optString("date")
        }
    }

    fun submit(newItems: List<JSONObject>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun currentItems(): List<JSONObject> = items.toList()
}
