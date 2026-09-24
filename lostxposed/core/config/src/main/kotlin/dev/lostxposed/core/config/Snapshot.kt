package dev.lostxposed.core.config

/**
 * The flat `key=value` mirror of the config: one line per setting, written by the UI process
 * and read back by hooked processes.
 *
 * Deliberately **not** `java.util.Properties`. Properties would do the escaping correctly, but
 * `store()` emits a timestamp comment and iterates in hash order, so the same config produces
 * a different file every write -- which makes "did this actually change on disk?" impossible
 * to answer by looking, and that question is exactly the one that took days to answer here.
 * This format is sorted and byte-stable: the same config always produces the same file.
 *
 * Escaping covers the escape character itself, `=`, CR and LF, in both keys and values, so a
 * notification keyword containing an `=` cannot split a line in the wrong place or silently
 * corrupt the following key.
 */
object Snapshot {

    /** Sorted, so an unchanged config produces a byte-identical file. */
    fun format(values: Map<String, Any?>): String = buildString {
        values.entries
            .mapNotNull { (k, v) -> v?.let { k to it.toString() } }
            .sortedBy { it.first }
            .forEach { (k, v) -> append(escape(k)).append('=').append(escape(v)).append('\n') }
    }

    fun parse(text: String): Map<String, String> = buildMap {
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            split(line)?.let { (k, v) -> put(k, v) }
        }
    }

    /** Split at the first `=` that is not itself escaped. */
    private fun split(line: String): Pair<String, String>? {
        var i = 0
        while (i < line.length) {
            when (line[i]) {
                ESCAPE -> i += 2
                '=' -> return unescape(line.substring(0, i)) to unescape(line.substring(i + 1))
                else -> i++
            }
        }
        return null
    }

    private fun escape(text: String): String = buildString(text.length) {
        text.forEach { c ->
            when (c) {
                ESCAPE -> append(ESCAPE).append(ESCAPE)
                '=' -> append(ESCAPE).append('=')
                '\n' -> append(ESCAPE).append('n')
                '\r' -> append(ESCAPE).append('r')
                else -> append(c)
            }
        }
    }

    /** A trailing lone escape is kept as itself rather than swallowing the end of the line. */
    private fun unescape(text: String): String = buildString(text.length) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != ESCAPE || i == text.lastIndex) {
                append(c)
                i++
            } else {
                when (val next = text[i + 1]) {
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(next)
                }
                i += 2
            }
        }
    }

    private const val ESCAPE = '\\'
}
