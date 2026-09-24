package dev.lostxposed.features.smartstatusbar

import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock

/**
 * Battery percentage for the clock suffix.
 *
 * Cached for thirty seconds. The clock repaints far more often than the battery meaningfully
 * changes, and this runs on SystemUI's UI thread — querying a system service on every repaint
 * would be a jank source for a value that moves once a minute at best.
 */
object BatteryProvider {

    private const val CACHE_MS = 30_000L

    @Volatile private var manager: BatteryManager? = null
    @Volatile private var cached: String = ""
    @Volatile private var readAt: Long = 0

    fun percent(): String {
        val now = SystemClock.elapsedRealtime()
        if (now - readAt < CACHE_MS && cached.isNotEmpty()) return cached

        val level = runCatching {
            manager() ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrNull() ?: return cached

        if (level in 0..100) {
            cached = "$level%"
            readAt = now
        }
        return cached
    }

    private fun manager(): BatteryManager? {
        manager?.let { return it }
        val context = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull() ?: return null
        return runCatching { context.getSystemService(BatteryManager::class.java) }
            .getOrNull()
            ?.also { manager = it }
    }
}
