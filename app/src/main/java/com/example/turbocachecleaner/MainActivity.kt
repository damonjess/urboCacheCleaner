package com.example.turbocachecleaner

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    data class AppCacheItem(val packageName: String, val appName: String, val cacheSize: Long)
    private val appsToClean = mutableListOf<AppCacheItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnScan = findViewById<Button>(R.id.btnScan)
        val btnTurbo = findViewById<Button>(R.id.btnTurbo)
        val tvAppCount = findViewById<TextView>(R.id.tvAppCount)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvTotalCache = findViewById<TextView>(R.id.tvTotalCache)
        val tvProgress = findViewById<TextView>(R.id.tvProgress)

        CacheClearAccessibilityService.onProgressUpdate = { progressText ->
            runOnUiThread { tvProgress.text = progressText }
        }

        CacheClearAccessibilityService.onQueueFinished = {
            runOnUiThread {
                btnTurbo.text = "TURBO CLEAN"
                btnTurbo.setBackgroundColor(Color.parseColor("#FF0000"))
                tvProgress.text = "All Cache Cleaned!"
                
                // Add a small delay before auto-rescanning so the UI can settle
                Handler(Looper.getMainLooper()).postDelayed({
                    btnScan.performClick()
                }, 1000)
            }
        }

        btnScan.setOnClickListener {
            if (!hasUsageStatsPermission()) {
                Toast.makeText(this, "Please grant Usage Access to calculate cache sizes.", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                return@setOnClickListener
            }

            tvStatus.text = "Scanning..."
            btnScan.isEnabled = false
            appsToClean.clear()

            CoroutineScope(Dispatchers.IO).launch {
                val pm = packageManager
                val storageStatsManager = getSystemService(STORAGE_STATS_SERVICE) as StorageStatsManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                var totalCacheBytes = 0L

                for (appInfo in packages) {
                    if (appInfo.packageName == packageName) continue

                    try {
                        val stats = storageStatsManager.queryStatsForPackage(
                            StorageManager.UUID_DEFAULT,
                            appInfo.packageName,
                            Process.myUserHandle()
                        )
                        
                        if (stats.cacheBytes > 12000) {
                            val appName = pm.getApplicationLabel(appInfo).toString()
                            appsToClean.add(AppCacheItem(appInfo.packageName, appName, stats.cacheBytes))
                            totalCacheBytes += stats.cacheBytes
                        }
                    } catch (e: Exception) {
                        // Crucial fix: Catch ALL exceptions so the coroutine doesn't silently die
                        continue
                    }
                }

                appsToClean.sortByDescending { it.cacheSize }

                withContext(Dispatchers.Main) {
                    tvAppCount.text = "Apps with Cache: ${appsToClean.size}"
                    tvTotalCache.text = "Total Cache: ${Formatter.formatFileSize(this@MainActivity, totalCacheBytes)}"
                    tvStatus.text = "Scan Complete!"
                    btnScan.isEnabled = true
                    btnTurbo.isEnabled = appsToClean.isNotEmpty()
                }
            }
        }

        btnTurbo.setOnClickListener {
            if (CacheClearAccessibilityService.isProcessing) {
                CacheClearAccessibilityService.stopProcessing()
                btnTurbo.text = "TURBO CLEAN"
                btnTurbo.setBackgroundColor(Color.parseColor("#FF0000"))
                tvProgress.text = "Stopped by user."
                return@setOnClickListener
            }

            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Please enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }

            if (appsToClean.isNotEmpty()) {
                btnTurbo.text = "STOP TURBO (TAP TO CANCEL)"
                btnTurbo.setBackgroundColor(Color.parseColor("#555555"))
                
                CacheClearAccessibilityService.appQueue.clear()
                CacheClearAccessibilityService.appNameQueue.clear()

                appsToClean.forEach { 
                    CacheClearAccessibilityService.appQueue.add(it.packageName)
                    CacheClearAccessibilityService.appNameQueue.add(it.appName)
                }
                
                CacheClearAccessibilityService.openNextApp(this)
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) { e.printStackTrace() }
        
        val prefString = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return accessibilityEnabled == 1 && prefString != null && prefString.contains(packageName)
    }
}