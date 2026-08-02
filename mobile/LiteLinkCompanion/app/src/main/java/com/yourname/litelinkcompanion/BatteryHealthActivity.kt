package com.yourname.litelinkcompanion

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.yourname.litelinkcompanion.commands.BatteryModule
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Battery Health (Screen 9): gauge, stats, history, export, alerts. */
class BatteryHealthActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var sampler: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery_health)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.batteryToolbar)
            .setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.exportBatteryButton).setOnClickListener { exportCsv() }

        refresh()
    }

    private var lastLevel = 50
    private var lastTemp = 0.0
    private var lastVolt = 0

    override fun onResume() {
        super.onResume()
        sampler = object : Runnable {
            override fun run() {
                val data = BatteryModule.readBattery(applicationContext)
                if (data.has("level")) {
                    lastLevel = data.optInt("level", 50)
                    lastTemp = data.optDouble("temperature_c", 0.0)
                    lastVolt = data.optInt("voltage_mv", 0)
                    BatteryHistory.add(this@BatteryHealthActivity, lastLevel, lastTemp, lastVolt)
                    refresh()
                }
                handler.postDelayed(this, 60_000)
            }
        }
        sampleNow()
        handler.postDelayed(sampler!!, 60_000)
    }

    private fun sampleNow() {
        val data = BatteryModule.readBattery(applicationContext)
        if (data.has("level")) {
            lastLevel = data.optInt("level", 50)
            lastTemp = data.optDouble("temperature_c", 0.0)
            lastVolt = data.optInt("voltage_mv", 0)
            BatteryHistory.add(this, lastLevel, lastTemp, lastVolt)
            refresh()
        }
    }

    override fun onPause() {
        super.onPause()
        sampler?.let { handler.removeCallbacks(it) }
        sampler = null
    }

    private fun refresh() {
        val data = BatteryModule.readBattery(applicationContext)
        val level = if (data.has("level")) data.optInt("level") else 0
        lastLevel = level
        lastTemp = data.optDouble("temperature_c", 0.0)
        lastVolt = data.optInt("voltage_mv", 0)

        findViewById<BatteryGaugeView>(R.id.batteryGauge).setPercent(level.toFloat())
        findViewById<TextView>(R.id.batteryPercent).text = "$level%"

        val temp = lastTemp
        val healthLabel = findViewById<TextView>(R.id.batteryHealthLabel)
        healthLabel.text = when {
            temp > 45 -> "🔴 Poor"
            temp > 40 -> "🟡 Fair"
            else -> "🟢 Good"
        }
        healthLabel.setTextColor(ContextCompat.getColor(this,
            if (temp > 40) R.color.danger else R.color.success
        ))

        val charging = data.optBoolean("charging")
        findViewById<TextView>(R.id.batteryStatusText).text = when {
            data.optInt("status", -1) == 5 -> "Full"
            charging -> "Charging (${data.optString("plugged", "none")})"
            else -> "Not charging"
        }

        findViewById<TextView>(R.id.batteryTemp).text = "Temperature: ${format1(temp)}°C"
        findViewById<TextView>(R.id.batteryVoltage).text = "Voltage: ${format1(lastVolt / 1000.0)}V"
        findViewById<TextView>(R.id.batteryHealth).text = "Health: ${healthEstimate(temp)}%"
        findViewById<TextView>(R.id.batteryCapacity).text = "Capacity: est. ${capacityEstimate(level)} mAh"

        val alert = findViewById<TextView>(R.id.batteryAlert)
        val messages = mutableListOf<String>()
        if (temp > 45) messages.add("⚠️ Battery temperature is high (${format1(temp)}°C).")
        else messages.add("✅ Battery temperature is normal.")
        alert.text = messages.joinToString("\n")
        alert.setTextColor(ContextCompat.getColor(this,
            if (temp > 45) R.color.danger else R.color.teal_700
        ))

        val samples = BatteryHistory.load(this)
        findViewById<TextView>(R.id.batteryHistory).text = if (samples.isEmpty())
            "No history yet — samples are captured every minute."
        else
            "Captured ${samples.size} sample(s) — export CSV for the full history."
    }

    private fun format1(v: Double): String = String.format(Locale.US, "%.1f", v)

    private fun healthEstimate(tempC: Double): Int = when {
        tempC <= 35 -> 95
        tempC <= 40 -> 90
        tempC <= 45 -> 80
        else -> 70
    }

    private fun capacityEstimate(level: Int): Int = 5000

    private fun exportCsv() {
        val samples = BatteryHistory.load(this)
        if (samples.isEmpty()) {
            toast("No history to export yet")
            return
        }
        val sb = StringBuilder("time,level_pct,temp_c,voltage_mv\n")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        samples.forEach {
            sb.append(fmt.format(Date(it.optLong("t")))).append(",")
                .append(it.optInt("level")).append(",")
                .append(it.optDouble("temp")).append(",")
                .append(it.optInt("volt")).append("\n")
        }
        try {
            val dir = File(getExternalFilesDir(null), "battery").apply { mkdirs() }
            val file = File(dir, "battery_${System.currentTimeMillis()}.csv")
            FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                    this@BatteryHealthActivity, "$packageName.fileprovider", file
                ))
                putExtra(Intent.EXTRA_TEXT, "Battery history")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share battery CSV"))
        } catch (e: Exception) {
            toast("Export failed: ${e.message}")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
