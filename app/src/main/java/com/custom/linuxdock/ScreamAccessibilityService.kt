package com.custom.linuxdock

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import org.json.JSONArray
import java.util.LinkedList

class ScreamAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ScreamAccessibilityService? = null
        val clipboardList = LinkedList<String>()

        fun addClipItem(context: Context, text: String) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            clipboardList.remove(trimmed)
            clipboardList.addFirst(trimmed)
            if (clipboardList.size > 10) clipboardList.removeLast()

            val prefs = context.getSharedPreferences("scream_dock_prefs", Context.MODE_PRIVATE)
            val array = JSONArray()
            for (item in clipboardList) array.put(item)
            prefs.edit().putString("clip_history_json", array.toString()).apply()

            DockService.instance?.updateClipBadge()
        }
    }

    private lateinit var clipManager: ClipboardManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipManager.addPrimaryClipChangedListener {
            try {
                if (clipManager.hasPrimaryClip()) {
                    val clip = clipManager.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).text?.toString()
                        if (!text.isNullOrEmpty()) {
                            addClipItem(this, text)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Фоновый перехват копирования при любых изменениях фокуса
        try {
            if (clipManager.hasPrimaryClip()) {
                val clip = clipManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString()
                    if (!text.isNullOrEmpty() && !clipboardList.contains(text.trim())) {
                        addClipItem(this, text)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Глобальный перехват сочетания клавиш Ctrl + V / Alt + V поверх любого окна
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_V && (event.isCtrlPressed || event.isAltPressed)) {
            DockService.instance?.openClipboardDirectly()
            return true
        }
        return super.onKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
