package dev.lostxposed.features.displayprofiles

import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.WindowManager
import dev.lostxposed.core.api.Category
import dev.lostxposed.core.api.FeatureDescriptor
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.api.HookEnv
import dev.lostxposed.core.api.Injection
import dev.lostxposed.core.api.InjectionFactory
import dev.lostxposed.core.api.InstallResult
import dev.lostxposed.core.api.Outcome
import dev.lostxposed.core.api.PackageFilter
import dev.lostxposed.core.api.Probe
import dev.lostxposed.core.api.ProcessTarget
import dev.lostxposed.core.api.Reason
import dev.lostxposed.core.api.RiskTier
import dev.lostxposed.core.api.SettingSpec
import dev.lostxposed.core.api.Template
import dev.lostxposed.core.api.applicableWhen
import dev.lostxposed.core.api.installHook
import dev.lostxposed.core.api.methodPresent
import dev.lostxposed.core.api.probe
import io.github.libxposed.api.XposedInterface

/**
 * Per-app density, font scale and refresh rate.
 *
 * Highest-scoring feature in the project (72/75) and the lowest-risk: it runs entirely in the
 * target app's own process, so a bad profile breaks one app rather than the boot, and the
 * hook surface (`Resources`/`DisplayMetrics`/`Configuration`) has been effectively frozen
 * since API 1.
 *
 * Per-app locale and dark mode are deliberately absent: Android has shipped both natively
 * since 13 and 10 respectively. Refresh rate is the one nobody else ships.
 */
class DisplayProfilesFeature : Injection {

    override val descriptor = FeatureDescriptor(
        id = ID,
        name = "Per-app display",
        detail = "Sets density, font scale and refresh rate for one app at a time, " +
            "instead of once for your whole phone.\n\n" +
            "Density is the big one. Lower it and an app fits more on screen, which " +
            "is what you want in a reader, a browser or a long list. Raise it and " +
            "everything gets bigger, which is what you want in an app you squint at. " +
            "Android has one value for all of them.\n\n" +
            "Refresh rate is the other one nobody else ships. Pin a mostly static " +
            "app to 60 Hz and it stops driving the panel at 120.\n\n" +
            "Put the app\u0027s package name in \"Applies to\", or use * for every " +
            "app. Presets above are percentages of what your device uses now, so " +
            "they mean the same thing on any phone. The app has to be in this " +
            "module\u0027s scope, and has to be restarted before you see a change.",
        description = "Density, font scale and refresh rate per application.",
        howToTest = "Add the app you want to change to Lost Xposed's own scope in your " +
            "framework manager first; nothing below does anything until you do. Set " +
            "\"Applies to\" to its package name and try \"Fit more on screen\", the easiest " +
            "change to actually see. Save, then force stop that app from Android's own " +
            "App info screen (not just swiping it away in recents) and reopen it. Everything " +
            "in it should look slightly smaller and more tightly packed than before.",
        category = Category.DISPLAY,
        injects = setOf(ProcessTarget.App(PackageFilter.ANY)),
        riskTier = RiskTier.APP_CRASH,
        settings = listOf(
            SettingSpec(
                DisplayProfile.KEY_DENSITY, "Density (dpi)", SettingSpec.Type.INT,
                baseline = SettingSpec.Baseline.DENSITY_DPI,
                help = "Higher means smaller UI. Leave empty to keep the system value.",
            ),
            SettingSpec(
                DisplayProfile.KEY_FONT_SCALE, "Font scale", SettingSpec.Type.FLOAT,
                min = 0.7f, max = 1.6f, step = 0.05f,
                baseline = SettingSpec.Baseline.FONT_SCALE,
                help = "1.0 is normal.",
            ),
            SettingSpec(
                DisplayProfile.KEY_REFRESH_RATE, "Refresh rate (Hz)", SettingSpec.Type.FLOAT,
                baseline = SettingSpec.Baseline.REFRESH_RATE,
                help = "A request, not a command. The display may refuse it under load.",
            ),
        ),
        /*
         * Percentages, not numbers. 85% of this device's density is advice that travels;
         * 357dpi is advice for one phone, and wrong on the next one. The editor resolves
         * them against what the device actually reports.
         */
        templates = listOf(
            Template(
                "Fit more on screen",
                "Smaller UI, more content. Good for reading apps and long lists.",
                mapOf(DisplayProfile.KEY_DENSITY to "%85"),
                anchor = DisplayProfile.KEY_DENSITY,
            ),
            Template(
                "Easier to read",
                "Larger UI and text, for an app you squint at.",
                mapOf(DisplayProfile.KEY_DENSITY to "%115", DisplayProfile.KEY_FONT_SCALE to "1.15"),
                anchor = DisplayProfile.KEY_DENSITY,
            ),
            Template(
                "Save battery here",
                "Hold this app at 60 Hz. Useful for anything that is mostly static.",
                mapOf(DisplayProfile.KEY_REFRESH_RATE to "60"),
                anchor = DisplayProfile.KEY_REFRESH_RATE,
            ),
            Template(
                "Keep it smooth",
                "Ask for the panel's top rate. The display may still refuse under load.",
                mapOf(DisplayProfile.KEY_REFRESH_RATE to "%100"),
                anchor = DisplayProfile.KEY_REFRESH_RATE,
            ),
        ),
    )

