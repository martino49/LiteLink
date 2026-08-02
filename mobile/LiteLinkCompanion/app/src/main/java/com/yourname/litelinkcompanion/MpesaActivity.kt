package com.yourname.litelinkcompanion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.yourname.litelinkcompanion.commands.MpesaModule
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

/** M-Pesa Tracker (Screen 10): totals, search/filter, transaction list, CSV export. */
class MpesaActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var emptyView: TextView
    private var filter = "All"
    private var query = ""
    private var txns = listOf<org.json.JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mpesa)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.mpesaToolbar)
            .setNavigationOnClickListener { finish() }

        container = findViewById(R.id.mpesaListContainer)
        emptyView = findViewById(R.id.mpesaEmpty)

        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.mpesaSearchInput)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    render()
                }
            })

        val filterButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.mpesaFilterButton)
        val menu = android.widget.PopupMenu(this, filterButton)
        for (m in arrayOf("All", "Sent", "Received", "Reversed", "Withdrawn", "Deposited")) {
            menu.menu.add(m)
        }
        menu.setOnMenuItemClickListener { item ->
            filter = item.title.toString()
            filterButton.text = filter
            render()
            true
        }
        filterButton.setOnClickListener { menu.show() }

        findViewById<View>(R.id.mpesaSyncButton).setOnClickListener { load() }
        findViewById<View>(R.id.mpesaExportButton).setOnClickListener { exportCsv() }

        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            emptyView.visibility = View.VISIBLE
            emptyView.text = "SMS access needed to scan M-Pesa messages.\nTap to grant."
            emptyView.setOnClickListener {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.READ_SMS), 200
                )
            }
            container.removeAllViews()
            return
        }
        emptyView.setOnClickListener(null)
        txns = MpesaModule.listTransactions(this, 100)
        render()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200) load()
    }

    private fun render() {
        val shown = txns.filter { txn ->
            val typeMatch = filter == "All" || txn.optString("type") == filter.lowercase()
            val q = query.lowercase()
            val queryMatch = q.isEmpty() ||
                txn.optString("peer").contains(q, ignoreCase = true) ||
                txn.optString("ref").contains(q, ignoreCase = true) ||
                txn.optString("raw").contains(q, ignoreCase = true)
            typeMatch && queryMatch
        }

        val fmt = NumberFormat.getNumberInstance(Locale.US)
        var sent = 0.0
        var received = 0.0
        shown.forEach {
            when (it.optString("type")) {
                "sent", "withdrawn" -> sent += it.optDouble("amount")
                "received", "deposited" -> received += it.optDouble("amount")
            }
        }
        findViewById<TextView>(R.id.mpesaSent).text = "KES ${fmt.format(sent)}"
        findViewById<TextView>(R.id.mpesaReceived).text = "KES ${fmt.format(received)}"
        findViewById<TextView>(R.id.mpesaNet).text = "KES ${fmt.format(received - sent)}"
        findViewById<TextView>(R.id.mpesaCount).text = shown.size.toString()

        container.removeAllViews()
        emptyView.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        if (shown.isEmpty()) {
            emptyView.text = if (txns.isEmpty())
                "No M-Pesa transactions found in your inbox."
            else
                "No transactions match this filter."
        }
        shown.forEach { txn ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_mpesa, container, false)
            val type = txn.optString("type")
            row.findViewById<TextView>(R.id.mpesaIcon).text = when (type) {
                "sent", "withdrawn" -> "\uD83D\uDCE4"
                "reversed" -> "\uD83D\uDD04"
                else -> "\uD83D\uDCE5"
            }
            row.findViewById<TextView>(R.id.mpesaPeer).text = txn.optString("peer", "M-Pesa")
            row.findViewById<TextView>(R.id.mpesaDate).text = txn.optString("date", "")
            val amount = row.findViewById<TextView>(R.id.mpesaAmount)
            amount.text = "KES ${fmt.format(txn.optDouble("amount"))}"
            amount.setTextColor(ContextCompat.getColor(this,
                if (type == "received" || type == "deposited") R.color.success else R.color.ink
            ))
            container.addView(row)
        }
    }

    private fun exportCsv() {
        if (txns.isEmpty()) {
            toast("No transactions to export")
            return
        }
        val sb = StringBuilder("date,type,amount,peer,ref\n")
        txns.forEach {
            sb.append("\"").append(it.optString("date")).append("\",")
                .append(it.optString("type")).append(",")
                .append(it.optDouble("amount")).append(",")
                .append("\"").append(it.optString("peer").replace("\"", "'")).append("\",")
                .append("\"").append(it.optString("ref")).append("\"\n")
        }
        try {
            val dir = File(getExternalFilesDir(null), "mpesa").apply { mkdirs() }
            val file = File(dir, "mpesa_${System.currentTimeMillis()}.csv")
            FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                    this@MpesaActivity, "$packageName.fileprovider", file
                ))
                putExtra(Intent.EXTRA_TEXT, "M-Pesa transactions")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share M-Pesa CSV"))
        } catch (e: Exception) {
            toast("Export failed: ${e.message}")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
