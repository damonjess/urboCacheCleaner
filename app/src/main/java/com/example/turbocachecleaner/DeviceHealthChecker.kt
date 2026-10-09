package com.example.turbocachecleaner

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.text.format.Formatter

/**
 * Honest, no-root device diagnostics.
 *
 * IMPORTANT: without root, an app cannot see system-wide CPU usage, force-close
 * other apps' processes, or "repair" anything at the OS level — those APIs don't
 * exist for third-party apps on modern Android. What we CAN read are real,
 * documented signals that commonly explain a phone feeling slow: low free RAM,
 * low free storage, thermal throttling, Battery Saver, and whether MagicOS is
 * likely to be killing this app's own background service. This class only
 * reports what it can actually verify — it never guesses or fabricates a cause.
 */
object DeviceHealthChecker {

    data class Report(
        val totalRamBytes: Long,
        val availRamBytes: Long,
        val ramFreePercent: Int,
        val systemReportsLowMemory: Boolean,
        val totalStorageBytes: Long,
        val freeStorageBytes: Long,
        val storageFreePercent: Int,
        val batteryPercent: Int?,
        val batteryTempCelsius: Float?,
        val isPowerSaveMode: Boolean,
        val thermalStatusLabel: String?,
        val isThrottling: Boolean,
        val isIgnoringBatteryOptimizations: Boolean,
        val likelyCauses: List<String>
    )

    fun run(context: Context): Report {
        val appContext = context.applicationContext

        // --- RAM ---
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem
        val availRam = memInfo.availMem
        val ramFreePercent = if (totalRam > 0) ((availRam * 100) / totalRam).toInt() else -1

        // --- Storage ---
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorage = stat.totalBytes
        val freeStorage = stat.availableBytes
        val storageFreePercent = if (totalStorage > 0) ((freeStorage * 100) / totalStorage).toInt() else -1

        // --- Battery ---
        val batteryIntent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val batteryPercent = batteryIntent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) (level * 100) / scale else null
        }
        val batteryTemp = batteryIntent?.let {
            val tenths = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            if (tenths != Int.MIN_VALUE) tenths / 10f else null
        }

        // --- Power / thermal ---
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isPowerSave = pm.isPowerSaveMode

        var thermalLabel: String? = null
        var isThrottling = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val status = pm.currentThermalStatus
            isThrottling = status >= PowerManager.THERMAL_STATUS_MODERATE
            thermalLabel = when (status) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Slightly warm"
                PowerManager.THERMAL_STATUS_MODERATE -> "Warm — performance may be reduced"
                PowerManager.THERMAL_STATUS_SEVERE -> "Hot — performance is being reduced"
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Very hot — the system is throttling heavily"
                else -> null
            }
        }

        val isIgnoringOptimizations = pm.isIgnoringBatteryOptimizations(appContext.packageName)

        // --- Build the cause list from verified signals only ---
        val causes = mutableListOf<String>()
        if (ramFreePercent in 0..14) {
            causes.add("Only about $ramFreePercent% of RAM is free, so the system is likely closing and reopening background apps often.")
        }
        if (storageFreePercent in 0..9) {
            causes.add("Storage is almost full (about $storageFreePercent% free) — this can slow down app updates, camera, and general responsiveness.")
        }
        if (isPowerSave) {
            causes.add("Battery Saver is currently on, which intentionally limits CPU performance and background activity.")
        }
        if (isThrottling && thermalLabel != null) {
            causes.add("The phone is running warm ($thermalLabel), so MagicOS is automatically reducing performance to cool down.")
        }
        if (memInfo.lowMemory) {
            causes.add("The system itself is reporting a low-memory state.")
        }
        if (!isIgnoringOptimizations) {
            causes.add("This app isn't exempt from battery optimization — MagicOS can suspend its background service, which is why Turbo Clean can silently stop working.")
        }
        if (causes.isEmpty()) {
            causes.add("Nothing abnormal was found in RAM, storage, temperature, or power mode — the slowdown may be app-specific or software-related rather than a resource issue this check can see.")
        }

        return Report(
            totalRamBytes = totalRam,
            availRamBytes = availRam,
            ramFreePercent = ramFreePercent,
            systemReportsLowMemory = memInfo.lowMemory,
            totalStorageBytes = totalStorage,
            freeStorageBytes = freeStorage,
            storageFreePercent = storageFreePercent,
            batteryPercent = batteryPercent,
            batteryTempCelsius = batteryTemp,
            isPowerSaveMode = isPowerSave,
            thermalStatusLabel = thermalLabel,
            isThrottling = isThrottling,
            isIgnoringBatteryOptimizations = isIgnoringOptimizations,
            likelyCauses = causes
        )
    }

    fun formatSummary(context: Context, report: Report): String {
        val sb = StringBuilder()
        sb.append("RAM free: ${report.ramFreePercent}% (")
            .append(Formatter.formatShortFileSize(context, report.availRamBytes))
            .append(" of ")
            .append(Formatter.formatShortFileSize(context, report.totalRamBytes))
            .append(")\n")
        sb.append("Storage free: ${report.storageFreePercent}% (")
            .append(Formatter.formatShortFileSize(context, report.freeStorageBytes))
            .append(" of ")
            .append(Formatter.formatShortFileSize(context, report.totalStorageBytes))
            .append(")\n")
        report.batteryPercent?.let { sb.append("Battery: $it%") }
        report.batteryTempCelsius?.let { sb.append("  (${it}°C)") }
        sb.append("\n")
        sb.append("Battery Saver: ${if (report.isPowerSaveMode) "ON" else "off"}\n")
        report.thermalStatusLabel?.let { sb.append("Thermal state: $it\n") }
        sb.append("Exempt from battery optimization: ${if (report.isIgnoringBatteryOptimizations) "yes" else "no"}\n")
        sb.append("\nLikely causes:\n")
        report.likelyCauses.forEach { sb.append("• $it\n") }
        return sb.toString().trim()
    }
}
