package com.yourname.litelinkcompanion

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** About screen (Screen 21): app info, links, legal, and share. */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.aboutToolbar)
            .setNavigationOnClickListener { finish() }

        findViewById<TextView>(R.id.aboutVersion).text = versionText()
        findViewById<TextView>(R.id.aboutDeveloper).text =
            "Developed by LiteLink · ${android.os.Build.MODEL}"

        findViewById<TextView>(R.id.aboutGithub).setOnClickListener { openLink("https://github.com/") }
        findViewById<TextView>(R.id.aboutWebsite).setOnClickListener { openLink("https://litelink.app/") }
        findViewById<TextView>(R.id.aboutReportBug).setOnClickListener { openLink("https://github.com/issues") }
        findViewById<TextView>(R.id.aboutRequestFeature).setOnClickListener { openLink("https://github.com/issues") }
        findViewById<TextView>(R.id.aboutPrivacy).setOnClickListener { showText("Privacy Policy") }
        findViewById<TextView>(R.id.aboutTerms).setOnClickListener { showText("Terms of Service") }
        findViewById<TextView>(R.id.aboutLicenses).setOnClickListener { showLicenses() }
        findViewById<TextView>(R.id.shareAppButton).setOnClickListener { shareApp() }
    }

    private fun versionText(): String {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            "v${info.versionName} (Build #${info.versionCode})"
        } catch (e: PackageManager.NameNotFoundException) {
            "v1.0.0"
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast("No browser available")
        }
    }

    private fun showText(title: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(
                "$title for LiteLink Companion.\n\n" +
                    "LiteLink mirrors notifications, syncs files, and controls your phone " +
                    "from your desktop over your local network. All data stays on your devices."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLicenses() {
        AlertDialog.Builder(this)
            .setTitle("Open Source Licenses")
            .setMessage(
                "LiteLink uses these open-source libraries:\n\n" +
                    "• OkHttp (Apache 2.0) — networking\n" +
                    "• Gson (Apache 2.0) — JSON\n" +
                    "• Material Components (Apache 2.0) — UI\n" +
                    "• AndroidX (Apache 2.0) — platform libraries\n" +
                    "• Kotlin (Apache 2.0) — language"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "LiteLink Companion")
            putExtra(Intent.EXTRA_TEXT, "Check out LiteLink Companion — mirror your phone to your desktop. ${versionText()}")
        }
        startActivity(Intent.createChooser(intent, "Share LiteLink"))
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
