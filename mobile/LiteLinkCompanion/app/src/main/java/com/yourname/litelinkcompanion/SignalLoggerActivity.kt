package com.yourname.litelinkcompanion

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.yourname.litelinkcompanion.commands.SignalModule
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Signal Logger (Screen 11): live signal, periodic recording, history, dead zones. */
class SignalLoggerActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var sampler: Runnable? = null
    private var recording = false
    private var startTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signal)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.signalToolbar)
            .setNavigationOnClickListener { finish() }

        findViewById<View>(R.id.signalStartButton).setOnClickListener { startRecording() }
        findViewById<View>(R.id.signalStopButton).setOnClickListener { stopRecording() }
        findViewById<View>(R.id.signalClearButton).setOnClickListener { clearHistory() }
        findViewById<View>(R.id.signalExportButton).setOnClickListener { exportCsv() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (recording) schedule()
        else refresh()
    }

    override fun onPause() {
        super.onPause()
        sampler?.let { handler.removeCallbacks(it) }
        sampler = null
    }

    private fun schedule() {
        sampler = object : Runnable {
            override fun run() {
                sample()
                handler.postDelayed(this, 10_000)
            }
        }
        handler.post(sampler!!)
    }

    private fun startRecording() {
        recording = true
        startTime = System.currentTimeMillis()
        findViewById<View>(R.id.signalStartButton).visibility = View.GONE
        findViewById<View>(R.id.signalStopButton).visibility = View.VISIBLE
        schedule()
        sample()
        toast("Recording started")
    }

    private fun stopRecording() {
        recording = false
        sampler?.let { handler.removeCallbacks(it) }
        sampler = null
        findViewById<View>(R.id.signalStartButton).visibility = View.VISIBLE
        findViewById<View>(R.id.signalStopButton).visibility = View.GONE
        findViewById<TextView>(R.id.signalRecording).text = "⏹ Not recording"
        findViewById<TextView>(R.id.signalRecording).setTextColor(getColorCompat(R.color.danger))
        toast("Recording stopped")
    }

    private fun sample() {
        val data = SignalModule.readSignal(applicationContext)
        val dbm = if (data.has("signal_dbm")) data.optInt("signal_dbm") else 0
        val level = if (data.has("signal_level")) data.optInt("signal_level") else 0
        val network = data.optString("network_type", "n/a")
        val operator = data.optString("operator", "")
        SignalHistory.add(applicationContext, dbm, level, network, operator)
        refresh()
    }

    private fun refresh() {
        val data = SignalModule.readSignal(applicationContext)
        val dbm = if (data.has("signal_dbm")) data.optInt("signal_dbm") else 0
        val level = if (data.has("signal_level")) data.optInt("signal_level") else 0
        val network = data.optString("network_type", "n/a")
        val operator = data.optString("operator", "")

        findViewById<TextView>(R.id.signalDbm).text = if (data.has("signal_dbm")) "$dbm dBm" else "-- dBm"
        findViewById<TextView>(R.id.signalBars).text = when {
            level >= 4 -> "\uD83D\uDCF6\uD83D\uDCF6\uD83D\uDCF6\uD83D\uDCF6"
            level == 3 -> "\uD83D\uDCF6\uD83D\uDCF6\uD83D\uDCF6"
            level == 2 -> "\uD83D\uDCF6\uD83D\uDCF6"
            level == 1 -> "\uD83D\uDCF6"
            else -> "----"
        }
        findViewById<TextView>(R.id.signalNetwork).text = network
        findViewById<TextView>(R.id.signalOperator).text = operator

        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        findViewById<TextView>(R.id.signalRecording).text = if (recording)
            "\uD83D\uDD34 Recording (${elapsed / 60}m ${elapsed % 60}s)"
        else
            "\uD83B\uDD50 Not recording"
        findViewById<TextView>(R.id.signalRecording).setTextColor(getColorCompat(
            if (recording) R.color.danger else R.color.muted
        ))

        val samples = SignalHistory.load(applicationContext)
        val deadZones = samples.count { it.optInt("dbm") < -95 }
        findViewById<TextView>(R.id.signalDeadZones).text =
            "\uD83D\uDCCD Dead zones detected: $deadZones (below -95 dBm)"

        findViewById<TextView>(R.id.signalHistoryText).text = if (samples.isEmpty())
            "No samples yet — tap Start to begin recording."
        else
            "Captured ${samples.size} sample(s) — export CSV for the full history."
    }

    private fun clearHistory() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear signal history?")
            .setMessage("All recorded samples will be removed.")
            .setPositiveButton("Clear") { _, _ ->
                SignalHistory.clear(this)
                refresh()
                toast("History cleared")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportCsv() {
        val samples = SignalHistory.load(this)
        if (samples.isEmpty()) {
            toast("No samples to export")
            return
        }
        val sb = StringBuilder("time,dbm,level,network,operator\n")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        samples.forEach {
            sb.append(fmt.format(Date(it.optLong("t")))).append(",")
                .append(it.optInt("dbm")).append(",")
                .append(it.optInt("level")).append(",")
                .append(it.optString("network")).append(",")
                .append(it.optString("operator")).append("\n")
        }
        try {
            val dir = File(getExternalFilesDir(null), "signal").apply { mkdirs() }
            val file = File(dir, "signal_${System.currentTimeMillis()}.csv")
            FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                    this@SignalLoggerActivity, "$packageName.fileprovider", file
                ))
                putExtra(Intent.EXTRA_TEXT, "Signal history")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share signal CSV"))
        } catch (e: Exception) {
            toast("Export failed: ${e.message}")
        }
    }

    private fun getColorCompat(color: Int): Int = getColor(color)

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
