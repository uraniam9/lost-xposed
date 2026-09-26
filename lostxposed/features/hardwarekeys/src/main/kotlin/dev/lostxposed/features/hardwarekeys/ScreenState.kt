package dev.lostxposed.features.hardwarekeys

import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.view.KeyEvent

/**
 * Screen state tracked by hooking the policy's own sleep/wake callbacks rather than asking
 * PowerManager.
 *
 * `interceptKeyBeforeQueueing` runs on the input hot path, before any Context is convenient,
 * and querying a system service per keystroke would be both slow and circular: the policy is
 * part of what decides interactivity in the first place.
 */
object ScreenState {

    @Volatile
    var interactive: Boolean = true
        private set

    fun markAsleep() {
        interactive = false
    }

    fun markAwake() {
        interactive = true
    }

    /**
     * Asked once, when the hooks go in. They go in after the boot now, once settings arrive,
     * and the callbacks only report changes: with the screen already off, keys would not be
     * taken until it had been on and off again.
     */
    fun sync() {
        runCatching {
            SystemContext.get()?.getSystemService(PowerManager::class.java)?.isInteractive
        }.getOrNull()?.let { interactive = it }
    }
}

object MediaControl {

    @Volatile
    private var audio: AudioManager? = null

    /**
     * Only true when something is actually playing. Without this check, a volume press on a
     * silent phone would be swallowed instead of changing the volume, turning a convenience
     * into a broken volume rocker.
     */
    fun isPlaying(): Boolean = runCatching { manager()?.isMusicActive == true }.getOrDefault(false)

    fun send(keyCode: Int): Boolean = runCatching {
        val am = manager() ?: return false
        val now = android.os.SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        true
    }.getOrDefault(false)

    private fun manager(): AudioManager? {
        audio?.let { return it }
        return runCatching { SystemContext.get()?.getSystemService(AudioManager::class.java) }
            .getOrNull()
            ?.also { audio = it }
    }
}

/** system_server's own Context, the one its ActivityThread was started with. */
internal object SystemContext {
    fun get(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    }.getOrNull()
}
