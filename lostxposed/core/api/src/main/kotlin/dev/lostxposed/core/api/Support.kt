package dev.lostxposed.core.api

import io.github.libxposed.api.XposedInterface

/*
 * `Support` used to live here: a sealed Ready/Degraded/Unsupported returned by a single
 * probe() call. It has been replaced by probe chains, which carry the same information with
 * the part that mattered — Outcome.Pass/Warn/Fail/Skip per check, so a report names the link
 * that broke instead of only the fact that something did. Degraded became Outcome.Warn.
 */

/**
 * [benign] separates "does not apply here" from "something is broken".
 *
 * A feature targeting every app is not-applicable in almost every process, and reporting
 * those as failures would bury the one report that matters under hundreds that do not.
 */
enum class Reason(val message: String, val benign: Boolean = false) {
    OK("supported", benign = true),
    WRONG_PROCESS("this injection does not apply to this process", benign = true),
    NO_STRATEGY_FOR_ENV("no compatibility strategy for this Android version or OEM"),
    CLASS_NOT_FOUND("a required class is missing in the target process"),
    METHOD_NOT_FOUND("a required method is missing"),
    HOOK_FAILED("the framework refused to install the hook"),
    FRAMEWORK_TOO_OLD("the framework API level is below this feature's minimum"),
    CAPABILITY_MISSING("the framework did not grant a required capability"),
    TARGET_VERSION_UNKNOWN("the target app version is not recognised"),
    NOT_CONFIGURED("no settings for this package", benign = true),
    DISABLED_BY_USER("turned off in settings", benign = true),
    DISABLED_AFTER_FAILED_BOOT("disabled automatically after a failed boot"),
    ;

    override fun toString(): String = message
}

sealed interface InstallResult {
    /**
     * Retaining handles is what makes disable() real rather than "reboot to apply" —
     * `HookHandle.unhook()` was confirmed working on device (spike-01, Vector 2.2 / API 102).
     */
    data class Installed(val handles: List<XposedInterface.HookHandle>) : InstallResult

    data class Failed(val reason: Reason, val thrown: Throwable? = null) : InstallResult

    data class Skipped(val reason: Reason) : InstallResult
}
