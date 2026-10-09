package com.example.turbocachecleaner

import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

enum class CleanStatus {
    WAITING,
    CLEANING,
    DONE
}

data class AppCacheItem(
    val packageName: String,
    val appName: String,
    val cacheSize: Long,
    var status: CleanStatus = CleanStatus.WAITING
)

class AppListAdapter(
    context: Context,
    private val items: List<AppCacheItem>
) : ArrayAdapter<AppCacheItem>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
        val item = getItem(position) ?: return view

        val tvAppName = view.findViewById<TextView>(R.id.tvAppName)
        val tvPackageName = view.findViewById<TextView>(R.id.tvPackageName)
        val tvCacheSize = view.findViewById<TextView>(R.id.tvCacheSize)
        val tvAppStatus = view.findViewById<TextView>(R.id.tvAppStatus)

        tvAppName.text = item.appName
        tvPackageName.text = item.packageName
        tvCacheSize.text = Formatter.formatFileSize(context, item.cacheSize)

        when (item.status) {
            CleanStatus.WAITING -> {
                tvAppStatus.text = "Pending"
                tvAppStatus.setTextColor(Color.GRAY)
            }
            CleanStatus.CLEANING -> {
                tvAppStatus.text = "Cleaning..."
                tvAppStatus.setTextColor(Color.parseColor("#FF9800"))
            }
            CleanStatus.DONE -> {
                tvAppStatus.text = "Cleaned"
                tvAppStatus.setTextColor(Color.parseColor("#4CAF50"))
            }
        }

        return view
    }
}

class MainActivity : AppCompatActivity() {

    private val appsToClean = mutableListOf<AppCacheItem>()
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnScan = findViewById<Button>(R.id.btnScan)
        val btnTurbo = findViewById<Button>(R.id.btnTurbo)
        val btnHealthCheck = findViewById<Button>(R.id.btnHealthCheck)
        val btnHonorFix = findViewById<Button>(R.id.btnHonorFix)
        val tvAppCount = findViewById<TextView>(R.id.tvAppCount)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvTotalCache = findViewById<TextView>(R.id.tvTotalCache)
        val tvProgress = findViewById<TextView>(R.id.tvProgress)
        val lvApps = findViewById<ListView>(R.id.lvApps)

        adapter = AppListAdapter(this, appsToClean)
        lvApps.adapter = adapter

        CacheClearAccessibilityService.onProgressUpdate = { progressText ->
            runOnUiThread { tvProgress.text = progressText }
        }

        CacheClearAccessibilityService.onAppCleaningStarted = { pkgName ->
            runOnUiThread {
                var updated = false
                appsToClean.forEach { item ->
                    if (item.packageName == pkgName) {
                        item.status = CleanStatus.CLEANING
                        updated = true
                    } else if (item.status == CleanStatus.CLEANING) {
                        item.status = CleanStatus.DONE
                        updated = true
                    }
                }
                if (updated) {
                    adapter.notifyDataSetChanged()
                }
            }
        }

        CacheClearAccessibilityService.onQueueFinished = {
            runOnUiThread {
                appsToClean.forEach { item ->
                    if (item.status == CleanStatus.CLEANING) {
                        item.status = CleanStatus.DONE
                    }
                }
                adapter.notifyDataSetChanged()

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
            adapter.notifyDataSetChanged()

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
                            appsToClean.add(
                                AppCacheItem(
                                    packageName = appInfo.packageName,
                                    appName = appName,
                                    cacheSize = stats.cacheBytes,
                                    status = CleanStatus.WAITING
                                )
                            )
                            totalCacheBytes += stats.cacheBytes
                        }
                    } catch (e: Exception) {
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
                    adapter.notifyDataSetChanged()

                    if (appsToClean.isEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            "No apps found above the cache threshold.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
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

            if (appsToClean.isEmpty()) {
                Toast.makeText(this, "Nothing to clean yet — tap Scan first.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            btnTurbo.text = "STOP TURBO (TAP TO CANCEL)"
            btnTurbo.setBackgroundColor(Color.parseColor("#555555"))

            // Reset all app statuses to WAITING
            appsToClean.forEach { it.status = CleanStatus.WAITING }
            adapter.notifyDataSetChanged()

            CacheClearAccessibilityService.appQueue.clear()
            CacheClearAccessibilityService.appNameQueue.clear()

            appsToClean.forEach {
                CacheClearAccessibilityService.appQueue.add(it.packageName)
                CacheClearAccessibilityService.appNameQueue.add(it.appName)
            }

            CacheClearAccessibilityService.openNextApp(this)
        }

        // Honest diagnostics: reads real RAM/storage/thermal/battery-saver
        // signals and explains, in plain terms, what's actually verifiable —
        // it never guesses at a cause it can't confirm.
        btnHealthCheck.setOnClickListener {
            val report = DeviceHealthChecker.run(this)
            val summary = DeviceHealthChecker.formatSummary(this, report)
            AlertDialog.Builder(this)
                .setTitle("Device Health")
                .setMessage(summary)
                .setPositiveButton("Close", null)
                .setNeutralButton("Open Honor checklist") { _, _ ->
                    HonorRepairAssistant.show(this)
                }
                .show()
        }

        // Guided, step-by-step Honor/MagicOS settings walkthrough. Opens real
        // Settings screens where a documented intent exists, and gives plain
        // instructions where Android doesn't expose a direct way to jump there.
        btnHonorFix.setOnClickListener {
            HonorRepairAssistant.show(this)
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
