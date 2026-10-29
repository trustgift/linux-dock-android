package com.custom.linuxdock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat

class DockService : Service() {

    private lateinit var windowManager: WindowManager
    private var triggerView: View? = null
    private var dockView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundService()
        createFloatingTrigger()
    }

    private fun startForegroundService() {
        val channelId = "dock_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Linux Dock Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Linux Dock")
            .setContentText("Служба дока активна")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1001, notification)
    }

    private fun createFloatingTrigger() {
        val btn = Button(this).apply {
            text = "▲ Dock"
            setBackgroundColor(0xCC313244.toInt())
            setTextColor(0xFFCDD6F4.toInt())
            textSize = 10f
            setPadding(12, 4, 12, 4)
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 10
        }

        btn.setOnClickListener {
            showDock()
        }

        triggerView = btn
        windowManager.addView(triggerView, params)
    }

    private fun showDock() {
        if (dockView != null) return

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.layout_dock, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 50
        }

        view.findViewById<Button>(R.id.btnClipboard).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: "Буфер пуст"
            Toast.makeText(this, "Буфер: $text", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btnApps).setOnClickListener {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            hideDock()
        }

        view.findViewById<Button>(R.id.btnClose).setOnClickListener {
            hideDock()
        }

        dockView = view
        windowManager.addView(dockView, params)
    }

    private fun hideDock() {
        dockView?.let {
            windowManager.removeView(it)
            dockView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideDock()
        triggerView?.let { windowManager.removeView(it) }
    }
}