    override fun probes(env: HookEnv): List<Probe> {
        val profile = DisplayProfile.from(env.configFor(ID))
        return buildList {
            // Cheapest and most excluding first: this targets every app, and almost none of
            // them have a profile.
            add(
                applicableWhen("profile configured", Reason.NOT_CONFIGURED) { !profile.isEmpty },
            )
            add(probe("requested") { Outcome.Pass(profile.describe()) })

            if (profile.overridesDensity || profile.overridesFontScale) {
                add(methodPresent("Resources.updateConfiguration", RESOURCES, UPDATE_CONFIGURATION))
            }
            if (profile.overridesRefreshRate) {
                add(methodPresent("WindowManagerImpl.addView", WINDOW_MANAGER_IMPL, ADD_VIEW))
            }
        }
    }

    override fun install(env: HookEnv): InstallResult {
        val profile = DisplayProfile.from(env.configFor(ID))
        if (profile.isEmpty) return InstallResult.Skipped(Reason.NOT_CONFIGURED)

        val handles = mutableListOf<XposedInterface.HookHandle>()

        if (profile.overridesDensity || profile.overridesFontScale) {
            val hooker = ConfigurationHooker(profile)
            configurationMethods(env).forEachIndexed { index, method ->
                runCatching { env.installHook(method, "display.config$index", hooker) }
                    .onSuccess { handles += it }
                    .onFailure { env.log("updateConfiguration hook failed", it) }
            }
        }

        if (profile.overridesRefreshRate) {
            addViewMethod(env)?.let { method ->
                runCatching {
                    env.installHook(method, "display.refresh", RefreshRateHooker(profile.refreshRate))
                }
                    .onSuccess { handles += it }
                    .onFailure { env.log("addView hook failed", it) }
            }
        }

        if (handles.isEmpty()) return InstallResult.Failed(Reason.HOOK_FAILED)

        env.log("applied ${profile.describe()}")
        return InstallResult.Installed(handles)
    }

    /**
     * Every overload, not a chosen one. The public signature takes (Configuration,
     * DisplayMetrics); a hidden three-argument form taking CompatibilityInfo also exists and
     * is the one that actually fires on some builds. Hooking by name covers both without
     * compiling against hidden API.
     */
    private fun configurationMethods(env: HookEnv) =
        env.findClassOrNull(RESOURCES)
            ?.declaredMethods
            ?.filter { it.name == UPDATE_CONFIGURATION }
            .orEmpty()

    private fun addViewMethod(env: HookEnv) =
        env.findClassOrNull(WINDOW_MANAGER_IMPL)
            ?.declaredMethods
            ?.firstOrNull { it.name == ADD_VIEW && it.parameterCount == 2 }

    /** Rewrites the arguments on the way in, using the chain's own `proceed(args)`. */
    private class ConfigurationHooker(
        private val profile: DisplayProfile,
    ) : XposedInterface.Hooker {

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val args = chain.args.toTypedArray()

            (args.getOrNull(0) as? Configuration)?.let { config ->
                if (profile.overridesDensity) config.densityDpi = profile.densityDpi
                if (profile.overridesFontScale) config.fontScale = profile.fontScale
            }

            (args.getOrNull(1) as? DisplayMetrics)?.let { metrics ->
                if (profile.overridesDensity) {
                    metrics.densityDpi = profile.densityDpi
                    metrics.density = profile.densityDpi / BASELINE_DPI
                }
                if (profile.overridesDensity || profile.overridesFontScale) {
                    val scale = if (profile.overridesFontScale) profile.fontScale else 1f
                    @Suppress("DEPRECATION")
                    metrics.scaledDensity = metrics.density * scale
                }
            }

            return chain.proceed(args)
        }
    }

    /**
     * `preferredRefreshRate` is a request, not a command. The display pipeline may refuse it
     * under thermal or battery pressure. Diagnostics should report what was asked for, never
     * claim what was granted.
     */
    private class RefreshRateHooker(private val hz: Float) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val args = chain.args.toTypedArray()
            (args.getOrNull(1) as? WindowManager.LayoutParams)?.preferredRefreshRate = hz
            return chain.proceed(args)
        }
    }

    companion object {
        val ID = FeatureId("core.displayprofiles")
        val FACTORY = InjectionFactory { DisplayProfilesFeature() }

        private const val RESOURCES = "android.content.res.Resources"
        private const val UPDATE_CONFIGURATION = "updateConfiguration"
        private const val WINDOW_MANAGER_IMPL = "android.view.WindowManagerImpl"
        private const val ADD_VIEW = "addView"
        private const val BASELINE_DPI = 160f
    }
}
