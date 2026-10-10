package com.example.turbocachecleaner

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/**
 * NOTE ON HONEST SCOPE: there is no public Android API to open Honor's
 * MagicOS-specific "Battery > App launch" screen, force-close other apps,
 * trim system caches, or otherwise "repair" the device — those don't exist
 * for third-party apps without root. This assistant only ever does one of
 * two things per step: (1) fires a real, documented AOSP Settings intent, or
 * (2) tells you exactly where to tap manually when no such intent exists.
 * It never claims to fix something it didn't actually do.
 */
object HonorRepairAssistant {

    data class Step(
        val title: String,
        val body: String,
        val actionLabel: String? = null,
        val action: ((Activity) -> Unit)? = null
    )

    private fun openSafely(activity: Activity, intent: Intent, fallbackMessage: String) {
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, fallbackMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun buildSteps(activity: Activity): List<Step> {
        val pkg = activity.packageName
        return listOf(
            Step(
                title = "1. Exempt this app from battery optimization",
                body = "MagicOS can suspend this app's background service to save power, which is the most common reason Turbo Clean silently stops mid-run. Tap Open, then choose \"Allow\" or \"Don't optimize\" for TurboCacheCleaner.",
                actionLabel = "Open",
                action = { act ->
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$pkg")
                    )
                    openSafely(
                        act, intent,
                        "Couldn't open that screen directly — go to Settings > Battery > App launch or App power management instead."
                    )
                }
            ),
            Step(
                title = "2. Allow auto-launch / background running",
                body = "MagicOS has a separate toggle for this beyond battery optimization. Go to:\n\nSettings → Battery → App launch → TurboCacheCleaner\n\nSwitch it to \"Manage manually\" and enable Auto-launch, Secondary launch, and Run in background.\n\nAndroid doesn't provide a way to jump straight to that screen, so this app will open its own App Info page — from there, look for a \"Battery\" entry to continue.",
                actionLabel = "Open App Info",
                action = { act ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                    }
                    openSafely(act, intent, "Couldn't open App Info — try Settings > Apps > TurboCacheCleaner manually.")
                }
            ),
            Step(
                title = "3. Check free storage",
                body = "Low free storage slows down the whole system, not just this app. If you're below about 10% free, consider clearing large files or unused apps.",
                actionLabel = "Open Storage settings",
                action = { act ->
                    val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                    openSafely(act, intent, "Couldn't open Storage settings — try Settings > Storage manually.")
                }
            ),
            Step(
                title = "4. Check for a software update",
                body = "Honor regularly ships performance and battery-management fixes for MagicOS. If you haven't updated recently, it's worth checking.",
                actionLabel = "Open Software Update",
                action = { act ->
                    val intent = Intent("android.settings.SYSTEM_UPDATE_SETTINGS")
                    openSafely(act, intent, "Couldn't open Software Update — check Settings > About phone > Software update manually.")
                }
            ),
            Step(
                title = "5. Restart the phone",
                body = "This can't be triggered by an app without system-level permissions, but a restart clears stuck background processes and is worth doing periodically, especially if things have felt slow for a while."
            )
        )
    }

    /** The same steps the guided dialog walks through, exposed for the Compose checklist UI. */
    fun steps(activity: Activity): List<Step> = buildSteps(activity)

    fun show(activity: Activity) {
        val steps = buildSteps(activity)
        showStep(activity, steps, 0)
    }

    private fun showStep(activity: Activity, steps: List<Step>, index: Int) {
        if (index >= steps.size) {
            AlertDialog.Builder(activity)
                .setTitle("Checklist complete")
                .setMessage("That covers what this app can check or open directly. If things are still slow after trying these, it's most likely a hardware or storage issue rather than something a cache/background-permission fix can solve.")
                .setPositiveButton("Done", null)
                .show()
            return
        }

        val step = steps[index]
        val builder = AlertDialog.Builder(activity)
            .setTitle(step.title)
            .setMessage(step.body)
            .setNegativeButton(if (index == steps.size - 1) "Done" else "Skip") { _, _ ->
                showStep(activity, steps, index + 1)
            }

        if (step.action != null && step.actionLabel != null) {
            builder.setPositiveButton(step.actionLabel) { _, _ ->
                step.action.invoke(activity)
                showStep(activity, steps, index + 1)
            }
        } else {
            builder.setPositiveButton("Next") { _, _ ->
                showStep(activity, steps, index + 1)
            }
        }

        builder.setCancelable(false)
        builder.show()
    }
}
