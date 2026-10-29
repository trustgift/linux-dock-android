package com.custom.linuxdock

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchDock: SwitchCompat
    private lateinit var previewDockBar: View
    private lateinit var ivPreviewSearch: ImageView
    private lateinit var ivPreviewClip: ImageView
    private lateinit var tvOpacityLabel: TextView
    private lateinit var tvBorderLabel: TextView
    private lateinit var seekOpacity: SeekBar
    private lateinit var seekBorder: SeekBar

    private var currentAccent = "#F34664"
    private var currentOpacity = 75
    private var currentBorderWidth = 1.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("scream_dock_prefs", Context.MODE_PRIVATE)

        switchDock = findViewById(R.id.switchDock)
        previewDockBar = findViewById(R.id.previewDockBar)
        ivPreviewSearch = findViewById(R.id.ivPreviewSearch)
        ivPreviewClip = findViewById(R.id.ivPreviewClip)
        tvOpacityLabel = findViewById(R.id.tvOpacityLabel)
        tvBorderLabel = findViewById(R.id.tvBorderLabel)
        seekOpacity = findViewById(R.id.seekOpacity)
        seekBorder = findViewById(R.id.seekBorder)

        currentAccent = prefs.getString("dock_accent", "#F34664") ?: "#F34664"
        currentOpacity = prefs.getInt("dock_opacity", 75)
        currentBorderWidth = prefs.getFloat("dock_border_width", 1.5f)

        seekOpacity.progress = currentOpacity
        seekBorder.progress = (currentBorderWidth * 2).toInt()

        updateLabels()
        applyPreview()

        setupHoverGlow(findViewById(R.id.cardService))
        setupHoverGlow(findViewById(R.id.cardDev))
        setupHoverGlow(findViewById(R.id.cardAccessibility))
        setupHoverGlow(findViewById(R.id.cardColor))
        setupHoverGlow(findViewById(R.id.btnApply))

        findViewById<Button>(R.id.btnTelegram).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/screamsoon")))
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Найдите 'Scream' и включите службу", Toast.LENGTH_LONG).show()
        }

        findViewById<View>(R.id.circleCrimson).setOnClickListener { setAccentColor("#F34664") }
        findViewById<View>(R.id.circlePurple).setOnClickListener { setAccentColor("#CBA6F7") }
        findViewById<View>(R.id.circleCyan).setOnClickListener { setAccentColor("#89B4FA") }
        findViewById<View>(R.id.circleMint).setOnClickListener { setAccentColor("#A6E3A1") }
        findViewById<View>(R.id.circleWhite).setOnClickListener { setAccentColor("#FFFFFF") }

        findViewById<Button>(R.id.btnOpenFullPalette).setOnClickListener {
            showFullPaletteDialog()
        }

        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentOpacity = progress.coerceAtLeast(15)
                updateLabels()
                applyPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBorder.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentBorderWidth = (progress / 2.0f)
                updateLabels()
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
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    Toast.makeText(this, "Разрешите отображение поверх других окон", Toast.LENGTH_LONG).show()
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
                .putFloat("dock_border_width", currentBorderWidth)
                .apply()
            Toast.makeText(this, "Конфигурация Scream сохранена!", Toast.LENGTH_SHORT).show()
            if (switchDock.isChecked) restartService()
        }
    }

    private fun setupHoverGlow(view: View) {
        view.setOnGenericMotionListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    view.scaleX = 1.015f
                    view.scaleY = 1.015f
                    true
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    view.scaleX = 1.0f
                    view.scaleY = 1.0f
                    true
                }
                else -> false
            }
        }
    }

    private fun updateLabels() {
        tvOpacityLabel.text = "Прозрачность стекла: $currentOpacity%"
        tvBorderLabel.text = if (currentBorderWidth == 0f) "Контур отключен (Чистое стекло)" else "Толщина контура: $currentBorderWidth dp"
    }

    private fun setAccentColor(hex: String) {
        currentAccent = hex
        applyPreview()
    }

    // Настоящий многослойный рендер жидкого стекла с бликом
    private fun applyPreview() {
        val alphaBase = (currentOpacity * 255 / 100).coerceIn(20, 255)
        val bgHex = String.format("#%02X151824", alphaBase)
        val strokeColor = Color.parseColor(currentAccent)
        val borderWidthPx = (currentBorderWidth * resources.displayMetrics.density).toInt()

        val baseGlass = GradientDrawable().apply {
            cornerRadius = 48f
            setColor(Color.parseColor(bgHex))
            if (borderWidthPx > 0) {
                setStroke(borderWidthPx, strokeColor)
            } else {
                setStroke(0, Color.TRANSPARENT)
            }
        }

        val topSpecular = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#38FFFFFF"), Color.parseColor("#00FFFFFF"))
        ).apply {
            cornerRadius = 48f
        }

        val layers = arrayOf(baseGlass, topSpecular)
        val layerDrawable = LayerDrawable(layers).apply {
            setLayerInset(1, 0, 0, 0, (26 * resources.displayMetrics.density).toInt())
        }

        previewDockBar.background = layerDrawable
        ivPreviewSearch.setColorFilter(strokeColor)
        ivPreviewClip.setColorFilter(strokeColor)
    }

    // Диалог со стеклянными кругляшками и спектром
    private fun showFullPaletteDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_color_palette)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val grid = dialog.findViewById<GridLayout>(R.id.gridPalette)
        val seekHue = dialog.findViewById<SeekBar>(R.id.seekHue)

        val colors = arrayOf(
            "#FFFFFF", "#E0DEF4", "#908CAA", "#6E6A86", "#524F67", "#403D52", "#26233A",
            "#F34664", "#FF2A55", "#E01A4F", "#C70039", "#900C3F", "#FF5733", "#FF8D1A",
            "#FFAA00", "#FFC300", "#EDD100", "#D4E157", "#2ECC71", "#1ABC9C", "#00CEC9",
            "#89B4FA", "#3498DB", "#2980B9", "#0984E3", "#CBA6F7", "#9B59B6", "#FD79A8"
        )

        val itemSize = (34 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()

        for (col in colors) {
            val sphere = View(this).apply {
                val params = GridLayout.LayoutParams().apply {
                    width = itemSize
                    height = itemSize
                    setMargins(margin, margin, margin, margin)
                }
                layoutParams = params
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(col))
                    setStroke(2, Color.parseColor("#66FFFFFF"))
                }
                setOnClickListener {
                    setAccentColor(col)
                    dialog.dismiss()
                }
            }
            grid.addView(sphere)
        }

        seekHue.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val hsv = floatArrayOf(progress.toFloat(), 0.85f, 0.98f)
                val colorInt = Color.HSVToColor(hsv)
                val hex = String.format("#%06X", (0xFFFFFF and colorInt))
                setAccentColor(hex)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialog.findViewById<Button>(R.id.btnClosePalette).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
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
