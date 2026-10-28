package com.custom.linuxdock

import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu

class DockService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var triggerView: View
    private lateinit var dockView: View
    private lateinit var clipboardManager: ClipboardManager

    private val clipboardHistory = mutableListOf<String>()
    private var isDockVisible = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboardManager.addPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrBlank() && !clipboardHistory.contains(text)) {
                    clipboardHistory.add(0, text)
                    if (clipboardHistory.size > 20) clipboardHistory.removeLast()
                }
            }
        }

        initTrigger()
        initDock()
    }

    private fun initTrigger() {
        triggerView = View(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            16,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        var startY = 0f
        triggerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (startY - event.rawY > 20 && !isDockVisible) {
                        showDock()
                    }
                    true
                }
                else -> false
            }
        }
        windowManager.addView(triggerView, params)
    }

    private fun initDock() {
        val inflater = LayoutInflater.from(this)
        dockView = inflater.inflate(R.layout.layout_dock, null)

        val dockParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 24
        }

        dockView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hideDock()
                true
            } else false
        }

        dockView.findViewById<ImageView>(R.id.btnClipboard).setOnClickListener { v ->
            showClipboardMenu(v)
        }

        loadPinnedApps(dockView.findViewById(R.id.appsLayout))
        dockView.visibility = View.GONE
        windowManager.addView(dockView, dockParams)
    }

    private fun loadPinnedApps(layout: LinearLayout) {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0).take(6)

        for (app in apps) {
            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(96, 96).apply {
                    setMargins(10, 0, 10, 0)
                }
                setImageDrawable(app.loadIcon(pm))
                setOnClickListener {
                    val launchIntent = pm.getLaunchIntentForPackage(app.activityInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                    hideDock()
                }
            }
            layout.addView(iconView)
        }
    }

    private fun showDock() {
        isDockVisible = true
        dockView.visibility = View.VISIBLE
    }

    private fun hideDock() {
        isDockVisible = false
        dockView.visibility = View.GONE
    }

    private fun showClipboardMenu(anchor: View) {
        if (clipboardHistory.isEmpty()) {
            Toast.makeText(this, "Буфер пуст", Toast.LENGTH_SHORT).show()
            return
        }

        val popup = PopupMenu(this, anchor)
        for ((index, item) in clipboardHistory.withIndex()) {
            val preview = if (item.length > 25) item.substring(0, 25) + "..." else item
            popup.menu.add(0, index, index, preview)
        }

        popup.setOnMenuItemClickListener { menuItem ->
            val text = clipboardHistory[menuItem.itemId]
            val clip = android.content.ClipData.newPlainText("copied", text)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(this, "Скопировано в буфер!", Toast.LENGTH_SHORT).show()
            hideDock()
            true
        }
        popup.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::triggerView.isInitialized) windowManager.removeView(triggerView)
        if (::dockView.isInitialized) windowManager.removeView(dockView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
