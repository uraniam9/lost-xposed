package dev.lostxposed.core.api

/**
 * Contracts shared by every process this module is injected into: system_server, SystemUI,
 * the current IME, and arbitrary app processes.
 *
 * KEEP THIS MODULE SMALL AND DEPENDENCY-FREE. Anything added here is loaded into
 * system_server on every boot and into every hooked app. The only permitted dependency is
 * `compileOnly(libxposed)`, which the framework supplies at runtime.
 */

@JvmInline
value class FeatureId(val value: String) {
    override fun toString(): String = value
}

enum class Category { CORE, INPUT, DISPLAY, STATUS_BAR, NOTIFICATIONS, POWER, PRIVACY, EXPERIMENTAL }

/**
 * What breaks if this feature throws. Drives the boot safety guard and decides whether a
 * feature may be auto-re-enabled after an unexplained reboot.
 */
enum class RiskTier { SAFE, APP_CRASH, SYSTEMUI_RESTART, BOOTLOOP }

enum class Stability { STABLE, BETA, EXPERIMENTAL }

fun interface PackageFilter {
    fun matches(packageName: String): Boolean

    companion object {
        val ANY = PackageFilter { true }
        fun of(vararg packages: String): PackageFilter {
            val set = packages.toSet()
            return PackageFilter { it in set }
        }
    }
}

/** Which process an injection needs to run in. */
sealed interface ProcessTarget {
    data object SystemServer : ProcessTarget
    data object SystemUi : ProcessTarget
    data object CurrentIme : ProcessTarget
    data object Self : ProcessTarget
    data class App(val filter: PackageFilter) : ProcessTarget
}

/**
 * Pure metadata, readable in every process.
 *
 * [name] and [description] are plain strings rather than resource ids on purpose: a hooked
 * process has no convenient handle on our resources, and the descriptor has to be readable
 * there. The UI localises separately.
 */
data class FeatureDescriptor(
    val id: FeatureId,
    val name: String,
    val description: String,
    /**
     * The longer explanation, shown on the feature's own screen.
     *
     * [description] has to fit on a card in a list, which means it can say what a feature is
     * but not what it actually does for you, what it cannot do, or what you have to do to
     * make it work. That is the difference between a label and an answer.
     */
    val detail: String? = null,
    /**
     * How to check this is actually doing something, in concrete steps rather than a
     * description of the feature repeated in the imperative. Filled in for features nobody
     * can just glance at and tell: a gesture, a per-app effect, something that has to not
     * happen. Null where the effect is obvious the moment you look, such as the status bar
     * clock changing the instant SystemUI restarts.
     */
    val howToTest: String? = null,
    val category: Category,
    val injects: Set<ProcessTarget>,
    val riskTier: RiskTier,
    val stability: Stability = Stability.STABLE,
    /** Declared so the UI can build an editor without knowing the feature. */
    val settings: List<SettingSpec> = emptyList(),
    /** Named starting points, offered above the settings. Filled in, not applied. */
    val templates: List<Template> = emptyList(),
    /**
     * False for the scaffolding features (the self check and the no-op reference), which
     * exist to prove the engine works rather than to do anything for the person holding the
     * phone. They are still registered, still probed and still reported; they just do not
     * belong at the top of a list of things you can turn on.
     */
    val userFacing: Boolean = true,
) {
    /**
     * What it takes for a change to this feature to take effect. For system_server, nothing:
     * the app hands the change over when it is saved, and the toast says whether it was taken.
     */
    val restartHint: String
        get() = when {
            injects.any { it is ProcessTarget.SystemServer } -> "applied when saved"
            injects.any { it is ProcessTarget.SystemUi } -> "restart SystemUI"
            injects.any { it is ProcessTarget.CurrentIme } -> "restart the keyboard"
            else -> "restart the target app"
        }

    /**
     * What has to be ticked in the framework manager before this can do anything at all.
     *
     * [ProcessTarget] already carries this; the main screen's scope card already works it out
     * precisely, per package. This is the short version, repeated on the feature's own screen,
     * because a feature whose process is out of scope looks broken from right here, not on a
     * different screen you would have to already know to go check.
     */
    val scopeHint: String
        get() = when {
            injects.any { it is ProcessTarget.SystemServer } -> "System framework"
            injects.any { it is ProcessTarget.SystemUi } -> "System UI"
            injects.any { it is ProcessTarget.CurrentIme } -> "your keyboard"
            injects.any { it is ProcessTarget.Self } -> "Lost Xposed itself"
            else -> "the app you set it for below"
        }
}
