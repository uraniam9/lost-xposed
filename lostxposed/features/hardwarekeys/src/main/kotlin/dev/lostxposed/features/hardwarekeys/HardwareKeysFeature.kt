package dev.lostxposed.features.hardwarekeys

import android.view.KeyEvent
import dev.lostxposed.core.api.Category
import dev.lostxposed.core.api.FeatureDescriptor
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.api.HookEnv
import dev.lostxposed.core.api.Injection
import dev.lostxposed.core.api.InjectionFactory
import dev.lostxposed.core.api.InstallResult
import dev.lostxposed.core.api.Outcome
import dev.lostxposed.core.api.ProcessTarget
import dev.lostxposed.core.api.Reason
import dev.lostxposed.core.api.RiskTier
import dev.lostxposed.core.api.SettingSpec
import dev.lostxposed.core.api.Stability
import dev.lostxposed.core.api.classPresent
import dev.lostxposed.core.api.enabledWhen
import dev.lostxposed.core.api.installHook
import dev.lostxposed.core.api.probe
import io.github.libxposed.api.XposedInterface

/**
 * Skip tracks with the volume keys while the screen is off.
 *
 * Still absent from stock Android a decade after custom ROMs shipped it, which is why it
 * scored as a cheap win rather than a nostalgia item.
 *
 * **Off by default.** This consumes key events on the input hot path inside system_server; a
 * mistake here does not crash, it silently breaks the volume rocker, which is worse because
 * the user cannot tell what did it. Three conditions must all hold before a press is taken:
 * the feature is enabled, the screen is off, and audio is actually playing.
 */
class HardwareKeysFeature : Injection {

    override val descriptor = FeatureDescriptor(
        id = ID,
        name = "Hardware keys",
        detail = "While the screen is off, volume up skips to the next track and " +
            "volume down goes back to the previous one. With the screen on, the " +
            "volume keys do exactly what they always did.\n\n" +
            "It is the one thing wired earbuds used to give you and the phone never " +
            "did: changing track without taking it out of your pocket.\n\n" +
            "Marked experimental because it intercepts key events inside " +
            "system_server, which is the riskiest place this project touches. The " +
            "boot guard will disable it automatically if a boot fails with it on.",
        description = "Volume keys skip tracks while the screen is off.",
        category = Category.INPUT,
        injects = setOf(ProcessTarget.SystemServer),
        riskTier = RiskTier.BOOTLOOP,
        stability = Stability.EXPERIMENTAL,
        settings = listOf(
            SettingSpec(
                KEY_ENABLED, "Volume keys skip tracks", SettingSpec.Type.BOOLEAN,
                default = "false", perPackage = false,
                help = "Only while the screen is off and audio is already playing.",
            ),
        ),
    )

    override fun probes(env: HookEnv) = listOf(
        // Default false. This consumes input events, so it must be asked for explicitly.
        enabledWhen("enabled", ID, KEY_ENABLED, default = false),
        classPresent("PhoneWindowManager", POLICY, "this build uses a different window policy"),
        probe("$INTERCEPT (int-returning)") {
            interceptMethod(it)
                ?.let { m -> Outcome.Pass(m.name) }
                ?: Outcome.Fail(Reason.METHOD_NOT_FOUND, "$INTERCEPT not found or not int-returning")
        },
        probe("screen-state callbacks") {
            val found = sleepWakeMethods(it)
            if (found.isEmpty()) {
                Outcome.Warn(
                    "startedGoingToSleep/startedWakingUp absent",
                    "screen state cannot be tracked, so keys will never be taken",
                )
            } else {
                Outcome.Pass("${found.size} callback(s)")
            }
        },
    )

    override fun install(env: HookEnv): InstallResult {
        val intercept = interceptMethod(env) ?: return InstallResult.Failed(Reason.METHOD_NOT_FOUND)
        val handles = mutableListOf<XposedInterface.HookHandle>()

        runCatching { env.installHook(intercept, "keys.intercept", KeyHooker(env)) }
            .onSuccess { handles += it }
            .onFailure { return InstallResult.Failed(Reason.HOOK_FAILED, it) }

        // Screen state comes from the policy's own callbacks; see ScreenState for why.
        sleepWakeMethods(env).forEachIndexed { i, method ->
            val asleep = method.name.contains("Sleep", ignoreCase = true)
            runCatching {
                env.installHook(method, "keys.screen$i", ScreenHooker(asleep))
            }.onSuccess { handles += it }
        }

        env.log("volume-key track skip armed (${handles.size} hooks)")
        return InstallResult.Installed(handles)
    }

    private fun interceptMethod(env: HookEnv) =
        env.findClassOrNull(POLICY)
            ?.declaredMethods
            ?.firstOrNull { it.name == INTERCEPT && it.returnType == Int::class.javaPrimitiveType }

    private fun sleepWakeMethods(env: HookEnv) =
        env.findClassOrNull(POLICY)
            ?.declaredMethods
            ?.filter { it.name == "startedGoingToSleep" || it.name == "startedWakingUp" }
            .orEmpty()

    private class ScreenHooker(private val asleep: Boolean) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            if (asleep) ScreenState.markAsleep() else ScreenState.markAwake()
            return chain.proceed()
        }
    }

    private class KeyHooker(private val env: HookEnv) : XposedInterface.Hooker {

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val handled = runCatching { handle(chain) }.getOrDefault(false)
            // 0 == consumed: not passed to the user. Anything else falls through untouched.
            return if (handled) 0 else chain.proceed()
        }

        private fun handle(chain: XposedInterface.Chain): Boolean {
            if (ScreenState.interactive) return false

            val event = chain.args.firstOrNull { it is KeyEvent } as? KeyEvent ?: return false
            if (event.action != KeyEvent.ACTION_DOWN) return false

            val media = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> KeyEvent.KEYCODE_MEDIA_NEXT
                KeyEvent.KEYCODE_VOLUME_DOWN -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> return false
            }

            if (!MediaControl.isPlaying()) return false

            return MediaControl.send(media).also {
                if (it) env.log("volume key -> ${KeyEvent.keyCodeToString(media)}")
            }
        }
    }

    companion object {
        val ID = FeatureId("core.hardwarekeys")
        val FACTORY = InjectionFactory { HardwareKeysFeature() }

        const val KEY_ENABLED = "enabled"

        private const val POLICY = "com.android.server.policy.PhoneWindowManager"
        private const val INTERCEPT = "interceptKeyBeforeQueueing"
    }
}
