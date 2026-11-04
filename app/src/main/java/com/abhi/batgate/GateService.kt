package com.abhi.batgate

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class GateService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager

    private var fgPkg = ""
    private var lastTick = 0L
    private var lastLaunch = 0L
    private var lastGuard = 0L

    private var chip: TextView? = null
    private var chipParams: WindowManager.LayoutParams? = null
    private var chipTouchable = false

    override fun onServiceConnected() {
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        // Fresh start: nothing carries over from before a restart / reboot
        prefs.unlocked = false
        prefs.gateActive = false
        prefs.progressMs = 0L
        lastTick = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        removeChip()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        val pkg = e.packageName?.toString() ?: return

        if (pkg in Const.GUARD_PKGS) guardSettings()

        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (pkg == packageName || pkg == "com.android.systemui" || pkg.contains("inputmethod")) return

        fgPkg = pkg
        // React instantly instead of waiting for the next tick
        if (pkg in Const.IG_PKGS && !prefs.unlocked) blockInstagram()
    }

    // ---------- main loop ----------

    private val tick = object : Runnable {
        override fun run() {
            try {
                step()
            } catch (_: Throwable) {
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun step() {
        val now = SystemClock.elapsedRealtime()
        val dt = (now - lastTick).coerceIn(0L, 2500L)
        lastTick = now

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            // Screen off = Instagram session ends
            if (prefs.unlocked) prefs.unlocked = false
            return
        }

        val ig = instagramInForeground()

        if (ig) {
            if (prefs.unlocked) {
                if (now - prefs.unlockedAt > Const.SESSION_MS) {
                    prefs.unlocked = false
                    blockInstagram()
                } else {
                    prefs.lastIgSeen = now
                }
            } else {
                blockInstagram()
            }
        } else if (prefs.unlocked && now - prefs.lastIgSeen > Const.IG_GRACE_MS) {
            prefs.unlocked = false
        }

        if (prefs.gateActive && !prefs.unlocked) {
            if (fgPkg == Const.SUBSTACK) {
                if (prefs.progressMs < Const.REQUIRED_MS) {
                    prefs.progressMs = minOf(Const.REQUIRED_MS, prefs.progressMs + dt)
                }
                prefs.lastProgressAt = now
            } else if (now - prefs.lastProgressAt > Const.RESET_GAP_MS) {
                prefs.gateActive = false
                prefs.progressMs = 0L
            }
        }

        updateChip()
    }

    // ---------- Instagram detection / blocking ----------

    private fun instagramInForeground(): Boolean {
        if (fgPkg in Const.IG_PKGS) return true
        val urlId = Const.BROWSER_URL_IDS[fgPkg] ?: return false
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(urlId) ?: return false
        return nodes.any { it.text?.toString()?.contains("instagram.com", ignoreCase = true) == true }
    }

    private fun blockInstagram() {
        val now = SystemClock.elapsedRealtime()
        if (!prefs.gateActive) {
            prefs.gateActive = true
            prefs.progressMs = 0L
            prefs.lastProgressAt = now
        }
        if (now - lastLaunch < 2000) return
        lastLaunch = now

        val intent = packageManager.getLaunchIntentForPackage(Const.SUBSTACK)
        if (intent == null) {
            // Substack not installed: fail closed, send to our app which says so
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startActivity(intent)
    }

    private fun unlockAndOpenInstagram() {
        val now = SystemClock.elapsedRealtime()
        prefs.unlocked = true
        prefs.unlockedAt = now
        prefs.lastIgSeen = now
        prefs.gateActive = false
        prefs.progressMs = 0L
        removeChip()
        val intent = packageManager.getLaunchIntentForPackage(Const.IG_MAIN) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    // ---------- tamper guard ----------

    private fun guardSettings() {
        if (!prefs.strictActive()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastGuard < 400) return
        lastGuard = now
        val root = rootInActiveWindow ?: return
        if (containsText(root, Const.APP_LABEL, 0)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun containsText(n: AccessibilityNodeInfo?, needle: String, depth: Int): Boolean {
        if (n == null || depth > 40) return false
        val t = (n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")
        if (t.contains(needle, ignoreCase = true)) return true
        for (i in 0 until n.childCount) {
            if (containsText(n.getChild(i), needle, depth + 1)) return true
        }
        return false
    }

    // ---------- overlay chip ----------

    private fun updateChip() {
        val show = prefs.gateActive && !prefs.unlocked && fgPkg == Const.SUBSTACK
        if (!show) {
            removeChip()
            return
        }
        val remaining = (Const.REQUIRED_MS - prefs.progressMs).coerceAtLeast(0L)
        val ready = remaining == 0L
        val v = chip ?: createChip().also { chip = it }
        val bg = v.background as GradientDrawable
        if (ready) {
            v.text = "▶  OPEN INSTAGRAM"
            v.setTextColor(Color.BLACK)
            bg.setColor(0xFFF5C518.toInt())
        } else {
            val s = remaining / 1000
            v.text = "🦇 SUBSTACK  %d:%02d".format(s / 60, s % 60)
            v.setTextColor(0xFFF5C518.toInt())
            bg.setColor(0xEE101010.toInt())
        }
        setTouchable(v, ready)
    }

    private fun createChip(): TextView {
        val density = resources.displayMetrics.density
        val v = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            val h = (18 * density).toInt()
            val vt = (10 * density).toInt()
            setPadding(h, vt, h, vt)
            background = GradientDrawable().apply {
                cornerRadius = 40 * density
                setStroke((2 * density).toInt(), 0xFFF5C518.toInt())
            }
            setOnClickListener { unlockAndOpenInstagram() }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (48 * density).toInt()
        }
        chipParams = lp
        chipTouchable = false
        wm.addView(v, lp)
        return v
    }

    private fun setTouchable(v: TextView, touchable: Boolean) {
        val lp = chipParams ?: return
        if (touchable == chipTouchable) return
        chipTouchable = touchable
        lp.flags = if (touchable) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        wm.updateViewLayout(v, lp)
    }

    private fun removeChip() {
        chip?.let {
            try {
                wm.removeView(it)
            } catch (_: Throwable) {
            }
        }
        chip = null
        chipParams = null
        chipTouchable = false
    }
}
