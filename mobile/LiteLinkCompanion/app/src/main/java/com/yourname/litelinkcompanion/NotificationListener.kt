package com.yourname.litelinkcompanion

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yourname.litelinkcompanion.commands.CommandCenter
import com.yourname.litelinkcompanion.commands.MpesaModule

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        Log.d("LiteLink", "Notification: $pkg | $title | $text")

        // Feed SMS-app notifications through the M-Pesa parser as a fallback
        // when the SMS receiver isn't granted the READ_SMS permission.
        MpesaModule.ingestNotificationBody((title + " " + text).trim())

        val app = CommandCenter.getContext()
        if (app != null) {
            WebSocketManager.sendNotification(appLabel(pkg), title, text)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional cleanup
    }

    private fun appLabel(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkg
        }
    }
}
