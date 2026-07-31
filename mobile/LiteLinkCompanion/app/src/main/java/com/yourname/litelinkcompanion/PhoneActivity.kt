package com.yourname.litelinkcompanion

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yourname.litelinkcompanion.ui.FilesFragment
import com.yourname.litelinkcompanion.ui.HomeFragment
import com.yourname.litelinkcompanion.ui.MessagesFragment
import com.yourname.litelinkcompanion.ui.SettingsFragment
import com.yourname.litelinkcompanion.ui.ToolsFragment

/** Hub for all phone-side pages (Home / Messages / Files / Tools / Settings). */
class PhoneActivity : AppCompatActivity() {

    private val home = HomeFragment()
    private val messages = MessagesFragment()
    private val files = FilesFragment()
    private val tools = ToolsFragment()
    private val settings = SettingsFragment()

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
            when (item.itemId) {
                R.id.nav_home -> show(home)
                R.id.nav_messages -> show(messages)
                R.id.nav_files -> show(files)
                R.id.nav_tools -> show(tools)
                R.id.nav_settings -> show(settings)
            }
            true
        }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
