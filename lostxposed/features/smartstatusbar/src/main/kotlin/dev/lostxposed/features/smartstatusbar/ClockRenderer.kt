package dev.lostxposed.features.smartstatusbar

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Turns settings into the text the clock shows.
 *
 * Extracted from the hook so the settings screen can render exactly the same thing. Without
 * it, checking whether a mixer line does what you meant costs a SystemUI restart and a walk
 * back to the status bar, and you find out what `{seconds}` looks like fifteen seconds after
 * you stopped caring.
 *
 * The date formatters are per-instance because `SimpleDateFormat` is not thread safe and the
 * clock repaints from more than one place.
 */
class ClockRenderer(private val settings: SmartStatusBarFeature.Settings) {

    private fun formatter(pattern: String): SimpleDateFormat? =
        runCatching { SimpleDateFormat(pattern, Locale.getDefault()) }.getOrNull()

    private val custom = formatter(settings.pattern)
    private val hhmm = formatter("HH:mm")
    private val h12 = formatter("h:mm a")
    private val secs = formatter("ss")
    private val dayName = formatter("EEE")
    private val dateShort = formatter("d MMM")

    /** One piece of a mixer line. Null leaves the token on screen, which is the point. */
    private fun piece(token: String, now: Date, battery: String): String? = when (token) {
        "fuzzy" -> FuzzyTime.format(compact = settings.compact)
        "time" -> hhmm?.format(now)
        "time12" -> h12?.format(now)
        "seconds" -> secs?.format(now)
        "day" -> dayName?.format(now)
        "date" -> dateShort?.format(now)
        "battery" -> battery
        else -> null
    }

    /**
     * The finished text, styled, or null when the style is `system` and there is nothing to
     * say — the caller then leaves whatever the clock already had.
     */
    fun text(
        now: Date = Calendar.getInstance().time,
        battery: String = BatteryProvider.percent(),
        fallback: CharSequence? = null,
    ): CharSequence? {
        val base = when (settings.style) {
            SmartStatusBarFeature.Style.FUZZY -> FuzzyTime.format(compact = settings.compact)
            SmartStatusBarFeature.Style.CUSTOM -> custom?.format(now)
            SmartStatusBarFeature.Style.MIXER ->
                ClockTemplate.expand(settings.template) { piece(it, now, battery) }

            // Still restyled: someone may want nothing but a condensed font.
            SmartStatusBarFeature.Style.SYSTEM -> fallback
        } ?: return null

        // The mixer already says where the battery goes, so appending it again would put it
        // on screen twice.
        val withBattery =
            if (settings.showBattery && settings.style != SmartStatusBarFeature.Style.MIXER) {
                "$base $battery"
            } else {
                base
            }

        return settings.appearance.apply(withBattery)
    }
}
