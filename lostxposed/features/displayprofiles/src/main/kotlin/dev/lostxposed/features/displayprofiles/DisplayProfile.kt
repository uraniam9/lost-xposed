package dev.lostxposed.features.displayprofiles

import dev.lostxposed.core.api.ConfigSource

/**
 * Per-app display overrides. Zero or negative means "leave alone" for every field, so a
 * profile that sets only one thing does not silently reset the others.
 */
data class DisplayProfile(
    val densityDpi: Int = 0,
    val fontScale: Float = 0f,
    val refreshRate: Float = 0f,
) {
    val overridesDensity: Boolean get() = densityDpi > 0
    val overridesFontScale: Boolean get() = fontScale > 0f
    val overridesRefreshRate: Boolean get() = refreshRate > 0f

    val isEmpty: Boolean
        get() = !overridesDensity && !overridesFontScale && !overridesRefreshRate

    fun describe(): String = buildList {
        if (overridesDensity) add("${densityDpi}dpi")
        if (overridesFontScale) add("font x$fontScale")
        if (overridesRefreshRate) add("${refreshRate.toInt()}Hz")
    }.joinToString(", ").ifEmpty { "nothing" }

    companion object {
        const val KEY_DENSITY = "densityDpi"
        const val KEY_FONT_SCALE = "fontScale"
        const val KEY_REFRESH_RATE = "refreshRate"

        fun from(config: ConfigSource) = DisplayProfile(
            densityDpi = config.int(KEY_DENSITY, 0),
            fontScale = config.float(KEY_FONT_SCALE, 0f),
            refreshRate = config.float(KEY_REFRESH_RATE, 0f),
        )
    }
}
