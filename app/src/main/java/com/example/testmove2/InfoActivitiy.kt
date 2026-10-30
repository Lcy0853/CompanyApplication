package com.example.testmove2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class InfoActivitiy : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_info_activitiy)

        val receivedData = intent.getStringExtra("status_data")

        Toast.makeText(this, "전달받은 상태: $receivedData", Toast.LENGTH_SHORT).show()

        val returnBtn = findViewById<Button>(R.id.btn_disconnect_return)

        returnBtn.setOnClickListener {
            val resultIntent = Intent()

            resultIntent.putExtra("action", "disconnect")

            setResult(RESULT_OK, resultIntent)

            finish()

            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        }
    }
}