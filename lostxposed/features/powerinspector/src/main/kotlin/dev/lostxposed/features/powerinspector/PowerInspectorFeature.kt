package dev.lostxposed.features.powerinspector

import android.os.Binder
import dev.lostxposed.core.api.Category
import dev.lostxposed.core.api.FeatureDescriptor
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.api.HookEnv
import dev.lostxposed.core.api.Injection
import dev.lostxposed.core.api.InjectionFactory
import dev.lostxposed.core.api.InstallResult
import dev.lostxposed.core.api.Outcome
import dev.lostxposed.core.api.ProcessTarget
import dev.lostxposed.core.api.Reason
import dev.lostxposed.core.api.RiskTier
import dev.lostxposed.core.api.Stability
import dev.lostxposed.core.api.installHook
import dev.lostxposed.core.api.probe
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * Read-only wakelock and alarm attribution.
 *
 * Amplify-style *blocking* is deliberately not here. Doze, App Standby buckets and the
 * phantom-process caps did that job years ago, and blocking wakelocks now mostly destabilises
 * apps rather than saving battery. What is still missing is visibility: `batterystats` is
 * neutered for ordinary apps, so nobody can see which app is actually asking for what.
 *
 * Every hook calls `proceed()` unchanged. This feature must never alter behaviour. It runs
 * in `system_server`, where a mistake is a bootloop rather than a crash, which is also why it
 * sits behind the boot guard.
 */
class PowerInspectorFeature : Injection {

    override val descriptor = FeatureDescriptor(
        id = ID,
        name = "Power inspector",
        detail = "Counts how often each app asks the system to keep your phone awake " +
            "(a wakelock), and how often it sets an alarm to wake it up later.\n\n" +
            "It only counts. It does not block anything, deny anything, or change how " +
            "any app behaves, so it cannot break an app that relies on either.\n\n" +
            "The point is to find out which app is draining the battery before you go " +
            "looking for something to fix. Android\u0027s own battery screen tells you " +
            "what used power; this tells you what kept asking.\n\n" +
            "There is nothing to configure. Open this card for the live count, fetched " +
            "straight from system_server. Nothing is saved anywhere: closing the app loses " +
            "nothing but the number on screen, since the real tally lives in system_server " +
            "and keeps counting until the next reboot.",
        description = "Counts wakelock and alarm requests per app. Read-only; blocks nothing.",
        category = Category.POWER,
        injects = setOf(ProcessTarget.SystemServer),
        riskTier = RiskTier.BOOTLOOP,
        stability = Stability.BETA,
    )

    override fun probes(env: HookEnv) = listOf(
        // Warn rather than Fail for each half: losing alarm attribution is a degraded
        // feature, not a broken one, and the chain should carry on to report the other.
        probe("wakelock entry points") {
            val found = wakelockMethods(it)
            if (found.isEmpty()) {
                Outcome.Warn("none on $POWER_SERVICE", "wakelock attribution unavailable")
            } else {
                Outcome.Pass("${found.size} overload(s)")
            }
        },
        probe("alarm entry points") {
            val found = alarmMethods(it)
            if (found.isEmpty()) {
                Outcome.Warn("none on AlarmManagerService", "alarm attribution unavailable")
            } else {
                Outcome.Pass("${found.size} overload(s)")
            }
        },
        probe("at least one target") {
            if (wakelockMethods(it).isEmpty() && alarmMethods(it).isEmpty()) {
                Outcome.Fail(Reason.CLASS_NOT_FOUND, "this build relocates both services")
            } else {
                Outcome.Pass("ready")
            }
        },
    )

    override fun install(env: HookEnv): InstallResult {
        val handles = mutableListOf<XposedInterface.HookHandle>()

        wakelockMethods(env).forEachIndexed { i, m ->
            runCatching { env.installHook(m, "power.wake$i", WakelockCounter) }
                .onSuccess { handles += it }
                .onFailure { env.log("wakelock hook failed on ${m.name}", it) }
        }
        alarmMethods(env).forEachIndexed { i, m ->
            runCatching { env.installHook(m, "power.alarm$i", AlarmCounter) }
                .onSuccess { handles += it }
                .onFailure { env.log("alarm hook failed on ${m.name}", it) }
        }

        if (handles.isEmpty()) return InstallResult.Failed(Reason.HOOK_FAILED)
        env.log("power inspector watching ${handles.size} entry point(s)")
        return InstallResult.Installed(handles)
    }

    /**
     * Matched by name across every overload. Signatures for these change most releases, so
     * binding to one exact form would break on the next Android version for no benefit: the
     * attribution comes from the binder caller, not the arguments.
     */
    private fun wakelockMethods(env: HookEnv): List<Method> =
        env.findClassOrNull(POWER_SERVICE)
            ?.declaredMethods
            ?.filter { it.name.startsWith("acquireWakeLock") }
            .orEmpty()

    private fun alarmMethods(env: HookEnv): List<Method> =
        ALARM_SERVICES.firstNotNullOfOrNull { env.findClassOrNull(it) }
            ?.declaredMethods
            ?.filter { it.name == "setImpl" || it.name == "set" }
            .orEmpty()

    private object WakelockCounter : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            runCatching { PowerLedger.recordWakelock(Binder.getCallingUid()) }
            return chain.proceed()
        }
    }

    private object AlarmCounter : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            runCatching { PowerLedger.recordAlarm(Binder.getCallingUid()) }
            return chain.proceed()
        }
    }

    companion object {
        val ID = FeatureId("core.powerinspector")
        val FACTORY = InjectionFactory { PowerInspectorFeature() }

        private const val POWER_SERVICE = "com.android.server.power.PowerManagerService"

        /** Moved package in Android 12; try both rather than branch on SDK. */
        private val ALARM_SERVICES = listOf(
            "com.android.server.alarm.AlarmManagerService",
            "com.android.server.AlarmManagerService",
        )
    }
}
