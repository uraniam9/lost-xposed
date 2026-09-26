package dev.lostxposed.core.engine

import dev.lostxposed.core.api.ProcessTarget

/** What the engine knows about the process it has just been loaded into. */
data class ProcessContext(
    val packageName: String,
    val processName: String,
    val isSystemServer: Boolean,
    val isFirstPackage: Boolean,
    val selfPackage: String,
) {
    val isSystemUi: Boolean get() = packageName == SYSTEM_UI

    companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val ANDROID = "android"
    }
}

object TargetMatcher {

    fun matches(target: ProcessTarget, ctx: ProcessContext): Boolean = when (target) {
        // The system itself, not every package Android happens to load into system_server.
        // The settings provider, telecom and fused location all run in there and were each
        // handed the system_server features again on the test phone, with a class loader that
        // cannot see the services those features hook.
        is ProcessTarget.SystemServer ->
            ctx.isSystemServer && ctx.packageName == ProcessContext.ANDROID
        is ProcessTarget.SystemUi -> ctx.isSystemUi
        is ProcessTarget.Self -> ctx.packageName == ctx.selfPackage
        is ProcessTarget.App -> target.filter.matches(ctx.packageName)

        // Needs the *currently selected* IME, which is a Settings.Secure lookup this process
        // may not be able to make. Resolved by the IME backend in a later phase; matching
        // nothing is the safe default until then.
        is ProcessTarget.CurrentIme -> false
    }

    fun anyMatches(targets: Set<ProcessTarget>, ctx: ProcessContext): Boolean =
        targets.any { matches(it, ctx) }
}
