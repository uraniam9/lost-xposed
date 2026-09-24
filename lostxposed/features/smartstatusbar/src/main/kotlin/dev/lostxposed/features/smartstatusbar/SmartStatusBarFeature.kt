package dev.lostxposed.features.smartstatusbar

import dev.lostxposed.core.api.Category
import dev.lostxposed.core.api.ConfigSource
import dev.lostxposed.core.api.FeatureDescriptor
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.api.HookEnv
import dev.lostxposed.core.api.Injection
import dev.lostxposed.core.api.InjectionFactory
import dev.lostxposed.core.api.InstallResult
import dev.lostxposed.core.api.Oem
import dev.lostxposed.core.api.Outcome
import dev.lostxposed.core.api.ProcessTarget
import dev.lostxposed.core.api.Reason
import dev.lostxposed.core.api.RiskTier
import dev.lostxposed.core.api.SettingSpec
import dev.lostxposed.core.api.Stability
import dev.lostxposed.core.api.Template
import dev.lostxposed.core.api.applicableWhen
import dev.lostxposed.core.api.installHook
import dev.lostxposed.core.api.probe
import dev.lostxposed.core.compat.CompatTable
import dev.lostxposed.core.compat.compatTable
import io.github.libxposed.api.XposedInterface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The status bar clock as a programmable surface.
 *
 * Adding seconds and a date is not a feature — Iconify, AOSP Mods, Pixel Xpert and most OEM
 * skins already do that, which is why this scored 49/75 and sits late in the plan. The
 * differentiator is the provider model: the clock renders whatever a named provider returns,
 * so "fuzzy time" and "battery percentage" are configuration rather than code.
 *
 * Structured so the data layer survives its own render layer. spike-01 found Compose and
 * `SceneWindowRootView` already live in SystemUI, so the `TextView` this hooks will eventually
 * disappear — at which point the providers can be re-pointed at a QS tile or an overlay
 * without rewriting them.
 */
class SmartStatusBarFeature : Injection {

