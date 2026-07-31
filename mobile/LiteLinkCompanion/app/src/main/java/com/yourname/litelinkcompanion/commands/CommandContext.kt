package com.yourname.litelinkcompanion.commands

import android.content.Context
import org.json.JSONObject

/** Runtime context handed to every command handler. */
class CommandContext(
    val app: Context,
    /** Reply to a specific command request ({"type":"cmd_result","id":...}). */
    val sendResult: (id: String, ok: Boolean, data: JSONObject) -> Unit,
    /** Push an unsolicited event to the desktop. */
    val sendEvent: (String, JSONObject) -> Unit,
    /** Called by file-heavy modules to upload a local file to the desktop. */
    val uploadFile: (path: String, name: String, dir: String) -> Unit,
) {

    fun getSystemService(name: String): Any? = app.getSystemService(name)

    /** Shortcut: reply success with data. */
    fun reply(id: String, data: JSONObject = JSONObject()) {
        sendResult(id, true, data)
    }

    /** Shortcut: reply failure with an error string. */
    fun fail(id: String, error: String) {
        sendResult(id, false, JSONObject().put("error", error))
    }
}
