package com.example.tanglish

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class OverlayService : AccessibilityService() {

    private val IG = "com.instagram.android"
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    // original text -> converted text ("" means: leave it alone)
    private val cache = ConcurrentHashMap<String, String>()
    private val pending: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    private var wm: WindowManager? = null
    private var layer: FrameLayout? = null
    private val scanTask = Runnable { scan() }

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        layer = FrameLayout(this)
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        wm?.addView(layer, params)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != IG) {
            layer?.removeAllViews()
            return
        }
        // While scrolling, hide old boxes so they don't float in the wrong place
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            layer?.removeAllViews()
        }
        handler.removeCallbacks(scanTask)
        handler.postDelayed(scanTask, 1000) // wait until things settle
    }

    private fun scan() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != IG) {
            layer?.removeAllViews()
            return
        }
        val items = ArrayList<Pair<String, Rect>>()
        collect(root, items)

        layer?.removeAllViews()
        val need = LinkedHashSet<String>()
        for ((text, rect) in items) {
            val done = cache[text]
            if (done == null) {
                if (!pending.contains(text)) need.add(text)
            } else if (done.isNotEmpty()) {
                draw(done, rect)
            }
        }
        if (need.isNotEmpty()) request(need.take(20))
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<Pair<String, Rect>>) {
        if (node == null) return
        val t = node.text?.toString()?.trim()
        if (!t.isNullOrEmpty() && t.length in 3..300 && node.isVisibleToUser &&
            !node.isEditable && looksLatin(t)
        ) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (!r.isEmpty) out.add(Pair(t, r))
        }
        for (i in 0 until node.childCount) collect(node.getChild(i), out)
    }

    private fun looksLatin(s: String): Boolean {
        var letters = 0
        var latin = 0
        for (c in s) {
            if (c.isLetter()) {
                letters++
                if (c.code < 128) latin++
            }
        }
        return letters >= 3 && latin * 10 >= letters * 8
    }

    private var blockedUntil = 0L

    private fun request(texts: List<String>) {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val key = prefs.getString("key", "") ?: ""
        val model = prefs.getString("model", "gemini-flash-lite-latest") ?: "gemini-flash-lite-latest"
        if (key.isEmpty()) {
            prefs.edit().putString("status", "No API key saved").apply()
            return
        }
        if (System.currentTimeMillis() < blockedUntil) return
        pending.addAll(texts)
        executor.execute {
            var ok = false
            try {
                val result = Api.convert(key, model, texts)
                for (t in texts) cache[t] = result[t] ?: ""
                prefs.edit().putString("status", "Working. Last request OK").apply()
                ok = true
            } catch (e: Exception) {
                // Wait 30 seconds before trying again, so we don't hammer a limit
                blockedUntil = System.currentTimeMillis() + 30000
                prefs.edit().putString("status", "Last error: " + (e.message ?: "unknown")).apply()
            } finally {
                pending.removeAll(texts.toSet())
            }
            if (ok) handler.post { scan() }
        }
    }

    private fun draw(text: String, r: Rect) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF222222.toInt())
            setPadding(8, 4, 8, 4)
            minHeight = r.height()
        }
        val lp = FrameLayout.LayoutParams(r.width(), ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = r.left
        lp.topMargin = r.top
        layer?.addView(tv, lp)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        layer?.let { wm?.removeView(it) }
        super.onDestroy()
    }
}
