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
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchDock: SwitchCompat
    private lateinit var previewDockBar: View
    private lateinit var ivPreviewSearch: ImageView
    private lateinit var ivPreviewClip: ImageView
    private lateinit var tvOpacityLabel: TextView
    private lateinit var seekOpacity: SeekBar

    private var currentAccent = "#F34664"
    private var currentOpacity = 75

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("scream_dock_prefs", Context.MODE_PRIVATE)

        switchDock = findViewById(R.id.switchDock)
        previewDockBar = findViewById(R.id.previewDockBar)
        ivPreviewSearch = findViewById(R.id.ivPreviewSearch)
        ivPreviewClip = findViewById(R.id.ivPreviewClip)
        tvOpacityLabel = findViewById(R.id.tvOpacityLabel)
        seekOpacity = findViewById(R.id.seekOpacity)

        currentAccent = prefs.getString("dock_accent", "#F34664") ?: "#F34664"
        currentOpacity = prefs.getInt("dock_opacity", 75)

        seekOpacity.progress = currentOpacity
        tvOpacityLabel.text = "Прозрачность стекла: $currentOpacity%"

        applyPreview()

        // Кнопка перехода в Telegram автора
        findViewById<Button>(R.id.btnTelegram).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/screamsoon"))
            startActivity(intent)
        }

        // Палитра
        findViewById<View>(R.id.colCrimson).setOnClickListener { setAccent("#F34664") }
        findViewById<View>(R.id.colPurple).setOnClickListener { setAccent("#CBA6F7") }
        findViewById<View>(R.id.colCyan).setOnClickListener { setAccent("#89B4FA") }
        findViewById<View>(R.id.colMint).setOnClickListener { setAccent("#A6E3A1") }
        findViewById<View>(R.id.colWhite).setOnClickListener { setAccent("#FFFFFF") }

        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentOpacity = progress.coerceAtLeast(15)
                tvOpacityLabel.text = "Прозрачность стекла: $currentOpacity%"
                applyPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        switchDock.isChecked = prefs.getBoolean("service_enabled", false)
        switchDock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    switchDock.isChecked = false
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                    Toast.makeText(this, "Разрешите оверлей поверх других окон", Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                prefs.edit().putBoolean("service_enabled", true).apply()
                startDockService()
            } else {
                prefs.edit().putBoolean("service_enabled", false).apply()
                stopDockService()
            }
        }

        findViewById<Button>(R.id.btnApply).setOnClickListener {
            prefs.edit()
                .putString("dock_accent", currentAccent)
                .putInt("dock_opacity", currentOpacity)
                .apply()
            Toast.makeText(this, "Настройки Scream сохранены!", Toast.LENGTH_SHORT).show()
            if (switchDock.isChecked) {
                restartService()
            }
        }
    }

    private fun setAccent(colorHex: String) {
        currentAccent = colorHex
        applyPreview()
    }

    private fun applyPreview() {
        val alpha = (currentOpacity * 255 / 100).coerceIn(20, 255)
        val bgHex = String.format("#%02X12131C", alpha)
        val strokeColor = Color.parseColor(currentAccent)

        val drawable = GradientDrawable().apply {
            cornerRadius = 48f
            setColor(Color.parseColor(bgHex))
            setStroke(3, strokeColor)
        }
        previewDockBar.background = drawable
        ivPreviewSearch.setColorFilter(strokeColor)
        ivPreviewClip.setColorFilter(strokeColor)
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
