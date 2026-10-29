package com.custom.linuxdock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.LinkedList

data class AppModel(val name: String, val packageName: String, val icon: Drawable)

class DockService : Service() {

    private lateinit var wm: WindowManager
    private var triggerView: View? = null
    private var dockRootView: View? = null

    private val clipboardHistory = LinkedList<String>()
    private val installedApps = ArrayList<AppModel>()

    private lateinit var clipManager: ClipboardManager
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChange()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipManager.addPrimaryClipChangedListener(clipListener)

        startForegroundNotice()
        loadInstalledApps()
        setupTriggerZone()
    }

    private fun startForegroundNotice() {
        val chId = "linux_dock_ch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(chId, "Linux Dock", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val notif: Notification = NotificationCompat.Builder(this, chId)
            .setContentTitle("Linux Dock")
            .setContentText("Служба активна (мышь/жест снизу)")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        startForeground(1001, notif)
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val intents = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intents, 0)
        installedApps.clear()
        for (info in resolveInfos) {
            if (info.activityInfo.packageName == packageName) continue
            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            val pName = info.activityInfo.packageName
            installedApps.add(AppModel(label, pName, icon))
        }
        installedApps.sortBy { it.name.lowercase() }
    }

    private fun handleClipboardChange() {
        val clip = clipManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                clipboardHistory.remove(text)
                clipboardHistory.addFirst(text)
                if (clipboardHistory.size > 10) {
                    clipboardHistory.removeLast()
                }
                updateClipboardButton()
            }
        }
    }

    private fun updateClipboardButton() {
        dockRootView?.let { root ->
            val btnClip = root.findViewById<Button>(R.id.btnClipboard)
            btnClip?.text = "Clipboard (${clipboardHistory.size})"
        }
    }

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun setupTriggerZone() {
        // Прозрачная тонкая зона высотой 6dp в самом низу экрана для вызова мышью или касанием
        val trigger = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnGenericMotionListener { _, event ->
                if (event.action == MotionEvent.ACTION_HOVER_ENTER || event.action == MotionEvent.ACTION_HOVER_MOVE) {
                    showDock()
                    true
                } else false
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    showDock()
                    true
                } else false
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            12,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        triggerView = trigger
        wm.addView(triggerView, params)
    }

    private fun showDock() {
        if (dockRootView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.layout_dock, null)
        val popup = view.findViewById<View>(R.id.popupContainer)
        val rvApps = view.findViewById<RecyclerView>(R.id.rvApps)
        val rvClip = view.findViewById<RecyclerView>(R.id.rvClipboard)
        val btnApps = view.findViewById<Button>(R.id.btnApps)
        val btnClip = view.findViewById<Button>(R.id.btnClipboard)
        val btnClose = view.findViewById<Button>(R.id.btnClose)

        btnClip.text = "Clipboard (${clipboardHistory.size})"

        // Инициализация адаптера приложений
        rvApps.layoutManager = GridLayoutManager(this, 5)
        rvApps.adapter = object : RecyclerView.Adapter<AppViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
                val item = LayoutInflater.from(parent.context).inflate(R.layout.item_dock_app, parent, false)
                return AppViewHolder(item)
            }
            override fun getItemCount() = installedApps.size
            override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
                val app = installedApps[position]
                holder.name.text = app.name
                holder.icon.setImageDrawable(app.icon)
                holder.itemView.setOnClickListener {
                    val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        hideDock()
                    }
                }
            }
        }

        // Инициализация адаптера буфера
        rvClip.layoutManager = LinearLayoutManager(this)
        rvClip.adapter = object : RecyclerView.Adapter<ClipViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipViewHolder {
                val item = LayoutInflater.from(parent.context).inflate(R.layout.item_clip, parent, false)
                return ClipViewHolder(item)
            }
            override fun getItemCount() = clipboardHistory.size
            override fun onBindViewHolder(holder: ClipViewHolder, position: Int) {
                val text = clipboardHistory[position]
                holder.text.text = text
                holder.itemView.setOnClickListener {
                    val clipData = ClipData.newPlainText("Copied", text)
                    clipManager.setPrimaryClip(clipData)
                    Toast.makeText(this@DockService, "Скопировано в буфер!", Toast.LENGTH_SHORT).show()
                    popup.visibility = View.GONE
                }
            }
        }

        btnApps.setOnClickListener {
            if (popup.visibility == View.VISIBLE && rvApps.visibility == View.VISIBLE) {
                popup.visibility = View.GONE
            } else {
                popup.visibility = View.VISIBLE
                rvApps.visibility = View.VISIBLE
                rvClip.visibility = View.GONE
            }
        }

        btnClip.setOnClickListener {
            if (popup.visibility == View.VISIBLE && rvClip.visibility == View.VISIBLE) {
                popup.visibility = View.GONE
            } else {
                popup.visibility = View.VISIBLE
                rvClip.visibility = View.VISIBLE
                rvApps.visibility = View.GONE
                rvClip.adapter?.notifyDataSetChanged()
            }
        }

        btnClose.setOnClickListener {
            hideDock()
        }

        // Автоскрытие дока, если мышь ушла выше рабочей зоны
        view.setOnGenericMotionListener { _, event ->
            if (event.action == MotionEvent.ACTION_HOVER_EXIT) {
                // Опционально: можно скрывать по уходу мыши
            }
            false
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 12
        }

        dockRootView = view
        wm.addView(dockRootView, params)
    }

    private fun hideDock() {
        dockRootView?.let {
            wm.removeView(it)
            dockRootView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clipManager.removePrimaryClipChangedListener(clipListener)
        hideDock()
        triggerView?.let { wm.removeView(it) }
    }

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
    }

    class ClipViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvClipText)
    }
}
