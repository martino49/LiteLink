package com.yourname.litelinkcompanion.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.yourname.litelinkcompanion.AboutActivity
import com.yourname.litelinkcompanion.LogViewerActivity
import com.yourname.litelinkcompanion.NotificationFilter
import com.yourname.litelinkcompanion.NotificationFilterActivity
import com.yourname.litelinkcompanion.NotificationStore
import com.yourname.litelinkcompanion.PairingActivity
import com.yourname.litelinkcompanion.R
import com.yourname.litelinkcompanion.SyncFoldersActivity

/** Settings (Screen 5): collapsible sections for connection, sync, privacy, battery, debug, about. */
class SettingsFragment : Fragment() {

    private var sectionBodies = listOf<Pair<View, View>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireContext().applicationContext

        sectionBodies = listOf<Pair<View, View>>(
            view.findViewById<View>(R.id.sectionConnectionHeader) to view.findViewById<View>(R.id.sectionConnectionBody),
            view.findViewById<View>(R.id.sectionNotificationsHeader) to view.findViewById<View>(R.id.sectionNotificationsBody),
            view.findViewById<View>(R.id.sectionSyncHeader) to view.findViewById<View>(R.id.sectionSyncBody),
            view.findViewById<View>(R.id.sectionPrivacyHeader) to view.findViewById<View>(R.id.sectionPrivacyBody),
            view.findViewById<View>(R.id.sectionBatteryHeader) to view.findViewById<View>(R.id.sectionBatteryBody),
            view.findViewById<View>(R.id.sectionDebugHeader) to view.findViewById<View>(R.id.sectionDebugBody),
            view.findViewById<View>(R.id.sectionAboutHeader) to view.findViewById<View>(R.id.sectionAboutBody)
        )
        sectionBodies.forEach { (header, body) ->
            header.setOnClickListener {
                body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        // Connection
        view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.settingsIpInput)
            .setText(app.getSharedPreferences("litelink", Context.MODE_PRIVATE).getString("host", "").orEmpty())
        view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.settingsVpsHostInput)
            .setText(app.getSharedPreferences("litelink", Context.MODE_PRIVATE).getString("vps_host", "").orEmpty())
        view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.settingsVpsPortInput)
            .setText(app.getSharedPreferences("litelink", Context.MODE_PRIVATE).getString("vps_port", "8765"))
        val modeButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.settingsModeButton)
        val modeMenu = android.widget.PopupMenu(requireContext(), modeButton)
        for (m in arrayOf("Local", "Cloud")) modeMenu.menu.add(m)
        modeMenu.setOnMenuItemClickListener { item ->
            modeButton.text = item.title
            app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
                .edit().putString("mode", item.title.toString().lowercase()).apply()
            true
        }
        modeButton.text = app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
            .getString("mode", "local").orEmpty().replaceFirstChar { it.uppercase() }
        modeButton.setOnClickListener { modeMenu.show() }
        view.findViewById<View>(R.id.settingsPairedDevicesButton).setOnClickListener {
            startActivity(Intent(requireContext(), PairingActivity::class.java))
        }

        // Notifications
        view.findViewById<SwitchMaterial>(R.id.settingsMirrorSwitch).apply {
            isChecked = NotificationFilter.enabled(app)
            setOnCheckedChangeListener { _, checked -> NotificationFilter.setEnabled(app, checked) }
        }
        view.findViewById<SwitchMaterial>(R.id.settingsFullTextSwitch).apply {
            isChecked = NotificationFilter.fullText(app)
            setOnCheckedChangeListener { _, checked -> NotificationFilter.setFullText(app, checked) }
        }
        view.findViewById<View>(R.id.settingsFilterAppsButton).setOnClickListener {
            startActivity(Intent(requireContext(), NotificationFilterActivity::class.java))
        }

        // Folder sync
        view.findViewById<SwitchMaterial>(R.id.settingsSyncSwitch).apply {
            isChecked = app.getSharedPreferences("litelink", Context.MODE_PRIVATE).getBoolean("sync_enabled", true)
            setOnCheckedChangeListener { _, checked ->
                app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
                    .edit().putBoolean("sync_enabled", checked).apply()
            }
        }
        val intervalButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.settingsSyncIntervalButton)
        val intervalMenu = android.widget.PopupMenu(requireContext(), intervalButton)
        for (i in arrayOf("5s", "30s", "60s", "5m")) intervalMenu.menu.add(i)
        intervalMenu.setOnMenuItemClickListener { item ->
            intervalButton.text = item.title
            app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
                .edit().putString("sync_interval", item.title.toString()).apply()
            true
        }
        intervalButton.text = app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
            .getString("sync_interval", "30s").orEmpty()
        intervalButton.setOnClickListener { intervalMenu.show() }
        view.findViewById<View>(R.id.settingsManageFoldersButton).setOnClickListener {
            startActivity(Intent(requireContext(), SyncFoldersActivity::class.java))
        }

        // Privacy
        view.findViewById<SwitchMaterial>(R.id.settingsEncryptionSwitch).apply {
            isChecked = app.getSharedPreferences("litelink", Context.MODE_PRIVATE).getBoolean("encryption", true)
            setOnCheckedChangeListener { _, checked ->
                app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
                    .edit().putBoolean("encryption", checked).apply()
            }
        }
        view.findViewById<TextView>(R.id.settingsDeviceId).text = "Device ID: ${deviceId(app)}"
        view.findViewById<View>(R.id.settingsPairingCodeButton).setOnClickListener {
            val code = app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
                .getString("pairing_code", "—").orEmpty()
            AlertDialog.Builder(requireContext())
                .setTitle("Pairing code")
                .setMessage("Enter this $code-digit code on your desktop to pair:\n\n$code")
                .setPositiveButton("OK", null)
                .setNegativeButton("Copy") { _, _ ->
                    (requireContext().getSystemService(Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager).setPrimaryClip(
                        android.content.ClipData.newPlainText("pairing", code)
                    )
                    Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        // Battery
        view.findViewById<SwitchMaterial>(R.id.settingsBatterySaverSwitch).apply {
            isChecked = app.getSharedPreferences("litelink", Context.MODE_PRIVATE).getBoolean("battery_saver", false)
            setOnCheckedChangeListener { _, checked ->
                app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
                    .edit().putBoolean("battery_saver", checked).apply()
            }
        }
        val lowBatteryButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.settingsLowBatteryButton)
        val lowBatteryMenu = android.widget.PopupMenu(requireContext(), lowBatteryButton)
        for (v in arrayOf("5%", "10%", "15%", "20%")) lowBatteryMenu.menu.add(v)
        lowBatteryMenu.setOnMenuItemClickListener { item ->
            lowBatteryButton.text = item.title
            app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
                .edit().putString("low_battery_alert", item.title.toString()).apply()
            true
        }
        lowBatteryButton.text = app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
            .getString("low_battery_alert", "15%").orEmpty()
        lowBatteryButton.setOnClickListener { lowBatteryMenu.show() }

        // Debug
        view.findViewById<SwitchMaterial>(R.id.settingsVerboseSwitch).apply {
            isChecked = app.getSharedPreferences("litelink", Context.MODE_PRIVATE).getBoolean("verbose_logging", false)
            setOnCheckedChangeListener { _, checked ->
                app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
                    .edit().putBoolean("verbose_logging", checked).apply()
            }
        }
        view.findViewById<View>(R.id.settingsViewLogsButton).setOnClickListener {
            startActivity(Intent(requireContext(), LogViewerActivity::class.java))
        }

        // About
        val version = try {
            "v" + requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) { "v1.0.0" }
        view.findViewById<TextView>(R.id.settingsVersion).text = version
        view.findViewById<View>(R.id.settingsAboutButton).setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
        view.findViewById<View>(R.id.settingsResetButton).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset all settings?")
                .setMessage("All settings will be reset to default.")
                .setPositiveButton("Reset") { _, _ ->
                    NotificationFilter.setEnabled(app, true)
                    NotificationFilter.setFullText(app, true)
                    NotificationFilter.setBlockSystem(app, false)
                    NotificationFilter.setBlockedApps(app, emptySet())
                    NotificationStore.clear(app)
                    Toast.makeText(requireContext(), "Settings reset", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun deviceId(app: Context): String {
        val key = "device_id"
        val prefs = app.getSharedPreferences("litelink", Context.MODE_PRIVATE)
        prefs.getString(key, null)?.let { return it }
        val id = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeLast(6)?.uppercase()?.chunked(3)?.joinToString("-") ?: "UNKNOWN"
        prefs.edit().putString(key, id).apply()
        return id
    }
}
