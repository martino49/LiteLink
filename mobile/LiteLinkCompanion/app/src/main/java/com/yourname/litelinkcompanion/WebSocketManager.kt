package com.yourname.litelinkcompanion

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object WebSocketManager {

    private const val TAG = "LiteLink"
    private const val PREFS = "litelink"
    private const val KEY_CODE = "pairing_code"
    private const val KEY_FP = "cert_fingerprint"
    private const val PORT = 8765

    private var appContext: Context? = null
    private val prefs: SharedPreferences
        get() = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var client: OkHttpClient? = null
    private var ws: WebSocket? = null
    private var hosts: List<String> = emptyList()
    private var hostIndex = 0
    private var pairingCode: String = ""
    private var shouldReconnect = false
    private var paired = false
    private val reconnecting = AtomicBoolean(false)
    private var retryDelay = 5000L

    private val connectionListeners = mutableListOf<(Boolean) -> Unit>()

    /** Receives desktop commands: (command name, request id, params). */
    @Volatile
    var onCommand: ((String, String, JSONObject) -> Unit)? = null

    /** Receives incoming file chunks: (file id, offset, decoded bytes). */
    @Volatile
    var onFileChunk: ((String, Long, ByteArray) -> Unit)? = null

    /** Receives file transfer start metadata: (file id, name, size, dir, overwrite). */
    @Volatile
    var onFileStart: ((String, String, Long, String, Boolean) -> Unit)? = null

    /** Receives file transfer completion for a file id. */
    @Volatile
    var onFileDone: ((String) -> Unit)? = null

    @get:Synchronized
    var isConnected: Boolean = false
        private set

    @get:Synchronized
    val currentHost: String?
        get() = hosts.getOrNull(hostIndex)

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

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
    fun connect(hosts: List<String>, code: String = "") {
        this.hosts = hosts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (this.hosts.isEmpty()) return

        if (code.isNotBlank()) {
            pairingCode = code
            prefs.edit().putString(KEY_CODE, code).apply()
        } else {
            pairingCode = prefs.getString(KEY_CODE, "").orEmpty()
        }

        hostIndex = 0
        shouldReconnect = true
        paired = false
        retryDelay = 5000L
        closeCurrent()
        openSocket()
    }

    @Synchronized
    fun disconnect() {
        shouldReconnect = false
        reconnecting.set(false)
        closeCurrent()
        setConnected(false)
    }

    @Synchronized
    private fun closeCurrent() {
        ws?.close(1000, "Disconnected")
        ws = null
    }

    private fun openSocket() {
        val host = hosts[hostIndex]
        val request = Request.Builder().url("wss://$host:$PORT").build()
        val c = client ?: buildClient().also { client = it }
        ws = c.newWebSocket(request, listener)
        Log.d(TAG, "Connecting wss://$host:$PORT")
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket open, sending pairing code")
            val json = JSONObject().put("type", "pair").put("code", pairingCode)
            webSocket.send(json.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val data = JSONObject(text)
                when (data.optString("type")) {
                    "pair_ok" -> {
                        paired = true
                        retryDelay = 5000L
                        Log.d(TAG, "Pairing OK")
                        setConnected(true)
                    }
                    "cmd" -> {
                        val cmd = data.optString("cmd")
                        val id = data.optString("id")
                        val params = data.optJSONObject("params") ?: JSONObject()
                        onCommand?.invoke(cmd, id, params)
                    }
                    "file" -> handleFileMessage(data)
                    else -> Log.d(TAG, "Message: $text")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Message: $text")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}")
            setConnected(false)
            handleFail()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $reason")
            setConnected(false)
            handleFail()
        }
    }

    @Synchronized
    private fun handleFail() {
        if (!shouldReconnect) return
        if (hostIndex < hosts.size - 1) {
            hostIndex++
            openSocket()
        } else {
            hostIndex = 0
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!reconnecting.compareAndSet(false, true)) return
        Thread {
            try {
                Thread.sleep(retryDelay)
            } catch (e: InterruptedException) {
                return@Thread
            }
            reconnecting.set(false)
            if (shouldReconnect) {
                Log.d(TAG, "Attempting reconnect (backoff ${retryDelay / 1000}s)")
                openSocket()
                retryDelay = (retryDelay * 2).coerceAtMost(60000L)
            }
        }.start()
    }

    private fun setConnected(value: Boolean) {
        if (isConnected != value) {
            isConnected = value
            val snapshot = synchronized(this) { connectionListeners.toList() }
            snapshot.forEach { it(value) }
        }
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

    fun sendPointer(action: String, x: Double, y: Double) {
        val params = JSONObject().put("action", action).put("x", x).put("y", y)
        sendInput("pointer", params)
    }

    fun sendScroll(dx: Double, dy: Double) {
        val params = JSONObject().put("x", dx).put("y", dy)
        sendInput("scroll", params)
    }

    fun sendKey(key: String) {
        val params = JSONObject().put("key", key)
        sendInput("key", params)
    }

    fun sendText(text: String) {
        val params = JSONObject().put("text", text)
        sendInput("text", params)
    }

    private fun sendInput(kind: String, params: JSONObject) {
        val json = JSONObject().apply {
            put("type", "input")
            put("kind", kind)
            put("params", params)
        }
        send(json)
    }

    /** Acknowledge a desktop command. */
    fun sendCommandResult(id: String, ok: Boolean, data: JSONObject = JSONObject()) {
        val json = JSONObject().apply {
            put("type", "cmd_result")
            put("id", id)
            put("ok", ok)
            put("data", data)
        }
        send(json)
    }

    /** Push an unsolicited event (battery, signal, clipboard, mpesa, sync change...). */
    fun sendEvent(name: String, data: JSONObject = JSONObject()) {
        val json = JSONObject().apply {
            put("type", "event")
            put("event", name)
            put("data", data)
        }
        send(json)
    }

    /** Open a file upload to the desktop. Returns a fresh file id. */
    fun sendFileStart(name: String, size: Long, dir: String = ""): String {
        val id = UUID.randomUUID().toString()
        val json = JSONObject().apply {
            put("type", "file")
            put("mode", "start")
            put("id", id)
            put("name", name)
            put("size", size)
            put("dir", dir)
        }
        send(json)
        return id
    }

    /** Send one chunk of a file upload. */
    fun sendFileChunk(id: String, offset: Long, bytes: ByteArray) {
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val json = JSONObject().apply {
            put("type", "file")
            put("mode", "chunk")
            put("id", id)
            put("offset", offset)
            put("b64", b64)
        }
        send(json)
    }

    /** Signal that a file upload is complete. */
    fun sendFileDone(id: String, ok: Boolean = true, error: String = "") {
        val json = JSONObject().apply {
            put("type", "file")
            put("mode", "done")
            put("id", id)
            put("ok", ok)
            if (error.isNotBlank()) put("error", error)
        }
        send(json)
    }

    private fun handleFileMessage(data: JSONObject) {
        val mode = data.optString("mode")
        val id = data.optString("id")
        when (mode) {
            "start" -> {
                val name = data.optString("name")
                val size = data.optLong("size", 0)
                val dir = data.optString("dir", "")
                val overwrite = data.optBoolean("overwrite", true)
                onFileStart?.invoke(id, name, size, dir, overwrite)
            }
            "chunk" -> {
                val offset = data.optLong("offset", 0)
                val b64 = data.optString("b64")
                val bytes = try {
                    Base64.decode(b64, Base64.NO_WRAP)
                } catch (e: Exception) {
                    ByteArray(0)
                }
                onFileChunk?.invoke(id, offset, bytes)
            }
            "done" -> onFileDone?.invoke(id)
        }
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

    private fun buildClient(): OkHttpClient {
        val tls = buildTls()
        val builder = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
        if (tls != null) {
            builder.sslSocketFactory(tls.first, tls.second)
                .hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    private fun buildTls(): Pair<SSLSocketFactory, X509TrustManager>? {
        return try {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    val digest = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
                    val fp = digest.joinToString("") { "%02x".format(it) }
                    val expected = prefs.getString(KEY_FP, "").orEmpty()
                    if (expected.isBlank()) {
                        prefs.edit().putString(KEY_FP, fp).apply()
                        Log.d(TAG, "Recorded cert fingerprint (TOFU): $fp")
                    } else if (!expected.equals(fp, ignoreCase = true)) {
                        throw CertificateException("LiteLink cert fingerprint mismatch")
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
            Pair(sslContext.socketFactory, trustManager)
        } catch (e: Exception) {
            Log.e(TAG, "TLS init failed", e)
            null
        }
    }
}
