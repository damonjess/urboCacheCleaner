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
            
            // Send live update to UI
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

        // Step 1: Click "Storage"
        val storageNodes = rootNode.findAccessibilityNodeInfosByText("Storage")
        if (storageNodes.isNotEmpty()) {
            val storageTextNode = storageNodes[0]
            val clickableStorageNode = if (storageTextNode.isClickable) storageTextNode else storageTextNode.parent
            
            if (clickableStorageNode?.isClickable == true) {
                clickableStorageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return 
            }
        }

        // Step 2: Click "Clear cache"
        val cacheNodes = rootNode.findAccessibilityNodeInfosByText("Clear cache")
        if (cacheNodes.isNotEmpty()) {
            val cacheTextNode = cacheNodes[0]
            val clickableCacheNode = if (cacheTextNode.isClickable) cacheTextNode else cacheTextNode.parent
            
            if (clickableCacheNode != null && clickableCacheNode.isEnabled) {
                clickableCacheNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                
                appQueue.removeAt(0)
                appNameQueue.removeAt(0)
                
                CoroutineScope(Dispatchers.Main).launch {
                    delay(600) // Wait for cache to clear
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(400) // Wait for UI transition
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(200) // Wait before firing next intent
                    openNextApp(this@CacheClearAccessibilityService)
                }
            }
        }
    }

    override fun onInterrupt() {
        stopProcessing()
    }
}