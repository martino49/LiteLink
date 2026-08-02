package com.yourname.litelinkcompanion

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Log Viewer (Screen 16): browse, filter, export, and clear debug logs. */
class LogViewerActivity : AppCompatActivity() {

    private var currentLevel = ""
    private var query = ""
    private lateinit var adapter: LogAdapter
    private lateinit var emptyView: TextView
    private lateinit var levelButton: com.google.android.material.button.MaterialButton
    private lateinit var levelMenu: android.widget.PopupMenu

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.logToolbar)
            .setNavigationOnClickListener { finish() }

        levelButton = findViewById(R.id.levelButton)
        emptyView = findViewById(R.id.logEmpty)
        levelMenu = android.widget.PopupMenu(this, levelButton)
        levelMenu.menu.add("All")
        LogStore.levels().forEach { levelMenu.menu.add(it) }
        levelMenu.setOnMenuItemClickListener { item ->
            currentLevel = item.title.toString().takeIf { it != "All" } ?: ""
            levelButton.text = item.title
            render()
            true
        }
        levelButton.setOnClickListener { levelMenu.show() }

        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.logSearchInput)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    render()
                }
            })

        findViewById<SwitchMaterial>(R.id.autoRefreshSwitch).apply {
            isChecked = LogStore.autoRefresh(this@LogViewerActivity)
            setOnCheckedChangeListener { _, checked ->
                LogStore.setAutoRefresh(this@LogViewerActivity, checked)
            }
        }

        findViewById<View>(R.id.exportLogsButton).setOnClickListener { exportLogs() }
        findViewById<View>(R.id.clearLogsButton).setOnClickListener { clearLogs() }

        val list = findViewById<RecyclerView>(R.id.logList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = LogAdapter(this)
        list.adapter = adapter

        render()
    }

    override fun onResume() {
        super.onResume()
        if (LogStore.autoRefresh(this)) render()
    }

    private fun render() {
        val entries = LogStore.list(this, currentLevel, query)
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        adapter.submit(entries)
    }

    private fun exportLogs() {
        val entries = LogStore.list(this, currentLevel, query)
        if (entries.isEmpty()) {
            toast("No logs to export")
            return
        }
        val sb = StringBuilder()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        entries.forEach {
            sb.append("[").append(it.optString("level")).append("] ")
                .append(fmt.format(Date(it.optLong("time")))).append("  ")
                .append(it.optString("message")).append("\n")
        }
        try {
            val dir = File(getExternalFilesDir(null), "logs")
            dir.mkdirs()
            val file = File(dir, "litelink_${System.currentTimeMillis()}.log")
            FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                    this@LogViewerActivity,
                    "$packageName.fileprovider",
                    file
                ))
                putExtra(Intent.EXTRA_TEXT, "LiteLink debug log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share log"))
        } catch (e: Exception) {
            LogStore.log(this, LogStore.LEVEL_ERROR, "Export failed: ${e.message}")
            toast("Export failed: ${e.message}")
        }
    }

    private fun clearLogs() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear logs?")
            .setMessage("All captured log entries will be removed.")
            .setPositiveButton("Clear") { _, _ ->
                LogStore.clear(this)
                render()
                toast("Logs cleared")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

private class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val badge: TextView = view.findViewById(R.id.logLevelBadge)
    val time: TextView = view.findViewById(R.id.logTime)
    val message: TextView = view.findViewById(R.id.logMessage)
}

private class LogAdapter(
    private val activity: LogViewerActivity
) : RecyclerView.Adapter<LogViewHolder>() {

    private val items = mutableListOf<JSONObject>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val item = items[position]
        val level = item.optString("level", LogStore.LEVEL_INFO)
        holder.badge.text = "[$level]"
        holder.badge.setTextColor(levelColor(level))
        holder.badge.background = ContextCompat.getDrawable(
            activity,
            R.drawable.bg_pill
        )
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
        holder.time.text = fmt.format(Date(item.optLong("time")))
        holder.message.text = item.optString("message")
    }

    fun submit(newItems: List<JSONObject>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun levelColor(level: String): Int = when (level) {
        LogStore.LEVEL_ERROR -> ContextCompat.getColor(
            activity, R.color.danger
        )
        LogStore.LEVEL_WARN -> Color.rgb(0xEF, 0x6C, 0x00)
        else -> ContextCompat.getColor(activity, R.color.teal_700)
    }
}
