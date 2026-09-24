package dev.lostxposed.core.engine

import android.util.Log
import dev.lostxposed.core.api.ConfigProvider
import dev.lostxposed.core.api.ConfigSource
import dev.lostxposed.core.api.Environment
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.api.HookEnv
import dev.lostxposed.core.api.NoConfig
import io.github.libxposed.api.XposedInterface

const val LOG_TAG = "LostXposed"

class HookEnvImpl(
    override val packageName: String,
    override val processName: String,
    override val classLoader: ClassLoader,
    override val isFirstPackage: Boolean,
    override val environment: Environment,
    override val xposed: XposedInterface,
    private val configProvider: ConfigProvider = NoConfig,
) : HookEnv {

    // Resolved once per process: config is immutable for a process lifetime, so caching is
    // correct rather than merely an optimisation.
    private val configCache = HashMap<FeatureId, ConfigSource>()

    override fun configFor(feature: FeatureId): ConfigSource =
        configCache.getOrPut(feature) { configProvider.forFeature(feature, packageName) }

    // Not cached by feature alone: the target package varies per call, and a system_server
    // feature may see hundreds of them.
    override fun configFor(feature: FeatureId, targetPackage: String): ConfigSource =
        configProvider.forFeature(feature, targetPackage)

    // logcat rather than the framework log: it is what `adb logcat -s LostXposed` reads, and
    // the framework's own log is only reachable through its manager UI.
    override fun log(message: String) {
        Log.i(LOG_TAG, "[$packageName] $message")
    }

    override fun log(message: String, thrown: Throwable) {
        Log.e(LOG_TAG, "[$packageName] $message", thrown)
    }
}
