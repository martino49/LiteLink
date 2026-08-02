package com.yourname.litelinkcompanion

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yourname.litelinkcompanion.commands.ApkModule
import java.io.File
import org.json.JSONArray

/** APK Installer (Screen 18): list pushed APKs, install, clear, history. */
class ApkInstallerActivity : AppCompatActivity() {

    private lateinit var adapter: ApkAdapter
    private lateinit var emptyView: TextView
    private lateinit var countText: TextView

    private val prefs get() = getSharedPreferences("litelink", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apk)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.apkToolbar)
            .setNavigationOnClickListener { finish() }

        emptyView = findViewById(R.id.apkEmpty)
        countText = findViewById(R.id.apkPendingCount)
        val list = findViewById<RecyclerView>(R.id.apkList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = ApkAdapter(this)
        list.adapter = adapter

        findViewById<View>(R.id.apkInstallAllButton).setOnClickListener { installAll() }
        findViewById<View>(R.id.apkClearButton).setOnClickListener { clearCompleted() }
        findViewById<View>(R.id.apkHistoryButton).setOnClickListener { showHistory() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val files = findApks()
        val items = files.map { file ->
            org.json.JSONObject().apply {
                put("path", file.absolutePath)
                put("name", file.name)
                put("size", file.length())
                put("status", "pending")
            }
        }
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        countText.text = "${items.size} APK(s) pending"
        adapter.submit(items)
    }

    private fun findApks(): List<File> {
        val out = mutableListOf<File>()
        listOf(getExternalFilesDir(null)?.let { File(it, "inbox") }, cacheDir)
            .filterNotNull()
            .forEach { dir ->
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.filter { it.extension.equals("apk", ignoreCase = true) }
                        ?.forEach { out.add(it) }
                }
            }
        return out
    }

    private fun installAll() {
        val paths = adapter.currentItems().map { it.optString("path") }
        if (paths.isEmpty()) {
            toast("No APKs pending")
            return
        }
        paths.forEach { installOne(it) }
    }

    fun installOne(path: String) {
        val file = File(path)
        if (!file.exists()) {
            toast("APK not found")
            return
        }
        addHistory(file.name, "Installing...")
        try {
            ApkModule.installApk(applicationContext, file)
            addHistory(file.name, "Installed (prompt shown)")
            toast("Installing ${file.name}")
        } catch (e: Exception) {
            addHistory(file.name, "Failed: ${e.message}")
            toast("Install failed: ${e.message}")
        }
    }

    private fun clearCompleted() {
        val files = findApks()
        var removed = 0
        files.forEach { if (it.delete()) removed++ }
        refresh()
        toast("Removed $removed file(s)")
    }

    private fun addHistory(name: String, status: String) {
        val raw = prefs.getString("apk_history", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val merged = JSONArray()
        merged.put(org.json.JSONObject().apply {
            put("name", name)
            put("status", status)
            put("time", System.currentTimeMillis())
        })
        for (i in 0 until arr.length()) {
            merged.put(arr.getJSONObject(i))
        }
        val kept = org.json.JSONArray()
        for (i in 0 until minOf(50, merged.length())) kept.put(merged.getJSONObject(i))
        prefs.edit().putString("apk_history", kept.toString()).apply()
    }

    private fun showHistory() {
        val raw = prefs.getString("apk_history", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        if (arr.length() == 0) {
            toast("No history yet")
            return
        }
        val lines = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val t = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
                .format(java.util.Date(o.optLong("time")))
            "${o.optString("name")} — ${o.optString("status")} ($t)"
        }
        AlertDialog.Builder(this)
            .setTitle("Install history")
            .setItems(lines.toTypedArray(), null)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

private class ApkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val name: TextView = view.findViewById(R.id.apkName)
    val version: TextView = view.findViewById(R.id.apkVersion)
    val install: com.google.android.material.button.MaterialButton = view.findViewById(R.id.apkInstallButton)
    val progress: ProgressBar = view.findViewById(R.id.apkProgress)
    val status: TextView = view.findViewById(R.id.apkStatus)
}

private class ApkAdapter(
    private val activity: ApkInstallerActivity
) : RecyclerView.Adapter<ApkViewHolder>() {

    private val items = mutableListOf<org.json.JSONObject>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_apk, parent, false)
        return ApkViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ApkViewHolder, position: Int) {
        val item = items[position]
        val path = item.optString("path")
        holder.name.text = item.optString("name")
        holder.version.text = String.format(
            java.util.Locale.US, "%.1f MB", item.optLong("size") / 1048576.0
        )
        holder.install.setOnClickListener { activity.installOne(path) }
    }

    fun submit(newItems: List<org.json.JSONObject>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun currentItems(): List<org.json.JSONObject> = items.toList()
}
