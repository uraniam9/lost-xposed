package dev.lostxposed.features.notificationrules

import android.app.Notification
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
 * Suppress notifications before they are ever posted.
 *
 * A `NotificationListenerService` can only read and dismiss — the notification has already
 * appeared, already buzzed, already lit the screen. Channels give coarse per-category control
 * but cannot match on content. Only a hook inside `NotificationManagerService` can decide
 * *before* display, and that is the whole differentiator over every non-root notification app.
 *
 * Suppression works by not calling `proceed()`. Because that silently drops something the
 * user might be waiting for, every suppression is logged — a notification that vanishes with
 * no trace is indistinguishable from a bug.
 */
class NotificationRulesFeature : Injection {

    override val descriptor = FeatureDescriptor(
        id = ID,
        name = "Notification rules",
        detail = "Stops a notification before it is posted, per app, and optionally " +
            "only when its text contains words you choose.\n\n" +
            "This is the part Android cannot do for you. Channels turn a whole " +
            "category off. A notification listener app can dismiss things, but only " +
            "after they have already arrived \u2014 it has buzzed, lit the screen and " +
            "landed on your lock screen by then. Running inside the notification " +
            "service means the decision happens first.\n\n" +
            "Set \"Applies to\" to the app whose notifications you want to filter. " +
            "Block all silences it completely; keywords are comma separated and match " +
            "anywhere in the title or body.\n\n" +
            "Be careful what you filter. A blocked notification is gone, not delayed.",
        description = "Block notifications per app, or by keyword, before they are posted.",
        category = Category.NOTIFICATIONS,
        injects = setOf(ProcessTarget.SystemServer),
        riskTier = RiskTier.BOOTLOOP,
        stability = Stability.BETA,
        settings = listOf(
            SettingSpec(
                NotificationRule.KEY_BLOCK_ALL, "Block everything",
                SettingSpec.Type.BOOLEAN, default = "false",
            ),
            SettingSpec(
                NotificationRule.KEY_KEYWORDS, "Block keywords", SettingSpec.Type.STRING,
                help = "Comma separated. Matches title, text and summary.",
            ),
            SettingSpec(
                NotificationRule.KEY_CASE_SENSITIVE, "Case sensitive",
                SettingSpec.Type.BOOLEAN, default = "false",
            ),
        ),
    )

    override fun probes(env: HookEnv) = listOf(
        classPresent(
            "NotificationManagerService",
            NMS,
            "this build relocates NotificationManagerService",
        ),
        probe("void $ENQUEUE overloads") {
            val found = enqueueMethods(it)
            if (found.isEmpty()) {
                Outcome.Fail(
                    Reason.METHOD_NOT_FOUND,
                    "no void $ENQUEUE — a non-void form cannot be suppressed safely",
                )
            } else {
                Outcome.Pass("${found.size} hookable")
            }
        },
    )

    override fun install(env: HookEnv): InstallResult {
        val handles = mutableListOf<XposedInterface.HookHandle>()
        val hooker = EnqueueHooker(env)

        enqueueMethods(env).forEachIndexed { i, method ->
            runCatching { env.installHook(method, "notify.enqueue$i", hooker) }
                .onSuccess { handles += it }
                .onFailure { env.log("enqueue hook failed on ${method.name}", it) }
        }

        if (handles.isEmpty()) return InstallResult.Failed(Reason.HOOK_FAILED)
        env.log("notification rules watching ${handles.size} entry point(s)")
        return InstallResult.Installed(handles)
    }

    /**
     * Only void overloads are hooked. Suppression means returning without calling
     * `proceed()`, and inventing a return value for a non-void method would be guessing at
     * what the caller expects — inside system_server that is not a guess worth making.
     */
    private fun enqueueMethods(env: HookEnv): List<Method> =
        env.findClassOrNull(NMS)
            ?.declaredMethods
            ?.filter { it.name == ENQUEUE && it.returnType == Void.TYPE }
            .orEmpty()

    private class EnqueueHooker(private val env: HookEnv) : XposedInterface.Hooker {

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val decision = runCatching { shouldBlock(chain) }.getOrDefault(false)
            if (decision) return null
            return chain.proceed()
        }

        private fun shouldBlock(chain: XposedInterface.Chain): Boolean {
            val args = chain.args
            // Signatures vary across releases, so locate arguments by type rather than index.
            val pkg = args.firstOrNull { it is String } as? String ?: return false
            val notification = args.firstOrNull { it is Notification } as? Notification

            val rule = NotificationRule.from(env.configFor(ID, pkg))
            if (!rule.isActive) return false

            return if (rule.blocks(notification)) {
                env.log("suppressed notification from $pkg (${rule.describe()})")
                true
            } else {
                false
            }
        }
    }

    companion object {
        val ID = FeatureId("core.notificationrules")
        val FACTORY = InjectionFactory { NotificationRulesFeature() }

        private const val NMS = "com.android.server.notification.NotificationManagerService"
        private const val ENQUEUE = "enqueueNotificationInternal"
    }
}
