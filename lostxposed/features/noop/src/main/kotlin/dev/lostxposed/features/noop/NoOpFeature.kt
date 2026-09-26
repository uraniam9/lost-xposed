package dev.lostxposed.features.noop

import dev.lostxposed.core.api.Category
import dev.lostxposed.core.api.FeatureDescriptor
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.api.HookEnv
import dev.lostxposed.core.api.Injection
import dev.lostxposed.core.api.InjectionFactory
import dev.lostxposed.core.api.InstallResult
import dev.lostxposed.core.api.ProcessTarget
import dev.lostxposed.core.api.Reason
import dev.lostxposed.core.api.RiskTier
import dev.lostxposed.core.api.classPresent
import dev.lostxposed.core.api.installHook
import dev.lostxposed.core.api.methodPresent
import io.github.libxposed.api.XposedInterface

/**
 * The reference feature. It changes nothing observable: it counts invocations and returns
 * the original result untouched.
 *
 * Its whole job is to be the Phase 3 exit criterion: prove that the engine can select a
 * feature for a process, probe it, install it, report diagnostics, and detach it again
 * without a reboot. Any real feature that cannot do those five things has a bug in the spine,
 * not in itself.
 *
 * It targets SystemUI because that is the surface spike-01 already validated.
 */
class NoOpFeature : Injection {

    override val descriptor = FeatureDescriptor(
        id = ID,
        name = "No-op reference",
        description = "Counts clock repaints and changes nothing. Validates the engine.",
        category = Category.CORE,
        injects = setOf(ProcessTarget.SystemUi),
        riskTier = RiskTier.SYSTEMUI_RESTART,
        userFacing = false,
    )

    override fun probes(env: HookEnv) = listOf(
        classPresent("clock class", CLOCK_CLASS, "SystemUI layout differs on this build"),
        methodPresent("clock method", CLOCK_CLASS, CLOCK_METHOD),
    )

    override fun install(env: HookEnv): InstallResult {
        val target = env.findMethodOrNull(CLOCK_CLASS, CLOCK_METHOD)
            ?: return InstallResult.Failed(Reason.METHOD_NOT_FOUND)

        return runCatching {
            InstallResult.Installed(listOf(env.installHook(target, "noop.counter", Counter)))
        }.getOrElse { InstallResult.Failed(Reason.HOOK_FAILED, it) }
    }

    /**
     * Counting proves the hook actually executes. Without it, "installed" and "installed but
     * never called" are indistinguishable, the trap spike-01 avoided the same way.
     */
    object Counter : XposedInterface.Hooker {
        @Volatile
        var invocations: Int = 0
            private set

        override fun intercept(chain: XposedInterface.Chain): Any? {
            invocations++
            return chain.proceed()
        }
    }

    companion object {
        val ID = FeatureId("core.noop")
        const val CLOCK_CLASS = "com.android.systemui.statusbar.policy.Clock"
        const val CLOCK_METHOD = "getSmallTime"

        val FACTORY = InjectionFactory { NoOpFeature() }
    }
}
