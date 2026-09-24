/*
 * Lost Xposed - the clever Android hacks you remember, rebuilt for modern Android.
 * Copyright (C) 2026 The Lost Xposed Authors
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package dev.lostxposed.entry.xposed

import android.util.Log
import dev.lostxposed.core.api.ConfigProvider
import dev.lostxposed.core.api.Environment
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.api.NoConfig
import dev.lostxposed.core.api.RiskTier
import dev.lostxposed.core.safety.BootGuard
import dev.lostxposed.core.compat.EnvironmentDetector
import dev.lostxposed.core.config.ConfigLoad
import dev.lostxposed.core.config.ConfigLoader
import dev.lostxposed.core.engine.HookEnvImpl
import dev.lostxposed.core.engine.InjectionEngine
import dev.lostxposed.core.engine.LOG_TAG
import dev.lostxposed.core.engine.ProcessContext
import dev.lostxposed.core.diagnostics.DiagnosticsReport
import dev.lostxposed.core.diagnostics.Status
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Entry point named by META-INF/xposed/java_init.list.
 *
 * Shape verified against api:102.0.0 with javap and confirmed running on Vector 2.2:
 * abstract class, public no-arg constructor, framework attaches itself, and
 * `PackageLoadedParam` exposes `getDefaultClassLoader()` rather than `getClassLoader()`.
 */
class LostXposedEntry : XposedModule() {

    /** Features the boot guard has switched off for this boot. Never re-enabled mid-boot. */
    @Volatile
    private var bootDisabled: Set<FeatureId> = emptySet()

    private val engine = InjectionEngine(Features.registry) { id -> id !in bootDisabled }

    @Volatile
    private var environment: Environment? = null

    @Volatile
    private var processName: String = "?"

    @Volatile
    private var isSystemServer: Boolean = false

    private fun environment(): Environment =
        environment ?: EnvironmentDetector.detect(this).also { environment = it }

    @Volatile
    private var configProvider: ConfigProvider = NoConfig

    @Volatile
    private var configStatus: String = "not loaded"

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        processName = param.processName
        isSystemServer = param.isSystemServer

        when (val load = ConfigLoader.load(this)) {
            is ConfigLoad.Ready -> {
                configProvider = load.provider
                configStatus = "${load.channel}, schema v${load.schemaVersion}"
            }

            is ConfigLoad.Unavailable -> {
                configProvider = NoConfig
                configStatus = "unavailable — ${load.detail}"
            }
        }

        Log.i(
            LOG_TAG,
            "module loaded in $processName — ${environment()} | config: $configStatus | " +
                ConfigLoader.report,
        )
    }

    /**
     * API 102 hot reload. Reported rather than acted on: hooks already installed in this
     * process keep running the old code, and swapping them would mean unhooking and
     * reinstalling everything mid-flight inside a live process. Saying so is more useful than
     * appearing to support something that half works.
     */
    override fun onHotReloaded(param: HotReloadedParam) {
        super.onHotReloaded(param)
        Log.i(
            LOG_TAG,
            "module hot-reloaded in $processName — existing hooks still run the previous " +
                "code; restart this process to pick up changes",
        )
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        super.onSystemServerStarting(param)
        isSystemServer = true
        applyBootGuard()
        dispatch(
            packageName = ProcessContext.ANDROID,
            classLoader = param.classLoader,
            isFirstPackage = true,
        )
    }

    /**
     * Runs before any system_server hook is installed. A feature that bootloops the device is
     * not a bug report, it is a factory reset, so the decision has to be made first.
     */
    private fun applyBootGuard() {
        val risky = Features.registry.registrations
            .filter { it.descriptor.riskTier == RiskTier.BOOTLOOP }
            .map { it.descriptor.id }
            .toSet()

        if (risky.isEmpty()) return

        val guard = BootGuard.default()
        when (val decision = guard.evaluate(risky)) {
            is BootGuard.Decision.Proceed -> {
                bootDisabled = emptySet()
                guard.scheduleSuccessSignal {
                    Log.i(LOG_TAG, "boot looks healthy — guard marker cleared")
                }
                Log.i(LOG_TAG, "boot guard armed for ${risky.size} bootloop-tier feature(s)")
            }

            is BootGuard.Decision.Disable -> {
                bootDisabled = risky
                Log.w(
                    LOG_TAG,
                    "BOOT GUARD TRIPPED: ${decision.reason} " +
                        "(failures=${decision.consecutiveFailures}). " +
                        "Disabled: ${risky.joinToString { it.value }}",
                )
                // A log line nobody will read is not telling anyone. Report it to the app,
                // which can put it in front of the person whose phone this happened to.
                IncidentReporter.report(decision, risky)
            }
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        dispatch(
            packageName = param.packageName,
            classLoader = param.defaultClassLoader,
            isFirstPackage = param.isFirstPackage,
        )
    }

    /**
     * Retry the config load now that the process's package name is known.
     *
     * Module load runs before this process is bound to an application, so the provider
     * channel has no Context to make its binder call with. By the time a package is handed
     * over, it does. Once per process, and only when the first attempt found nothing — a
     * channel that already worked is not disturbed.
     */
    private fun ensureConfig(packageName: String) {
        if (configProvider !== NoConfig || configRetried) return
        configRetried = true

        when (val load = ConfigLoader.load(this)) {
            is ConfigLoad.Ready -> {
                configProvider = load.provider
                configStatus = "${load.channel}, schema v${load.schemaVersion}"
                Log.i(LOG_TAG, "config arrived in $packageName on retry — ${ConfigLoader.report}")
            }

            is ConfigLoad.Unavailable ->
                Log.w(LOG_TAG, "config unavailable in $packageName — ${load.detail}")
        }
    }

    private fun dispatch(packageName: String, classLoader: ClassLoader, isFirstPackage: Boolean) {
        ensureConfig(packageName)

        val ctx = ProcessContext(
            packageName = packageName,
            processName = processName,
            isSystemServer = isSystemServer,
            isFirstPackage = isFirstPackage,
            selfPackage = SELF_PACKAGE,
        )

        val hookEnv = HookEnvImpl(
            packageName = packageName,
            processName = processName,
            classLoader = classLoader,
            isFirstPackage = isFirstPackage,
            environment = environment(),
            xposed = this,
            configProvider = configProvider,
        )

        val report = runCatching { engine.onProcessLoaded(hookEnv, ctx) }.getOrElse { thrown ->
            // A throw here would take the host process down with us. In system_server that is
            // a bootloop, so the engine is never allowed to propagate.
            Log.e(LOG_TAG, "engine failed in $packageName", thrown)
            DiagnosticsReport("LostXposed — $packageName").apply {
                fail("Engine", "${thrown.javaClass.simpleName}: ${thrown.message}")
            }
        }

        // Display profiles target every app, so most processes produce a report that says
        // only "not configured". Logging those would drown the interesting ones.
        if (report.worst != Status.INFO) {
            report.render().lineSequence().forEach { Log.i(LOG_TAG, it) }
        }

        if (!debugControlInstalled && engine.activeFeatures().isNotEmpty()) {
            debugControlInstalled = true
            DebugControl.install(engine, packageName)
            // Settings are read at process start, so the app needs a way to restart the
            // process it just reconfigured. Only installed where something is actually
            // hooked, because there is nothing to restart for otherwise.
            ProcessControl.install(packageName)
        }
    }

    @Volatile
    private var debugControlInstalled = false

    @Volatile
    private var configRetried = false

    private companion object {
        const val SELF_PACKAGE = "io.github.uraniam9.lostxposed"
    }
}
