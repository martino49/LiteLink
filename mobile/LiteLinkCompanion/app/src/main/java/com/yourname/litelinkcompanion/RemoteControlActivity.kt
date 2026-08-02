package com.yourname.litelinkcompanion

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class RemoteControlActivity : AppCompatActivity() {

    private var lastTouchCount = 0
    private var touchStart = 0L
    private var startPinch = 0f
    private var scrollAccum = 0f
    private var trackpadDim = false
    private var trackpadOff = false
    private var lastX = 0f
    private var lastY = 0f

    // gesture sensitivity (feature 69-71), persisted
    private var sensitivity = 1.0f
    private var scrollSpeed = 1.0f
    private var invertScroll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.remote_control)

        WebSocketManager.init(this)
        loadSettings()

        val toolbar = findViewById<MaterialToolbar>(R.id.remoteToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val touchpad = findViewById<View>(R.id.touchpad)
        val textInput = findViewById<EditText>(R.id.textInput)
        val typeButton = findViewById<Button>(R.id.typeButton)
        val backButton = findViewById<Button>(R.id.backButton)
        val homeButton = findViewById<Button>(R.id.homeButton)
        val recentsButton = findViewById<Button>(R.id.recentsButton)
        val dimButton = findViewById<Button>(R.id.dimButton)

        // Dim / off mode toggle (features 66-67)
        dimButton.setOnClickListener {
            trackpadDim = !trackpadDim
            applyDimMode()
            haptic()
            dimButton.text = if (trackpadDim) "Dim: ON" else "Dim: OFF"
        }

        touchpad.setOnTouchListener { v, event ->
            if (trackpadDim) haptic()
            val w = v.width.toFloat()
            val h = v.height.toFloat()
            if (w <= 0 || h <= 0) return@setOnTouchListener true
            val count = event.pointerCount
            val now = System.currentTimeMillis()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchCount = count
                    touchStart = now
                    startPinch = 0f
                    lastX = event.x
                    lastY = event.y
                    if (count == 1) {
                        WebSocketManager.sendPointer("down", 0.5, 0.5)
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    lastTouchCount = count
                    if (count == 2) startPinch = gestureSpan(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    when (count) {
                        1 -> {
                            // gentle relative movement
                            val dx = (event.x - lastX) / w
                            val dy = (event.y - lastY) / h
                            WebSocketManager.sendPointerRel(dx.toDouble(), dy.toDouble(), sensitivity.toDouble())
                            lastX = event.x
                            lastY = event.y
                        }
                        2 -> {
                            val span = gestureSpan(event)
                            if (startPinch > 0f) {
                                val dz = (span - startPinch) / maxOf(1f, w)
                                if (kotlin.math.abs(dz) > 0.02f) {
                                    WebSocketManager.sendGesture("pinch", 0.0, (dz * sensitivity).toDouble())
                                    startPinch = span
                                }
                            } else {
                                scrollAccum += (event.getY(0) - event.getY(1)) * scrollSpeed
                                if (kotlin.math.abs(scrollAccum) > 40f) {
                                    val dy = (scrollAccum / 40f).toDouble() * (if (invertScroll) -1 else 1)
                                    WebSocketManager.sendGesture("two_finger_scroll", 0.0, dy)
                                    scrollAccum = 0f
                                }
                            }
                        }
                        3 -> {
                            val dx = (event.x - lastX) / w
                            val dy = (event.y - lastY) / h
                            WebSocketManager.sendPointerRel(dx.toDouble(), dy.toDouble(), sensitivity.toDouble())
                            lastX = event.x
                            lastY = event.y
                        }
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    lastTouchCount = count
                }
                MotionEvent.ACTION_UP -> {
                    val duration = now - touchStart
                    when {
                        lastTouchCount == 1 && duration < 250 -> {
                            WebSocketManager.sendPointer("up", 0.5, 0.5)
                        }
                        lastTouchCount == 2 -> {
                            WebSocketManager.sendGesture("right_click", 0.0, 0.0)
                            haptic()
                        }
                        lastTouchCount == 3 -> {
                            WebSocketManager.sendGesture("three_finger_swipe", 0.0, 0.0)
                        }
                    }
                    lastTouchCount = 0
                }
            }
            true
        }

        typeButton.setOnClickListener {
            val text = textInput.text.toString()
            if (text.isNotEmpty()) {
                WebSocketManager.sendText(text)
                textInput.setText("")
            }
        }

        backButton.setOnClickListener { WebSocketManager.sendKey("back") }
        homeButton.setOnClickListener { WebSocketManager.sendKey("home") }
        recentsButton.setOnClickListener { WebSocketManager.sendKey("recents") }

        // special keys
        findViewById<Button>(R.id.kbCtrl).setOnClickListener { WebSocketManager.sendKey("ctrl") }
        findViewById<Button>(R.id.kbShift).setOnClickListener { WebSocketManager.sendKey("shift") }
        findViewById<Button>(R.id.kbAlt).setOnClickListener { WebSocketManager.sendKey("alt") }
        findViewById<Button>(R.id.kbWin).setOnClickListener { WebSocketManager.sendKey("win") }
        findViewById<Button>(R.id.kbF5).setOnClickListener { WebSocketManager.sendKey("f5") }
        findViewById<Button>(R.id.kbEnter).setOnClickListener { WebSocketManager.sendKey("enter") }
        findViewById<Button>(R.id.kbEsc).setOnClickListener { WebSocketManager.sendKey("esc") }
        findViewById<Button>(R.id.kbTab).setOnClickListener { WebSocketManager.sendKey("tab") }

        // find my PC
        findViewById<Button>(R.id.ringPcButton).setOnClickListener { WebSocketManager.sendPc("ring.pc") }
        findViewById<Button>(R.id.stopRingButton).setOnClickListener { WebSocketManager.sendPc("ring.pc.stop") }

        if (!WebSocketManager.isConnected) {
            Toast.makeText(this, "Not connected to the desktop", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSettings() {
        val p = getSharedPreferences("litelink", Context.MODE_PRIVATE)
        sensitivity = p.getFloat("trackpad_sens", 1.0f)
        scrollSpeed = p.getFloat("trackpad_scroll", 1.0f)
        invertScroll = p.getBoolean("trackpad_invert", false)
    }

    private fun applyDimMode() {
        // Dim the phone screen to minimum while keeping the touch layer active.
        val lp = window.attributes
        lp.screenBrightness = if (trackpadDim) 0.05f else -1f
        window.attributes = lp
    }

    private fun haptic() {
        // Haptic feedback (feature 68)
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(15)
            }
        } catch (e: Exception) {
            // no vibrator
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // restore brightness
        val lp = window.attributes
        lp.screenBrightness = -1f
        window.attributes = lp
    }

    private fun gestureSpan(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    override fun onResume() {
        super.onResume()
        val status = findViewById<TextView>(R.id.remoteStatus)
        status.text = if (WebSocketManager.isConnected) "Connected — touch to move the cursor" else "Not connected"
    }
}
