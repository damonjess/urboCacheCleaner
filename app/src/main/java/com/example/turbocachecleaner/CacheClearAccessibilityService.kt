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
import com.example.turbocachecleaner.model.CleanStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the "Turbo Clean" queue and publishes its progress as observable state so
 * the Compose UI can react live.
 *
 * The *navigation* logic is unchanged from the original implementation — the
 * watchdog, the transition debounce lock, the "Clear cache first" ordering that
 * avoids the MagicOS back-button loop, greyed-out-button handling and the OEM
 * label variants all behave exactly as before. Only the reporting layer moved
 * from static callbacks to [MutableStateFlow].
 */
object TurboCleaner {

    private const val TAG = "CacheClearService"
    private const val WATCHDOG_TIMEOUT_MS = 6000L

    // ---------------- Observable state ----------------
    val running = MutableStateFlow(false)
    val totalCount = MutableStateFlow(0)
    val cleanedCount = MutableStateFlow(0)
    val currentAppName = MutableStateFlow("")
    val currentPackage = MutableStateFlow("")
    /** Increments whenever a run ends so the UI can refresh its numbers. */
    val finishedEvent = MutableStateFlow(0L)
    /** packageName -> status, drives the per-row Pending/Cleaning/Cleaned label. */
    val statuses = MutableStateFlow<Map<String, CleanStatus>>(emptyMap())

    // ---------------- Queue ----------------
    var isProcessing = false

    @Volatile
    private var delayMs = 600L

    internal val appQueue = mutableListOf<String>()
    internal val appNameQueue = mutableListOf<String>()

    // Different OEM skins (MagicOS included) label these slightly differently.
    // findAccessibilityNodeInfosByText() is already case-insensitive, so we
    // only need to cover *different wording*, not different casing.
    private val storageLabels = listOf("Storage", "Storage usage", "Storage & cache")
    private val cacheLabels = listOf("Clear cache", "Clear Cache")

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null
    private var serviceRef: CacheClearAccessibilityService? = null

    // Guards against the flicker bug: onAccessibilityEvent fires many times per
    // screen while it's still animating in/out (typeWindowContentChanged fires
    // on every intermediate frame, not just once the screen settles). Without
    // this lock a single transition could trigger the same click — or a stray
    // "Storage" match on a half-rendered frame — multiple times, which is what
    // caused the rapid back-and-forth flicker.
    @Volatile
    private var isHandlingAction = false
    private val actionLockHandler = Handler(Looper.getMainLooper())
    private var actionLockRunnable: Runnable? = null

    // ---------------- Lifecycle ----------------

    internal fun attachService(service: CacheClearAccessibilityService) {
        serviceRef = service
    }

    internal fun detachService(service: CacheClearAccessibilityService) {
        if (serviceRef === service) serviceRef = null
        stop()
    }

    // ---------------- Control ----------------

    /** Starts a one-tap clean run over [packageList] / [nameList]. */
    fun start(
        context: Context,
        packageList: List<String>,
        nameList: List<String>,
        delay: Long = 600L,
    ) {
        stop()
        delayMs = delay.coerceAtLeast(400L)
        appQueue.addAll(packageList)
        appNameQueue.addAll(nameList)
        totalCount.value = packageList.size
        cleanedCount.value = 0
        statuses.value = packageList.associateWith { CleanStatus.WAITING }
        running.value = true
        openNext(context)
    }

    fun stop() {
        isProcessing = false
        running.value = false
        appQueue.clear()
        appNameQueue.clear()
        currentAppName.value = ""
        currentPackage.value = ""
        cancelWatchdog()
        unlockActions()
    }

    /** Pops the finished app, marks it done and reports progress. */
    private fun completeCurrent() {
        if (appQueue.isNotEmpty()) {
            val pkg = appQueue[0]
            statuses.value = statuses.value + (pkg to CleanStatus.DONE)
            appQueue.removeAt(0)
            appNameQueue.removeAt(0)
        }
        cleanedCount.value = (totalCount.value - appQueue.size).coerceAtLeast(0)
    }

    internal fun openNext(context: Context) {
        cancelWatchdog()
        unlockActions()

        if (appQueue.isEmpty()) {
            isProcessing = false
            statuses.value = statuses.value.mapValues { CleanStatus.DONE }
            running.value = false
            currentAppName.value = ""
            currentPackage.value = ""
            finishedEvent.value = finishedEvent.value + 1
            return
        }

        isProcessing = true
        val nextPackage = appQueue[0]
        val nextAppName = appNameQueue[0]
        currentPackage.value = nextPackage
        currentAppName.value = nextAppName
        statuses.value = statuses.value + (nextPackage to CleanStatus.CLEANING)

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$nextPackage")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }

        // If this app's settings screen never produces a matching node (custom
        // layout, restricted system app, a stray dialog, slow render, ...)
        // skip it instead of hanging forever.
        armWatchdog(context)
    }

