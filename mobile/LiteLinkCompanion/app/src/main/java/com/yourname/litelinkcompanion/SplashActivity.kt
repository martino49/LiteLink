package com.yourname.litelinkcompanion

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.DecelerateInterpolator
import android.view.animation.ScaleAnimation
import androidx.appcompat.app.AppCompatActivity

/** Screen 1 — branded splash with entry animation and first-run routing. */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "litelink"
        private const val KEY_ONBOARDED = "onboarded"
        private const val SPLASH_MS = 2200L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        animateEntry()
        findViewById<android.widget.TextView>(R.id.splashTitle)
            .postDelayed({ route() }, SPLASH_MS)
    }

    private fun animateEntry() {
        val logo = findViewById<View>(R.id.splashLogo)
        val title = findViewById<View>(R.id.splashTitle)
        val tagline = findViewById<View>(R.id.splashTagline)
        val dots = findViewById<View>(R.id.splashDots)

        val scale = ScaleAnimation(
            0.7f, 1f, 0.7f, 1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
        }
        logo.animation = scale

        fadeIn(logo, 500, 0)
        fadeIn(title, 500, 250)
        fadeIn(tagline, 500, 450)
        fadeIn(dots, 400, 700)
    }

    private fun fadeIn(view: View, duration: Long, startOffset: Long) {
        val anim = AlphaAnimation(0f, 1f).apply {
            this.duration = duration
            this.startOffset = startOffset
            fillAfter = true
        }
        view.startAnimation(anim)
    }

    private fun route() {
        val onboarded = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDED, false)
        // Onboarded users land on the connect gate (discovery-first), which
        // auto-navigates to the Dashboard once connected.
        val target = if (onboarded) MainActivity::class.java else OnboardingActivity::class.java
        startActivity(Intent(this, target))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
