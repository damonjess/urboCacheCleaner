package com.example.turbocachecleaner

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class CacheClearAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CacheClearService"
        private const val WATCHDOG_TIMEOUT_MS = 6000L

        var appQueue = mutableListOf<String>()
        var appNameQueue = mutableListOf<String>()
        var isProcessing = false

        var onProgressUpdate: ((String) -> Unit)? = null
        var onAppCleaningStarted: ((String) -> Unit)? = null
        var onQueueFinished: (() -> Unit)? = null

        // Different OEM skins (MagicOS included) label these slightly differently.
        // findAccessibilityNodeInfosByText() is already case-insensitive, so we
        // only need to cover *different wording*, not different casing.
        private val storageLabels = listOf("Storage", "Storage usage", "Storage & cache")
        private val cacheLabels = listOf("Clear cache", "Clear Cache")

        private val watchdogHandler = Handler(Looper.getMainLooper())
        private var watchdogRunnable: Runnable? = null
        private var serviceRef: CacheClearAccessibilityService? = null

        // Guards against the flicker bug: onAccessibilityEvent fires many times
        // per screen while it's still animating in/out (typeWindowContentChanged
        // fires on every intermediate frame, not just once the screen settles).
        // Without this lock, a single screen transition could trigger the same
        // click (or a stray "Storage" match on a half-rendered frame) multiple
        // times, which is what caused the rapid back-and-forth flicker.
        @Volatile
        private var isHandlingAction = false
        private val actionLockHandler = Handler(Looper.getMainLooper())
        private var actionLockRunnable: Runnable? = null

        private fun lockActions(forMs: Long) {
            actionLockRunnable?.let { actionLockHandler.removeCallbacks(it) }
            isHandlingAction = true
            val runnable = Runnable { isHandlingAction = false }
            actionLockRunnable = runnable
            actionLockHandler.postDelayed(runnable, forMs)
        }

        private fun unlockActions() {
            actionLockRunnable?.let { actionLockHandler.removeCallbacks(it) }
            actionLockRunnable = null
            isHandlingAction = false
        }

        fun openNextApp(context: Context) {
            cancelWatchdog()
            unlockActions()
            // FIX: this used to also bail out if `!isProcessing` — but isProcessing
            // is only ever set to true a few lines below, so on the very first call
            // of any run it was always false, and this returned immediately without
            // ever starting anything. stopProcessing() already empties appQueue when
            // a run is cancelled, so checking the queue alone is sufficient here.
            if (appQueue.isEmpty()) {
                isProcessing = false
                onQueueFinished?.invoke()
                return
            }
            isProcessing = true
            val nextPackage = appQueue[0]
            val nextAppName = appNameQueue[0]

            onProgressUpdate?.invoke("Cleaning: $nextAppName (${appQueue.size} remaining)")
            onAppCleaningStarted?.invoke(nextPackage)

            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$nextPackage")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            // If this app's settings screen never produces a matching node
            // (custom layout, restricted system app, a stray dialog, slow
            // render, etc.) skip it instead of hanging forever.
            armWatchdog(context)
        }

        fun stopProcessing() {
            isProcessing = false
            appQueue.clear()
            appNameQueue.clear()
            cancelWatchdog()
            unlockActions()
        }

        private fun armWatchdog(context: Context) {
            cancelWatchdog()
            val runnable = Runnable {
                Log.w(TAG, "Watchdog fired: no matching node found in time, skipping app")
                unlockActions()
                if (appQueue.isNotEmpty()) {
                    appQueue.removeAt(0)
                    appNameQueue.removeAt(0)
                }
                serviceRef?.performGlobalAction(GLOBAL_ACTION_BACK)
                openNextApp(context)
            }
            watchdogRunnable = runnable
            watchdogHandler.postDelayed(runnable, WATCHDOG_TIMEOUT_MS)
        }

        private fun cancelWatchdog() {
            watchdogRunnable?.let { watchdogHandler.removeCallbacks(it) }
            watchdogRunnable = null
        }

        private fun findFirstMatch(
            root: AccessibilityNodeInfo,
            labels: List<String>
        ): AccessibilityNodeInfo? {
            for (label in labels) {
                val nodes = root.findAccessibilityNodeInfosByText(label)
                if (nodes.isNotEmpty()) return nodes[0]
            }
            return null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceRef = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceRef === this) serviceRef = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // While an action is "in flight" (a click just happened and the screen
        // is still transitioning), ignore further events entirely — see
        // isHandlingAction above for why this matters.
        if (!isProcessing || appQueue.isEmpty() || isHandlingAction) return
        val rootNode = rootInActiveWindow ?: return

        // STEP 1: Check for "Clear cache" FIRST to prevent the MagicOS back-button loop
        val cacheTextNode = findFirstMatch(rootNode, cacheLabels)
        if (cacheTextNode != null) {
            cancelWatchdog()
            val clickableCacheNode = if (cacheTextNode.isClickable) cacheTextNode else cacheTextNode.parent

            if (clickableCacheNode != null && clickableCacheNode.isEnabled) {
                // Button is enabled (Cache > 0)
                lockActions(1600)
                clickableCacheNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                appQueue.removeAt(0)
                appNameQueue.removeAt(0)

                CoroutineScope(Dispatchers.Main).launch {
                    delay(600)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(400)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(300)
                    unlockActions()
                    openNextApp(this@CacheClearAccessibilityService)
                }
            } else {
                // Button is greyed out (Cache is 0)
                lockActions(1300)
                appQueue.removeAt(0)
                appNameQueue.removeAt(0)

                CoroutineScope(Dispatchers.Main).launch {
                    delay(300)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(400)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(300)
                    unlockActions()
                    openNextApp(this@CacheClearAccessibilityService)
                }
            }
            // CRITICAL: Return so it doesn't try to click "Storage" on this screen
            return
        }

        // STEP 2: If we are here, "Clear cache" isn't on screen. Look for "Storage".
        val storageNode = findFirstMatch(rootNode, storageLabels)
        if (storageNode != null) {
            val clickableStorageNode = if (storageNode.isClickable) storageNode else storageNode.parent
            if (clickableStorageNode?.isClickable == true) {
                // Lock while the Storage screen loads, so we don't click it
                // again on an intermediate frame of the same transition.
                lockActions(900)
                clickableStorageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                // Reset the timer now that we've navigated one level deeper.
                armWatchdog(this)
            }
            return
        }

        // STEP 3: Neither found yet. On slower phones the Storage screen often
        // shows a "Computing app size..." spinner before the Clear Cache button
        // becomes available — that's normal, not stuck. As long as that text is
        // visible, push the watchdog back out instead of giving up on it.
        val computingNodes = rootNode.findAccessibilityNodeInfosByText("computing")
        if (computingNodes.isNotEmpty()) {
            armWatchdog(this)
        }
    }

    override fun onInterrupt() {
        stopProcessing()
    }
}
