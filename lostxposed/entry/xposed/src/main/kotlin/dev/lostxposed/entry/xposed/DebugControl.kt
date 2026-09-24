package dev.lostxposed.entry.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.engine.InjectionEngine
import dev.lostxposed.core.engine.LOG_TAG
import dev.lostxposed.features.noop.NoOpFeature
import dev.lostxposed.features.powerinspector.PowerLedger

/**
 * A temporary control channel so a hooked process can be driven from adb:
 *
 *     adb shell am broadcast -a dev.lostxposed.DISABLE --es feature core.noop
 *     adb shell am broadcast -a dev.lostxposed.STATUS
 *
 * This exists to make the Phase 3 exit criterion demonstrable — "installs, detaches, and
 * reports diagnostics" is not provable without a way to ask for the detach.
 *
 * It is NOT the real control path. That is `getRemotePreferences()` in core:config, which is
 * confirmed available but not yet designed. Delete this once that lands.
 */
internal object DebugControl {

    const val ACTION_DISABLE = "dev.lostxposed.DISABLE"
    const val ACTION_STATUS = "dev.lostxposed.STATUS"
    const val ACTION_POWER = "dev.lostxposed.POWER"

    private const val CONTEXT_DELAY_MS = 6_000L

    fun install(engine: InjectionEngine, packageName: String) {
        // onPackageLoaded runs before the Application exists, so there is no Context yet.
        // Waiting is cruder than hooking Application.onCreate, but this is throwaway.
        Handler(Looper.getMainLooper()).postDelayed({
            val context = currentApplication()
            if (context == null) {
                Log.w(LOG_TAG, "[$packageName] no Context — debug control unavailable")
                return@postDelayed
            }
            runCatching {
                context.registerReceiver(
                    receiver(engine, packageName),
                    IntentFilter().apply {
                        addAction(ACTION_DISABLE)
                        addAction(ACTION_STATUS)
                        addAction(ACTION_POWER)
                    },
                    Context.RECEIVER_EXPORTED,
                )
                Log.i(LOG_TAG, "[$packageName] debug control ready")
            }.onFailure { Log.w(LOG_TAG, "[$packageName] debug control failed: $it") }
        }, CONTEXT_DELAY_MS)
    }

    private fun receiver(engine: InjectionEngine, packageName: String) =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_DISABLE -> {
                        val raw = intent.getStringExtra("feature")
                        if (raw == null) {
                            Log.w(LOG_TAG, "[$packageName] DISABLE needs --es feature <id>")
                            return
                        }
                        val removed = engine.disable(FeatureId(raw))
                        Log.i(
                            LOG_TAG,
                            "[$packageName] disable($raw) removed $removed hook(s); " +
                                "active now: ${engine.activeFeatures().joinToString().ifEmpty { "none" }}",
                        )
                    }

                    // The counter is what distinguishes "installed" from "installed but never
                    // called" — the same trap spike-01 avoided.
                    ACTION_STATUS -> Log.i(
                        LOG_TAG,
                        "[$packageName] active: " +
                            engine.activeFeatures().joinToString { "$it(${engine.hookCount(it)})" }
                                .ifEmpty { "none" } +
                            " | noop invocations: ${NoOpFeature.Counter.invocations}",
                    )

                    ACTION_POWER -> PowerLedger.render()
                        .lineSequence()
                        .forEach { Log.i(LOG_TAG, "[$packageName] $it") }
                }
            }
        }

    private fun currentApplication(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    }.getOrNull()
}
