package com.yourname.litelinkcompanion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton

/** Screen 2 — onboarding flow (Welcome / Permissions / Connect / Complete). */
class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "litelink"
        private const val KEY_ONBOARDED = "onboarded"
        private const val REQ_PERMISSIONS = 300

        private val perms = listOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private lateinit var pager: ViewPager2
    private lateinit var skipButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private val dots = mutableListOf<View>()

    private var currentPage = 0
    private val pageCount = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        pager = findViewById(R.id.onboardingPager)
        skipButton = findViewById(R.id.skipButton)
        nextButton = findViewById(R.id.nextButton)

        dots.add(findViewById(R.id.dot1))
        dots.add(findViewById(R.id.dot2))
        dots.add(findViewById(R.id.dot3))
        dots.add(findViewById(R.id.dot4))

        pager.adapter = OnboardingAdapter()
        pager.offscreenPageLimit = pageCount
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateUi()
            }
        })

        nextButton.setOnClickListener {
            if (currentPage == pageCount - 1) {
                finishOnboarding()
            } else {
                pager.setCurrentItem(currentPage + 1, true)
            }
        }
        skipButton.setOnClickListener { finishOnboarding() }

        updateUi()
    }

    private fun updateUi() {
        dots.forEachIndexed { index, dot ->
            dot.alpha = if (index == currentPage) 1.0f else 0.3f
        }
        skipButton.visibility = if (currentPage == pageCount - 1) View.INVISIBLE else View.VISIBLE
        nextButton.text = if (currentPage == pageCount - 1) "Get Started" else "Next"
        if (currentPage == 1) refreshPermissionSummary()
    }

    private fun refreshPermissionSummary() {
        val summary = pager.findViewWithTag<TextView>("permSummary") ?: return
        val granted = perms.count {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        summary.text = "$granted of ${perms.size} permissions granted"
    }

    private fun requestPerms() {
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMISSIONS)
        } else {
            refreshPermissionSummary()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) refreshPermissionSummary()
    }

    override fun onResume() {
        super.onResume()
        if (currentPage == 1) refreshPermissionSummary()
    }

    private fun finishOnboarding() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit().putBoolean(KEY_ONBOARDED, true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    /** Inflates the 4 onboarding pages into the ViewPager. */
    private inner class OnboardingAdapter : RecyclerView.Adapter<OnboardingAdapter.PageHolder>() {

        private val layouts = listOf(
            R.layout.onboarding_welcome,
            R.layout.onboarding_permissions,
            R.layout.onboarding_connect,
            R.layout.onboarding_complete
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
            return PageHolder(view)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            if (position == 1) {
                val view = holder.itemView
                val summary = view.findViewById<TextView>(R.id.permSummary)
                summary.tag = "permSummary"
                view.findViewById<MaterialButton>(R.id.grantPermsButton)
                    .setOnClickListener { requestPerms() }
                view.findViewById<MaterialButton>(R.id.specialAccessButton)
                    .setOnClickListener {
                        startActivity(
                            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        )
                    }
            }
        }

        override fun getItemViewType(position: Int): Int = layouts[position]

        override fun getItemCount(): Int = pageCount

        inner class PageHolder(view: View) : RecyclerView.ViewHolder(view)
    }
}
