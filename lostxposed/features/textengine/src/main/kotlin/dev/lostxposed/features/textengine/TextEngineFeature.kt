package dev.lostxposed.features.textengine

import android.inputmethodservice.InputMethodService
import android.view.View
import dev.lostxposed.core.api.Category
import dev.lostxposed.core.api.FeatureDescriptor
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.api.HookEnv
import dev.lostxposed.core.api.Injection
import dev.lostxposed.core.api.InjectionFactory
import dev.lostxposed.core.api.InstallResult
import dev.lostxposed.core.api.Outcome
import dev.lostxposed.core.api.PackageFilter
import dev.lostxposed.core.api.ProcessTarget
import dev.lostxposed.core.api.Reason
import dev.lostxposed.core.api.RiskTier
import dev.lostxposed.core.api.SettingSpec
import dev.lostxposed.core.api.Stability
import dev.lostxposed.core.api.applicableWhen
import dev.lostxposed.core.api.enabledWhen
import dev.lostxposed.core.api.installHook
import dev.lostxposed.core.api.methodPresent
import dev.lostxposed.core.api.probe
import io.github.libxposed.api.XposedInterface

/**
 * Cursor and selection gestures for every app, from any keyboard.
 *
 * This is the Exi revival, and deliberately not a port. Exi hooked SwiftKey's internals and
 * died of it: SwiftKey is closed, R8-obfuscated and ships monthly, so every release broke a
 * runtime signature scanner. The features users actually wanted (cursor movement, selection,
 * undo) were never keyboard-specific; they are text editing.
 *
 * So this hooks `android.inputmethodservice.InputMethodService`, an AOSP class that is never
 * obfuscated and has been stable for a decade, and drives editing through `InputConnection`,
 * the contract every keyboard already uses to talk to every editor. Coverage therefore
 * includes Compose `BasicTextField`, WebView and Flutter, none of which a `TextView` hook can
 * reach, and that coverage grows as the platform moves to Compose rather than shrinking.
 */
class TextEngineFeature : Injection {

    override val descriptor = FeatureDescriptor(
        id = ID,
        name = "Text engine",
        detail = "Slide two fingers across the keyboard to move the cursor, instead of " +
            "poking at the text and missing.\n\n" +
            "It works at the keyboard level rather than the app level, so it applies " +
            "everywhere you type rather than only in apps that bothered to implement " +
            "it. Jump by word moves a word at a time instead of a character. Extend " +
            "selection turns the same gesture into a way to select text.\n\n" +
            "Tick your keyboard below. The gesture is read by the keyboard\u0027s own " +
            "process, so that process has to be in this module\u0027s scope, and it " +
            "has to restart before anything happens. Switching to another keyboard " +
            "and back is usually enough.",
        description = "Two-finger cursor and selection gestures on any keyboard, in any app.",
        howToTest = "Open any text field so your keyboard appears, and type a sentence. Put " +
            "two fingers down on the keyboard itself, not the text field, and drag sideways: " +
            "the cursor should move, with no letters typed. Drag up or down instead and it " +
            "moves by line. Turn on \"Jump by word\" and the same sideways drag jumps a word " +
            "at a time. Turn on \"Extend selection\" and it selects text instead of just " +
            "moving the caret. One finger still types normally the whole time; only two " +
            "fingers together do anything here.",
        category = Category.INPUT,
        injects = setOf(ProcessTarget.App(PackageFilter.ANY)),
        riskTier = RiskTier.APP_CRASH,
        stability = Stability.BETA,
        settings = listOf(
            SettingSpec(
                TextEngineSettings.KEY_ENABLED, "Enabled", SettingSpec.Type.BOOLEAN,
                default = "true", perPackage = false,
            ),
            SettingSpec(
                TextEngineSettings.KEY_PX_PER_STEP, "Pixels per step", SettingSpec.Type.FLOAT,
                default = "28", perPackage = false,
                help = "Drag distance for one cursor step. Lower is faster and twitchier.",
            ),
            SettingSpec(
                TextEngineSettings.KEY_WORD_JUMP, "Jump by word", SettingSpec.Type.BOOLEAN,
                default = "false", perPackage = false,
            ),
            SettingSpec(
                TextEngineSettings.KEY_SELECT_MODE, "Extend selection", SettingSpec.Type.BOOLEAN,
                default = "false", perPackage = false,
            ),
            SettingSpec(
                TextEngineSettings.KEY_IME_PACKAGES, "Keyboards",
                SettingSpec.Type.IME_LIST, perPackage = false,
                help = "Which keyboards to work inside. Leave all unticked to use the "
                    + "built-in list of known ones.",
            ),
        ),
    )

    override fun probes(env: HookEnv) = listOf(
        // Excludes almost every process, so it goes first.
        applicableWhen("known keyboard", Reason.WRONG_PROCESS) {
            it.packageName in TextEngineSettings.imePackages(it.configFor(ID))
        },
        enabledWhen("enabled", ID, TextEngineSettings.KEY_ENABLED, default = true),
        methodPresent("InputMethodService.onCreateInputView", IME_SERVICE, ON_CREATE_INPUT_VIEW),
        probe("gesture settings") {
            val s = TextEngineSettings.from(it.configFor(ID))
            Outcome.Pass(
                "${s.pxPerStep}px/step" +
                    (if (s.wordJump) ", word jump" else "") +
                    (if (s.selectMode) ", select mode" else ""),
            )
        },
    )

    override fun install(env: HookEnv): InstallResult {
        val settings = TextEngineSettings.from(env.configFor(ID))
        val target = onCreateInputView(env)
            ?: return InstallResult.Failed(Reason.METHOD_NOT_FOUND)

        return runCatching {
            val handle = env.installHook(
                target,
                "textengine.inputview",
                InputViewHooker(settings) { env.log(it) },
            )
            env.log("gestures active on ${env.packageName} (${settings.pxPerStep}px/step)")
            InstallResult.Installed(listOf(handle))
        }.getOrElse { InstallResult.Failed(Reason.HOOK_FAILED, it) }
    }

    private fun onCreateInputView(env: HookEnv) =
        env.findClassOrNull(IME_SERVICE)
            ?.declaredMethods
            ?.firstOrNull { it.name == ON_CREATE_INPUT_VIEW && it.parameterCount == 0 }

    /**
     * Wraps whatever view the keyboard built. We never inspect or modify it: the host
     * keyboard keeps full control of its own layout, which is what keeps this working across
     * keyboard updates.
     */
    private class InputViewHooker(
        private val settings: TextEngineSettings,
        private val log: (String) -> Unit,
    ) : XposedInterface.Hooker {

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val created = chain.proceed() as? View ?: return null
            val service = chain.thisObject as? InputMethodService ?: return created

            return runCatching {
                GestureContainer(
                    context = created.context,
                    settings = settings,
                    connection = { service.currentInputConnection },
                    log = log,
                ).apply { addView(created) }
            }.getOrElse {
                // Never take the keyboard down: a broken gesture layer is an annoyance,
                // a keyboard that will not open is a bricked phone from the user's side.
                log("failed to wrap input view: ${it.javaClass.simpleName}")
                created
            }
        }
    }

    companion object {
        val ID = FeatureId("core.textengine")
        val FACTORY = InjectionFactory { TextEngineFeature() }

        private const val IME_SERVICE = "android.inputmethodservice.InputMethodService"
        private const val ON_CREATE_INPUT_VIEW = "onCreateInputView"
    }
}
