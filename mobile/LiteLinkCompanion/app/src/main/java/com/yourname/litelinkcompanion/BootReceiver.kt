package com.yourname.litelinkcompanion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("litelink", Context.MODE_PRIVATE)
            if (prefs.getBoolean("enabled", false)) {
                val host = prefs.getString("host", "").orEmpty()
                if (host.isNotBlank()) {
                    ConnectionService.start(context, host)
                }
            }
        }
    }
}
