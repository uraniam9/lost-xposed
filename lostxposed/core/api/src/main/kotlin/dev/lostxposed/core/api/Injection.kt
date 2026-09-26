package dev.lostxposed.core.api

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Method

/**
 * What an [Injection] is handed when it runs inside a target process.
 *
 * There is no hook multiplexer. spike-01 confirmed on device that two independent hooks on
 * the same method both install and both execute, ordered by `setPriority()`, so features hook
 * the framework directly.
 */
interface HookEnv {
    val packageName: String
    val processName: String
    val classLoader: ClassLoader
    val isFirstPackage: Boolean
    val environment: Environment
    val xposed: XposedInterface

    fun log(message: String)
    fun log(message: String, thrown: Throwable)

    /** Config for [feature] as it applies to [packageName]. Never null; may be empty. */
    fun configFor(feature: FeatureId): ConfigSource

    /**
     * Config for [feature] as it applies to some *other* package.
     *
     * Needed by system_server features, which run in one process but act on behalf of many:
     * a notification rule belongs to the app that posted the notification, not to
     * `system_server` itself.
     */
    fun configFor(feature: FeatureId, targetPackage: String): ConfigSource

    fun findClassOrNull(name: String): Class<*>? =
        runCatching { classLoader.loadClass(name) }.getOrNull()

    fun findMethodOrNull(className: String, method: String, vararg params: Class<*>): Method? =
        runCatching { findClassOrNull(className)?.getDeclaredMethod(method, *params) }.getOrNull()
}

/**
 * Install [hooker] on [target], returning the handle so the engine can undo it later.
 * [id] shows up in diagnostics and in `HookHandle.getId()`.
 */
fun HookEnv.installHook(
    target: Executable,
    id: String,
    hooker: XposedInterface.Hooker,
    priority: Int = XposedInterface.PRIORITY_DEFAULT,
    exceptionMode: XposedInterface.ExceptionMode = XposedInterface.ExceptionMode.DEFAULT,
): XposedInterface.HookHandle =
    xposed.hook(target)
        .setId(id)
        .setPriority(priority)
        .setExceptionMode(exceptionMode)
        .intercept(hooker)

/**
 * The hook-side half of a feature. One instance per process it is injected into.
 *
 * Note this is NOT the same object as the UI-side controller: they live in different
 * processes and never share memory. Conflating them is the mistake the first architecture
 * sketch made.
 */
interface Injection {
    val descriptor: FeatureDescriptor

    /**
     * Ordered live checks, run before [install]. The first one that fails or skips ends the
     * chain and its reason is what the user sees.
     *
     * A list rather than one opaque boolean because "this feature does not work" is useless;
     * "the class is present, the method is missing" is actionable. Order them cheapest and
     * most-likely-to-exclude first.
     */
    fun probes(env: HookEnv): List<Probe>

    fun install(env: HookEnv): InstallResult

    /*
     * There was an onHotReload(env) here. Nothing ever called it: the framework signals hot
     * reload on the MODULE, not per injection, and reacting properly would mean retaining
     * every injection instance and its HookEnv for the life of the process, state the engine
     * deliberately does not keep. An interface method with no caller is a promise the code
     * does not keep, so it is gone. The module-level signal is handled in the entry point.
     */
}

/** Creates a fresh [Injection] per process. Registered once, invoked per injection. */
fun interface InjectionFactory {
    fun create(): Injection
}
