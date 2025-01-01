package com.example.timestream

import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var myEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        myEditText = findViewById(R.id.myEditText)

        // Optional: load previously saved text
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val savedText = prefs.getString("KEY_SAVED_TEXT", "")
        myEditText.setText(savedText)
    }

    override fun onPause() {
        super.onPause()
        // Save text on pause
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE).edit()
        prefs.putString("KEY_SAVED_TEXT", myEditText.text.toString())
        prefs.apply()
    }
}