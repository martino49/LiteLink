package com.yourname.litelinkcompanion

import android.util.Log
import okhttp3.*
import org.json.JSONObject

class WebSocketManager(
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {}
) {
    private var ws: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun connect(host: String, port: Int = 8765) {
        val request = Request.Builder().url("ws://$host:$port").build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("LiteLink", "WebSocket connected")
                onConnected()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("LiteLink", "WebSocket error: ${t.message}")
                onDisconnected()
                // TODO: reconnect after delay
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d("LiteLink", "WebSocket closed: $reason")
                onDisconnected()
            }
        })
    }

    fun sendNotification(app: String, title: String, text: String) {
        val json = JSONObject().apply {
            put("type", "notification")
            put("app", app)
            put("title", title)
            put("text", text)
            put("timestamp", System.currentTimeMillis())
        }
        ws?.send(json.toString())
    }

    fun disconnect() {
        ws?.close(1000, "User disconnected")
        ws = null
    }
}
