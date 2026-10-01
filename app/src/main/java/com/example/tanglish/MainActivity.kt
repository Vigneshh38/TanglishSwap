package com.example.tanglish

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Tanglish Swap"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "1. Get a free Gemini key at aistudio.google.com and paste it below.\n" +
                "2. Turn on the Accessibility service for this app.\n" +
                "3. Open Instagram. Tamil/Telugu text in English letters will be swapped."
            setPadding(0, pad / 2, 0, pad / 2)
        })

        val keyBox = EditText(this).apply {
            hint = "Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("key", ""))
        }
        root.addView(keyBox)

        val modelBox = EditText(this).apply {
            hint = "Model name"
            setText(prefs.getString("model", "gemini-flash-lite-latest"))
        }
        root.addView(modelBox)

        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener {
                prefs.edit()
                    .putString("key", keyBox.text.toString().trim())
                    .putString("model", modelBox.text.toString().trim().ifEmpty { "gemini-flash-lite-latest" })
                    .putString("status", "Saved. Open Instagram to start")
                    .apply()
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
                refreshStatus()
            }
        })
        root.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        statusView = TextView(this).apply { setPadding(0, pad / 2, 0, 0) }
        root.addView(statusView)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val s = getSharedPreferences("prefs", MODE_PRIVATE).getString("status", "Not started yet")
        statusView.text = "Status: $s"
    }
}
