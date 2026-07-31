package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray

/**
 * Scans M-Pesa SMS and parses transactions into structured records.
 * Also exposes ingestMessage() for live parsing of incoming SMS.
 */
object MpesaModule {

    fun register(app: Context) {
        CommandCenter.register("mpesa.parse") { ctx, id, _ ->
            if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ctx.fail(id, "READ_SMS permission not granted")
                return@register
            }
            val txns = mutableListOf<JSONObject>()
            val uri = Uri.parse("content://sms/inbox")
            val cursor: Cursor? = app.contentResolver.query(uri, null, null, null, "date DESC")
            cursor?.use {
                while (it.moveToNext()) {
                    val body = it.getString(it.getColumnIndexOrThrow("body")).orEmpty()
                    if (!matches(body)) continue
                    val parsed = parse(body)
                    if (parsed != null) {
                        val date = it.getLong(it.getColumnIndexOrThrow("date"))
                        parsed.put("date_ms", date)
                        parsed.put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(date)))
                        txns.add(parsed)
                    }
                }
            }
            ctx.reply(id, JSONObject().put("transactions", JSONArray(txns)))
        }

        CommandCenter.register("mpesa.balance") { ctx, id, _ ->
            // Latest M-Pesa balance from the most recent matching SMS.
            if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ctx.fail(id, "READ_SMS permission not granted")
                return@register
            }
            val uri = Uri.parse("content://sms/inbox")
            val cursor: Cursor? = app.contentResolver.query(uri, null, null, null, "date DESC LIMIT 200")
            var balance: Double? = null
            var lastBody: String? = null
            cursor?.use {
                while (it.moveToNext()) {
                    val body = it.getString(it.getColumnIndexOrThrow("body")).orEmpty()
                    if (matches(body)) {
                        val m = Regex("balance is KES\\s*([\\d,]+(?:\\.\\d+)?)").find(body)
                        if (m != null) {
                            balance = cleanAmount(m.groupValues[1])
                            lastBody = body
                            break
                        }
                    }
                }
            }
            ctx.reply(id, JSONObject().apply {
                put("balance", balance ?: JSONObject.NULL)
                put("raw", lastBody ?: "")
            })
        }
    }

    /** Parsed M-Pesa transactions from the inbox for local UI display. */
    fun listTransactions(app: Context, limit: Int = 30): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return out
        }
        val uri = Uri.parse("content://sms/inbox")
        val cursor: Cursor? = app.contentResolver.query(uri, null, null, null, "date DESC LIMIT 500")
        cursor?.use {
            while (it.moveToNext() && out.size < limit) {
                val body = it.getString(it.getColumnIndexOrThrow("body")).orEmpty()
                if (!matches(body)) continue
                val parsed = parse(body) ?: continue
                val date = it.getLong(it.getColumnIndexOrThrow("date"))
                parsed.put("date_ms", date)
                parsed.put(
                    "date",
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(date))
                )
                out.add(parsed)
            }
        }
        return out
    }

    /** Called on every incoming SMS; emits an event when an M-Pesa transaction is detected. */
    fun ingestMessage(app: Context, body: String) {
        if (!matches(body)) return
        val parsed = parse(body)
        if (parsed != null) {
            parsed.put("date_ms", System.currentTimeMillis())
            CommandBridge.sendEvent("mpesa.transaction", parsed)
        }
    }

    /** Parses a raw M-Pesa SMS and emits it if recognized (for NotificationListener). */
    fun ingestNotificationBody(body: String) {
        if (!matches(body)) return
        val parsed = parse(body) ?: return
        parsed.put("date_ms", System.currentTimeMillis())
        CommandBridge.sendEvent("mpesa.transaction", parsed)
    }

    private fun matches(body: String): Boolean {
        return body.contains("M-PESA", ignoreCase = true) && (
            body.contains("KES", ignoreCase = true)
                || body.contains("sent", ignoreCase = true)
                || body.contains("received", ignoreCase = true)
                || body.contains("reversed", ignoreCase = true)
            )
    }

    internal fun parse(body: String): JSONObject? {
        val result = JSONObject()
        val amountMatch = Regex("KES\\s*([\\d,]+(?:\\.\\d+)?)").find(body) ?: return null
        result.put("amount", cleanAmount(amountMatch.groupValues[1]))

        val type = when {
            Regex("(you have|confirmed\\s*\\.?.?\\s*)?sent").containsMatchIn(body) -> "sent"
            Regex("(has received|you have received|received from)").containsMatchIn(body) -> "received"
            body.contains("reversed", ignoreCase = true) -> "reversed"
            body.contains("withdrawn", ignoreCase = true) || body.contains("withdrawal", ignoreCase = true) -> "withdrawn"
            body.contains("deposited", ignoreCase = true) || body.contains("deposit", ignoreCase = true) -> "deposited"
            else -> "unknown"
        }
        result.put("type", type)

        // Peer: "to JOHN DOE", "from 254712345678", "to JOHN DOE on ..."
        var peer = ""
        Regex("(?:to|from)\\s+([A-Za-z0-9 .'&\\-]+?)(?=\\s+(?:on|at|for|KES|balance|$))").find(body)?.let {
            peer = it.groupValues[1].trim()
        }
        if (peer.isBlank()) {
            Regex("([A-Za-z]+\\s+[A-Za-z]+)(?=\\s+(?:on|at|has|for))").find(body)?.let {
                peer = it.groupValues[1].trim()
            }
        }
        result.put("peer", peer)

        val bal = Regex("balance is KES\\s*([\\d,]+(?:\\.\\d+)?)").find(body)
        result.put("balance", bal?.let { cleanAmount(it.groupValues[1]) } ?: JSONObject.NULL)

        val ref = Regex("Ref\\s*([A-Za-z0-9]+)").find(body)
        result.put("ref", ref?.groupValues?.getOrNull(1) ?: "")
        result.put("raw", body.take(500))
        return result
    }

    private fun cleanAmount(s: String): Double = s.replace(",", "").toDoubleOrNull() ?: 0.0
}
