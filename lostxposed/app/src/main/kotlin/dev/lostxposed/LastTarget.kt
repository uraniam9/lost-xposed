package dev.lostxposed

import android.content.Context
import dev.lostxposed.core.api.FeatureId
import java.io.File

/**
 * The last package typed into "Applies to" for each feature, remembered so a per-package rule
 * does not look like it vanished the moment you leave the screen.
 *
 * "Applies to" always used to reopen on the * default, so a rule saved for one specific app
 * was invisible the next time the screen was opened: still on disk, still doing its job, but
 * showing as blank and unset until you retyped the exact package name. That read as the app
 * having reset itself.
 *
 * Deliberately outside [ConfigWriter]'s own store. That flat key space is served to hooked
 * processes over the provider, and which package you last typed into a text field is not a
 * setting any hook has a use for.
 */
object LastTarget {

    private const val FILE = "last-targets.txt"

    fun get(context: Context, feature: FeatureId): String? =
        entries(context)[feature.value]

    fun set(context: Context, feature: FeatureId, target: String) {
        runCatching {
            val updated = entries(context) + (feature.value to target)
            File(context.filesDir, FILE).writeText(
                updated.entries.joinToString("\n") { (k, v) -> "$k\t$v" },
            )
        }
    }

    private fun entries(context: Context): Map<String, String> = runCatching {
        File(context.filesDir, FILE)
            .takeIf { it.exists() }
            ?.readLines()
            .orEmpty()
            .mapNotNull { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2 && parts[0].isNotEmpty()) parts[0] to parts[1] else null
            }
            .toMap()
    }.getOrDefault(emptyMap())
}
