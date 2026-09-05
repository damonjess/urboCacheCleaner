package com.example.turbocachecleaner

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class CacheClearAccessibilityService : AccessibilityService() {

    companion object {
        var appQueue = mutableListOf<String>()
        var appNameQueue = mutableListOf<String>()
        var isProcessing = false
        
        var onProgressUpdate: ((String) -> Unit)? = null
        var onQueueFinished: (() -> Unit)? = null

        fun openNextApp(context: Context) {
            if (appQueue.isEmpty() || !isProcessing) {
                isProcessing = false
                onQueueFinished?.invoke()
                return
            }
            isProcessing = true
            val nextPackage = appQueue[0]
            val nextAppName = appNameQueue[0]
            
            onProgressUpdate?.invoke("Cleaning: $nextAppName (${appQueue.size} remaining)")
            
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$nextPackage")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        fun stopProcessing() {
            isProcessing = false
            appQueue.clear()
            appNameQueue.clear()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isProcessing || appQueue.isEmpty()) return
        val rootNode = rootInActiveWindow ?: return

        // STEP 1: Check for "Clear cache" FIRST to prevent the MagicOS back-button loop
        val cacheNodes = rootNode.findAccessibilityNodeInfosByText("Clear cache")
        if (cacheNodes.isNotEmpty()) {
            val cacheTextNode = cacheNodes[0]
            val clickableCacheNode = if (cacheTextNode.isClickable) cacheTextNode else cacheTextNode.parent
            
            if (clickableCacheNode != null && clickableCacheNode.isEnabled) {
                // Button is enabled (Cache > 0)
                clickableCacheNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                
                appQueue.removeAt(0)
                appNameQueue.removeAt(0)
                
                CoroutineScope(Dispatchers.Main).launch {
                    delay(600) 
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(400) 
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(300) 
                    openNextApp(this@CacheClearAccessibilityService)
                }
            } else {
                // Button is greyed out (Cache is 0)
                appQueue.removeAt(0)
                appNameQueue.removeAt(0)
                
                CoroutineScope(Dispatchers.Main).launch {
                    delay(300) 
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(400) 
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(300) 
                    openNextApp(this@CacheClearAccessibilityService)
                }
            }
            // CRITICAL: Return so it doesn't try to click "Storage" on this screen
            return 
        }

        // STEP 2: If we are here, "Clear cache" isn't on screen. Look for "Storage".
        val storageNodes = rootNode.findAccessibilityNodeInfosByText("Storage")
        if (storageNodes.isNotEmpty()) {
            // Loop through all nodes with "Storage" and click the first valid one
            for (node in storageNodes) {
                val clickableStorageNode = if (node.isClickable) node else node.parent
                if (clickableStorageNode?.isClickable == true) {
                    clickableStorageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return 
                }
            }
        }
    }

    override fun onInterrupt() {
        stopProcessing()
    }
}