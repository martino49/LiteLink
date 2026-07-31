package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * Execute a scripted sequence of actions on the phone.
 * Taps/swipes/input need a rooted device or an accessibility service; the
 * non-root subset (open app, wait, clipboard, ussd, notification) always works.
 */
object MacroModule {

    fun register(app: Context) {
        CommandCenter.register("macro.exec") { ctx, id, params ->
            val actions = params.optJSONArray("actions") ?: JSONArray()
            if (actions.length() == 0) {
                ctx.fail(id, "empty actions list")
                return@register
            }
            var result = JSONObject().put("executed", 0)
            var error: String? = null
            for (i in 0 until actions.length()) {
                val a = actions.optJSONObject(i) ?: continue
                try {
                    if (!runAction(app, ctx, id, a)) {
                        error = "action ${a.optString("action")} unsupported on this device"
                        break
                    }
                    result.put("executed", result.optInt("executed") + 1)
                } catch (e: Exception) {
                    error = "action ${a.optString("action")} failed: ${e.message}"
                    break
                }
            }
            if (error != null) ctx.fail(id, error) else ctx.reply(id, result)
        }
    }

    private fun runAction(app: Context, ctx: CommandContext, id: String, a: JSONObject): Boolean {
        return when (a.optString("action")) {
            "wait" -> {
                val ms = a.optLong("ms", 1000).coerceIn(0, 60000)
                Thread.sleep(ms)
                true
            }
            "open" -> {
                val pkg = a.optString("package")
                if (pkg.isEmpty()) return false
                val intent = app.packageManager.getLaunchIntentForPackage(pkg)
                    ?: return false
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(intent)
                true
            }
            "clipboard.set" -> {
                val text = a.optString("text")
                val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("litelink", text))
                true
            }
            "ussd" -> {
                val code = a.optString("code")
                if (code.isEmpty()) return false
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$code"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(intent)
                true
            }
            "notification" -> {
                val title = a.optString("title", "LiteLink")
                val text = a.optString("text")
                val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val builder = android.app.Notification.Builder(app)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                nm.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
                true
            }
            "tap", "swipe", "input", "key" -> shellInput(a)
            else -> false
        }
    }

    /** Best-effort input via the `input` shell command (requires root or shell access). */
    private fun shellInput(a: JSONObject): Boolean {
        val cmd = when (a.optString("action")) {
            "tap" -> "input tap ${a.optInt("x")} ${a.optInt("y")}"
            "swipe" -> "input swipe ${a.optInt("x1")} ${a.optInt("y1")} ${a.optInt("x2")} ${a.optInt("y2")} ${a.optLong("ms", 200)}"
            "input" -> "input text ${a.optString("text")}"
            "key" -> "input keyevent ${a.optInt("code")}"
            else -> return false
        }
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            p.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
