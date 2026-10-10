package com.example.turbocachecleaner.data

import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import com.example.turbocachecleaner.model.AppCacheItem
import com.example.turbocachecleaner.model.StorageInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads per-app cache sizes via [StorageStatsManager] and reports scan progress. */
class CacheScanner(private val context: Context) {

    data class Progress(
        val scanned: Int,
        val total: Int,
        val currentApp: String,
    )

    /**
     * Scans every installed app and returns the ones whose cache is at least [minCacheBytes].
     * Results are sorted largest-first. Throws [SecurityException] when Usage Access is missing.
     */
    suspend fun scan(
        minCacheBytes: Long,
        includeSystem: Boolean,
        onProgress: (Progress) -> Unit = {},
    ): List<AppCacheItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val stats = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val total = packages.size
        val result = ArrayList<AppCacheItem>()

        var index = 0
        for (info in packages) {
            index++
            if (info.packageName == context.packageName) continue
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && !includeSystem) continue

            val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName)
            try {
                val stat: StorageStats = stats.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    info.packageName,
                    Process.myUserHandle(),
                )
                if (stat.cacheBytes >= minCacheBytes) {
                    val icon = runCatching { pm.getApplicationIcon(info) }.getOrNull()
                    result.add(AppCacheItem(info.packageName, label, stat.cacheBytes, icon, isSystem))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // App vanished mid-scan or is protected; skip it.
            }

            if (index % 4 == 0 || index == total) {
                onProgress(Progress(index, total, label))
            }
        }

        result.sortByDescending { it.cacheSize }
        result
    }

    companion object {
        /** Total/used/free space of the primary data partition. */
        fun storageInfo(): StorageInfo = try {
            val stat = StatFs(Environment.getDataDirectory().path)
            StorageInfo(stat.totalBytes, stat.availableBytes)
        } catch (e: Exception) {
            StorageInfo(0L, 0L)
        }
    }
}
