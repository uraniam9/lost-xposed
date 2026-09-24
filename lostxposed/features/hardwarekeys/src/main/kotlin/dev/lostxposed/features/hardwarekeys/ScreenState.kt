package dev.lostxposed.features.hardwarekeys

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/**
 * Screen state tracked by hooking the policy's own sleep/wake callbacks rather than asking
 * PowerManager.
 *
 * `interceptKeyBeforeQueueing` runs on the input hot path, before any Context is convenient,
 * and querying a system service per keystroke would be both slow and circular — the policy is
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
}

object MediaControl {

    @Volatile
    private var audio: AudioManager? = null

    /**
     * Only true when something is actually playing. Without this check, a volume press on a
     * silent phone would be swallowed instead of changing the volume — turning a convenience
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
        val context = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull() ?: return null
        return runCatching { context.getSystemService(AudioManager::class.java) }
            .getOrNull()
            ?.also { audio = it }
    }
}
