package com.example.turbocachecleaner.util

import com.example.turbocachecleaner.model.AppCacheItem
import com.example.turbocachecleaner.model.SortOrder

/** Pure helpers for searching and ordering the scanned app list. Kept side-effect free for testing. */
object AppFilters {

    fun apply(apps: List<AppCacheItem>, query: String, sort: SortOrder): List<AppCacheItem> {
        val q = query.trim().lowercase()
        val base = if (q.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        return when (sort) {
            SortOrder.SIZE_DESC -> base.sortedByDescending { it.cacheSize }
            SortOrder.SIZE_ASC -> base.sortedBy { it.cacheSize }
            SortOrder.NAME_ASC -> base.sortedBy { it.appName.lowercase() }
        }
    }

    /** Total cache bytes of the apps whose package is in [selected]. */
    fun selectedSize(apps: List<AppCacheItem>, selected: Set<String>): Long =
        apps.filter { it.packageName in selected }.sumOf { it.cacheSize }
}
