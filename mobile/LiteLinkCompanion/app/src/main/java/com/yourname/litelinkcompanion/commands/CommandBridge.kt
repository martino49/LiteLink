package com.yourname.litelinkcompanion.commands

import org.json.JSONObject

/**
 * Lets non-command code (SMS receiver, notification listener, sync watchers)
 * push events/errors/uploads to the desktop without holding a WebSocket ref.
 * Wired up by ConnectionService.
 */
object CommandBridge {

    /** Most recent SMS sender (set by the SMS receiver); used by qr.reply_last. */
    @Volatile
    var lastSender: String? = null

    @Volatile
    var sendResult: ((id: String, ok: Boolean, data: JSONObject) -> Unit)? = null

    @Volatile
    var sendEvent: ((name: String, data: JSONObject) -> Unit)? = null

    @Volatile
    var uploadFile: ((path: String, name: String, dir: String) -> Unit)? = null

    fun sendEvent(name: String, data: JSONObject = JSONObject()) {
        sendEvent?.invoke(name, data)
    }

    fun sendResult(id: String, ok: Boolean, data: JSONObject = JSONObject()) {
        sendResult?.invoke(id, ok, data)
    }

    fun uploadFile(path: String, name: String = "", dir: String = "") {
        uploadFile?.invoke(path, name, dir)
    }
}
