package dev.lostxposed.features.textengine

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Wraps the keyboard's own input view and steals **two-finger** drags.
 *
 * Two fingers rather than one is what makes this keyboard-agnostic: typing is always a
 * single pointer, so there is no gesture to disambiguate and no per-keyboard knowledge of
 * where the spacebar is. Exi needed that knowledge because it lived inside SwiftKey.
 *
 * The wrapped view never sees a two-finger sequence, so the host keyboard cannot fight us
 * for it.
 */
@SuppressLint("ViewConstructor")
class GestureContainer(
    context: Context,
    private val settings: TextEngineSettings,
    private val connection: () -> InputConnection?,
    private val log: (String) -> Unit,
) : FrameLayout(context) {

    private var tracking = false
    private var axisDecided = false
    private var horizontal = false
    private var anchorX = 0f
    private var anchorY = 0f
    private var emitted = 0

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!settings.enabled) return false
        if (ev.pointerCount >= 2 && !tracking) {
            begin(ev)
            return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!settings.enabled) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (ev.pointerCount >= 2 && !tracking) begin(ev)

            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dx = ev.x - anchorX
                val dy = ev.y - anchorY

                if (!axisDecided && (abs(dx) > AXIS_THRESHOLD || abs(dy) > AXIS_THRESHOLD)) {
                    axisDecided = true
                    horizontal = abs(dx) >= abs(dy)
                }
                if (!axisDecided) return true

                val travel = if (horizontal) dx else dy
                val target = settings.steps(travel)
                val delta = target - emitted
                if (delta != 0) {
                    emitted = target
                    apply(delta)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return true
    }

    private fun begin(ev: MotionEvent) {
        tracking = true
        axisDecided = false
        emitted = 0
        anchorX = ev.x
        anchorY = ev.y
    }

    private fun apply(steps: Int) {
        val ic = connection() ?: return
        runCatching {
            when {
                !horizontal -> EditorActions.moveLine(ic, steps, settings.selectMode)
                settings.wordJump -> EditorActions.moveWord(ic, steps, settings.selectMode)
                else -> EditorActions.moveCursor(ic, steps, settings.selectMode)
            }
        }.onFailure { log("gesture failed: ${it.javaClass.simpleName}") }
    }

    private companion object {
        /** Ignore the first few pixels so a sloppy two-finger tap does not move the cursor. */
        const val AXIS_THRESHOLD = 12f
    }
}
