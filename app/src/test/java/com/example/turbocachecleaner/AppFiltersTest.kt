package com.example.turbocachecleaner

import com.example.turbocachecleaner.model.AppCacheItem
import com.example.turbocachecleaner.model.SortOrder
import com.example.turbocachecleaner.model.StorageInfo
import com.example.turbocachecleaner.util.AppFilters
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFiltersTest {

    private fun item(pkg: String, name: String, size: Long) =
        AppCacheItem(packageName = pkg, appName = name, cacheSize = size, icon = null, isSystem = false)

    private val apps = listOf(
        item("com.tencent.mm", "WeChat", 300),
        item("com.android.chrome", "Chrome", 120),
        item("com.spotify.music", "Spotify", 45),
        item("com.example.maps", "Maps", 900),
    )

    @Test
    fun blankQueryReturnsEverything() {
        assertEquals(4, AppFilters.apply(apps, "", SortOrder.SIZE_DESC).size)
        assertEquals(4, AppFilters.apply(apps, "   ", SortOrder.SIZE_DESC).size)
    }

    @Test
    fun filtersByAppNameCaseInsensitively() {
        val result = AppFilters.apply(apps, "CHROME", SortOrder.SIZE_DESC)
        assertEquals(listOf("Chrome"), result.map { it.appName })
    }

    @Test
    fun substringQueryMatchesMultipleApps() {
        val result = AppFilters.apply(apps, "ch", SortOrder.SIZE_DESC)
        assertEquals(listOf("WeChat", "Chrome"), result.map { it.appName })
    }

    @Test
    fun filtersByPackageName() {
        val result = AppFilters.apply(apps, "spotify", SortOrder.SIZE_DESC)
        assertEquals(listOf("Spotify"), result.map { it.appName })
    }

    @Test
    fun sortsBySizeDescending() {
        val result = AppFilters.apply(apps, "", SortOrder.SIZE_DESC)
        assertEquals(listOf("Maps", "WeChat", "Chrome", "Spotify"), result.map { it.appName })
    }

    @Test
    fun sortsBySizeAscending() {
        val result = AppFilters.apply(apps, "", SortOrder.SIZE_ASC)
        assertEquals(listOf("Spotify", "Chrome", "WeChat", "Maps"), result.map { it.appName })
    }

    @Test
    fun sortsByNameAlphabetically() {
        val result = AppFilters.apply(apps, "", SortOrder.NAME_ASC)
        assertEquals(listOf("Chrome", "Maps", "Spotify", "WeChat"), result.map { it.appName })
    }

    @Test
    fun selectedSizeOnlyCountsSelectedPackages() {
        val selected = setOf("com.tencent.mm", "com.spotify.music", "not.installed")
        assertEquals(345L, AppFilters.selectedSize(apps, selected))
    }

    @Test
    fun storageInfoComputesUsedBytesAndFraction() {
        val info = StorageInfo(totalBytes = 1000, freeBytes = 250)
        assertEquals(750L, info.usedBytes)
        assertEquals(0.75f, info.usedFraction, 0.0001f)
    }
}
