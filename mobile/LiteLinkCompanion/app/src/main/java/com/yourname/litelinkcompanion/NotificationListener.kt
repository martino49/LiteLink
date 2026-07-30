package com.yourname.litelinkcompanion

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        Log.d("LiteLink", "Notification: $pkg | $title | $text")
        // TODO: Send via WebSocketManager
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional cleanup
    }
}
