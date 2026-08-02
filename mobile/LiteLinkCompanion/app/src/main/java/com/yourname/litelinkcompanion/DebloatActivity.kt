package com.yourname.litelinkcompanion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yourname.litelinkcompanion.commands.ApkModule
import com.yourname.litelinkcompanion.commands.LocalExecutor
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Debloat Guardian (Screen 14): rate, select, back up, and remove packages. */
class DebloatActivity : AppCompatActivity() {

    private lateinit var adapter: DebloatAdapter
    private lateinit var statusText: TextView
    private var filter = "All"
    private var query = ""
    private var apps = listOf<JSONObject>()
    private val selected = ConcurrentHashMap<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debloat)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.debloatToolbar)
            .setNavigationOnClickListener { finish() }

        statusText = findViewById(R.id.debloatStatus)
        val list = findViewById<RecyclerView>(R.id.debloatList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = DebloatAdapter(this)
        list.adapter = adapter

        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.debloatSearchInput)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    render()
                }
            })

        val filterButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.debloatFilterButton)
        val menu = android.widget.PopupMenu(this, filterButton)
        for (m in arrayOf("All", "Safe", "Caution", "Critical")) menu.menu.add(m)
        menu.setOnMenuItemClickListener { item ->
            filter = item.title.toString()
            filterButton.text = filter
            render()
            true
        }
        filterButton.setOnClickListener { menu.show() }

        findViewById<View>(R.id.debloatUninstallButton).setOnClickListener { uninstallSelected() }
        findViewById<View>(R.id.debloatBackupButton).setOnClickListener { backupSelected() }

        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val all = ApkModule.listApps(this)
        apps = (0 until all.length()).map { all.getJSONObject(it) }
        render()
    }

    private fun render() {
        val q = query.lowercase()
        val shown = apps.filter { app ->
            val matchesQuery = q.isEmpty() ||
                app.optString("label").contains(q, ignoreCase = true) ||
                app.optString("package").contains(q, ignoreCase = true)
            val risk = risk(app.optString("package"), app.optBoolean("system"))
            val matchesFilter = filter == "All" || risk.equals(filter, ignoreCase = true)
            matchesQuery && matchesFilter
        }
        adapter.submit(shown)
        val count = selected.size
        statusText.text = if (count > 0)
            "$count package(s) selected.  \uD83D\uDD34 Caution: some selected packages may affect system stability."
        else
            "Tap checkboxes to select packages."
        if (count > 0) statusText.setTextColor(ContextCompat.getColor(this, R.color.danger))
        else statusText.setTextColor(ContextCompat.getColor(this, R.color.muted))
    }

    fun toggleSelection(pkg: String, checked: Boolean) {
        if (checked) selected[pkg] = true else selected.remove(pkg)
        render()
    }

    fun isSelected(pkg: String): Boolean = selected.containsKey(pkg)

    fun risk(pkg: String, system: Boolean): String {
        if (!system) return "Safe"
        val critical = listOf(
            "com.android.systemui", "com.android.phone", "com.android.settings",
            "com.android.providers.contacts", "com.android.providers.telephony",
            "com.android.providers.media", "com.android.providers.downloads",
            "com.google.android.gms", "com.google.android.gsf",
            "com.miui.core", "com.miui.securitycenter", "com.miui.securitycore",
            "com.xiaomi.miui.systemui"
        )
        val safe = listOf(
            "com.xiaomi.glgm", "com.miui.notes", "com.miui.videoplayer",
            "com.miui.miservice", "com.miui.cleanmaster", "com.miui.player",
            "com.miui.fm", "com.miui.compass", "com.android.chrome",
            "com.android.gallery3d", "com.miui.gallery", "com.xiaomi.mipicks",
            "com.xiaomi.gamecenter", "com.miui.bugreport", "com.miui.cloudbackup"
        )
        return when {
            pkg in critical -> "Critical"
            pkg in safe -> "Safe"
            else -> "Caution"
        }
    }

    private fun uninstallSelected() {
        if (selected.isEmpty()) {
            toast("Select packages first")
            return
        }
        val pkgs = selected.keys.toList()
        AlertDialog.Builder(this)
            .setTitle("Uninstall ${pkgs.size} package(s)?")
            .setMessage("System packages may need adb/root. User apps will open the system uninstaller.")
            .setPositiveButton("Uninstall") { _, _ ->
                pkgs.forEach { uninstallOne(it) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun uninstallOne(pkg: String) {
        val info = apps.find { it.optString("package") == pkg }
        val system = info?.optBoolean("system") ?: false
        if (!system) {
            try {
                startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg")))
            } catch (e: Exception) {
                toast("Can't uninstall $pkg")
            }
        } else {
            // System packages: try pm disable-user (needs root or adb shell).
            AlertDialog.Builder(this)
                .setTitle("System package: $pkg")
                .setMessage("Uninstalling system packages requires adb or root.\n\nOn your computer run:\n  adb shell pm uninstall --user 0 $pkg")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun backupSelected() {
        if (selected.isEmpty()) {
            toast("Select packages first")
            return
        }
        if (!WebSocketManager.isConnected) {
            toast("Not connected to desktop")
            return
        }
        val pkgs = selected.keys.toList()
        var done = 0
        pkgs.forEach { pkg ->
            LocalExecutor.run(applicationContext, "apk.extract", JSONObject().put("package", pkg)) { ok, _ ->
                done++
                if (done == pkgs.size) {
                    runOnUiThread { toast("APK backup finished (${pkgs.size} file(s))") }
                }
            }
        }
        toast("Backing up ${pkgs.size} APK(s) to desktop...")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

private class DebloatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val check: com.google.android.material.checkbox.MaterialCheckBox = view.findViewById(R.id.debloatCheck)
    val label: TextView = view.findViewById(R.id.debloatLabel)
    val packageName: TextView = view.findViewById(R.id.debloatPackage)
    val risk: TextView = view.findViewById(R.id.debloatRisk)
}

private class DebloatAdapter(
    private val activity: DebloatActivity
) : RecyclerView.Adapter<DebloatViewHolder>() {

    private val items = mutableListOf<JSONObject>()
    private val riskTextColor = mapOf(
        "Safe" to R.color.success,
        "Caution" to R.color.muted,
        "Critical" to R.color.danger
    )
    private val riskBg = mapOf(
        "Safe" to R.color.pill_green,
        "Caution" to R.color.pill_grey,
        "Critical" to R.color.pill_red
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DebloatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_debloat, parent, false)
        return DebloatViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: DebloatViewHolder, position: Int) {
        val item = items[position]
        val pkg = item.optString("package")
        holder.label.text = item.optString("label")
        holder.packageName.text = pkg
        val risk = activity.risk(pkg, item.optBoolean("system"))
        holder.risk.text = risk
        holder.risk.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, riskTextColor[risk] ?: R.color.muted))
        holder.risk.background.setTint(androidx.core.content.ContextCompat.getColor(holder.itemView.context, riskBg[risk] ?: R.color.pill_grey))
        holder.check.isChecked = activity.isSelected(pkg)
        holder.check.setOnCheckedChangeListener { _, checked ->
            activity.toggleSelection(pkg, checked)
        }
        holder.itemView.setOnClickListener {
            holder.check.isChecked = !holder.check.isChecked
        }
    }

    fun submit(newItems: List<JSONObject>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
