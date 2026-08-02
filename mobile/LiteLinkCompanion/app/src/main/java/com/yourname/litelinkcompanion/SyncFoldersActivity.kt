package com.yourname.litelinkcompanion

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yourname.litelinkcompanion.commands.LocalExecutor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Sync Folders (Screen 8): pick phone folders and mirror them to the desktop. */
class SyncFoldersActivity : AppCompatActivity() {

    private lateinit var adapter: FolderAdapter
    private lateinit var statusText: TextView
    private lateinit var emptyView: TextView

    private val prefs get() = getSharedPreferences("litelink", Context.MODE_PRIVATE)
    private val folders get() = loadFolders()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync_folders)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.syncToolbar)
            .setNavigationOnClickListener { finish() }

        statusText = findViewById(R.id.syncStatus)
        emptyView = findViewById(R.id.syncEmpty)

        findViewById<View>(R.id.syncNowButton).setOnClickListener { syncAll() }
        findViewById<View>(R.id.addFolderButton).setOnClickListener { showAddFolder() }

        val list = findViewById<RecyclerView>(R.id.syncList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = FolderAdapter(this)
        list.adapter = adapter

        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val items = folders
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        adapter.submit(items)
        statusText.text = if (WebSocketManager.isConnected)
            "Connected to ${WebSocketManager.currentHost ?: "desktop"} — files upload here"
        else
            "Not connected — connect in Settings to upload files"
    }

    private fun loadFolders(): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        val arr = prefs.getString("sync_folders", null)
        if (arr != null) {
            val parsed = JSONArray(arr)
            for (i in 0 until parsed.length()) out.add(parsed.getJSONObject(i))
        }
        if (out.isEmpty()) {
            val defaults = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            )
            defaults.forEach { dir ->
                if (dir.exists() && dir.isDirectory) {
                    out.add(JSONObject().apply {
                        put("path", dir.absolutePath)
                        put("lastSync", 0L)
                    })
                }
            }
            saveFolders(out)
        }
        return out
    }

    private fun saveFolders(items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString("sync_folders", arr.toString()).apply()
    }

    private fun syncAll() {
        val items = folders
        if (items.isEmpty()) {
            toast("Add a folder first")
            return
        }
        if (!WebSocketManager.isConnected) {
            toast("Not connected to desktop")
            return
        }
        statusText.text = "Syncing ${items.size} folder(s)..."
        var remaining = items.size
        items.forEachIndexed { index, folder ->
            val path = folder.optString("path")
            LocalExecutor.run(applicationContext, "folder.push", JSONObject().put("path", path)) { ok, data ->
                runOnUiThread {
                    val updated = loadFolders()
                    if (index < updated.size) {
                        updated[index].put("lastSync", System.currentTimeMillis())
                        updated[index].put("lastResult", if (ok) "ok" else "failed")
                        saveFolders(updated)
                    }
                    remaining--
                    if (remaining == 0) {
                        statusText.text = if (ok)
                            "Sync finished — ${data.optInt("uploaded")} file(s) uploaded"
                        else
                            "Sync finished with errors"
                        render()
                    }
                }
            }
        }
    }

    private fun showAddFolder() {
        val presets = mapOf(
            "DCIM" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
            "Downloads" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
            "Pictures" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath,
            "Documents" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath,
            "Movies" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath
        )
        val options = presets.keys.toMutableList().apply { add("Custom path...") }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add folder to sync")
            .setItems(options.toTypedArray()) { _, which ->
                if (which < presets.size) {
                    addPath(presets.getValue(options[which]))
                } else {
                    showCustomPath()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomPath() {
        val input = EditText(this).apply {
            hint = "/storage/emulated/0/..."
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Custom path")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val path = input.text.toString().trim()
                if (path.isNotEmpty()) addPath(path) else toast("Enter a path")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addPath(path: String) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            toast("Not a directory: $path")
            return
        }
        val items = loadFolders().toMutableList()
        if (items.any { it.optString("path") == path }) {
            toast("Folder already added")
            return
        }
        items.add(JSONObject().apply {
            put("path", path)
            put("lastSync", 0L)
        })
        saveFolders(items)
        render()
    }

    fun removeFolder(path: String) {
        val items = loadFolders().filterNot { it.optString("path") == path }
        saveFolders(items)
        render()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

private class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val name: TextView = view.findViewById(R.id.folderName)
    val path: TextView = view.findViewById(R.id.folderPath)
    val status: TextView = view.findViewById(R.id.folderStatus)
    val remove: ImageButton = view.findViewById(R.id.folderRemoveButton)
}

private class FolderAdapter(
    private val activity: SyncFoldersActivity
) : RecyclerView.Adapter<FolderViewHolder>() {

    private val items = mutableListOf<JSONObject>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sync_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val item = items[position]
        val path = item.optString("path")
        holder.name.text = File(path).name.ifBlank { path }
        holder.path.text = path
        val lastSync = item.optLong("lastSync")
        holder.status.text = if (lastSync > 0) {
            val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
            "Last sync ${fmt.format(Date(lastSync))}  ·  ${item.optString("lastResult", "ok")}"
        } else {
            "Not synced yet"
        }
        holder.remove.setOnClickListener { activity.removeFolder(path) }
    }

    fun submit(newItems: List<JSONObject>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
