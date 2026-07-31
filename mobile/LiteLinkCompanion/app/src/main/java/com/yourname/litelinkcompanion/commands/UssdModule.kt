package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import org.json.JSONObject

/** Sends a USSD code (e.g. *544#) and streams the response text back to the laptop. */
object UssdModule {

    private const val TAG = "LiteLink.Ussd"
    private var activeUssdId: String = ""

    fun register(app: Context) {
        CommandCenter.register("ussd.send") { ctx, id, params ->
            val code = params.optString("code").trim()
            if (code.isEmpty()) {
                ctx.fail(id, "empty USSD code")
                return@register
            }
            activeUssdId = id
            send(app, code) { ok, data ->
                if (ok) CommandBridge.sendResult(activeUssdId, true, data)
                else CommandBridge.sendResult(activeUssdId, false, data)
                activeUssdId = ""
            }
        }
    }

    /**
     * Sends a USSD request and delivers the result to [onResult].
     * The result callback always runs on the main thread.
     */
    fun send(app: Context, code: String, onResult: (ok: Boolean, data: JSONObject) -> Unit) {
        if (Build.VERSION.SDK_INT < 26) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$code"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            onResult(true, JSONObject().put("status", "dialer_opened").put("code", code))
            return
        }
        try {
            val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val handler = Handler(Looper.getMainLooper())
            val callback = object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager,
                    request: String,
                    response: CharSequence
                ) {
                    onResult(true, JSONObject().apply {
                        put("code", request)
                        put("response", response.toString())
                    })
                }

                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager,
                    request: String,
                    failureCode: Int
                ) {
                    onResult(false, JSONObject().apply {
                        put("code", request)
                        put("error", "USSD failed (code $failureCode)")
                    })
                }
            }
            tm.sendUssdRequest(code, callback, handler)
        } catch (e: Exception) {
            Log.e(TAG, "sendUssdRequest failed", e)
            onResult(false, JSONObject().put("error", e.message ?: "ussd failed"))
        }
    }
}
