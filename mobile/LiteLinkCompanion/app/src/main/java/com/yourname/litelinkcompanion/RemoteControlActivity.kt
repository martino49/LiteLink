package com.yourname.litelinkcompanion

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class RemoteControlActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.remote_control)

        WebSocketManager.init(this)

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

        touchpad.setOnTouchListener { _, event ->
            val w = touchpad.width.toFloat()
            val h = touchpad.height.toFloat()
            if (w > 0 && h > 0) {
                val x = (event.x / w).toDouble().coerceIn(0.0, 1.0)
                val y = (event.y / h).toDouble().coerceIn(0.0, 1.0)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> WebSocketManager.sendPointer("down", x, y)
                    MotionEvent.ACTION_MOVE -> WebSocketManager.sendPointer("move", x, y)
                    MotionEvent.ACTION_UP -> WebSocketManager.sendPointer("up", x, y)
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

        if (!WebSocketManager.isConnected) {
            Toast.makeText(this, "Not connected to the desktop", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val status = findViewById<TextView>(R.id.remoteStatus)
        status.text = if (WebSocketManager.isConnected) "Connected — touch to move the cursor" else "Not connected"
    }
}
