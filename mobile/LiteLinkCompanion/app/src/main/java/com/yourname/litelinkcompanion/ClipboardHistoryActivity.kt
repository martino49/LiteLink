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

/** Clipboard History (feature 30): browse recent copies from the PC. */
class ClipboardHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: ClipAdapter
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clipboard_history)

        WebSocketManager.init(this)
        WebSocketManager.onPcResult = { cmd, ok, data ->
            runOnUiThread {
                if (cmd == "clipboard.history") {
                    if (ok) {
                        val arr = data.optJSONArray("items") ?: org.json.JSONArray()
                        val items = (0 until arr.length()).map { arr.getString(it) }
                        adapter.submit(items)
                        statusView.text = "${items.size} item(s)"
                    } else {
                        statusView.text = "Failed"
                    }
                } else if (cmd == "clipboard.history.clear") {
                    adapter.submit(emptyList())
                    statusView.text = "Cleared"
                }
            }
        }

        findViewById<MaterialToolbar>(R.id.cliphistToolbar)
            .setNavigationOnClickListener { finish() }

        statusView = findViewById(R.id.cliphistStatus)
        val list = findViewById<RecyclerView>(R.id.cliphistList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = ClipAdapter()
        list.adapter = adapter

        findViewById<View>(R.id.cliphistRefresh).setOnClickListener { refresh() }
        findViewById<View>(R.id.cliphistClear).setOnClickListener {
            if (WebSocketManager.isConnected) WebSocketManager.sendPc("clipboard.history.clear")
        }

        refresh()
    }

    private fun refresh() {
        if (!WebSocketManager.isConnected) {
            statusView.text = "Not connected"
            return
        }
        WebSocketManager.sendPc("clipboard.history")
    }
}

private class ClipViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val text: TextView = view.findViewById(R.id.clipText)
}

private class ClipAdapter : RecyclerView.Adapter<ClipViewHolder>() {

    private val items = mutableListOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clipboard, parent, false)
        return ClipViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ClipViewHolder, position: Int) {
        holder.text.text = items[position]
    }

    fun submit(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
