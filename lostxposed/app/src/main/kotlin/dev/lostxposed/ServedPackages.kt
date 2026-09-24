package dev.lostxposed

import android.content.Context
import java.io.File

/**
 * Which packages have actually read their settings through the provider.
 *
 * This is evidence, not a scope list. The app cannot ask the framework what is ticked in its
 * manager — no API offers that, and guessing would be worse than saying nothing. What it can
 * know is which processes have come and asked, which is a stronger fact anyway: a package
 * here is one where the module is loaded *and* the settings channel works.
 *
 * Persisted, because processes ask at their own start and this app is usually not running
 * then. A tick that vanished every time the app was reopened would be useless.
 */
object ServedPackages {

    private const val FILE = "served-packages.txt"

    fun record(context: Context, packages: Set<String>) {
        if (packages.isEmpty()) return
        runCatching {
            val known = read(context)
            if (known.containsAll(packages)) return
            File(context.filesDir, FILE).writeText((known + packages).sorted().joinToString("\n"))
        }
    }

    fun read(context: Context): Set<String> = runCatching {
        File(context.filesDir, FILE)
            .takeIf { it.exists() }
            ?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    }.getOrDefault(emptySet())
}
