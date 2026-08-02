package com.yourname.litelinkcompanion

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/** Game Controller (feature 85): virtual gamepad mapped to keyboard keys. */
class GameControllerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_controller)

        WebSocketManager.init(this)

        findViewById<MaterialToolbar>(R.id.gameToolbar)
            .setNavigationOnClickListener { finish() }

        // D-pad -> arrows
        findViewById<Button>(R.id.gameUp).setOnClickListener { key("up") }
        findViewById<Button>(R.id.gameDown).setOnClickListener { key("down") }
        findViewById<Button>(R.id.gameLeft).setOnClickListener { key("left") }
        findViewById<Button>(R.id.gameRight).setOnClickListener { key("right") }
        // Action buttons -> keys (WASD-style mapping; customize later)
        findViewById<Button>(R.id.gameA).setOnClickListener { key("z") }
        findViewById<Button>(R.id.gameB).setOnClickListener { key("x") }
        findViewById<Button>(R.id.gameX).setOnClickListener { key("space") }
        findViewById<Button>(R.id.gameY).setOnClickListener { key("enter") }
        findViewById<Button>(R.id.gameStart).setOnClickListener { key("enter") }
        findViewById<Button>(R.id.gameSelect).setOnClickListener { key("esc") }

        if (!WebSocketManager.isConnected) {
            findViewById<TextView>(R.id.gameStatus).text = "Not connected to the PC"
        }
    }

    private fun key(k: String) {
        if (!WebSocketManager.isConnected) {
            toast("Not connected")
            return
        }
        WebSocketManager.sendKey(k)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
