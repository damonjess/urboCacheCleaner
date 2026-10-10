package com.example.turbocachecleaner.model

import android.graphics.drawable.Drawable

/** A single installed app whose cache can be cleared. */
data class AppCacheItem(
    val packageName: String,
    val appName: String,
    val cacheSize: Long,
    val icon: Drawable?,
    val isSystem: Boolean,
)

/** Device storage usage, used to render the hero ring. */
data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction: Float get() = if (totalBytes <= 0L) 0f else usedBytes.toFloat() / totalBytes.toFloat()
}

enum class SortOrder(val label: String) {
    SIZE_DESC("Largest"),
    SIZE_ASC("Smallest"),
    NAME_ASC("Name"),
}

/** Live per-app progress shown in the app list during a Turbo Clean run. */
enum class CleanStatus {
    WAITING,
    CLEANING,
    DONE,
}

enum class AppScreen {
    HOME,
    DEVICE_HEALTH,
    CHECKLIST,
    HONOR,
    SETTINGS,
}
