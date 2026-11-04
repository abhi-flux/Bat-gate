package com.abhi.batgate

import android.content.Context

object Const {
    const val APP_LABEL = "Bat Gate"

    // Verify with: adb shell pm list packages | grep substack
    const val SUBSTACK = "com.substack.app"

    val IG_PKGS = setOf("com.instagram.android", "com.instagram.lite")
    const val IG_MAIN = "com.instagram.android"

    // Browsers where an open instagram.com tab also counts as Instagram
    val BROWSER_URL_IDS = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "com.brave.browser" to "com.brave.browser:id/url_bar",
        "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar"
    )

    // Screens that can disable/uninstall this app
    val GUARD_PKGS = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    const val REQUIRED_MS = 5 * 60 * 1000L        // Substack time needed
    const val RESET_GAP_MS = 2 * 60 * 1000L       // leave Substack longer than this -> timer resets
    const val IG_GRACE_MS = 15 * 1000L            // leaving Instagram this long re-locks it
    const val SESSION_MS = 20 * 60 * 1000L        // max Instagram time per unlock
    const val STRICT_OFF_DELAY_MS = 24 * 60 * 60 * 1000L
}

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("gate", Context.MODE_PRIVATE)

    var gateActive: Boolean
        get() = p.getBoolean("gateActive", false)
        set(v) = p.edit().putBoolean("gateActive", v).apply()

    var progressMs: Long
        get() = p.getLong("progressMs", 0L)
        set(v) = p.edit().putLong("progressMs", v).apply()

    var lastProgressAt: Long
        get() = p.getLong("lastProgressAt", 0L)
        set(v) = p.edit().putLong("lastProgressAt", v).apply()

    var unlocked: Boolean
        get() = p.getBoolean("unlocked", false)
        set(v) = p.edit().putBoolean("unlocked", v).apply()

    var unlockedAt: Long
        get() = p.getLong("unlockedAt", 0L)
        set(v) = p.edit().putLong("unlockedAt", v).apply()

    var lastIgSeen: Long
        get() = p.getLong("lastIgSeen", 0L)
        set(v) = p.edit().putLong("lastIgSeen", v).apply()

    /** 0 = strict mode on. Otherwise wall-clock time when protection switches off. */
    var strictOffAt: Long
        get() = p.getLong("strictOffAt", 0L)
        set(v) = p.edit().putLong("strictOffAt", v).apply()

    fun strictActive(): Boolean {
        val off = strictOffAt
        return off == 0L || System.currentTimeMillis() < off
    }
}
