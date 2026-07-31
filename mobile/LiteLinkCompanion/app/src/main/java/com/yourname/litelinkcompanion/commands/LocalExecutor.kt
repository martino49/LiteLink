package com.yourname.litelinkcompanion.commands

import android.content.Context
import org.json.JSONObject

/**
 * Runs a registered command handler locally (no WebSocket round-trip) so the
 * in-app pages can reuse the exact same feature code. Results come back via
 * the callback; the callback may fire on a background thread.
 */
object LocalExecutor {

    fun run(app: Context, cmd: String, params: JSONObject = JSONObject(), onResult: (ok: Boolean, data: JSONObject) -> Unit) {
        val ctx = CommandContext(
            app = app,
            sendResult = { _, ok, data -> onResult(ok, data) },
            sendEvent = { _, _ -> },
            uploadFile = { path, name, dir -> FileTransferModule.uploadFile(app, path, name, dir) }
        )
        CommandCenter.dispatch(ctx, cmd, "local", params)
    }
}
