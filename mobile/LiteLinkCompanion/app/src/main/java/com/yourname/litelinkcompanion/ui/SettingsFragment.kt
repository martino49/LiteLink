package com.yourname.litelinkcompanion.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.yourname.litelinkcompanion.R

/** Permissions, special access, and about info. */
class SettingsFragment : Fragment() {

    private val perms = listOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val statusView = view.findViewById<TextView>(R.id.permissionStatus)
        val aboutView = view.findViewById<TextView>(R.id.aboutText)

        view.findViewById<Button>(R.id.requestPermsButton).setOnClickListener {
            requestRuntimePerms()
        }
        view.findViewById<Button>(R.id.notifAccessButton).setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        view.findViewById<Button>(R.id.storageAccessButton).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")
                    )
                )
            } else {
                toast("Not needed on this Android version")
            }
        }
        view.findViewById<Button>(R.id.backupSmsPermButton).setOnClickListener {
            requestRuntimePerms(perms.filterNot { it == Manifest.permission.POST_NOTIFICATIONS })
        }

        refreshStatus(statusView, aboutView)
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<TextView>(R.id.permissionStatus)?.let { refreshStatus(it, null) }
    }

    private fun refreshStatus(statusView: TextView?, aboutView: TextView?) {
        val app = requireContext().applicationContext
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(app, it) != PackageManager.PERMISSION_GRANTED
        }
        statusView?.text = if (missing.isEmpty()) {
            "All runtime permissions granted."
        } else {
            "Missing: " + missing.joinToString(", ") { it.removePrefix("android.permission.") }
        }
        aboutView?.text = "LiteLink Companion\n" +
            "Model: ${android.os.Build.MODEL}\n" +
            "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
    }

    private fun requestRuntimePerms(list: List<String> = perms) {
        val needed = list.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            toast("All permissions already granted")
            return
        }
        ActivityCompat.requestPermissions(requireActivity(), needed.toTypedArray(), 200)
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
