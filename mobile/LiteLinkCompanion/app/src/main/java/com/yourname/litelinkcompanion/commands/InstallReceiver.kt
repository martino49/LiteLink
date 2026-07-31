package com.yourname.litelinkcompanion.commands

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import org.json.JSONObject

/** Receives the PackageInstaller session result and reports it to the laptop. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: ""
        val ok = status == PackageInstaller.STATUS_SUCCESS
        CommandBridge.sendResult("install_$sessionId", ok, JSONObject().apply {
            put("status", status)
            put("package", packageName)
        })
        Toast.makeText(
            context,
            if (ok) "Installed $packageName" else "Install failed (status $status)",
            Toast.LENGTH_LONG
        ).show()
    }
}
