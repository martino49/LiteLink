package com.yourname.litelinkcompanion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

class ConnectionService : Service() {

    companion object {
        private const val TAG = "LiteLink"
        private const val CHANNEL_ID = "litelink_connection"
        private const val NOTIFICATION_ID = 1
        private const val PREFS = "litelink"
        private const val KEY_HOST = "host"
        const val EXTRA_HOST = "host"

        fun start(context: Context, host: String) {
            val intent = Intent(context, ConnectionService::class.java)
            intent.putExtra(EXTRA_HOST, host)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var host: String = ""

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        host = intent?.getStringExtra(EXTRA_HOST)
            ?: getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_HOST, "").orEmpty()
        if (host.isNotBlank()) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_HOST, host).apply()
            WebSocketManager.addConnectionListener(connectionListener)
            WebSocketManager.connect(host)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        WebSocketManager.removeConnectionListener(connectionListener)
        WebSocketManager.disconnect()
        super.onDestroy()
    }

    private val connectionListener: (Boolean) -> Unit = { connected ->
        updateNotification(if (connected) "Connected to $host" else "Disconnected")
        Log.d(TAG, "Connection state: connected=$connected")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LiteLink Connection",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("LiteLink")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
