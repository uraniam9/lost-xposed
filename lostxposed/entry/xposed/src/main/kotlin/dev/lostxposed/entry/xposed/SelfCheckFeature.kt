package dev.lostxposed.entry.xposed

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
import dev.lostxposed.core.api.installHook
import dev.lostxposed.core.api.methodPresent
import io.github.libxposed.api.XposedInterface

/**
 * Makes the UI able to state truthfully whether the framework loaded us, instead of guessing
 * from settings. The classic Xposed self-check idiom, and the only honest way for an ordinary
 * app process to know.
 *
 * Targets by class NAME rather than by type: :app depends on :entry, so a compile-time
 * reference in the other direction would be a dependency cycle.
 */
class SelfCheckFeature : Injection {

    override val descriptor = FeatureDescriptor(
        id = FeatureId("core.selfcheck"),
        name = "Self check",
        description = "Reports module activity to our own UI process.",
        category = Category.CORE,
        injects = setOf(ProcessTarget.Self),
        riskTier = RiskTier.SAFE,
        userFacing = false,
    )

    override fun probes(env: HookEnv) = listOf(
        methodPresent("hook point", ACTIVITY, METHOD),
    )

    override fun install(env: HookEnv): InstallResult {
        val target = env.findMethodOrNull(ACTIVITY, METHOD)
            ?: return InstallResult.Failed(Reason.METHOD_NOT_FOUND)
        return runCatching {
            InstallResult.Installed(listOf(env.installHook(target, "selfcheck", AlwaysTrue)))
        }.getOrElse { InstallResult.Failed(Reason.HOOK_FAILED, it) }
    }

    private object AlwaysTrue : XposedInterface.Hooker {
        // Deliberately does not call proceed(): the original always returns false.
        override fun intercept(chain: XposedInterface.Chain): Any = true
    }

    companion object {
        const val ACTIVITY = "dev.lostxposed.ModuleStatus"
        const val METHOD = "isActive"
        val FACTORY = InjectionFactory { SelfCheckFeature() }
    }
}
