package com.yourname.litelinkcompanion

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object WebSocketManager {

    private const val TAG = "LiteLink"

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var host: String = ""
    private var port: Int = 8765
    private var shouldReconnect = false
    private val reconnecting = AtomicBoolean(false)
    private var retryDelay = 5000L

    private val connectionListeners = mutableListOf<(Boolean) -> Unit>()

    @get:Synchronized
    var isConnected: Boolean = false
        private set

    @Synchronized
    fun addConnectionListener(listener: (Boolean) -> Unit) {
        if (!connectionListeners.contains(listener)) {
            connectionListeners.add(listener)
        }
    }

    @Synchronized
    fun removeConnectionListener(listener: (Boolean) -> Unit) {
        connectionListeners.remove(listener)
    }

    @Synchronized
    fun connect(host: String, port: Int = 8765) {
        this.host = host
        this.port = port
        shouldReconnect = true
        retryDelay = 5000L
        ws?.close(1000, "Reconnecting")
        ws = null
        openSocket()
    }

    @Synchronized
    fun disconnect() {
        shouldReconnect = false
        reconnecting.set(false)
        ws?.close(1000, "User disconnected")
        ws = null
        setConnected(false)
    }

    fun sendNotification(app: String, title: String, text: String) {
        val json = JSONObject().apply {
            put("type", "notification")
            put("app", app)
            put("title", title)
            put("text", text)
            put("timestamp", System.currentTimeMillis())
        }
        send(json)
    }

    @Synchronized
    private fun send(json: JSONObject): Boolean {
        val socket = ws
        if (socket == null || !isConnected) {
            Log.d(TAG, "Not connected, dropping message: ${json.optString("type")}")
            return false
        }
        return socket.send(json.toString())
    }

    private fun openSocket() {
        val request = Request.Builder().url("ws://$host:$port").build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected to $host:$port")
                retryDelay = 5000L
                setConnected(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message: $text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                setConnected(false)
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                setConnected(false)
                scheduleReconnect()
            }
        })
    }

    private fun setConnected(value: Boolean) {
        if (isConnected != value) {
            isConnected = value
            val snapshot = synchronized(this) { connectionListeners.toList() }
            snapshot.forEach { it(value) }
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        if (!reconnecting.compareAndSet(false, true)) return
        Thread {
            try {
                Thread.sleep(retryDelay)
            } catch (e: InterruptedException) {
                return@Thread
            }
            reconnecting.set(false)
            if (shouldReconnect) {
                Log.d(TAG, "Attempting reconnect to $host:$port")
                openSocket()
                retryDelay = (retryDelay * 2).coerceAtMost(60000L)
            }
        }.start()
    }
}
