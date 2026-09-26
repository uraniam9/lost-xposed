package dev.lostxposed

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Asks a hooked process to end itself so the system brings it back with the new settings.
 *
 * The app cannot restart SystemUI from outside: `force-stop` on it is a no-op, measured, and
 * everything else needs shell. What it can do is ask the module, which is already running
 * inside that process, to kill its own pid. The receiver on the other end requires a
 * signature-level permission, so this is not a button any other app can press.
 */
object RestartControl {

    const val SYSTEM_UI = "com.android.systemui"

    private const val TAG = "LostXposed"

    private const val ACTION = "dev.lostxposed.RESTART_PROCESS"
    private const val EXTRA_TARGET = "target"

    fun restart(context: Context, packageName: String) {
        val intent = Intent(ACTION).apply {
            putExtra(EXTRA_TARGET, packageName)
            // A receiver registered at runtime is not in any manifest, so the broadcast has
            // to be allowed to reach a package that may currently be idle.
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        // Sent without a receiver permission, deliberately. The guard belongs on the other
        // side: the receiver is registered with a broadcastPermission, which requires the
        // SENDER to hold it, and only a build signed with this key does. Passing the
        // permission here would instead require SystemUI to hold it, which it never can,
        // and the broadcast would be filtered out before it arrived. Measured: it was.
        // Logged either way. This is a fire-and-forget broadcast with no reply, so without a
        // line here a failure to send is indistinguishable from a failure to receive.
        runCatching { context.sendBroadcast(intent) }
            .onSuccess { Log.i(TAG, "restart broadcast sent to $packageName") }
            .onFailure { Log.w(TAG, "restart broadcast failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

    /**
     * Whether anything the user has configured needs [packageName] restarted.
     *
     * Only true when there is something to restart *for*. Showing the button on a screen
     * where nothing has been set would be an invitation to a pointless flicker.
     */
    fun isNeededFor(descriptorsWithSettings: List<String>): Boolean =
        descriptorsWithSettings.isNotEmpty()
}
