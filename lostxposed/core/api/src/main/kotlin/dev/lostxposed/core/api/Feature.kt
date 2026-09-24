package dev.lostxposed.core.api

/**
 * Contracts shared by every process this module is injected into — system_server, SystemUI,
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
    val category: Category,
    val injects: Set<ProcessTarget>,
    val riskTier: RiskTier,
    val stability: Stability = Stability.STABLE,
    /** Declared so the UI can build an editor without knowing the feature. */
    val settings: List<SettingSpec> = emptyList(),
    /** Named starting points, offered above the settings. Filled in, not applied. */
    val templates: List<Template> = emptyList(),
    /**
     * False for the scaffolding features — the self check and the no-op reference — which
     * exist to prove the engine works rather than to do anything for the person holding the
     * phone. They are still registered, still probed and still reported; they just do not
     * belong at the top of a list of things you can turn on.
     */
    val userFacing: Boolean = true,
) {
    /** Which processes have to restart for a change to this feature to take effect. */
    val restartHint: String
        get() = when {
            injects.any { it is ProcessTarget.SystemServer } -> "reboot required"
            injects.any { it is ProcessTarget.SystemUi } -> "restart SystemUI"
            injects.any { it is ProcessTarget.CurrentIme } -> "restart the keyboard"
            else -> "restart the target app"
        }
}
