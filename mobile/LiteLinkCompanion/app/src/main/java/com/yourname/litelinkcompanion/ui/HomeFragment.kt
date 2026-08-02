package com.yourname.litelinkcompanion.ui

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.yourname.litelinkcompanion.ActivityLog
import com.yourname.litelinkcompanion.ConnectionService
import com.yourname.litelinkcompanion.MainActivity
import com.yourname.litelinkcompanion.R
import com.yourname.litelinkcompanion.RemoteControlActivity
import com.yourname.litelinkcompanion.WebSocketManager
import com.yourname.litelinkcompanion.commands.SignalModule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home dashboard (Screen 4): status, stats, quick actions, recent activity. */
class HomeFragment : Fragment() {

    private var statusText: TextView? = null
    private var detailText: TextView? = null
    private var statusDot: View? = null
    private var batteryStat: TextView? = null
    private var networkStat: TextView? = null
    private var reconnectCard: View? = null
    private var recentContainer: LinearLayout? = null
    private var emptyView: TextView? = null
    private var serviceSwitch: com.google.android.material.switchmaterial.SwitchMaterial? = null
    private var serviceDetail: TextView? = null
    private var statNotifications: TextView? = null
    private var statSyncs: TextView? = null
    private var statStorage: TextView? = null
    private var swipeRefresh: SwipeRefreshLayout? = null

    private val connectionListener: (Boolean) -> Unit = { _ ->
        view?.post { refreshStatus() }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusText = view.findViewById(R.id.connectionStatus)
        detailText = view.findViewById(R.id.connectionDetail)
        statusDot = view.findViewById(R.id.statusDot)
        batteryStat = view.findViewById(R.id.batteryStat)
        networkStat = view.findViewById(R.id.networkStat)
        reconnectCard = view.findViewById(R.id.reconnectCard)
        recentContainer = view.findViewById(R.id.recentContainer)
        emptyView = view.findViewById(R.id.activityEmpty)
        serviceSwitch = view.findViewById(R.id.serviceSwitch)
        serviceDetail = view.findViewById(R.id.serviceDetail)
        statNotifications = view.findViewById(R.id.statNotifications)
        statSyncs = view.findViewById(R.id.statSyncs)
        statStorage = view.findViewById(R.id.statStorage)
        swipeRefresh = view.findViewById(R.id.homeSwipeRefresh)

        serviceSwitch?.setOnCheckedChangeListener { _, isChecked ->
            val app = requireContext().applicationContext
            val prefs = app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("enabled", isChecked).apply()
            if (isChecked) {
                ConnectionService.start(
                    app,
                    prefs.getString("host", "").orEmpty(),
                    prefs.getString("pairing_code", "").orEmpty()
                )
            } else {
                ConnectionService.stop(app)
                refreshStatus()
            }
        }

        view.findViewById<View>(R.id.searchBar).setOnClickListener {
            // Tapping the search bar focuses the command entry point.
            toast("Search & commands")
        }
        reconnectCard?.setOnClickListener {
            startActivity(Intent(requireContext(), MainActivity::class.java))
        }
        view.findViewById<View>(R.id.qaMirror).setOnClickListener {
            startActivity(Intent(requireContext(), RemoteControlActivity::class.java))
        }
        view.findViewById<View>(R.id.qaFind).setOnClickListener {
            if (com.yourname.litelinkcompanion.WebSocketManager.isConnected) {
                com.yourname.litelinkcompanion.WebSocketManager.sendPc("ring.pc")
                ActivityLog.log(requireContext(), "\uD83D\uDD0D", "Rang the PC")
                toast("Ringing the PC…")
            } else {
                toast("Not connected to the PC")
            }
            refreshRecent()
        }
        view.findViewById<View>(R.id.qaConnect).setOnClickListener {
            startActivity(Intent(requireContext(), MainActivity::class.java))
        }
        view.findViewById<View>(R.id.qaNotify).setOnClickListener {
            // Notifications live in the hub — open the hub so the user can switch to them.
            startActivity(Intent(requireContext(), com.yourname.litelinkcompanion.PhoneActivity::class.java))
        }
        view.findViewById<View>(R.id.qaSync).setOnClickListener {
            com.yourname.litelinkcompanion.commands.LocalExecutor.run(
                requireContext().applicationContext,
                "folder.sync",
                org.json.JSONObject().put("path", "/sdcard")
            ) { ok, _ ->
                toast(if (ok) "Sync started" else "Sync failed")
            }
        }

        swipeRefresh?.setOnRefreshListener {
            refreshAll()
            swipeRefresh?.isRefreshing = false
        }

        WebSocketManager.addConnectionListener(connectionListener)
        refreshAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        WebSocketManager.removeConnectionListener(connectionListener)
        statusText = null
        detailText = null
        statusDot = null
        batteryStat = null
        networkStat = null
        recentContainer = null
        serviceSwitch = null
        swipeRefresh = null
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun refreshAll() {
        refreshStatus()
        refreshStats()
        refreshRecent()
    }

    private fun refreshStatus() {
        val app = requireContext().applicationContext
        val connected = WebSocketManager.isConnected
        val host = WebSocketManager.currentHost ?: "laptop"
        statusText?.text = if (connected) "Connected" else "Disconnected"
        statusDot?.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(app, if (connected) R.color.success else R.color.danger)
        )
        detailText?.text = if (connected) "Linked to $host" else "Not connected to laptop"
        reconnectCard?.visibility = if (connected) View.GONE else View.VISIBLE

        val prefs = app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
        serviceSwitch?.isChecked = prefs.getBoolean("enabled", false)
        serviceDetail?.text = buildString {
            append(if (prefs.getBoolean("notifications_enabled", true)) "Notifications: ON" else "Notifications: OFF")
            append("  \u2022  ")
            append(if (prefs.getBoolean("folder_sync_enabled", true)) "Folder Sync: ON" else "Folder Sync: OFF")
        }

        batteryStat?.text = "\uD83D\uDD0B " + batteryLevel(app)
        networkStat?.text = "\uD83D\uDCF1 " + SignalModule.readSignal(app).optString("network_type", "n/a")
    }

    private fun refreshStats() {
        val app = requireContext().applicationContext
        statNotifications?.text = ActivityLog.notificationsToday(app).toString()
        statSyncs?.text = ActivityLog.syncsToday(app).toString()
        statStorage?.text = storageUsed(app)
    }

    private fun refreshRecent() {
        val app = requireContext().applicationContext
        val events = ActivityLog.recent(app, 6)
        emptyView?.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        recentContainer?.removeAllViews()
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        events.forEachIndexed { index, event ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_activity, recentContainer, false)
            row.findViewById<TextView>(R.id.activityIcon).text = event.optString("icon", "\u2022")
            row.findViewById<TextView>(R.id.activityText).text = event.optString("text", "")
            val time = event.optLong("time", System.currentTimeMillis())
            row.findViewById<TextView>(R.id.activityTime).text = fmt.format(Date(time))
            if (index > 0) {
                row.findViewById<View>(R.id.activityDivider).visibility = View.VISIBLE
            }
            recentContainer?.addView(row)
        }
    }

    private fun batteryLevel(app: Context): String {
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return try {
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level in 0..100) "$level%" else "--"
        } catch (e: Exception) {
            "--"
        }
    }

    private fun storageUsed(app: Context): String {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            if (total <= 0) "--" else "${(total - free) * 100 / total}%"
        } catch (e: Exception) {
            "--"
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
