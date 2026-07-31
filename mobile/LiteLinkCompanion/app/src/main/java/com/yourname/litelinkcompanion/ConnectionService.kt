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
import com.yourname.litelinkcompanion.commands.CommandBridge
import com.yourname.litelinkcompanion.commands.CommandCenter
import com.yourname.litelinkcompanion.commands.CommandContext
import com.yourname.litelinkcompanion.commands.FileTransferModule

class ConnectionService : Service() {

    companion object {
        private const val TAG = "LiteLink"
        private const val CHANNEL_ID = "litelink_connection"
        private const val NOTIFICATION_ID = 1
        private const val PREFS = "litelink"
        private const val KEY_HOST = "host"
        private const val KEY_CODE = "pairing_code"
        private const val KEY_ENABLED = "enabled"
        const val EXTRA_HOST = "host"
        const val EXTRA_CODE = "code"

        fun start(context: Context, host: String, code: String = "") {
            val intent = Intent(context, ConnectionService::class.java)
            intent.putExtra(EXTRA_HOST, host)
            intent.putExtra(EXTRA_CODE, code)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }

    private var host: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WebSocketManager.init(this)
        setupCommandLayer()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
    }

    /** Registers the 24-feature command center and bridges events/results/uploads. */
    private fun setupCommandLayer() {
        val ctx = CommandContext(
            app = this,
            sendResult = { id, ok, data -> WebSocketManager.sendCommandResult(id, ok, data) },
            sendEvent = { name, data -> WebSocketManager.sendEvent(name, data) },
            uploadFile = { path, name, dir -> FileTransferModule.uploadFile(this, path, name, dir) }
        )
        CommandCenter.registerAll(this, ctx)
        CommandBridge.sendResult = ctx.sendResult
        CommandBridge.sendEvent = ctx.sendEvent
        CommandBridge.uploadFile = ctx.uploadFile
        WebSocketManager.onCommand = { cmd, id, params -> CommandCenter.dispatch(ctx, cmd, id, params) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        host = intent?.getStringExtra(EXTRA_HOST) ?: prefs.getString(KEY_HOST, "").orEmpty()
        val code = intent?.getStringExtra(EXTRA_CODE).orEmpty()
        if (code.isNotBlank()) {
            prefs.edit().putString(KEY_CODE, code).apply()
        }
        if (host.isNotBlank()) {
            prefs.edit().putString(KEY_HOST, host).putBoolean(KEY_ENABLED, true).apply()
            WebSocketManager.addConnectionListener(connectionListener)
            // Try Wi-Fi host first, fall back to localhost (adb reverse over USB)
            WebSocketManager.connect(listOf(host, "localhost"), code)
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
