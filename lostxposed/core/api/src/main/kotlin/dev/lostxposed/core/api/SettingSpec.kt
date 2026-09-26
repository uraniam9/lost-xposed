package dev.lostxposed.core.api

/**
 * A feature describing its own settings, so the UI can render an editor without knowing
 * anything about the feature.
 *
 * The alternative is a bespoke screen per feature, which means every new feature also means
 * UI work and the two drift apart. Here a feature that adds a setting gets an editor for it
 * for free, and a setting that is renamed cannot leave a stale control behind.
 */
data class SettingSpec(
    val key: String,
    val label: String,
    val type: Type,
    /** Shown as the placeholder when nothing is set. */
    val default: String? = null,
    val help: String? = null,
    /** For [Type.ENUM]: the accepted values. */
    val options: List<String> = emptyList(),
    /** False for settings that only make sense globally, such as a keyboard allow-list. */
    val perPackage: Boolean = true,
    /**
     * Give a numeric setting a slider instead of a text field.
     *
     * Typing "1.15" into a box to nudge a font size is a worse experience than dragging, and
     * it lets someone enter 40 where the feature clamps at 1.4 and then wonder why nothing
     * happened. A declared range makes the limit visible.
     */
    val min: Float? = null,
    val max: Float? = null,
    val step: Float? = null,
    /**
     * What "100%" means for this setting on this device.
     *
     * Templates can express a value as `%85`, which is only meaningful against something. A
     * density of 85% is portable advice; 357dpi is advice for one phone. The feature declares
     * the reference and the UI, which has a `Resources`, resolves it.
     */
    val baseline: Baseline = Baseline.NONE,
    /**
     * Only usable while another setting holds one of these values.
     *
     * A custom date pattern does nothing unless the style is `custom`, and a live
     * editable field that does nothing is a question the person has to answer
     * themselves by reading the help text. Greyed out answers it for them.
     */
    val enabledWhen: Dependency? = null,
) {
    data class Dependency(val key: String, val values: Set<String>)

    enum class Type {
        BOOLEAN,
        INT,
        FLOAT,
        STRING,
        ENUM,

        /**
         * A choice of installed keyboards. Rendered as a list of the IMEs actually enabled on
         * the device, because asking someone to type `com.touchtype.swiftkey` is asking them
         * to go and look it up.
         */
        IME_LIST,
    }

    enum class Baseline { NONE, DENSITY_DPI, FONT_SCALE, REFRESH_RATE }

    val isSlider: Boolean get() = min != null && max != null
}

/**
 * A named set of values a feature suggests, filled into the editor when picked.
 *
 * Fills the form rather than saving: someone picking "Fit more on screen" should be able to
 * see what that means and adjust it before committing, not discover it afterwards.
 *
 * A value may be `%N`: N percent of whatever the setting declares as its [SettingSpec.Baseline].
 */
data class Template(
    val name: String,
    val detail: String,
    val values: Map<String, String>,
    /**
     * The setting this preset belongs under, so it can be offered beside the control it
     * changes rather than in a list of its own at the top of the screen.
     *
     * A colour preset next to the colour field is obvious. The same preset in a "Start from"
     * section three screens up is a thing you have to go and look for, and a separate section
     * also pushes the actual settings below the fold.
     *
     * A preset may still set several values; this only says where it is shown.
     */
    val anchor: String? = null,
)