    override val descriptor = FeatureDescriptor(
        id = ID,
        name = "Clock Studio",
        description = "Build the status bar clock yourself: what it says, and how it looks.",
        detail = "Four ways to write the clock, and you pick one with Clock style.\n\n" +
            "system \u2014 leave the text alone. Pick this if you only want to change the " +
            "size, colour, weight or font below.\n\n" +
            "fuzzy \u2014 the time as people say it out loud: \"quarter past three\", " +
            "\"twenty to nine\". Rounded to the nearest five minutes on purpose; an " +
            "exact clock is what you already had.\n\n" +
            "custom \u2014 your own date format, using SimpleDateFormat letters. HH:mm is " +
            "24-hour, h:mm a is 12-hour with am/pm, EEE is the short day name.\n\n" +
            "mixer \u2014 write the line yourself and drop pieces into it. Type anything " +
            "you like around them; only the {pieces} are replaced.\n\n" +
            "Every change here needs SystemUI restarted before you see it, which is " +
            "what the button at the bottom of the main screen is for.",
        category = Category.STATUS_BAR,
        injects = setOf(ProcessTarget.SystemUi),
        riskTier = RiskTier.SYSTEMUI_RESTART,
        stability = Stability.BETA,
        settings = listOf(
            SettingSpec(
                KEY_STYLE, "Clock style", SettingSpec.Type.ENUM, default = "system",
                options = listOf("system", "fuzzy", "custom", "mixer"), perPackage = false,
                help = "system leaves the text alone \u2014 use it if you only want to " +
                    "restyle. fuzzy says the time out loud. custom takes a date format. " +
                    "mixer lets you write the line. Only the setting for the style you " +
                    "pick is used; the others are ignored.",
            ),
            SettingSpec(
                KEY_PATTERN, "Custom pattern", SettingSpec.Type.STRING, default = "HH:mm",
                perPackage = false,
                enabledWhen = SettingSpec.Dependency(KEY_STYLE, setOf("custom")),
                help = "Only used when style is custom. HH:mm gives 14:05, h:mm a gives " +
                    "2:05 pm, EEE HH:mm gives Tue 14:05, d MMM gives 23 Sep.",
            ),
            SettingSpec(
                KEY_TEMPLATE, "Mixer", SettingSpec.Type.STRING, default = ClockTemplate.DEFAULT,
                perPackage = false,
                enabledWhen = SettingSpec.Dependency(KEY_STYLE, setOf("mixer")),
                help = "Only used when style is mixer. Pieces: " +
                    ClockTemplate.TOKENS.joinToString(" ") { "{$it}" } +
                    ". Anything else on the line is printed exactly as you type it, so " +
                    "separators and brackets are yours to choose. A misspelt piece " +
                    "stays on screen so you can see it.",
            ),
            SettingSpec(
                KEY_COMPACT, "Shorter phrasing", SettingSpec.Type.BOOLEAN, default = "false",
                perPackage = false,
                // Applies to fuzzy time, and to {fuzzy} inside a mixer line.
                enabledWhen = SettingSpec.Dependency(KEY_STYLE, setOf("fuzzy", "mixer")),
                help = "Affects fuzzy time, including {fuzzy} in the mixer. On, you get " +
                    "\"25 to 4\"; off, \"twenty-five to four\". Quarter and half stay as " +
                    "words either way because they are already short.",
            ),
            SettingSpec(
                KEY_BATTERY, "Append battery", SettingSpec.Type.BOOLEAN, default = "false",
                perPackage = false,
                // Not in mixer mode: {battery} already says where it goes.
                enabledWhen = SettingSpec.Dependency(
                    KEY_STYLE,
                    setOf("system", "fuzzy", "custom"),
                ),
                help = "Adds the charge level after the time. Ignored when style is " +
                    "mixer, where {battery} already says where you want it \u2014 " +
                    "otherwise it would appear twice.",
            ),
            SettingSpec(
                ClockAppearance.KEY_SCALE, "Text size", SettingSpec.Type.FLOAT, default = "1.0",
                perPackage = false,
                min = ClockAppearance.MIN_SCALE,
                max = ClockAppearance.MAX_SCALE,
                step = 0.05f,
                help = "Relative to the status bar's own size. The range is the range: a " +
                    "clock taller than the bar gets clipped by the bar.",
            ),
            SettingSpec(
                ClockAppearance.KEY_COLOUR, "Text colour", SettingSpec.Type.STRING,
                default = "", perPackage = false,
                help = "#RRGGBB, or #AARRGGBB for transparency. Empty leaves it the " +
                    "colour SystemUI already uses. The presets above set this for you.",
            ),
            SettingSpec(
                ClockAppearance.KEY_WEIGHT, "Weight", SettingSpec.Type.ENUM, default = "normal",
                options = ClockAppearance.WEIGHTS, perPackage = false,
            ),
            SettingSpec(
                ClockAppearance.KEY_FAMILY, "Font", SettingSpec.Type.ENUM, default = "default",
                options = ClockAppearance.FAMILIES, perPackage = false,
                help = "sans-serif-condensed is the one that buys horizontal room.",
            ),
        ),
        templates = listOf(
            Template(
                "Fuzzy time and battery",
                "half past three \u00b7 53%. The mixer line, set up for you.",
                mapOf(KEY_STYLE to "mixer", KEY_TEMPLATE to "{fuzzy} \u00b7 {battery}"),
                anchor = KEY_TEMPLATE,
            ),
            Template(
                "Day and time",
                "Tue 15:32. Useful when every day looks the same.",
                mapOf(KEY_STYLE to "mixer", KEY_TEMPLATE to "{day} {time}"),
                anchor = KEY_TEMPLATE,
            ),
            Template(
                "Short fuzzy",
                "25 to 4. Speech, but narrow enough to leave room for icons.",
                mapOf(KEY_STYLE to "fuzzy", KEY_COMPACT to "true"),
                anchor = KEY_COMPACT,
            ),
            Template(
                "Seconds",
                "15:32:07. The one thing a stock clock will never give you.",
                mapOf(KEY_STYLE to "mixer", KEY_TEMPLATE to "{time}:{seconds}"),
                anchor = KEY_TEMPLATE,
            ),
            Template(
                "Candlelight",
                "Warm amber. Easier at night than plain white.",
                mapOf(ClockAppearance.KEY_COLOUR to "#FFB86B"),
                anchor = ClockAppearance.KEY_COLOUR,
            ),
            Template(
                "Mint",
                "A cool green that stays readable on a dark bar.",
                mapOf(ClockAppearance.KEY_COLOUR to "#7BE0AD"),
                anchor = ClockAppearance.KEY_COLOUR,
            ),
            Template(
                "Violet",
                "The app's own accent colour.",
                mapOf(ClockAppearance.KEY_COLOUR to "#A996FF"),
                anchor = ClockAppearance.KEY_COLOUR,
            ),
            Template(
                "Narrow",
                "Condensed face, slightly smaller. Buys room for notification icons.",
                mapOf(
                    ClockAppearance.KEY_FAMILY to "sans-serif-condensed",
                    ClockAppearance.KEY_SCALE to "0.95",
                ),
                anchor = ClockAppearance.KEY_FAMILY,
            ),
            Template(
                "Back to plain",
                "Clears colour, weight, font and size. The text itself is untouched.",
                mapOf(
                    ClockAppearance.KEY_COLOUR to "",
                    ClockAppearance.KEY_WEIGHT to "normal",
                    ClockAppearance.KEY_FAMILY to "default",
                    ClockAppearance.KEY_SCALE to "1.0",
                ),
                anchor = ClockAppearance.KEY_COLOUR,
            ),
        ),
    )

