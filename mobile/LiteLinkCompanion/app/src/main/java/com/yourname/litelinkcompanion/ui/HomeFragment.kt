package com.yourname.litelinkcompanion.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.yourname.litelinkcompanion.R
import com.yourname.litelinkcompanion.RemoteControlActivity
import com.yourname.litelinkcompanion.WebSocketManager
import com.yourname.litelinkcompanion.commands.BatteryModule
import com.yourname.litelinkcompanion.commands.FindPhoneModule
import com.yourname.litelinkcompanion.commands.SignalModule

/** Dashboard: connection status, battery, signal, and quick actions. */
class HomeFragment : Fragment() {

    private var statusView: TextView? = null
    private var batteryView: TextView? = null
    private var signalView: TextView? = null

    private val connectionListener: (Boolean) -> Unit = {
        view?.post { refresh() }
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
        statusView = view.findViewById(R.id.homeStatus)
        batteryView = view.findViewById(R.id.batteryText)
        signalView = view.findViewById(R.id.signalText)

        view.findViewById<android.widget.Button>(R.id.ringButton).setOnClickListener {
            val app = requireContext().applicationContext
            FindPhoneModule.startRing(app, 30000)
            toast("Ringing 30s...")
        }
        view.findViewById<android.widget.Button>(R.id.stopRingButton).setOnClickListener {
            FindPhoneModule.stopRing()
            toast("Ring stopped")
        }
        view.findViewById<android.widget.Button>(R.id.refreshHomeButton).setOnClickListener {
            refresh()
        }
        view.findViewById<android.widget.Button>(R.id.openRemoteButton).setOnClickListener {
            startActivity(Intent(requireContext(), RemoteControlActivity::class.java))
        }

        WebSocketManager.addConnectionListener(connectionListener)
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        WebSocketManager.removeConnectionListener(connectionListener)
        statusView = null
        batteryView = null
        signalView = null
    }

    private fun refresh() {
        val app = requireContext().applicationContext
        statusView?.let {
            val connected = WebSocketManager.isConnected
            it.text = if (connected) "Connected to desktop" else "Disconnected"
            it.setTextColor(ContextCompat.getColor(it.context, if (connected) R.color.success else R.color.danger))
        }
        batteryView?.let {
            val b = BatteryModule.readBattery(app)
            val level = b.opt("level").let { v -> if (v is Int) "$v%" else "--" }
            val charging = b.optString("charging").ifBlank { "" }
            val temp = b.optDouble("temperature_c", -1.0)
            val tempStr = if (temp > 0) String.format("%.1f°C", temp) else ""
            it.text = "Battery: $level  ${if (charging == "true") "charging" else ""}  $tempStr".trim()
        }
        signalView?.let {
            val s = SignalModule.readSignal(app)
            val dbm = s.opt("signal_dbm")
            val dbmStr = if (dbm is Int) "$dbm dBm" else "n/a"
            it.text = "Signal: ${s.optString("network_type")}  ${s.optString("operator")}  $dbmStr"
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
