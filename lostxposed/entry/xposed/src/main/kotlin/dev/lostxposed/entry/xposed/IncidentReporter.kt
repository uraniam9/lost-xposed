package dev.lostxposed.entry.xposed

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.config.ConfigContract
import dev.lostxposed.core.engine.LOG_TAG
import dev.lostxposed.core.safety.BootGuard

/**
 * Tells the settings app that the boot guard turned features off.
 *
 * Without this the guard is silent: it does the right thing at a moment nobody can observe,
 * and the only evidence a person gets is a feature that stopped working for no visible reason.
 * Somebody whose phone just failed to boot should be told what happened, and handed something
 * they can send on rather than being asked to describe it.
 *
 * Reported late on purpose. This runs in `system_server` during boot, and reaching into a
 * ContentProvider then would mean starting the settings app in the middle of the one boot that
 * has already been going badly.
 */
internal object IncidentReporter {

    /** Well clear of boot, and after the guard's own success window. */
    private const val DELAY_MS = 120_000L

    fun report(decision: BootGuard.Decision.Disable, disabled: Set<FeatureId>) {
        Handler(Looper.getMainLooper()).postDelayed({
            val context = currentApplication()
            if (context == null) {
                Log.w(LOG_TAG, "boot incident not reported: no Context in system_server")
                return@postDelayed
            }

            val extras = Bundle().apply {
                putString(ConfigContract.KEY_INCIDENT_REASON, decision.reason)
                putString(
                    ConfigContract.KEY_INCIDENT_FEATURES,
                    disabled.joinToString { it.value },
                )
                putInt(ConfigContract.KEY_INCIDENT_FAILURES, decision.consecutiveFailures)
            }

            runCatching {
                context.contentResolver.call(
                    Uri.parse(ConfigContract.URI),
                    ConfigContract.METHOD_REPORT_INCIDENT,
                    null,
                    extras,
                )
            }.onSuccess {
                Log.i(LOG_TAG, "boot incident reported to the settings app")
            }.onFailure {
                Log.w(LOG_TAG, "boot incident not reported: ${it.javaClass.simpleName}: ${it.message}")
            }
        }, DELAY_MS)
    }

    private fun currentApplication(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    }.getOrNull()
}
