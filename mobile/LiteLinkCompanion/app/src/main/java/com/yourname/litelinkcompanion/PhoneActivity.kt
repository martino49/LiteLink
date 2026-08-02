package com.yourname.litelinkcompanion

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yourname.litelinkcompanion.ui.FeaturesFragment
import com.yourname.litelinkcompanion.ui.FilesFragment
import com.yourname.litelinkcompanion.ui.HomeFragment
import com.yourname.litelinkcompanion.ui.NotificationsFragment
import com.yourname.litelinkcompanion.ui.SettingsFragment

/** Hub for all phone-side pages (Home / Notifications / Files / Features / Settings). */
class PhoneActivity : AppCompatActivity() {

    private val home = HomeFragment()
    private val notifications = NotificationsFragment()
    private val files = FilesFragment()
    private val features = FeaturesFragment()
    private val settings = SettingsFragment()
    private var currentItem = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebSocketManager.init(this)
        setContentView(R.layout.activity_phone)

        val toolbar = findViewById<MaterialToolbar>(R.id.phoneToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        if (savedInstanceState == null) show(home)

        nav.setOnItemSelectedListener { item ->
            // Tapping Features again closes it back to Home.
            if (item.itemId == R.id.nav_features && currentItem == R.id.nav_features) {
                nav.selectedItemId = R.id.nav_home
                show(home)
                currentItem = R.id.nav_home
                return@setOnItemSelectedListener true
            }
            when (item.itemId) {
                R.id.nav_home -> show(home)
                R.id.nav_notifications -> show(notifications)
                R.id.nav_files -> show(files)
                R.id.nav_features -> show(features)
                R.id.nav_settings -> show(settings)
            }
            currentItem = item.itemId
            true
        }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /** Floating menu — no longer used; the Features tab replaces it. */
    @Suppress("unused")
    private fun showFeaturesSheet() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        sheet.setContentView(R.layout.sheet_features)
        val launch = { activity: Class<*> -> sheet.dismiss(); startActivity(Intent(this, activity)) }
        sheet.findViewById<View>(R.id.sheetBattery)?.setOnClickListener { launch(BatteryHealthActivity::class.java) }
        sheet.show()
    }
}
