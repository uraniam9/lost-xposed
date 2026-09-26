package dev.lostxposed.core.api

/**
 * A single live check, and the reason it failed.
 *
 * Lives in core:api rather than core:diagnostics because [Injection] declares its probes;
 * putting them in diagnostics would make api depend on it, and diagnostics already depends
 * on api. The *runner* stays in diagnostics; this is only the contract.
 */
sealed interface Outcome {
    data class Pass(val detail: String) : Outcome
    data class Warn(val detail: String, val hint: String? = null) : Outcome
    data class Fail(val reason: Reason, val hint: String? = null) : Outcome

    /** Not applicable here: ends the chain quietly rather than as a failure. */
    data class Skip(val reason: Reason) : Outcome
}

interface Probe {
    val label: String
    fun check(env: HookEnv): Outcome
}

fun probe(label: String, check: (HookEnv) -> Outcome): Probe {
    val probeLabel = label
    return object : Probe {
        override val label = probeLabel
        override fun check(env: HookEnv) = check(env)
    }
}

/** Passes when [className] is loadable in the target process. */
fun classPresent(label: String, className: String, hint: String? = null): Probe =
    probe(label) { env ->
        env.findClassOrNull(className)
            ?.let { Outcome.Pass(className) }
            ?: Outcome.Fail(Reason.CLASS_NOT_FOUND, hint ?: "$className absent in ${env.packageName}")
    }

/** Passes when at least one method named [name] exists on [className]. */
fun methodPresent(label: String, className: String, name: String): Probe =
    probe(label) { env ->
        val matches = env.findClassOrNull(className)?.declaredMethods?.filter { it.name == name }
        when {
            matches == null -> Outcome.Fail(Reason.CLASS_NOT_FOUND, "$className absent")
            matches.isEmpty() -> Outcome.Fail(Reason.METHOD_NOT_FOUND, "$className.$name() absent")
            else -> Outcome.Pass("${matches.size} overload(s)")
        }
    }

/** Ends the chain quietly when this process is not one the feature applies to. */
fun applicableWhen(label: String, reason: Reason, predicate: (HookEnv) -> Boolean): Probe =
    probe(label) { env -> if (predicate(env)) Outcome.Pass("applies here") else Outcome.Skip(reason) }

/** Ends the chain quietly unless the feature has been switched on in config. */
fun enabledWhen(label: String, feature: FeatureId, key: String, default: Boolean = true): Probe =
    probe(label) { env ->
        if (env.configFor(feature).boolean(key, default)) {
            Outcome.Pass("enabled")
        } else {
            Outcome.Skip(Reason.DISABLED_BY_USER)
        }
    }
