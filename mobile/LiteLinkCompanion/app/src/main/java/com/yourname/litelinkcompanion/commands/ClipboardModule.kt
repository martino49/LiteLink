package com.yourname.litelinkcompanion.commands

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.json.JSONObject

/** Read/set the phone clipboard and stream clipboard changes to the desktop. */
object ClipboardModule {

    private var monitoring = false

    fun register(app: Context) {
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        CommandCenter.register("clipboard.get") { ctx, id, _ ->
            val clip = cm.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(app)?.toString() ?: ""
            ctx.reply(id, JSONObject().put("text", text))
        }

        CommandCenter.register("clipboard.set") { ctx, id, params ->
            val text = params.optString("text")
            cm.setPrimaryClip(ClipData.newPlainText("litelink", text))
            CommandBridge.sendEvent("clipboard.change", JSONObject().put("text", text))
            ctx.reply(id, JSONObject().put("status", "ok").put("text", text))
        }

        CommandCenter.register("clipboard.monitor") { ctx, id, params ->
            val enabled = params.optBoolean("enabled", true)
            if (enabled && !monitoring) {
                monitoring = true
                cm.addPrimaryClipChangedListener(listener)
                ctx.reply(id, JSONObject().put("status", "monitoring"))
            } else if (!enabled && monitoring) {
                monitoring = false
                cm.removePrimaryClipChangedListener(listener)
                ctx.reply(id, JSONObject().put("status", "stopped"))
            } else {
                ctx.reply(id, JSONObject().put("status", if (monitoring) "monitoring" else "stopped"))
            }
        }
    }

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        val app = CommandCenter.getContext()
        if (app == null || !monitoring) return@OnPrimaryClipChangedListener
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString() ?: return@OnPrimaryClipChangedListener
        CommandBridge.sendEvent("clipboard.change", JSONObject().put("text", text))
    }
}
