package com.example.turbocachecleaner.data

import android.content.Context

/**
 * Small SharedPreferences wrapper for user settings and checklist progress.
 * Kept dependency-free so it can be used from both the UI and the accessibility service.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Ignore caches smaller than this (bytes). Default ~12 KB to skip ghost system caches. */
    var minCacheBytes: Long
        get() = sp.getLong(KEY_MIN_CACHE, DEFAULT_MIN_CACHE)
        set(v) = sp.edit().putLong(KEY_MIN_CACHE, v).apply()

    /** Delay between clearing two apps, giving the system UI time to settle. */
    var delayMs: Long
        get() = sp.getLong(KEY_DELAY, DEFAULT_DELAY)
        set(v) = sp.edit().putLong(KEY_DELAY, v).apply()

    /** Include pre-installed (system) apps when scanning. */
    var showSystemApps: Boolean
        get() = sp.getBoolean(KEY_SYSTEM_APPS, true)
        set(v) = sp.edit().putBoolean(KEY_SYSTEM_APPS, v).apply()

    /** Vibrate on key actions. */
    var hapticsEnabled: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, true)
        set(v) = sp.edit().putBoolean(KEY_HAPTICS, v).apply()

    /** Keep the screen awake while the one-tap cleaner is running. */
    var keepScreenOn: Boolean
        get() = sp.getBoolean(KEY_KEEP_SCREEN, true)
        set(v) = sp.edit().putBoolean(KEY_KEEP_SCREEN, v).apply()

    /** Remember the last successful scan time (epoch millis). */
    var lastScanTime: Long
        get() = sp.getLong(KEY_LAST_SCAN, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_SCAN, v).apply()

    /** Ids of completed optimisation checklist items. */
    var checklistDone: Set<String>
        get() = sp.getStringSet(KEY_CHECKLIST, emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_CHECKLIST, v).apply()

    companion object {
        private const val NAME = "turbo_cache_prefs"
        private const val KEY_MIN_CACHE = "min_cache_bytes"
        private const val KEY_DELAY = "delay_ms"
        private const val KEY_SYSTEM_APPS = "show_system_apps"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_KEEP_SCREEN = "keep_screen_on"
        private const val KEY_LAST_SCAN = "last_scan_time"
        private const val KEY_CHECKLIST = "checklist_done"

        const val DEFAULT_MIN_CACHE = 12_000L
        const val DEFAULT_DELAY = 700L
    }
}
