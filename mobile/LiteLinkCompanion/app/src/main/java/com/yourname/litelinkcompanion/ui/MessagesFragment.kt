package com.yourname.litelinkcompanion.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.yourname.litelinkcompanion.R
import com.yourname.litelinkcompanion.commands.CommandBridge
import com.yourname.litelinkcompanion.commands.MpesaModule
import com.yourname.litelinkcompanion.commands.QuickReplyModule
import com.yourname.litelinkcompanion.commands.SmsArchiveModule
import org.json.JSONObject

/** SMS, M-PESA transactions, and quick replies. */
class MessagesFragment : Fragment() {

    private var logView: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_messages, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logView = view.findViewById(R.id.messagesLog)
        val app = requireContext().applicationContext

        view.findViewById<Button>(R.id.loadSmsButton).setOnClickListener {
            val list = SmsArchiveModule.listRecentSms(app, 30)
            if (list.isEmpty()) {
                log("No SMS (grant SMS permission first)")
            } else {
                log(list.joinToString("\n") { s ->
                    val who = s.optString("person").ifBlank { s.optString("address") }
                    "${s.optString("date")}  $who\n    ${s.optString("body")}"
                })
            }
        }

        view.findViewById<Button>(R.id.mpesaButton).setOnClickListener {
            val list = MpesaModule.listTransactions(app, 30)
            if (list.isEmpty()) {
                log("No M-PESA transactions found")
            } else {
                log(list.joinToString("\n") { t ->
                    val amt = t.optDouble("amount", 0.0)
                    val peer = t.optString("peer")
                    "${t.optString("date")}  ${t.optString("type")}  KES $amt  $peer"
                })
            }
        }

        val phoneInput = view.findViewById<EditText>(R.id.replyPhoneInput)
        val textInput = view.findViewById<EditText>(R.id.replyTextInput)

        view.findViewById<Button>(R.id.sendReplyButton).setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            val msg = textInput.text.toString().trim()
            if (phone.isEmpty() || msg.isEmpty()) {
                toast("Enter phone + message")
                return@setOnClickListener
            }
            val err = QuickReplyModule.sendSms(app, phone, msg)
            if (err == null) log("Sent to $phone") else toast(err)
        }

        view.findViewById<Button>(R.id.replyLastButton).setOnClickListener {
            val phone = CommandBridge.lastSender
            val msg = textInput.text.toString().trim()
            if (phone.isNullOrEmpty()) {
                toast("No last sender recorded yet")
                return@setOnClickListener
            }
            if (msg.isEmpty()) {
                toast("Enter a message first")
                return@setOnClickListener
            }
            val err = QuickReplyModule.sendSms(app, phone, msg)
            if (err == null) log("Replied to $phone") else toast(err)
        }
    }

    private fun log(text: String) {
        logView?.text = text
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
