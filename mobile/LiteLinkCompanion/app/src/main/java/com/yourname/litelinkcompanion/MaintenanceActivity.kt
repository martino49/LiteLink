package com.yourname.litelinkcompanion

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yourname.litelinkcompanion.commands.LocalExecutor
import com.yourname.litelinkcompanion.commands.MaintenanceModule
import org.json.JSONObject

/** Maintenance Mode (Screen 17): schedule, tasks, run now, diagnostics. */
class MaintenanceActivity : AppCompatActivity() {

    private val prefs get() = getSharedPreferences("litelink", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maintenance)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.maintToolbar)
            .setNavigationOnClickListener { finish() }

        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.maintScheduledSwitch).apply {
            isChecked = prefs.getBoolean("maint_scheduled", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("maint_scheduled", checked).apply()
            }
        }

        val timeButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.maintTimeButton)
        timeButton.text = prefs.getString("maint_time", "02:00 AM").orEmpty()
        timeButton.setOnClickListener {
            val current = prefs.getInt("maint_hour", 2) to prefs.getInt("maint_minute", 0)
            android.app.TimePickerDialog(
                this,
                { _, hour, minute ->
                    prefs.edit()
                        .putInt("maint_hour", hour)
                        .putInt("maint_minute", minute)
                        .putString("maint_time", String.format("%02d:%02d", hour, minute))
                        .apply()
                    timeButton.text = String.format("%02d:%02d", hour, minute)
                },
                current.first,
                current.second,
                false
            ).show()
        }

        findViewById<TextView>(R.id.maintDaysText).text = "Days: Mon Tue Wed Thu Fri Sat Sun"

        findViewById<View>(R.id.maintRunButton).setOnClickListener { runNow() }
        refreshDiagnostics()
    }

    override fun onResume() {
        super.onResume()
        refreshDiagnostics()
        val lastRun = prefs.getLong("maint_last_run", 0L)
        val status = prefs.getString("maint_last_status", "Not run yet").orEmpty()
        findViewById<TextView>(R.id.maintStatus).text = if (lastRun > 0)
            "Last run: ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
                .format(java.util.Date(lastRun))}  ·  Status: $status"
        else
            "Status: $status"
    }

    private fun refreshDiagnostics() {
        val data = MaintenanceModule.diagnostics(applicationContext)
        findViewById<TextView>(R.id.maintDiagnostics).text = buildString {
            append("\uD83D\uDCA1 Diagnostics\n")
            append("RAM: ${data.optLong("avail_mem") / 1048576} MB free / ${data.optLong("total_mem") / 1048576} MB\n")
            append("Storage: ${data.optLong("internal_free") / 1048576} MB free\n")
            append("CPU cores: ${data.optInt("cpu_cores")}  ·  Uptime: ${data.optLong("uptime_ms") / 60000} min")
        }
    }

    private fun runNow() {
        findViewById<TextView>(R.id.maintStatus).text = "Status: Running..."
        findViewById<TextView>(R.id.maintStatus).setTextColor(getColor(R.color.muted))
        val taskCache = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.maintTaskCache).isChecked
        val taskPhotos = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.maintTaskPhotos).isChecked
        val taskWhatsApp = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.maintTaskWhatsApp).isChecked
        val taskOldFiles = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.maintTaskOldFiles).isChecked
        val taskReboot = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.maintTaskReboot).isChecked

        val app = applicationContext
        var step = 0
        val report = StringBuilder()

        fun finish() {
            prefs.edit()
                .putLong("maint_last_run", System.currentTimeMillis())
                .putString("maint_last_status", "Completed")
                .apply()
            runOnUiThread {
                findViewById<TextView>(R.id.maintStatus).text =
                    "Status: \u2705 Completed\n$report"
                findViewById<TextView>(R.id.maintStatus).setTextColor(getColor(R.color.success))
                toast("Maintenance completed")
                refreshDiagnostics()
            }
        }

        lateinit var runNext: () -> Unit
        runNext = {
            when (step) {
                0 -> if (taskCache) {
                    LocalExecutor.run(app, "maint.clear_cache") { ok, data ->
                        report.append(if (ok) "✅ Cache cleared (${data.optLong("freed") / 1048576} MB)\n"
                            else "❌ Cache clear failed\n")
                        step++; runNext()
                    }
                } else { step++; runNext() }
                1 -> if (taskPhotos) {
                    report.append("ℹ️ Photo compression needs desktop processing — skipped\n")
                    step++; runNext()
                } else { step++; runNext() }
                2 -> if (taskWhatsApp) {
                    report.append("ℹ️ WhatsApp backup needs desktop — skipped\n")
                    step++; runNext()
                } else { step++; runNext() }
                3 -> if (taskOldFiles) {
                    LocalExecutor.run(app, "maint.clear_cache") { ok, data ->
                        report.append(if (ok) "✅ Old files cleanup ran\n" else "❌ Cleanup failed\n")
                        step++; runNext()
                    }
                } else { step++; runNext() }
                4 -> if (taskReboot) {
                    report.append("ℹ️ Reboot requires root — skipped\n")
                    step++; runNext()
                } else { step++; runNext() }
                else -> finish()
            }
        }
        runNext()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
