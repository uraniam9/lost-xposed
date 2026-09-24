package dev.lostxposed.features.powerinspector

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Counts who asked for what, keyed by calling uid.
 *
 * Attribution uses `Binder.getCallingUid()` rather than parsing arguments. Wakelock and alarm
 * method signatures churn between Android releases; the binder caller identity does not. That
 * makes this survive version changes that would break argument-position parsing.
 */
object PowerLedger {

    data class Row(val uid: Int, val wakelocks: Long, val alarms: Long)

    private val wakelocks = ConcurrentHashMap<Int, AtomicLong>()
    private val alarms = ConcurrentHashMap<Int, AtomicLong>()

    fun recordWakelock(uid: Int) {
        wakelocks.computeIfAbsent(uid) { AtomicLong() }.incrementAndGet()
    }

    fun recordAlarm(uid: Int) {
        alarms.computeIfAbsent(uid) { AtomicLong() }.incrementAndGet()
    }

    fun snapshot(): List<Row> =
        (wakelocks.keys + alarms.keys)
            .distinct()
            .map { uid ->
                Row(
                    uid = uid,
                    wakelocks = wakelocks[uid]?.get() ?: 0L,
                    alarms = alarms[uid]?.get() ?: 0L,
                )
            }
            .sortedByDescending { it.wakelocks + it.alarms }

    fun reset() {
        wakelocks.clear()
        alarms.clear()
    }

    fun render(limit: Int = 15): String {
        val rows = snapshot().take(limit)
        if (rows.isEmpty()) return "no wakelock or alarm activity recorded yet"
        return buildString {
            appendLine("uid        wakelocks   alarms")
            rows.forEach { appendLine("%-10d %9d %8d".format(it.uid, it.wakelocks, it.alarms)) }
        }
    }
}
