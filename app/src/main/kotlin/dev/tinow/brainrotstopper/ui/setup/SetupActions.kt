package dev.tinow.brainrotstopper.ui.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.tinow.brainrotstopper.data.anki.AnkiContract

/** System screens the setup sends the user to. Each falls back to the general settings if the device has no such screen. */
object SetupActions {
    fun openAccessibilitySettings(context: Context) = start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAppInfo(context: Context) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    /** The list of battery-optimisation exceptions; the direct request intent needs a permission the app must not have. */
    fun openBatterySettings(context: Context) = start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    /** AnkiDroid's own launcher entry (also where its API switch and storage setup live). */
    fun openAnkiDroid(context: Context) {
        context.packageManager.getLaunchIntentForPackage(AnkiContract.PACKAGE)?.let { start(context, it) }
    }

    /** The store page of AnkiDroid; without a store app the same page in the browser. */
    fun installAnkiDroid(context: Context) {
        val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${AnkiContract.PACKAGE}"))
        try {
            context.startActivity(store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${AnkiContract.PACKAGE}"))
            runCatching { context.startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
