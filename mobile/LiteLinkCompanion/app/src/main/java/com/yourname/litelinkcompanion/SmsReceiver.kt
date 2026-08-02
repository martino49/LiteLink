package com.yourname.litelinkcompanion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.yourname.litelinkcompanion.commands.CommandBridge
import com.yourname.litelinkcompanion.commands.MpesaModule

/** Captures incoming SMS: forwards to the PC, parses M-Pesa, stores for quick replies. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val first = messages.firstOrNull() ?: return
        val sender = first.originatingAddress ?: ""
        val body = messages.joinToString("") { it.messageBody ?: "" }
        if (sender.isNotBlank()) CommandBridge.lastSender = sender
        MpesaModule.ingestMessage(context, body)

        // Mirror the SMS to the PC as a notification (same as app notifications).
        if (WebSocketManager.isConnected) {
            WebSocketManager.sendNotification("SMS", sender.ifBlank { "Message" }, body)
        }
    }
}
