package dev.lostxposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.config.ConfigSchema
import dev.lostxposed.core.config.ConfigWriter
import dev.lostxposed.entry.xposed.Features
import dev.lostxposed.features.displayprofiles.DisplayProfile
import dev.lostxposed.features.displayprofiles.DisplayProfilesFeature

/**
 * Configuration from adb, until the settings UI exists.
 *
 *     # generic: works for every feature
 *     am broadcast -p dev.lostxposed -a dev.lostxposed.config.SET \
 *         --es feature core.smartstatusbar --es key style --es value fuzzy
 *     am broadcast -p dev.lostxposed -a dev.lostxposed.config.SET \
 *         --es feature core.hardwarekeys --es key enabled --ez bvalue true
 *
 *     # display-profile shorthand
 *     am broadcast -p dev.lostxposed -a dev.lostxposed.config.SET \
 *         --es package com.example --ei dpi 400 --ef refresh 60
 *
 *     am broadcast -p dev.lostxposed -a dev.lostxposed.config.DUMP
 *
 * Writing happens here rather than in a hooked process: hooks can only READ the remote
 * preferences, and the file belongs to this app.
 */
class ConfigReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val writer = ConfigWriter.open(context)
        val target = intent.getStringExtra("package") ?: ConfigSchema.ANY_PACKAGE
        // Every one of these reports what happened, not that a call returned. The mode being
        // accepted does not mean it was applied, and the snapshot is the file hooked
        // processes actually fall back to.
        Log.i(
            TAG,
            "prefs opened ${ConfigWriter.lastMode}, readable=${ConfigWriter.lastPublished}, " +
                "dataDir ${ConfigWriter.lastDataDir}, snapshot ${ConfigWriter.lastSnapshot}",
        )

        when (intent.action) {
            ACTION_SET -> if (intent.hasExtra("feature")) {
                generic(writer, intent, target)
            } else {
                displayShorthand(writer, intent, target)
            }

            ACTION_CLEAR -> clear(writer, intent, target)
            ACTION_DUMP -> dump(writer)

            // Opening the writer above rewrote the device-protected copy, which is the whole
            // point. Without this, the first reboot after an update would have no copy for
            // SystemUI to read before unlock.
            Intent.ACTION_MY_PACKAGE_REPLACED ->
                Log.i(TAG, "updated, settings copy refreshed for the next boot")
        }
    }

    private fun generic(writer: ConfigWriter, intent: Intent, target: String) {
        val feature = FeatureId(intent.getStringExtra("feature") ?: return)
        val key = intent.getStringExtra("key") ?: run {
            Log.w(TAG, "SET needs --es key <name>")
            return
        }

        when {
            intent.hasExtra("ivalue") ->
                writer.putInt(feature, target, key, intent.getIntExtra("ivalue", 0))

            intent.hasExtra("fvalue") ->
                writer.putFloat(feature, target, key, intent.getFloatExtra("fvalue", 0f))

            intent.hasExtra("bvalue") ->
                writer.putBoolean(feature, target, key, intent.getBooleanExtra("bvalue", false))

            intent.hasExtra("value") ->
                writer.putString(feature, target, key, intent.getStringExtra("value") ?: "")

            else -> {
                Log.w(TAG, "SET needs one of --es value / --ei ivalue / --ef fvalue / --ez bvalue")
                return
            }
        }
        Log.i(
            TAG,
            "$feature[$target].$key -> write ${ConfigWriter.lastWrite}, " +
                "snapshot ${ConfigWriter.lastSnapshot}",
        )
    }

    private fun displayShorthand(writer: ConfigWriter, intent: Intent, target: String) {
        val feature = DisplayProfilesFeature.ID
        val applied = mutableListOf<String>()

        intent.getIntExtra("dpi", 0).takeIf { it > 0 }?.let {
            writer.putInt(feature, target, DisplayProfile.KEY_DENSITY, it)
            applied += "${it}dpi"
        }
        intent.getFloatExtra("font", 0f).takeIf { it > 0f }?.let {
            writer.putFloat(feature, target, DisplayProfile.KEY_FONT_SCALE, it)
            applied += "font x$it"
        }
        intent.getFloatExtra("refresh", 0f).takeIf { it > 0f }?.let {
            writer.putFloat(feature, target, DisplayProfile.KEY_REFRESH_RATE, it)
            applied += "${it.toInt()}Hz"
        }

        if (applied.isEmpty()) {
            Log.w(TAG, "SET with nothing to set; pass --ei dpi / --ef font / --ef refresh")
        } else {
            Log.i(TAG, "$target <- ${applied.joinToString(", ")} (restart $target to apply)")
        }
    }

    private fun clear(writer: ConfigWriter, intent: Intent, target: String) {
        val featureId = intent.getStringExtra("feature")
        if (featureId != null) {
            val feature = FeatureId(featureId)
            intent.getStringExtra("key")?.let {
                writer.remove(feature, target, it)
                Log.i(TAG, "$feature[$target].$it cleared")
                return
            }
            writer.removeAll(feature, target)
            Log.i(TAG, "$feature[$target] cleared")
            return
        }

        listOf(
            DisplayProfile.KEY_DENSITY,
            DisplayProfile.KEY_FONT_SCALE,
            DisplayProfile.KEY_REFRESH_RATE,
        ).forEach { writer.remove(DisplayProfilesFeature.ID, target, it) }
        Log.i(TAG, "$target display profile cleared (restart $target to apply)")
    }

    private fun dump(writer: ConfigWriter) {
        var any = false
        Features.registry.registrations.forEach { (descriptor, _) ->
            val entries = writer.entriesFor(descriptor.id)
            if (entries.isNotEmpty()) {
                any = true
                Log.i(TAG, "${descriptor.id}:")
                entries.forEach { (pkg, values) -> Log.i(TAG, "    $pkg -> $values") }
            }
        }
        if (!any) Log.i(TAG, "no configuration set for any feature")
    }

    private companion object {
        const val TAG = "LostXposed"
        const val ACTION_SET = "dev.lostxposed.config.SET"
        const val ACTION_CLEAR = "dev.lostxposed.config.CLEAR"
        const val ACTION_DUMP = "dev.lostxposed.config.DUMP"
    }
}
