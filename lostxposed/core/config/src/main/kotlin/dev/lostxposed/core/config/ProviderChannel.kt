package dev.lostxposed.core.config

import android.content.Context
import android.net.Uri
import android.os.Bundle

/**
 * Reads config out of the settings app over Binder, which is the only transport measured to
 * cross into a hooked app process on this device. See [ConfigContract] for why the others do
 * not.
 *
 * Two things make this awkward and both are handled rather than assumed away:
 *
 * - **There is no Context yet.** A hook is installed while the target app's ActivityThread is
 *   binding its application, before `Application.onCreate`, so `currentApplication()` is
 *   still null. See [obtainContext] for what is used instead and why the obvious substitutes
 *   do not work.
 * - **The call can be re-entrant.** In the module's own process this resolves in-process; in
 *   any other it is a binder call that may start the settings app. It is wrapped like every
 *   other channel, because a throw here would take down the process being hooked.
 */
internal object ProviderChannel {

    /** Why the last attempt failed, for the load report. Null once it has succeeded. */
    var lastFailure: String? = null
        private set

    fun read(): ConfigStore? {
        val context = obtainContext() ?: return null
        val resolver = context.contentResolver ?: run {
            lastFailure = "no ContentResolver"
            return null
        }

        val bundle: Bundle = resolver.call(Uri.parse(ConfigContract.URI), ConfigContract.METHOD_READ, null, null)
            ?: run {
                // The provider returning null means it is there and declined; the provider
                // being absent throws instead, and the caller reports that message.
                lastFailure = "provider returned nothing"
                return null
            }

        lastFailure = null
        return MapConfigStore(
            bundle.keySet().mapNotNull { key -> bundle.getString(key)?.let { key to it } }.toMap(),
        )
    }

    /**
     * A Context whose package name matches this process's uid.
     *
     * That qualifier is the whole difficulty. `ContentResolver.call` attaches the context's
     * package name and the platform rejects a name that does not match the calling uid, so
     * two obvious candidates are useless: the system context is package `android`, and a
     * package context created from it inherits that name rather than taking the one asked
     * for. Both were measured failing in SystemUI with *"Given calling package android does
     * not match caller's uid 10192"*.
     *
     * So: the Application if it exists, otherwise a context built from the LoadedApk that
     * ActivityThread has already bound for this process, which carries the right name.
     *
     * All of this is hidden API. Module code normally runs with hidden-API restrictions
     * lifted by the framework, so it is expected to work -- expected, not assumed, which is
     * why each step names its own failure.
     */
    private fun obtainContext(): Context? {
        val activityThread = runCatching { Class.forName("android.app.ActivityThread") }
            .getOrElse { return fail("no ActivityThread class: ${it.javaClass.simpleName}") }

        val current = runCatching {
            activityThread.getMethod("currentActivityThread").invoke(null)
        }.getOrNull() ?: return fail("no current ActivityThread")

        runCatching { activityThread.getMethod("getApplication").invoke(current) as? Context }
            .getOrNull()
            ?.let { return it }

        // Before Application exists. mBoundApplication is set at the top of
        // handleBindApplication, well before the module is given the classloader, and its
        // LoadedApk is the one this process was bound to.
        val loadedApk = runCatching {
            val bound = activityThread.getDeclaredField("mBoundApplication")
                .apply { isAccessible = true }
                .get(current) ?: return fail("process is not bound to an application yet")
            bound.javaClass.getDeclaredField("info").apply { isAccessible = true }.get(bound)
        }.getOrElse { return fail("no bound LoadedApk: ${it.javaClass.simpleName}: ${it.message}") }
            ?: return fail("bound application carries no LoadedApk")

        return runCatching {
            Class.forName("android.app.ContextImpl")
                .getDeclaredMethod("createAppContext", activityThread, loadedApk.javaClass)
                .apply { isAccessible = true }
                .invoke(null, current, loadedApk) as? Context
        }.getOrElse { fail("createAppContext: ${it.javaClass.simpleName}: ${it.message}") }
            ?: fail("createAppContext returned nothing")
    }

    private fun fail(reason: String): Context? {
        lastFailure = reason
        return null
    }

}
