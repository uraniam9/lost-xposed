package dev.lostxposed

import android.content.Context
import android.os.Bundle
import dev.lostxposed.core.config.ConfigContract
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the boot guard did, kept so the app can say so the next time it is opened.
 *
 * The guard runs in `system_server` long before anything here exists, and by the time you
 * unlock the phone the only evidence is a feature that quietly stopped working. Somebody whose
 * phone just failed to boot deserves to be told what happened and given a way to report it,
 * not left to work out on their own why their notification rules went away.
 */
object BootIncidents {

    private const val FILE = "boot-incidents.log"
    private const val LIMIT = 20

    data class Incident(
        val at: Long,
        val reason: String,
        val features: String,
        val failures: Int,
    ) {
        fun render(): String {
            val when_ = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(at))
            return "$when_  $reason\n  disabled: $features\n  consecutive failed boots: $failures"
        }
    }

    /** Called by the provider, on the binder thread, with a platform caller already checked. */
    fun record(context: Context, extras: Bundle?) {
        val incident = Incident(
            at = System.currentTimeMillis(),
            reason = extras?.getString(ConfigContract.KEY_INCIDENT_REASON) ?: "unknown",
            features = extras?.getString(ConfigContract.KEY_INCIDENT_FEATURES) ?: "",
            failures = extras?.getInt(ConfigContract.KEY_INCIDENT_FAILURES) ?: 0,
        )

        runCatching {
            val file = File(context.filesDir, FILE)
            val kept = (read(context) + incident).takeLast(LIMIT)
            file.writeText(kept.joinToString("\n") { line(it) })
        }
    }

    fun read(context: Context): List<Incident> = runCatching {
        File(context.filesDir, FILE)
            .takeIf { it.exists() }
            ?.readLines()
            ?.mapNotNull(::parse)
            .orEmpty()
    }.getOrDefault(emptyList())

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    /** Tab separated, so a reason containing anything cannot break the next field. */
    private fun line(i: Incident) =
        listOf(i.at.toString(), i.failures.toString(), i.features, i.reason)
            .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    private fun parse(raw: String): Incident? {
        val parts = raw.split('\t')
        if (parts.size < 4) return null
        return Incident(
            at = parts[0].toLongOrNull() ?: return null,
            failures = parts[1].toIntOrNull() ?: 0,
            features = parts[2],
            reason = parts[3],
        )
    }
}
