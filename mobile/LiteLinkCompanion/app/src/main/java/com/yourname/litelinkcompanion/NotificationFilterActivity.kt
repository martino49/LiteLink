package com.yourname.litelinkcompanion

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

/** Notification Filter (Screen 13): pick which apps may mirror to the desktop. */
class NotificationFilterActivity : AppCompatActivity() {

    internal data class AppRow(val packageName: String, val label: String, val isSystem: Boolean)

    private val allApps = mutableListOf<AppRow>()
    private var visibleApps = mutableListOf<AppRow>()
    private var blocked = mutableSetOf<String>()
    private lateinit var adapter: AppAdapter
    private var blockSystem = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_filter)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.filterToolbar)
            .setNavigationOnClickListener { finish() }

        blocked = NotificationFilter.blockedApps(this).toMutableSet()
        blockSystem = NotificationFilter.blockSystem(this)
        findViewById<SwitchMaterial>(R.id.blockSystemSwitch).apply {
            isChecked = blockSystem
            setOnCheckedChangeListener { _, checked ->
                blockSystem = checked
            }
        }

        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.filterSearchInput)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    applyFilter(s?.toString() ?: "")
                }
            })

        findViewById<View>(R.id.selectAllButton).setOnClickListener {
            val allVisibleChecked = visibleApps.none { blocked.contains(it.label) }
            if (allVisibleChecked) {
                visibleApps.forEach { blocked.remove(it.label) }
            } else {
                visibleApps.forEach { blocked.add(it.label) }
            }
            adapter.notifyDataSetChanged()
        }

        findViewById<View>(R.id.saveFilterButton).setOnClickListener {
            NotificationFilter.setBlockedApps(this, blocked)
            NotificationFilter.setBlockSystem(this, blockSystem)
            Toast.makeText(this, "Filter saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        val list = findViewById<RecyclerView>(R.id.filterAppList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(
            visible = { visibleApps },
            isBlocked = { blocked.contains(it) },
            onToggle = { label, checked -> if (checked) blocked.remove(label) else blocked.add(label) }
        )
        list.adapter = adapter

        loadApps()
    }

    private fun loadApps() {
        Thread {
            val pm = applicationContext.packageManager
            val rows = pm.getInstalledApplications(0)
                .mapNotNull { ai ->
                    try {
                        AppRow(
                            ai.packageName,
                            pm.getApplicationLabel(ai).toString(),
                            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                .filter { it.label.isNotBlank() }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                allApps.clear()
                allApps.addAll(rows)
                applyFilter("")
            }
        }.start()
    }

    private fun applyFilter(query: String) {
        visibleApps = if (query.isBlank()) {
            allApps.toMutableList()
        } else {
            allApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }.toMutableList()
        }
        adapter.notifyDataSetChanged()
    }
}

private class FilterAppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val badge: TextView = view.findViewById(R.id.filterAppBadge)
    val label: TextView = view.findViewById(R.id.filterAppLabel)
    val switch: SwitchMaterial = view.findViewById(R.id.filterAppSwitch)
}

private class AppAdapter(
    private val visible: () -> List<NotificationFilterActivity.AppRow>,
    private val isBlocked: (String) -> Boolean,
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<FilterAppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterAppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter_app, parent, false)
        return FilterAppViewHolder(view)
    }

    override fun getItemCount(): Int = visible().size

    override fun onBindViewHolder(holder: FilterAppViewHolder, position: Int) {
        val row = visible()[position]
        holder.badge.text = if (row.isSystem) "SYS" else "APP"
        holder.label.text = row.label
        val blocked = isBlocked(row.label)
        holder.switch.isChecked = !blocked
        holder.switch.setOnCheckedChangeListener { _, checked ->
            onToggle(row.label, checked)
        }
    }
}