    override fun probes(env: HookEnv) = listOf(
        applicableWhen("something to change", Reason.NOT_CONFIGURED) {
            // Appearance counts. Someone who only wants a condensed font has configured this
            // feature just as much as someone who picked fuzzy time, and skipping them would
            // leave a setting that silently does nothing.
            val settings = Settings.from(it.configFor(ID))
            settings.style != Style.SYSTEM || !settings.appearance.isDefault
        },
        probe("clock strategy") {
            when (val resolved = CLOCK_CLASSES.resolve(it.environment)) {
                is CompatTable.Resolution.Matched ->
                    Outcome.Pass("${it.environment.oem} -> ${resolved.strategy}")

                is CompatTable.Resolution.None -> Outcome.Fail(
                    resolved.reason,
                    "${it.environment.oem} on SDK ${it.environment.sdkInt} is not a combination " +
                        "this build has been verified against",
                )
            }
        },
        probe("clock method") {
            smallTime(it)
                ?.let { m -> Outcome.Pass("${m.declaringClass.simpleName}.${m.name}()") }
                ?: Outcome.Fail(Reason.METHOD_NOT_FOUND, "$GET_SMALL_TIME() absent")
        },
    )

    override fun install(env: HookEnv): InstallResult {
        val settings = Settings.from(env.configFor(ID))
        val target = smallTime(env) ?: return InstallResult.Failed(Reason.METHOD_NOT_FOUND)

        return runCatching {
            val handle = env.installHook(target, "clock.text", ClockHooker(settings))
            env.log(
                "clock style=${settings.style} compact=${settings.compact} " +
                    "suffix=${settings.showBattery} appearance=${settings.appearance}",
            )
            InstallResult.Installed(listOf(handle))
        }.getOrElse { InstallResult.Failed(Reason.HOOK_FAILED, it) }
    }

    private fun smallTime(env: HookEnv) =
        CLOCK_CLASSES.resolveOrNull(env.environment)
            ?.let { env.findClassOrNull(it) }
            ?.declaredMethods
            ?.firstOrNull { it.name == GET_SMALL_TIME && it.parameterCount == 0 }

    enum class Style { SYSTEM, FUZZY, CUSTOM, MIXER }

    data class Settings(
        val style: Style,
        val pattern: String,
        val showBattery: Boolean,
        val compact: Boolean,
        val template: String,
        val appearance: ClockAppearance,
    ) {
        companion object {
            fun from(config: ConfigSource) = Settings(
                style = when (config.string(KEY_STYLE, "system")?.lowercase()) {
                    "fuzzy" -> Style.FUZZY
                    "custom" -> Style.CUSTOM
                    "mixer" -> Style.MIXER
                    else -> Style.SYSTEM
                },
                pattern = config.string(KEY_PATTERN, "HH:mm") ?: "HH:mm",
                showBattery = config.boolean(KEY_BATTERY, false),
                compact = config.boolean(KEY_COMPACT, false),
                template = config.string(KEY_TEMPLATE, ClockTemplate.DEFAULT)
                    ?: ClockTemplate.DEFAULT,
                appearance = ClockAppearance.from(config),
            )
        }
    }

    /**
     * Delegates to [ClockRenderer], which the settings screen uses as well. One code path,
     * so a preview cannot be a polite fiction about what the status bar will do.
     */
    private class ClockHooker(settings: Settings) : XposedInterface.Hooker {

        private val renderer = ClockRenderer(settings)

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val original = chain.proceed()
            // Any failure returns the system's own text: a clock that renders nothing is a
            // far worse outcome than a clock that ignores our settings.
            return runCatching {
                renderer.text(fallback = original as? CharSequence) ?: original
            }.getOrDefault(original)
        }
    }

    companion object {
        val ID = FeatureId("core.smartstatusbar")
        val FACTORY = InjectionFactory { SmartStatusBarFeature() }

        const val KEY_STYLE = "style"
        const val KEY_PATTERN = "pattern"
        const val KEY_BATTERY = "showBattery"
        const val KEY_COMPACT = "compact"
        const val KEY_TEMPLATE = "template"

        private const val AOSP_CLOCK = "com.android.systemui.statusbar.policy.Clock"
        private const val GET_SMALL_TIME = "getSmallTime"

        /**
         * Which clock class to hook, per environment.
         *
         * Every entry here is AOSP's today — measured on Nothing OS / Android 16, and the
         * same class is what Iconify and Pixel Xpert hook on Pixel and Samsung. The table is
         * not redundant despite that: an unlisted OEM resolves to **no strategy** and says so
         * in diagnostics, rather than silently hooking a class that may not be the clock. A
         * future divergence becomes one row, not a refactor.
         */
        private val CLOCK_CLASSES = compatTable<String> {
            match(sdk = 33..40, Oem.AOSP, Oem.PIXEL, Oem.NOTHING) using AOSP_CLOCK
            match(sdk = 33..40, Oem.ONE_UI, Oem.HYPER_OS) using AOSP_CLOCK
            fallbackUnsupported()
        }
    }
}
