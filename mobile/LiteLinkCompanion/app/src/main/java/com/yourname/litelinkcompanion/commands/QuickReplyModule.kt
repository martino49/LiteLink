package com.yourname.litelinkcompanion.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** Send SMS replies straight from the laptop. */
object QuickReplyModule {

    fun register(app: Context) {
        CommandCenter.register("qr.send") { ctx, id, params ->
            val phone = params.optString("phone")
            val message = params.optString("message")
            if (phone.isEmpty() || message.isEmpty()) {
                ctx.fail(id, "phone and message required")
                return@register
            }
            val err = sendSms(app, phone, message)
            if (err == null) ctx.reply(id, JSONObject().put("status", "sent").put("phone", phone))
            else ctx.fail(id, err)
        }

        CommandCenter.register("qr.reply_last") { ctx, id, params ->
            val message = params.optString("message")
            if (message.isEmpty()) {
                ctx.fail(id, "message required")
                return@register
            }
            val phone = CommandBridge.lastSender ?: run {
                ctx.fail(id, "no last sender recorded")
                return@register
            }
            val err = sendSms(app, phone, message)
            if (err == null) ctx.reply(id, JSONObject().put("status", "sent").put("phone", phone))
            else ctx.fail(id, err)
        }
    }

    /** Sends an SMS; returns null on success or an error string. */
    fun sendSms(app: Context, phone: String, message: String): String? {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "SEND_SMS permission not granted"
        }
        return try {
            val sms = SmsManager.getDefault()
            if (message.length <= 160) {
                sms.sendTextMessage(phone, null, message, null, null)
            } else {
                sms.sendMultipartTextMessage(phone, null, sms.divideMessage(message), null, null)
            }
            null
        } catch (e: Exception) {
            e.message ?: "send failed"
        }
    }
}
