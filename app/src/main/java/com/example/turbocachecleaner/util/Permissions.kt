package com.example.turbocachecleaner.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings

/** Helpers for the two special permissions the cleaner relies on. */
object Permissions {

    /** Usage Access is required for StorageStatsManager to report per-app cache sizes. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** The accessibility service performs the actual tapping to clear each app's cache. */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
            )
        } catch (e: Settings.SettingNotFoundException) {
            0
        }
        if (enabled != 1) return false
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return services.split(':').any {
            it.equals("${context.packageName}/.CacheClearAccessibilityService", ignoreCase = true) ||
                it.startsWith("${context.packageName}/", ignoreCase = true)
        }
    }
}
