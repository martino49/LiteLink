package com.yourname.litelinkcompanion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONObject

/** Remote File Browser (companion): browse PC folders over the link. */
class PcFilesActivity : AppCompatActivity() {

    private lateinit var adapter: PcFileAdapter
    private lateinit var emptyView: TextView
    private lateinit var statusView: TextView
    private var currentPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pc_files)

        WebSocketManager.init(this)
        WebSocketManager.onPcResult = { cmd, ok, data ->
            runOnUiThread {
                when (cmd) {
                    "files.list" -> onList(ok, data)
                    "files.get" -> {
                        statusView.text = if (ok) "Downloading ${data.optString("path")}" else "Failed: ${data.optString("error")}"
                    }
                }
            }
        }

        findViewById<MaterialToolbar>(R.id.pcfilesToolbar)
            .setNavigationOnClickListener { finish() }

        emptyView = findViewById(R.id.pcfilesEmpty)
        statusView = findViewById(R.id.pcfilesStatus)
        val list = findViewById<RecyclerView>(R.id.pcfilesList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = PcFileAdapter { entry -> onFileTap(entry) }
        list.adapter = adapter

        findViewById<View>(R.id.pcfilesGo).setOnClickListener { browse() }
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.pcfilesPath)
            .setOnEditorActionListener { _, _, _ -> browse(); true }

        browse("C:\\Users")
    }

    private fun browse(path: String = "") {
        val p = path.ifBlank {
            findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.pcfilesPath)
                .text.toString().trim()
        }
        if (p.isEmpty()) return
        currentPath = p
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.pcfilesPath).setText(p)
        emptyView.text = "Loading $p…"
        emptyView.visibility = View.VISIBLE
        adapter.submit(emptyList())
        WebSocketManager.sendPc("files.list", JSONObject().put("path", p))
    }

    private fun onList(ok: Boolean, data: JSONObject) {
        if (!ok) {
            emptyView.text = "Failed: ${data.optString("error")}"
            emptyView.visibility = View.VISIBLE
            return
        }
        val arr = data.optJSONArray("entries") ?: org.json.JSONArray()
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        if (items.isEmpty()) emptyView.text = "Folder is empty."
        adapter.submit(items)
        statusView.text = data.optString("path")
    }

    private fun onFileTap(entry: JSONObject) {
        if (entry.optBoolean("is_dir")) {
            browse(entry.optString("path"))
        } else {
            WebSocketManager.sendPc("files.get", JSONObject().put("path", entry.optString("path")))
        }
    }
}

private class PcFileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val name: TextView = view.findViewById(R.id.pcfileName)
    val size: TextView = view.findViewById(R.id.pcfileSize)
}

private class PcFileAdapter(
    private val onClick: (JSONObject) -> Unit
) : RecyclerView.Adapter<PcFileViewHolder>() {

    private val items = mutableListOf<JSONObject>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PcFileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pc_file, parent, false)
        return PcFileViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: PcFileViewHolder, position: Int) {
        val e = items[position]
        val isDir = e.optBoolean("is_dir")
        val size = e.optLong("size")
        holder.name.text = if (isDir) "📁 ${e.optString("name")}" else e.optString("name")
        holder.size.text = if (isDir) "→" else fmt(size)
        holder.itemView.setOnClickListener { onClick(e) }
    }

    fun submit(newItems: List<JSONObject>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun fmt(n: Long): String {
        val kb = n / 1024.0
        return when {
            n < 1024 -> "$n B"
            kb < 1024 -> String.format(java.util.Locale.US, "%.0f KB", kb)
            else -> String.format(java.util.Locale.US, "%.1f MB", kb / 1024)
        }
    }
}
