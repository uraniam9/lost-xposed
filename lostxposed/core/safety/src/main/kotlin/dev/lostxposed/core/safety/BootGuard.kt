package dev.lostxposed.core.safety

import android.os.Handler
import android.os.Looper
import dev.lostxposed.core.api.FeatureId
import java.io.File

/**
 * Stops a bad `system_server` hook from turning into a bootloop the user can only escape
 * with a factory reset.
 *
 * The rule from the architecture doc is that nothing touches `system_server` before this
 * exists. This is that.
 *
 * How it works: drop a marker before installing risky hooks, and clear it once the boot has
 * plainly succeeded. Finding a marker still present at the next `system_server` start means
 * the previous boot did not get that far, so the risky features are disabled for this boot.
 *
 * **Fails closed.** If the marker cannot be written — SELinux denial, read-only filesystem,
 * anything — risky features are disabled rather than run unprotected. A crash-detector that
 * cannot detect crashes is worse than no feature at all, because it invites the risk while
 * providing none of the protection.
 */
class BootGuard(private val marker: File, private val clock: () -> Long = System::currentTimeMillis) {

    sealed interface Decision {
        /** Safe to install risky hooks. */
        data object Proceed : Decision

        /** Install nothing risky this boot, and say why. */
        data class Disable(val reason: String, val consecutiveFailures: Int) : Decision
    }

    fun evaluate(riskyFeatures: Set<FeatureId>): Decision {
        if (riskyFeatures.isEmpty()) return Decision.Proceed

        val previous = read()

        if (previous != null) {
            val failures = previous.failures + 1
            val reason = if (failures >= FAILURE_LIMIT) {
                "previous $failures boots failed with these features enabled"
            } else {
                "previous boot did not complete with these features enabled"
            }
            // Record the higher failure count so a repeated crash escalates rather than
            // oscillating between disabled and enabled.
            write(State(failures, riskyFeatures))
            return Decision.Disable(reason, failures)
        }

        return if (write(State(0, riskyFeatures))) {
            Decision.Proceed
        } else {
            Decision.Disable("boot marker is not writable at ${marker.path}", 0)
        }
    }

    /**
     * Clears the marker once boot has plainly succeeded.
     *
     * Survival is the signal. A device that bootloops never reaches this, and a timer needs no
     * cooperation from any particular Android version — which matters because the internals
     * that would signal "boot complete" move between releases and this code must not.
     */
    fun scheduleSuccessSignal(delayMs: Long = SUCCESS_DELAY_MS, onCleared: () -> Unit = {}) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (clearMarker()) onCleared()
        }, delayMs)
    }

    fun clearMarker(): Boolean = runCatching { !marker.exists() || marker.delete() }.getOrDefault(false)

    fun consecutiveFailures(): Int = read()?.failures ?: 0

    private data class State(val failures: Int, val features: Set<FeatureId>)

    private fun read(): State? = runCatching {
        if (!marker.exists()) return null
        val lines = marker.readLines()
        val failures = lines.firstOrNull { it.startsWith(KEY_FAILURES) }
            ?.substringAfter('=')?.trim()?.toIntOrNull() ?: 0
        val features = lines.firstOrNull { it.startsWith(KEY_FEATURES) }
            ?.substringAfter('=')?.split(',')?.filter { it.isNotBlank() }?.map { FeatureId(it.trim()) }
            ?.toSet().orEmpty()
        State(failures, features)
    }.getOrNull()

    private fun write(state: State): Boolean = runCatching {
        marker.parentFile?.mkdirs()
        marker.writeText(
            buildString {
                appendLine("$KEY_FAILURES=${state.failures}")
                appendLine("$KEY_FEATURES=${state.features.joinToString(",") { it.value }}")
                appendLine("$KEY_AT=${clock()}")
            },
        )
        marker.exists()
    }.getOrDefault(false)

    companion object {
        /** system_server runs as the system user and can write here. */
        const val DEFAULT_PATH = "/data/system/lostxposed-boot.marker"

        /** Disable outright, rather than retry, once this many boots have failed. */
        const val FAILURE_LIMIT = 2

        /** Long enough that a bootloop cannot reach it, short enough to clear on a real boot. */
        const val SUCCESS_DELAY_MS = 90_000L

        private const val KEY_FAILURES = "failures"
        private const val KEY_FEATURES = "features"
        private const val KEY_AT = "at"

        fun default() = BootGuard(File(DEFAULT_PATH))
    }
}
