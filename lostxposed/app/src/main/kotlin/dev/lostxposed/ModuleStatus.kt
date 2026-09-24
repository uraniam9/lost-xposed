package dev.lostxposed

/**
 * The single hook point by which the module tells its own UI it is loaded.
 *
 * One place rather than a copy per Activity: each copy is a separate method the self-check
 * would have to hook, and the first one anybody forgot would report "not loaded" while the
 * module was running perfectly.
 */
object ModuleStatus {

    /** Replaced by SelfCheckFeature when the module is injected into this process. */
    fun isActive(): Boolean = false
}
