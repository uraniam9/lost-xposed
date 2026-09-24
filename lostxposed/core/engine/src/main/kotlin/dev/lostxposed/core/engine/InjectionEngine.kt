package dev.lostxposed.core.engine

import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.api.HookEnv
import dev.lostxposed.core.api.InstallResult
import dev.lostxposed.core.api.Reason
import dev.lostxposed.core.diagnostics.DiagnosticsReport
import dev.lostxposed.core.diagnostics.ProbeChain
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Selects the features that apply to this process, probes them, installs them, and keeps the
 * hook handles so they can be undone without a reboot.
 *
 * One engine per process. It never talks to another process.
 */
class InjectionEngine(
    private val registry: FeatureRegistry,
    private val isEnabled: (FeatureId) -> Boolean = { true },
) {

    private val handles = ConcurrentHashMap<FeatureId, CopyOnWriteArrayList<XposedInterface.HookHandle>>()

    fun onProcessLoaded(env: HookEnv, ctx: ProcessContext): DiagnosticsReport {
        val report = DiagnosticsReport("LostXposed — ${ctx.packageName}")
        report.info("Process", "${ctx.processName} (pid ${android.os.Process.myPid()})")
        report.info("Environment", env.environment.toString())
        report.info(
            "Capabilities",
            env.environment.framework.capabilityNames().joinToString(", ").ifEmpty { "none" },
        )

        val applicable = registry.registrations.filter {
            TargetMatcher.anyMatches(it.descriptor.injects, ctx)
        }
        if (applicable.isEmpty()) {
            report.info("Features", "none target this process")
            return report
        }

        applicable.forEach { registration ->
            val descriptor = registration.descriptor

            if (!isEnabled(descriptor.id)) {
                report.info(descriptor.name, Reason.DISABLED_BY_USER.message)
                return@forEach
            }

            val injection = registration.factory.create()

            // Probe chain first. Its entries are what make the report diagnosable — the user
            // sees which link broke, not merely that something did.
            report.info(descriptor.name, "${descriptor.category} · ${descriptor.stability}")
            val probes = runCatching { injection.probes(env) }.getOrDefault(emptyList())
            val outcome = ProbeChain(probes).runInto(report, env, prefix = PROBE_INDENT)
            if (outcome is ProbeChain.Result.Stopped) return@forEach

            val result = runCatching { injection.install(env) }.getOrElse {
                InstallResult.Failed(Reason.HOOK_FAILED, it)
            }
            when (result) {
                is InstallResult.Installed -> {
                    handles.getOrPut(descriptor.id) { CopyOnWriteArrayList() }
                        .addAll(result.handles)
                    report.pass(
                        "${PROBE_INDENT}installed",
                        "${result.handles.size} hook(s): " +
                            result.handles.joinToString(", ") { it.id ?: "unnamed" },
                    )
                }

                is InstallResult.Failed -> report.fail(
                    "${PROBE_INDENT}install",
                    result.reason.message +
                        (result.thrown?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""),
                )

                // INFO, not a failure: the feature works, it is simply not configured for
                // this package. DisplayProfiles targets every app, so this is the common case.
                is InstallResult.Skipped -> report.info("${PROBE_INDENT}install", result.reason.message)
            }
        }
        return report
    }

    /**
     * Undo every hook this feature installed in this process. Real because
     * `HookHandle.unhook()` was confirmed on device — see spike-01.
     *
     * @return how many hooks were removed.
     */
    fun disable(id: FeatureId): Int {
        val installed = handles.remove(id) ?: return 0
        var removed = 0
        installed.forEach { handle ->
            runCatching { handle.unhook() }.onSuccess { removed++ }
        }
        return removed
    }

    fun activeFeatures(): Set<FeatureId> = handles.keys.toSet()

    fun hookCount(id: FeatureId): Int = handles[id]?.size ?: 0

    private companion object {
        const val PROBE_INDENT = "  "
    }
}
