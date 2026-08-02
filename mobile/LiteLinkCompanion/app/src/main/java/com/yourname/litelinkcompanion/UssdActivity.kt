package com.yourname.litelinkcompanion

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yourname.litelinkcompanion.commands.UssdModule
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** USSD Shortcut (Screen 19): quick codes, custom send, favorites, response, history. */
class UssdActivity : AppCompatActivity() {

    private val main = Handler(Looper.getMainLooper())
    private val prefs get() = getSharedPreferences("litelink", Context.MODE_PRIVATE)

    private val quickCodes = listOf(
        "*544#" to "Check balance (M-Pesa)",
        "*455#" to "M-Pesa menu",
        "*100#" to "Airtime balance",
        "*200#" to "Customer care",
        "*150#" to "Safaricom offers"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ussd)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.ussdToolbar)
            .setNavigationOnClickListener { finish() }

        renderQuickCodes()
        renderHistory()

        findViewById<View>(R.id.ussdSendButton).setOnClickListener { send() }
        findViewById<View>(R.id.ussdSaveButton).setOnClickListener { saveFavorite() }
    }

    private fun renderQuickCodes() {
        val container = findViewById<LinearLayout>(R.id.ussdQuickContainer)
        container.removeAllViews()
        val favorites = favorites()
        val all = favorites + quickCodes.filter { (code, _) -> favorites.none { it.first == code } }
        all.forEachIndexed { index, (code, label) ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_ussd_quick, container, false)
            row.findViewById<TextView>(R.id.ussdQuickCode).text = code
            row.findViewById<TextView>(R.id.ussdQuickLabel).text = label
            row.setOnClickListener {
                findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.ussdInput).setText(code)
            }
            container.addView(row)
            if (index < all.lastIndex) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { marginStart = 56 }
                    setBackgroundColor(getColor(R.color.pill_grey))
                }
                container.addView(divider)
            }
        }
    }

    private fun favorites(): List<Pair<String, String>> {
        val raw = prefs.getString("ussd_favorites", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.optString("code") to o.optString("label")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFavorite() {
        val code = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.ussdInput)
            .text.toString().trim()
        if (code.isEmpty()) {
            toast("Enter a USSD code")
            return
        }
        val current = favorites().toMutableList()
        current.removeAll { it.first == code }
        current.add(0, code to "Favorite")
        val arr = JSONArray()
        current.forEach { arr.put(org.json.JSONObject().put("code", it.first).put("label", it.second)) }
        prefs.edit().putString("ussd_favorites", arr.toString()).apply()
        renderQuickCodes()
        toast("Saved $code")
    }

    private fun send() {
        val code = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.ussdInput)
            .text.toString().trim()
        if (code.isEmpty()) {
            toast("Enter a USSD code")
            return
        }
        findViewById<TextView>(R.id.ussdResponse).text = "Sending $code..."
        addHistory(code)
        UssdModule.send(applicationContext, code) { ok, data ->
            main.post {
                findViewById<TextView>(R.id.ussdResponse).text = if (ok)
                    data.optString("response", "(no response)")
                else
                    "Failed: ${data.optString("error", "unknown error")}"
            }
        }
    }

    private fun addHistory(code: String) {
        val raw = prefs.getString("ussd_history", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val merged = JSONArray()
        merged.put(org.json.JSONObject().apply {
            put("code", code)
            put("time", System.currentTimeMillis())
        })
        for (i in 0 until arr.length()) merged.put(arr.getJSONObject(i))
        val kept = JSONArray()
        for (i in 0 until minOf(10, merged.length())) kept.put(merged.getJSONObject(i))
        prefs.edit().putString("ussd_history", kept.toString()).apply()
        renderHistory()
    }

    private fun renderHistory() {
        val raw = prefs.getString("ussd_history", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        if (arr.length() == 0) {
            findViewById<TextView>(R.id.ussdHistory).text = "No history yet"
            return
        }
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
        findViewById<TextView>(R.id.ussdHistory).text = (0 until arr.length())
            .joinToString("\n") { i ->
                val o = arr.getJSONObject(i)
                "${o.optString("code")}  ·  ${fmt.format(Date(o.optLong("time")))}"
            }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
