package dev.lostxposed.core.compat

import dev.lostxposed.core.api.Environment
import dev.lostxposed.core.api.Oem
import dev.lostxposed.core.api.Reason

/**
 * Declarative environment matching, so version handling is a data table rather than
 * `if (SDK_INT >= ...)` scattered through feature code. A new Android release should be new
 * rows, not a refactor.
 *
 *     val clockStrategy = compatTable {
 *         match(sdk = 35..37, Oem.AOSP, Oem.PIXEL) using ClockViewStrategy
 *         match(sdk = 35..37, Oem.ONE_UI)          using OneUiClockStrategy
 *         fallbackUnsupported()
 *     }
 */
class CompatTable<S> internal constructor(
    private val rules: List<Rule<S>>,
    private val fallback: S?,
    private val fallbackReason: Reason,
) {
    internal data class Rule<S>(val sdk: IntRange, val oems: Set<Oem>?, val strategy: S) {
        fun matches(env: Environment): Boolean =
            env.sdkInt in sdk && (oems == null || env.oem in oems)
    }

    sealed interface Resolution<out S> {
        data class Matched<S>(val strategy: S) : Resolution<S>
        data class None(val reason: Reason) : Resolution<Nothing>
    }

    fun resolve(env: Environment): Resolution<S> {
        rules.firstOrNull { it.matches(env) }?.let { return Resolution.Matched(it.strategy) }
        return fallback?.let { Resolution.Matched(it) } ?: Resolution.None(fallbackReason)
    }

    /** Convenience for call sites that only need the strategy or null. */
    fun resolveOrNull(env: Environment): S? =
        (resolve(env) as? Resolution.Matched)?.strategy
}

class CompatTableBuilder<S> {
    private val rules = mutableListOf<CompatTable.Rule<S>>()
    private var fallback: S? = null
    private var fallbackReason: Reason = Reason.NO_STRATEGY_FOR_ENV

    class Pending<S>(val sdk: IntRange, val oems: Set<Oem>?)

    fun match(sdk: IntRange, vararg oem: Oem): Pending<S> =
        Pending(sdk, oem.toSet().ifEmpty { null })

    infix fun Pending<S>.using(strategy: S) {
        rules += CompatTable.Rule(sdk, oems, strategy)
    }

    fun fallback(strategy: S) {
        fallback = strategy
    }

    /** No strategy for unknown environments: produces a diagnosable reason, not a no-op. */
    fun fallbackUnsupported(reason: Reason = Reason.NO_STRATEGY_FOR_ENV) {
        fallback = null
        fallbackReason = reason
    }

    internal fun build() = CompatTable(rules.toList(), fallback, fallbackReason)
}

fun <S> compatTable(block: CompatTableBuilder<S>.() -> Unit): CompatTable<S> =
    CompatTableBuilder<S>().apply(block).build()
