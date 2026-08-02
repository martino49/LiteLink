package com.yourname.litelinkcompanion.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.yourname.litelinkcompanion.AboutActivity
import com.yourname.litelinkcompanion.ApkInstallerActivity
import com.yourname.litelinkcompanion.ArchiveActivity
import com.yourname.litelinkcompanion.BatteryHealthActivity
import com.yourname.litelinkcompanion.CameraRemoteActivity
import com.yourname.litelinkcompanion.ClipboardHistoryActivity
import com.yourname.litelinkcompanion.DebloatActivity
import com.yourname.litelinkcompanion.GameControllerActivity
import com.yourname.litelinkcompanion.LogViewerActivity
import com.yourname.litelinkcompanion.MaintenanceActivity
import com.yourname.litelinkcompanion.MediaControllerActivity
import com.yourname.litelinkcompanion.MpesaActivity
import com.yourname.litelinkcompanion.NotificationFilterActivity
import com.yourname.litelinkcompanion.PairingActivity
import com.yourname.litelinkcompanion.PcFilesActivity
import com.yourname.litelinkcompanion.PcPowerActivity
import com.yourname.litelinkcompanion.R
import com.yourname.litelinkcompanion.RemoteControlActivity
import com.yourname.litelinkcompanion.ScreenMirrorActivity
import com.yourname.litelinkcompanion.SecondScreenActivity
import com.yourname.litelinkcompanion.SignalLoggerActivity
import com.yourname.litelinkcompanion.SyncFoldersActivity
import com.yourname.litelinkcompanion.UssdActivity
import com.yourname.litelinkcompanion.VoiceCommandActivity
import com.yourname.litelinkcompanion.WebSocketManager

/** Features hub: full-screen icon grid of every LiteLink feature. */
class FeaturesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_features, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val c = requireContext()

        fun open(activity: Class<*>) {
            startActivity(Intent(c, activity))
        }

        // PC Remote
        addTile(view, R.id.pcRemoteGrid, R.drawable.ic_compass, "Screen Mirror") {
            open(ScreenMirrorActivity::class.java)
        }
        addTile(view, R.id.pcRemoteGrid, R.drawable.ic_compass, "2nd Screen") {
            open(SecondScreenActivity::class.java)
        }
        addTile(view, R.id.pcRemoteGrid, R.drawable.ic_play, "Media") {
            open(MediaControllerActivity::class.java)
        }
        addTile(view, R.id.pcRemoteGrid, R.drawable.ic_power, "PC Power") {
            open(PcPowerActivity::class.java)
        }
        addTile(view, R.id.pcRemoteGrid, R.drawable.ic_files, "PC Files") {
            open(PcFilesActivity::class.java)
        }
        addTile(view, R.id.pcRemoteGrid, R.drawable.ic_compass, "Touchpad") {
            open(RemoteControlActivity::class.java)
        }
        addTile(view, R.id.pcRemoteGrid, R.drawable.ic_play, "Gamepad") {
            open(GameControllerActivity::class.java)
        }
        addTile(view, R.id.pcRemoteGrid, R.drawable.ic_send, "Voice") {
            open(VoiceCommandActivity::class.java)
        }
        addTile(view, R.id.pcRemoteGrid, R.drawable.ic_clipboard, "Clipboard") {
            open(ClipboardHistoryActivity::class.java)
        }
        addTile(view, R.id.pcRemoteGrid, R.drawable.ic_ring, "Ring PC", tag = "ring_pc_tile") {
            if (WebSocketManager.isConnected) {
                WebSocketManager.sendPc("ring.pc")
                // feedback: show "Ringing…" then restore
                val tile = view.findViewWithTag<View>("ring_pc_tile")
                val lbl = tile?.findViewById<TextView>(R.id.tileLabel)
                lbl?.text = "Ringing…"
                lbl?.postDelayed({ lbl.text = "Ring PC" }, 2500)
            }
        }

        // Monitoring
        addTile(view, R.id.monitoringGrid, R.drawable.ic_home, "Battery") {
            open(BatteryHealthActivity::class.java)
        }
        addTile(view, R.id.monitoringGrid, R.drawable.ic_money, "M-Pesa") {
            open(MpesaActivity::class.java)
        }
        addTile(view, R.id.monitoringGrid, R.drawable.ic_wifi, "Signal") {
            open(SignalLoggerActivity::class.java)
        }
        addTile(view, R.id.monitoringGrid, R.drawable.ic_message, "SMS") {
            open(ArchiveActivity::class.java)
        }
        addTile(view, R.id.monitoringGrid, R.drawable.ic_camera, "Camera") {
            open(CameraRemoteActivity::class.java)
        }
        addTile(view, R.id.monitoringGrid, R.drawable.ic_apps, "Apps") {
            open(ApkInstallerActivity::class.java)
        }

        // Phone tools
        addTile(view, R.id.phoneGrid, R.drawable.ic_tools, "Debloat") {
            open(DebloatActivity::class.java)
        }
        addTile(view, R.id.phoneGrid, R.drawable.ic_gear, "Maintenance") {
            open(MaintenanceActivity::class.java)
        }
        addTile(view, R.id.phoneGrid, R.drawable.ic_dial, "USSD") {
            open(UssdActivity::class.java)
        }
        addTile(view, R.id.phoneGrid, R.drawable.ic_files, "Sync Folders") {
            open(SyncFoldersActivity::class.java)
        }
        addTile(view, R.id.phoneGrid, R.drawable.ic_ring, "Pairing") {
            open(PairingActivity::class.java)
        }
        addTile(view, R.id.phoneGrid, R.drawable.ic_apps, "Notify Filter") {
            open(NotificationFilterActivity::class.java)
        }
        addTile(view, R.id.phoneGrid, R.drawable.ic_info, "Logs") {
            open(LogViewerActivity::class.java)
        }
        addTile(view, R.id.phoneGrid, R.drawable.ic_info, "About") {
            open(AboutActivity::class.java)
        }
    }

    private fun addTile(view: View, gridId: Int, icon: Int, label: String, tag: String? = null, action: () -> Unit = {}) {
        val grid = view.findViewById<GridLayout>(gridId)
        val tile = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_feature_tile, grid, false)
        tile.findViewById<ImageView>(R.id.tileIcon).setImageResource(icon)
        tile.findViewById<TextView>(R.id.tileLabel).text = label
        if (tag != null) tile.tag = tag
        tile.setOnClickListener { action() }
        grid.addView(tile)
    }
}
