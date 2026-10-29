package com.custom.linuxdock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchDock: SwitchCompat
    private lateinit var previewDockBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("linux_dock_prefs", Context.MODE_PRIVATE)

        switchDock = findViewById(R.id.switchDock)
        previewDockBar = findViewById(R.id.previewDockBar)
        val rgThemes = findViewById<RadioGroup>(R.id.rgThemes)
        val rbGlass = findViewById<RadioButton>(R.id.rbGlass)
        val rbObsidian = findViewById<RadioButton>(R.id.rbObsidian)
        val rbCyberpunk = findViewById<RadioButton>(R.id.rbCyberpunk)
        val rbEmerald = findViewById<RadioButton>(R.id.rbEmerald)
        val rbMinimal = findViewById<RadioButton>(R.id.rbMinimal)

        val currentTheme = prefs.getString("dock_theme", "glass") ?: "glass"
        when (currentTheme) {
            "obsidian" -> rbObsidian.isChecked = true
            "cyberpunk" -> rbCyberpunk.isChecked = true
            "emerald" -> rbEmerald.isChecked = true
            "minimal" -> rbMinimal.isChecked = true
            else -> rbGlass.isChecked = true
        }
        applyThemeToPreview(currentTheme)

        rgThemes.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.rbObsidian -> "obsidian"
                R.id.rbCyberpunk -> "cyberpunk"
                R.id.rbEmerald -> "emerald"
                R.id.rbMinimal -> "minimal"
                else -> "glass"
            }
            prefs.edit().putString("dock_theme", theme).apply()
            applyThemeToPreview(theme)

            if (switchDock.isChecked) {
                restartService()
            }
        }

        switchDock.isChecked = prefs.getBoolean("service_enabled", false)
        switchDock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    switchDock.isChecked = false
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                    Toast.makeText(this, "Предоставьте доступ к оверлеям", Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                prefs.edit().putBoolean("service_enabled", true).apply()
                startDockService()
            } else {
                prefs.edit().putBoolean("service_enabled", false).apply()
                stopDockService()
            }
        }
    }

    private fun applyThemeToPreview(theme: String) {
        val drawable = GradientDrawable().apply {
            cornerRadius = 48f
            when (theme) {
                "obsidian" -> {
                    setColor(Color.parseColor("#CC11111B"))
                    setStroke(3, Color.parseColor("#89B4FA"))
                }
                "cyberpunk" -> {
                    setColor(Color.parseColor("#CC2A1435"))
                    setStroke(3, Color.parseColor("#F38BA8"))
                }
                "emerald" -> {
                    setColor(Color.parseColor("#CC132620"))
                    setStroke(3, Color.parseColor("#A6E3A1"))
                }
                "minimal" -> {
                    setColor(Color.parseColor("#1FFFFFFF"))
                    setStroke(2, Color.parseColor("#33FFFFFF"))
                }
                else -> {
                    setColor(Color.parseColor("#33FFFFFF"))
                    setStroke(3, Color.parseColor("#66FFFFFF"))
                }
            }
        }
        previewDockBar.background = drawable
    }

    private fun startDockService() {
        val intent = Intent(this, DockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopDockService() {
        stopService(Intent(this, DockService::class.java))
    }

    private fun restartService() {
        stopDockService()
        startDockService()
    }
}
