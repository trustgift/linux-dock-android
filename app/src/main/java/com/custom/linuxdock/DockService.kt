package com.custom.linuxdock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import java.util.LinkedList

data class AppItem(val name: String, val packageName: String, val icon: Drawable)

class DockService : Service() {

    private lateinit var wm: WindowManager
    private var triggerZone: View? = null
    private var dockRootView: View? = null
    private var dockParams: WindowManager.LayoutParams? = null

    private lateinit var prefs: SharedPreferences
    private val pinnedPackages = LinkedHashSet<String>()

    private val allApps = ArrayList<AppItem>()
    private val filteredApps = ArrayList<AppItem>()
    private val clipboardHistory = LinkedList<String>()

    private lateinit var clipManager: ClipboardManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var clipPollerRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        prefs = getSharedPreferences("linux_dock_prefs", Context.MODE_PRIVATE)

        loadClipboardFromStorage()
        loadPinnedApps()
        startForegroundNotice()
        loadInstalledApps()
        setupTriggerZone()
        startClipboardPoller()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }

    private fun loadClipboardFromStorage() {
        clipboardHistory.clear()
        val json = prefs.getString("clip_history_json", null)
        if (!json.isNullOrEmpty()) {
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    clipboardHistory.add(array.getString(i))
                }
            } catch (_: Exception) {}
        }
    }

    private fun saveClipboardToStorage() {
        val array = JSONArray()
        for (item in clipboardHistory) {
            array.put(item)
        }
        prefs.edit().putString("clip_history_json", array.toString()).apply()
    }

    // Регулярная синхронизация буфера без вылетов и ограничений Android 10+
    private fun syncClipboard() {
        try {
            if (clipManager.hasPrimaryClip()) {
                val clip = clipManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString()?.trim()
                    if (!text.isNullOrEmpty() && !clipboardHistory.contains(text)) {
                        clipboardHistory.addFirst(text)
                        if (clipboardHistory.size > 10) clipboardHistory.removeLast()
                        saveClipboardToStorage()
                        dockRootView?.findViewById<TextView>(R.id.tvClipBadge)?.text = clipboardHistory.size.toString()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun startClipboardPoller() {
        clipPollerRunnable = object : Runnable {
            override fun run() {
                syncClipboard()
                mainHandler.postDelayed(this, 900)
            }
        }
        mainHandler.post(clipPollerRunnable!!)
    }

    private fun loadPinnedApps() {
        val saved = prefs.getStringSet("pinned_apps", null)
        pinnedPackages.clear()
        if (saved != null) pinnedPackages.addAll(saved)
    }

    private fun savePinnedApps() {
        prefs.edit().putStringSet("pinned_apps", pinnedPackages).apply()
        rebuildDockIcons()
    }

    private fun startForegroundNotice() {
        val chId = "linux_dock_ch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(chId, "Linux Dock", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        val notif: Notification = NotificationCompat.Builder(this, chId)
            .setContentTitle("Linux Dock")
            .setContentText("Наведите курсор вниз экрана для вызова")
            .setSmallIcon(R.drawable.ic_app_logo)
            .build()
        startForeground(1001, notif)
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        allApps.clear()
        for (info in resolveInfos) {
            if (info.activityInfo.packageName == packageName) continue
            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            val pName = info.activityInfo.packageName
            allApps.add(AppItem(label, pName, icon))
        }
        allApps.sortBy { it.name.lowercase() }

        if (pinnedPackages.isEmpty() && allApps.isNotEmpty()) {
            for (i in 0 until minOf(5, allApps.size)) {
                pinnedPackages.add(allApps[i].packageName)
            }
            savePinnedApps()
        }
    }

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun setupTriggerZone() {
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
            dpToPx(8f),
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        triggerZone = trigger
        wm.addView(triggerZone, params)
    }

    private fun setWindowFocusable(focusable: Boolean) {
        val root = dockRootView ?: return
        val params = dockParams ?: return
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        wm.updateViewLayout(root, params)
    }

    private fun getCustomDockDrawable(): GradientDrawable {
        val theme = prefs.getString("dock_theme", "glass")
        return GradientDrawable().apply {
            cornerRadius = dpToPx(24f).toFloat()
            when (theme) {
                "obsidian" -> {
                    setColor(Color.parseColor("#D911111B"))
                    setStroke(dpToPx(1.5f), Color.parseColor("#89B4FA"))
                }
                "cyberpunk" -> {
                    setColor(Color.parseColor("#D9251230"))
                    setStroke(dpToPx(1.5f), Color.parseColor("#F38BA8"))
                }
                "emerald" -> {
                    setColor(Color.parseColor("#D910241E"))
                    setStroke(dpToPx(1.5f), Color.parseColor("#A6E3A1"))
                }
                "minimal" -> {
                    setColor(Color.parseColor("#26FFFFFF"))
                    setStroke(dpToPx(1f), Color.parseColor("#38FFFFFF"))
                }
                else -> {
                    setColor(Color.parseColor("#38FFFFFF"))
                    setStroke(dpToPx(1.5f), Color.parseColor("#66FFFFFF"))
                }
            }
        }
    }

    private fun rebuildDockIcons() {
        val root = dockRootView ?: return
        val container = root.findViewById<LinearLayout>(R.id.dockAppsContainer) ?: return
        container.removeAllViews()

        val itemSize = dpToPx(44f)
        val iconSize = dpToPx(32f)

        for (pkg in pinnedPackages) {
            val app = allApps.find { it.packageName == pkg } ?: continue
            val frame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(itemSize, itemSize).apply {
                    setMargins(dpToPx(3f), 0, dpToPx(3f), 0)
                }
                setBackgroundResource(R.drawable.bg_dock_item_glow)
                isClickable = true
                isFocusable = true
            }

            val img = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
                setImageDrawable(app.icon)
            }
            frame.addView(img)

            // Индикатор подсветки снизу иконки
            val dot = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(dpToPx(12f), dpToPx(3f), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = dpToPx(2f)
                }
                setBackgroundColor(Color.parseColor("#CDD6F4"))
                visibility = View.GONE
            }
            frame.addView(dot)

            frame.setOnGenericMotionListener { _, event ->
                if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                    dot.visibility = View.VISIBLE
                    frame.scaleX = 1.1f
                    frame.scaleY = 1.1f
                    true
                } else if (event.action == MotionEvent.ACTION_HOVER_EXIT) {
                    dot.visibility = View.GONE
                    frame.scaleX = 1.0f
                    frame.scaleY = 1.0f
                    true
                } else false
            }

            frame.setOnClickListener { launchPackage(app.packageName) }
            frame.setOnLongClickListener {
                pinnedPackages.remove(app.packageName)
                savePinnedApps()
                Toast.makeText(this, "Откреплено", Toast.LENGTH_SHORT).show()
                true
            }

            container.addView(frame)
        }
    }

    private fun showDock() {
        syncClipboard()
        if (dockRootView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.layout_dock, null)
        val dockBar = view.findViewById<LinearLayout>(R.id.dockBar)
        val popupLauncher = view.findViewById<LinearLayout>(R.id.popupLauncher)
        val popupClip = view.findViewById<LinearLayout>(R.id.popupClipboard)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val rvApps = view.findViewById<RecyclerView>(R.id.rvApps)
        val rvClip = view.findViewById<RecyclerView>(R.id.rvClipboard)
        val btnSearch = view.findViewById<FrameLayout>(R.id.btnSearchLauncher)
        val btnClip = view.findViewById<FrameLayout>(R.id.btnClipboardToggle)
        val tvBadge = view.findViewById<TextView>(R.id.tvClipBadge)
        val btnClearClip = view.findViewById<Button>(R.id.btnClearClip)

        dockBar.background = getCustomDockDrawable()
        tvBadge.text = clipboardHistory.size.toString()
        dockRootView = view
        rebuildDockIcons()

        // Настройка списка приложений
        filteredApps.clear()
        filteredApps.addAll(allApps)
        val appsAdapter = object : RecyclerView.Adapter<AppViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
                val item = LayoutInflater.from(parent.context).inflate(R.layout.item_dock_app, parent, false)
                return AppViewHolder(item)
            }
            override fun getItemCount() = filteredApps.size
            override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
                val app = filteredApps[position]
                holder.name.text = app.name
                holder.icon.setImageDrawable(app.icon)
                holder.itemView.setOnClickListener { launchPackage(app.packageName) }
                holder.itemView.setOnLongClickListener {
                    if (pinnedPackages.contains(app.packageName)) {
                        pinnedPackages.remove(app.packageName)
                        Toast.makeText(this@DockService, "Откреплено", Toast.LENGTH_SHORT).show()
                    } else {
                        pinnedPackages.add(app.packageName)
                        Toast.makeText(this@DockService, "Закреплено в доке!", Toast.LENGTH_SHORT).show()
                    }
                    savePinnedApps()
                    true
                }
            }
        }
        rvApps.layoutManager = GridLayoutManager(this, 5)
        rvApps.adapter = appsAdapter

        // Поиск на лету
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                filteredApps.clear()
                if (q.isEmpty()) {
                    filteredApps.addAll(allApps)
                } else {
                    filteredApps.addAll(allApps.filter { it.name.lowercase().contains(q) })
                }
                appsAdapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                val query = etSearch.text.toString().trim()
                if (filteredApps.isNotEmpty()) {
                    launchPackage(filteredApps[0].packageName)
                } else if (query.isNotEmpty()) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(browserIntent)
                    hideDock()
                }
                true
            } else false
        }

        // Адаптер буфера
        val clipAdapter = object : RecyclerView.Adapter<ClipViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipViewHolder {
                val item = LayoutInflater.from(parent.context).inflate(R.layout.item_clip, parent, false)
                return ClipViewHolder(item)
            }
            override fun getItemCount() = clipboardHistory.size
            override fun onBindViewHolder(holder: ClipViewHolder, position: Int) {
                val itemText = clipboardHistory[position]
                holder.text.text = itemText
                holder.itemView.setOnClickListener {
                    val data = ClipData.newPlainText("Copied", itemText)
                    clipManager.setPrimaryClip(data)
                    Toast.makeText(this@DockService, "Скопировано в буфер!", Toast.LENGTH_SHORT).show()
                    popupClip.visibility = View.GONE
                    setWindowFocusable(false)
                }
            }
        }
        rvClip.layoutManager = LinearLayoutManager(this)
        rvClip.adapter = clipAdapter

        btnClearClip.setOnClickListener {
            clipboardHistory.clear()
            saveClipboardToStorage()
            clipAdapter.notifyDataSetChanged()
            tvBadge.text = "0"
        }

        btnSearch.setOnClickListener {
            if (popupLauncher.visibility == View.VISIBLE) {
                popupLauncher.visibility = View.GONE
                setWindowFocusable(false)
            } else {
                popupLauncher.visibility = View.VISIBLE
                popupClip.visibility = View.GONE
                setWindowFocusable(true)
                etSearch.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        btnClip.setOnClickListener {
            if (popupClip.visibility == View.VISIBLE) {
                popupClip.visibility = View.GONE
                setWindowFocusable(false)
            } else {
                syncClipboard()
                tvBadge.text = clipboardHistory.size.toString()
                clipAdapter.notifyDataSetChanged()
                popupClip.visibility = View.VISIBLE
                popupLauncher.visibility = View.GONE
                setWindowFocusable(false)
            }
        }

        // Хоткей физической клавиатуры (Ctrl + V / Alt + V)
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_V && (event.isCtrlPressed || event.isAltPressed)) {
                btnClip.performClick()
                true
            } else false
        }

        // Моментальное скрытие при уходе курсора мыши
        view.setOnGenericMotionListener { _, event ->
            if (event.action == MotionEvent.ACTION_HOVER_EXIT) {
                if (popupLauncher.visibility != View.VISIBLE && popupClip.visibility != View.VISIBLE) {
                    hideDock()
                }
                true
            } else false
        }

        dockParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(8f)
        }

        wm.addView(dockRootView, dockParams)
    }

    private fun launchPackage(pName: String) {
        val intent = packageManager.getLaunchIntentForPackage(pName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            hideDock()
        }
    }

    private fun hideDock() {
        val root = dockRootView ?: return
        setWindowFocusable(false)
        wm.removeView(root)
        dockRootView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        clipPollerRunnable?.let { mainHandler.removeCallbacks(it) }
        hideDock()
        triggerZone?.let { wm.removeView(it) }
    }

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
    }

    class ClipViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvClipText)
    }
}
