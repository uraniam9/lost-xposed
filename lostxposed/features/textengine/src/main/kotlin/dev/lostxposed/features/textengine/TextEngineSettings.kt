package dev.lostxposed.features.textengine

import dev.lostxposed.core.api.ConfigSource

data class TextEngineSettings(
    val enabled: Boolean = true,
    /** Drag distance per cursor step. Smaller is faster and twitchier. */
    val pxPerStep: Float = 28f,
    /** Horizontal drags jump whole words instead of characters. */
    val wordJump: Boolean = false,
    /** Extend the selection while moving, rather than just moving the caret. */
    val selectMode: Boolean = false,
) {

    /**
     * How many cursor steps a drag of [travelPx] is worth.
     *
     * Truncated, not rounded: a drag has to fully cross a step before it counts, so
     * resting a finger just past the threshold does not jitter the caret back and
     * forth. This is the whole feel of the gesture, which is why it is here and
     * tested rather than inline in a touch handler.
     */
    fun steps(travelPx: Float): Int = (travelPx / pxPerStep).toInt()

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_PX_PER_STEP = "pxPerStep"
        const val KEY_WORD_JUMP = "wordJump"
        const val KEY_SELECT_MODE = "selectMode"
        const val KEY_IME_PACKAGES = "imePackages"

        /**
         * Which packages count as keyboards. Hooking every app process for a feature that
         * only fires in an IME would be wasteful, and there is no cheap way to detect an IME
         * from inside `onPackageLoaded`, where no Context exists yet.
         *
         * Overridable via config so an unlisted keyboard can be added without a new build.
         */
        val DEFAULT_IME_PACKAGES = setOf(
            "com.google.android.inputmethod.latin",   // Gboard
            "com.touchtype.swiftkey",                 // Microsoft SwiftKey
            "com.touchtype.swiftkey.beta",
            "org.futo.inputmethod.latin",             // FUTO Keyboard
            "helium314.keyboard",                     // HeliBoard
            "org.dslul.openboard.inputmethod.latin",  // OpenBoard
            "com.menny.android.anysoftkeyboard",      // AnySoftKeyboard
            "com.samsung.android.honeyboard",         // Samsung Keyboard
        )

        fun from(config: ConfigSource) = TextEngineSettings(
            enabled = config.boolean(KEY_ENABLED, true),
            pxPerStep = config.float(KEY_PX_PER_STEP, 28f).coerceAtLeast(4f),
            wordJump = config.boolean(KEY_WORD_JUMP, false),
            selectMode = config.boolean(KEY_SELECT_MODE, false),
        )

        /**
         * Falls back when the setting is absent *or* names nothing usable.
         *
         * The `takeIf` is the whole point. A stored value of "," is not null, so the elvis
         * never fired and this returned an empty set -- which means the feature hooks no
         * keyboard at all and does nothing, silently, with every setting still looking
         * correct. Found by a test, not on a device.
         */
        fun imePackages(config: ConfigSource): Set<String> =
            config.string(KEY_IME_PACKAGES)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_IME_PACKAGES
    }
}
