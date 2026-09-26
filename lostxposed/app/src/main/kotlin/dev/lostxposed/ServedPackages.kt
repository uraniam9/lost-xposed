package dev.lostxposed

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import kotlin.math.abs

/**
 * Which packages have actually read their settings through the provider, and in which boot.
 *
 * This is evidence, not a scope list. The app cannot ask the framework what is ticked in its
 * manager: no API offers that, and guessing would be worse than saying nothing. What it can
 * know is which processes have come and asked, which is a stronger fact anyway: a package
 * here is one where the module is loaded *and* the settings channel works.
 *
 * Persisted, because processes ask at their own start and this app is usually not running
 * then. A tick that vanished every time the app was reopened would be useless.
 *
 * Counted per boot, because a tick that outlived a reboot was worse than none. SystemUI can
 * come back from a reboot without its settings, and the tick from the boot before said it
 * had them while the status bar showed the plain clock.
 *
 * Kept in device-protected storage, because the provider answers before the first unlock
 * and the normal files dir is still locked then. That read is the one most worth recording.
 *
 * One line per package: the boot it last asked in, a tab, the package. Lines written before
 * boots were tracked have no boot and are ignored. They cannot say whether a reboot happened
 * since, and guessing either way puts a false sentence on the main screen.
 */
object ServedPackages {

    private const val FILE = "served-packages.txt"

    /** Stands in for any boot before the current one, so the file stays one line a package. */
    private const val EARLIER = "-"

    @Synchronized
    fun record(context: Context, packages: Set<String>) {
        if (packages.isEmpty()) return
        runCatching {
            val boot = bootId()
            val (current, before) = entries(context).partition { sameBoot(it.first, boot) }
            val now = current.map { it.second }.toSet()
            if (now.containsAll(packages)) return
            val seen = now + packages
            val earlier = before.map { it.second }.toSet() - seen
            val lines = seen.sorted().map { "$boot\t$it" } + earlier.sorted().map { "$EARLIER\t$it" }
            File(dir(context), FILE).writeText(lines.joinToString("\n"))
        }
    }

    /** Read since the phone last started. The ticks and the status card go by this. */
    fun thisBoot(context: Context): Set<String> {
        val boot = bootId()
        return entries(context).filter { sameBoot(it.first, boot) }.map { it.second }.toSet()
    }

    /** Read in an earlier boot and not since. */
    fun onlyBeforeThisBoot(context: Context): Set<String> {
        val boot = bootId()
        val (current, before) = entries(context).partition { sameBoot(it.first, boot) }
        return before.map { it.second }.toSet() - current.map { it.second }.toSet()
    }

    /** How boots are told apart on this phone, for the diagnostics report. */
    fun method(): String =
        if (kernelBootId != null) "the kernel boot id" else "the clock, to within $SLACK_S seconds"

    private fun entries(context: Context): List<Pair<String, String>> = runCatching {
        File(dir(context), FILE)
            .takeIf { it.exists() }
            ?.readLines()
            .orEmpty()
            .mapNotNull { line ->
                val parts = line.trim().split('\t')
                if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }
    }.getOrDefault(emptyList())

    private fun dir(context: Context): File = context.createDeviceProtectedStorageContext().filesDir

    /**
     * The kernel makes a random id at every boot, and it holds from the first instant to the
     * last, which is exactly the question. Read once per process: it cannot change while the
     * process is alive, and a phone that refuses it should not be asked on every call.
     *
     * BOOT_COUNT looked like the obvious choice and is wrong for this. Measured on the test
     * phone, Android raises it a good while into the boot, after SystemUI has already asked
     * for its settings, so the read that matters most was filed under the boot before.
     */
    private val kernelBootId: String? by lazy {
        val id = runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
        Log.i(TAG, "boots told apart by " + if (id != null) "the kernel boot id" else "the clock")
        id
    }

    /**
     * Names the current boot. Without the kernel id, the moment the boot started, worked out
     * from the two clocks, stands in. That holds for the whole boot unless the clock itself is
     * corrected, which is what the slack in [sameBoot] is for.
     */
    private fun bootId(): String =
        kernelBootId?.let { "$KERNEL$it" }
            ?: "$CLOCK${System.currentTimeMillis() - SystemClock.elapsedRealtime()}"

    /**
     * Two boots cannot start within [SLACK_S] seconds of each other: the first has to get as
     * far as SystemUI asking before anything is recorded at all.
     */
    private fun sameBoot(a: String, b: String): Boolean {
        if (a == b) return true
        if (!a.startsWith(CLOCK) || !b.startsWith(CLOCK)) return false
        val x = a.removePrefix(CLOCK).toLongOrNull() ?: return false
        val y = b.removePrefix(CLOCK).toLongOrNull() ?: return false
        return abs(x - y) <= SLACK_S * 1000L
    }

    private const val KERNEL = "k"
    private const val CLOCK = "c"
    private const val SLACK_S = 10
    private const val TAG = "LostXposed"
}
