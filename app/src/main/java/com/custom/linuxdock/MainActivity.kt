package com.custom.linuxdock

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
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
        val rbMinimal = findViewById<RadioButton>(R.id.rbMinimal)

        val currentTheme = prefs.getString("dock_theme", "glass") ?: "glass"
        when (currentTheme) {
            "obsidian" -> rbObsidian.isChecked = true
            "minimal" -> rbMinimal.isChecked = true
            else -> rbGlass.isChecked = true
        }
        updatePreview(currentTheme)

        rgThemes.setOnCheckedChangeListener { _, checkedId ->
            val selectedTheme = when (checkedId) {
                R.id.rbObsidian -> "obsidian"
                R.id.rbMinimal -> "minimal"
                else -> "glass"
            }
            prefs.edit().putString("dock_theme", selectedTheme).apply()
            updatePreview(selectedTheme)

            // Перезапуск сервиса для применения стиля, если он активен
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

    private fun updatePreview(theme: String) {
        val res = when (theme) {
            "obsidian" -> R.drawable.bg_dock_obsidian
            "minimal" -> R.drawable.bg_dock_minimal
            else -> R.drawable.bg_dock_glass
        }
        previewDockBar.setBackgroundResource(res)
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
        val intent = Intent(this, DockService::class.java)
        stopService(intent)
    }

    private fun restartService() {
        stopDockService()
        startDockService()
    }
}
