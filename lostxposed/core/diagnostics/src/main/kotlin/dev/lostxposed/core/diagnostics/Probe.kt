package dev.lostxposed.core.diagnostics

import dev.lostxposed.core.api.HookEnv
import dev.lostxposed.core.api.Outcome
import dev.lostxposed.core.api.Probe
import dev.lostxposed.core.api.Reason

/**
 * Runs a feature's probes in order and stops at the first that ends the chain, so the user
 * sees the first broken link rather than a wall of red.
 *
 * Three rules make the output trustworthy:
 *  - probes execute live; nothing is cached, and no probe may report Pass from belief
 *  - a probe that throws becomes a Fail, never a silent skip
 *  - "not applicable here" is [Outcome.Skip], never Fail
 */
class ProbeChain(private val probes: List<Probe>) {

    constructor(vararg probes: Probe) : this(probes.toList())

    sealed interface Result {
        data object Ready : Result
        data class Stopped(val reason: Reason, val benign: Boolean) : Result
    }

    fun runInto(report: DiagnosticsReport, env: HookEnv, prefix: String = ""): Result {
        probes.forEach { probe ->
            val outcome = runCatching { probe.check(env) }.getOrElse { thrown ->
                Outcome.Fail(Reason.HOOK_FAILED, "${thrown.javaClass.simpleName}: ${thrown.message}")
            }
            val label = "$prefix${probe.label}"

            when (outcome) {
                is Outcome.Pass -> report.pass(label, outcome.detail)
                is Outcome.Warn -> report.warn(label, outcome.detail, outcome.hint)

                is Outcome.Skip -> {
                    report.info(label, outcome.reason.message)
                    return Result.Stopped(outcome.reason, benign = true)
                }

                is Outcome.Fail -> {
                    report.fail(label, outcome.reason.message, outcome.hint)
                    return Result.Stopped(outcome.reason, benign = false)
                }
            }
        }
        return Result.Ready
    }
}
