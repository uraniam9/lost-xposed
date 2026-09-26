package dev.lostxposed.features.smartstatusbar

import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import dev.lostxposed.core.api.ConfigSource

/**
 * How the clock text looks, applied as spans on the string the clock already returns.
 *
 * Spans rather than touching the `TextView`. The hook replaces the return value of
 * `getSmallTime()`, and a span travels with that value, so nothing reaches into SystemUI's
 * view tree, nothing has to be undone when the feature is disabled, and a repaint from
 * somewhere unexpected cannot leave the clock permanently restyled.
 *
 * Size is a multiplier on whatever the status bar already uses, not an absolute, because the
 * status bar's own size differs per OEM and per display density. It is clamped: a clock large
 * enough to be clipped by the status bar is worse than one that ignored the setting.
 */
data class ClockAppearance(
    val scale: Float = 1f,
    val colour: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val family: String? = null,
) {

    val isDefault: Boolean
        get() = scale == 1f && colour == null && !bold && !italic && family == null

    fun apply(text: CharSequence): CharSequence {
        if (isDefault || text.isEmpty()) return text

        return runCatching {
            SpannableStringBuilder(text).also { out ->
                val end = out.length
                if (scale != 1f) out.setSpan(RelativeSizeSpan(scale), 0, end, SPAN_INCLUSIVE_EXCLUSIVE)
                colour?.let { out.setSpan(ForegroundColorSpan(it), 0, end, SPAN_INCLUSIVE_EXCLUSIVE) }
                styleFlag()?.let { out.setSpan(StyleSpan(it), 0, end, SPAN_INCLUSIVE_EXCLUSIVE) }
                family?.let { out.setSpan(TypefaceSpan(it), 0, end, SPAN_INCLUSIVE_EXCLUSIVE) }
            }
        }.getOrDefault(text)
    }

    /** Mirrors `android.graphics.Typeface`, as plain ints so this class stays JVM-testable. */
    private fun styleFlag(): Int? = when {
        bold && italic -> 3
        bold -> 1
        italic -> 2
        else -> null
    }

    companion object {

        const val KEY_SCALE = "textScale"
        const val KEY_COLOUR = "textColor"
        const val KEY_WEIGHT = "textWeight"
        const val KEY_FAMILY = "textFont"

        /**
         * Clamped so a setting cannot make the clock unreadable or clip it out of the bar.
         * A status bar is a fixed height that the app does not control.
         *
         * The ceiling was 1.4 and that was wrong: measured on a Nothing AIN065, a serif face
         * at 1.4 loses its ascenders and descenders to the top and bottom of the bar. 1.15 is
         * the largest that still fits with room for a tall glyph, so it is the ceiling now.
         */
        const val MIN_SCALE = 0.7f
        const val MAX_SCALE = 1.15f

        val WEIGHTS = listOf("normal", "bold", "italic", "bold-italic")

        /** `sans-serif-condensed` is the one that actually buys horizontal room. */
        val FAMILIES = listOf("default", "sans-serif", "sans-serif-condensed", "serif", "monospace")

        fun from(config: ConfigSource): ClockAppearance {
            val weight = config.string(KEY_WEIGHT, "normal")?.lowercase() ?: "normal"
            val family = config.string(KEY_FAMILY, "default")?.lowercase()
            return ClockAppearance(
                scale = config.float(KEY_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE),
                colour = parseColour(config.string(KEY_COLOUR)),
                bold = weight == "bold" || weight == "bold-italic",
                italic = weight == "italic" || weight == "bold-italic",
                family = family?.takeIf { it != "default" && it in FAMILIES },
            )
        }

        /**
         * `#RGB`, `#RRGGBB` or `#AARRGGBB`, opaque unless an alpha is given.
         *
         * Hand-rolled rather than `Color.parseColor`, for two reasons: that throws on bad
         * input where this returns null and leaves the clock alone, and this stays testable
         * on the JVM where `android.graphics.Color` is a stub that throws.
         */
        fun parseColour(raw: String?): Int? {
            val hex = raw?.trim()?.removePrefix("#")?.takeIf { it.isNotEmpty() } ?: return null
            if (hex.any { it.digitToIntOrNull(16) == null }) return null

            val expanded = when (hex.length) {
                3 -> "FF" + hex.map { "$it$it" }.joinToString("")
                6 -> "FF$hex"
                8 -> hex
                else -> return null
            }
            return expanded.toLongOrNull(16)?.toInt()
        }
    }
}
