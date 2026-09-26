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
import dev.lostxposed.core.api.installHook
import dev.lostxposed.core.api.probe
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * With the screen off: hold a volume key to skip tracks, and hold power for the torch.
 *
 * Both are a hold rather than a tap, and for the same reason. A tap is the key's ordinary
 * job, which a hold has no business taking over: an ordinary volume press still has to change
 * the volume, and an ordinary power press still has to wake or lock the phone. Holding past
 * half a second is what marks a press as meant for this instead.
 *
 * The track skip is still absent from stock Android a decade after custom ROMs shipped it,
 * which is why it scored as a cheap win rather than a nostalgia item. The torch was asked for
 * on top of it, and belongs here for the same reason: it is a key doing something useful
 * while the screen is off, and nothing else.
 *
 * **Both off by default.** This consumes key events on the input hot path inside
 * system_server; a mistake here does not crash, it silently breaks a key, which is worse
 * because the user cannot tell what did it. Both are handled the same way: the down is held
 * back until the key is released (an ordinary press, replayed exactly as it arrived) or the
 * hold fires (this feature's own action instead). See [VolumeHold] and [PowerHold] for why a
 * press cannot simply be judged and then passed on.
 */
class HardwareKeysFeature : Injection {

    override val descriptor = FeatureDescriptor(
        id = ID,
        name = "Hardware keys",
        detail = "While the screen is off, holding a volume key for half a second skips to " +
            "the next track (volume up) or the previous one (volume down), and holding " +
            "power for half a second switches the torch on or off. A shorter press of " +
            "either key still does exactly what it always did: volume changes, power " +
            "wakes or locks the phone. With the screen on, both keys are left alone " +
            "entirely.\n\n" +
            "The track skip is the one thing wired earbuds used to give you and the phone " +
            "never did: changing track without taking it out of your pocket, and without " +
            "reaching for the volume rocker every time a normal press would just change " +
            "the volume instead. The torch is for the dark, when unlocking to find a tile " +
            "is the last thing you want to do.\n\n" +
            "Marked experimental because it intercepts key events inside " +
            "system_server, which is the riskiest place this project touches. The " +
            "boot guard will disable it automatically if a boot fails with it on.",
        description = "Hold a volume key to skip tracks, or power for the torch, while the " +
            "screen is off. A shorter press still does what it always did.",
        category = Category.INPUT,
        injects = setOf(ProcessTarget.SystemServer),
        riskTier = RiskTier.BOOTLOOP,
        stability = Stability.EXPERIMENTAL,
        settings = listOf(
            SettingSpec(
                KEY_ENABLED, "Hold volume keys to skip tracks", SettingSpec.Type.BOOLEAN,
                default = "false", perPackage = false,
                help = "Only while the screen is off and audio is already playing. Hold " +
                    "past half a second; a shorter press still changes the volume as it " +
                    "always did.",
            ),
            SettingSpec(
                KEY_TORCH, "Hold power for the torch", SettingSpec.Type.BOOLEAN,
                default = "false", perPackage = false,
                help = "Only while the screen is off. Hold for half a second to switch the " +
                    "torch on, and again to switch it off. A short buzz says it worked. A " +
                    "shorter press still wakes the phone, when you let go rather than when " +
                    "you press.",
            ),
        ),
    )

    override fun probes(env: HookEnv) = listOf(
        // Both default to false. This consumes input events, so it must be asked for explicitly.
        probe("enabled") {
            val asked = wanted(it)
            if (asked.isEmpty()) {
                Outcome.Skip(Reason.DISABLED_BY_USER)
            } else {
                Outcome.Pass(asked.joinToString(" + "))
            }
        },
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
        val config = env.configFor(ID)
        val hooker = KeyHooker(
            volume = if (config.boolean(KEY_ENABLED, false)) VolumeHold(env, intercept) else null,
            power = if (config.boolean(KEY_TORCH, false)) PowerHold(env, intercept) else null,
        )
        val handles = mutableListOf<XposedInterface.HookHandle>()

        // Ahead of every other module's hook on this method. A power press held back for the
        // torch has to be held back from all of them; one that ran first would see it twice.
        runCatching {
            env.installHook(intercept, "keys.intercept", hooker, XposedInterface.PRIORITY_HIGHEST)
        }
            .onSuccess { handles += it }
            .onFailure { return InstallResult.Failed(Reason.HOOK_FAILED, it) }

        // Screen state comes from the policy's own callbacks; see ScreenState for why.
        sleepWakeMethods(env).forEachIndexed { i, method ->
            val asleep = method.name.contains("Sleep", ignoreCase = true)
            runCatching {
                env.installHook(method, "keys.screen$i", ScreenHooker(asleep))
            }.onSuccess { handles += it }
        }
        // Settings reach system_server after the boot, so these hooks go in with the screen
        // in whatever state it is already in. Ask once rather than assume it is on.
        ScreenState.sync()

        env.log("hardware keys armed: ${wanted(env).joinToString(" + ")} (${handles.size} hooks)")
        return InstallResult.Installed(handles)
    }

    private fun wanted(env: HookEnv): List<String> {
        val config = env.configFor(ID)
        return listOfNotNull(
            "holding volume keys skips tracks".takeIf { config.boolean(KEY_ENABLED, false) },
            "holding power works the torch".takeIf { config.boolean(KEY_TORCH, false) },
        )
    }

    private fun interceptMethod(env: HookEnv): Method? =
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

    private class KeyHooker(
        private val volume: VolumeHold?,
        private val power: PowerHold?,
    ) : XposedInterface.Hooker {

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val handled = runCatching { handle(chain) }.getOrDefault(false)
            // 0 == consumed: not passed to the user. Anything else falls through untouched.
            return if (handled) 0 else chain.proceed()
        }

        private fun handle(chain: XposedInterface.Chain): Boolean {
            val event = chain.args.firstOrNull { it is KeyEvent } as? KeyEvent ?: return false
            return when (event.keyCode) {
                KeyEvent.KEYCODE_POWER -> power?.handle(chain, event) ?: false
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN ->
                    volume?.handle(chain, event) ?: false
                else -> false
            }
        }
    }

    companion object {
        val ID = FeatureId("core.hardwarekeys")
        val FACTORY = InjectionFactory { HardwareKeysFeature() }

        /** The volume track skip. Named before there was a second switch. */
        const val KEY_ENABLED = "enabled"
        const val KEY_TORCH = "powerTorch"

        private const val POLICY = "com.android.server.policy.PhoneWindowManager"
        private const val INTERCEPT = "interceptKeyBeforeQueueing"
    }
}
