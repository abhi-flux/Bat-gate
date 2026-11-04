package com.abhi.batgate

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val bg = 0xFF0A0A0A.toInt()
    private val yellow = 0xFFF5C518.toInt()
    private val grey = 0xFFB0B0B0.toInt()

    private lateinit var prefs: Prefs
    private lateinit var statusService: TextView
    private lateinit var statusSubstack: TextView
    private lateinit var statusStrict: TextView
    private lateinit var strictBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(56, 120, 56, 56)
        }

        root.addView(text("🦇 BAT GATE", 30f, yellow, true))
        root.addView(text("Instagram opens only after 5 minutes of Substack.", 14f, grey, false).apply {
            setPadding(0, 12, 0, 48)
        })

        statusService = text("", 15f, yellow, false)
        statusSubstack = text("", 15f, yellow, false)
        statusStrict = text("", 15f, yellow, false)
        root.addView(statusService)
        root.addView(statusSubstack)
        root.addView(statusStrict)

        root.addView(button("ENABLE ACCESSIBILITY SERVICE") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        strictBtn = button("") { onStrictClick() }
        root.addView(strictBtn)

        root.addView(text(
            "Setup: install Substack and log in. Turn on \"Bat Gate\" under Accessibility. " +
                "If the toggle is greyed out: Settings > Apps > Bat Gate > ⋮ > Allow restricted settings.",
            12f, grey, false
        ).apply { setPadding(0, 48, 0, 0) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )?.contains("$packageName/") == true
        statusService.text = if (enabled) "✅ Service: ON" else "⚠ Service: OFF (enable it below)"

        val hasSubstack = packageManager.getLaunchIntentForPackage(Const.SUBSTACK) != null
        statusSubstack.text = if (hasSubstack) "✅ Substack app: found" else "⚠ Substack app: NOT installed (Instagram stays blocked)"

        val now = System.currentTimeMillis()
        val off = prefs.strictOffAt
        when {
            off == 0L -> {
                statusStrict.text = "🔒 Strict mode: ON"
                strictBtn.text = "REQUEST DISABLE (24H WAIT)"
            }
            now < off -> {
                val h = (off - now) / 3_600_000L + 1
                statusStrict.text = "⏳ Strict mode ends in ~${h}h"
                strictBtn.text = "CANCEL — KEEP STRICT ON"
            }
            else -> {
                statusStrict.text = "🔓 Strict mode: OFF"
                strictBtn.text = "TURN STRICT MODE BACK ON"
            }
        }
    }

    private fun onStrictClick() {
        val now = System.currentTimeMillis()
        val off = prefs.strictOffAt
        prefs.strictOffAt = if (off == 0L) now + Const.STRICT_OFF_DELAY_MS else 0L
        refresh()
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        setPadding(0, 8, 0, 8)
    }

    private fun button(label: String, onClick: (View) -> Unit) = Button(this).apply {
        text = label
        setTextColor(yellow)
        setBackgroundColor(0xFF1A1A1A.toInt())
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setOnClickListener(onClick)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 32 }
    }
}