    /**
     * The screen is showing a "Clear cache" entry. Clicks it (or just advances
     * when it's greyed out because the cache is already 0), then navigates back
     * and continues with the next app.
     */
    internal fun handleCacheNode(
        node: AccessibilityNodeInfo,
        context: Context,
        enabled: Boolean,
    ) {
        cancelWatchdog()
        lockActions(if (enabled) 1600 else 1300)
        if (enabled) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        completeCurrent()

        CoroutineScope(Dispatchers.Main).launch {
            delay(delayMs)
            serviceRef?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(400)
            serviceRef?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(300)
            unlockActions()
            openNext(context)
        }
    }

    /** Deepens the navigation: reset the watchdog now that we went one level in. */
    internal fun onNavigatedToStorage(context: Context) {
        armWatchdog(context)
    }

    // ---------------- Matching helpers ----------------

    internal fun isActionLocked(): Boolean = isHandlingAction

    internal fun lockActions(forMs: Long) {
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

    internal fun findCacheNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findFirstMatch(root, cacheLabels)

    internal fun findStorageNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findFirstMatch(root, storageLabels)

    internal fun isComputing(root: AccessibilityNodeInfo): Boolean =
        root.findAccessibilityNodeInfosByText("computing").isNotEmpty()

    private fun findFirstMatch(
        root: AccessibilityNodeInfo,
        labels: List<String>,
    ): AccessibilityNodeInfo? {
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        return null
    }

    // ---------------- Watchdog ----------------

    private fun armWatchdog(context: Context) {
        cancelWatchdog()
        val runnable = Runnable {
            Log.w(TAG, "Watchdog fired: no matching node found in time, skipping app")
            unlockActions()
            completeCurrent()
            serviceRef?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            openNext(context)
        }
        watchdogRunnable = runnable
        watchdogHandler.postDelayed(runnable, WATCHDOG_TIMEOUT_MS)
    }

    private fun cancelWatchdog() {
        watchdogRunnable?.let { watchdogHandler.removeCallbacks(it) }
        watchdogRunnable = null
    }
}

/** Drives the system Settings UI one app at a time. See [TurboCleaner] for the queue. */
class CacheClearAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        TurboCleaner.attachService(this)
    }

    override fun onDestroy() {
        TurboCleaner.detachService(this)
        super.onDestroy()
    }

    override fun onInterrupt() {
        TurboCleaner.stop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // While an action is "in flight" (a click just happened and the screen
        // is still transitioning), ignore further events entirely.
        if (!TurboCleaner.isProcessing || TurboCleaner.appQueue.isEmpty()) return
        if (TurboCleaner.isActionLocked()) return
        val rootNode = rootInActiveWindow ?: return

        // STEP 1: Check for "Clear cache" FIRST to prevent the MagicOS back-button loop.
        val cacheTextNode = TurboCleaner.findCacheNode(rootNode)
        if (cacheTextNode != null) {
            val clickableCacheNode =
                if (cacheTextNode.isClickable) cacheTextNode else cacheTextNode.parent
            if (clickableCacheNode != null) {
                val enabled = clickableCacheNode.isEnabled
                TurboCleaner.handleCacheNode(clickableCacheNode, this, enabled)
            }
            // CRITICAL: return so it doesn't try to click "Storage" on this screen.
            return
        }

        // STEP 2: "Clear cache" isn't on screen — look for "Storage".
        val storageNode = TurboCleaner.findStorageNode(rootNode)
        if (storageNode != null) {
            val clickableStorageNode =
                if (storageNode.isClickable) storageNode else storageNode.parent
            if (clickableStorageNode?.isClickable == true) {
                // Lock while the Storage screen loads, so we don't click it
                // again on an intermediate frame of the same transition.
                TurboCleaner.lockActions(900)
                clickableStorageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                TurboCleaner.onNavigatedToStorage(this)
            }
            return
        }

        // STEP 3: On slower phones the Storage screen often shows a
        // "Computing app size..." spinner before Clear Cache appears — normal,
        // not stuck. While that text is visible, push the watchdog back out.
        if (TurboCleaner.isComputing(rootNode)) {
            TurboCleaner.onNavigatedToStorage(this)
        }
    }
}
