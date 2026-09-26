package dev.lostxposed.features.textengine

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * Editing primitives expressed entirely through [InputConnection].
 *
 * This is the whole reason Text Engine is not a SwiftKey mod. `InputConnection` is the
 * contract every keyboard uses to talk to every editor, so these work in View-based apps,
 * Jetpack Compose `BasicTextField`, WebView and Flutter alike, none of which a `TextView`
 * hook can reach.
 *
 * Cursor movement goes through DPAD key events rather than `setSelection`, because absolute
 * offsets are not reliably knowable: `getTextBeforeCursor` is capped and returns nothing
 * useful in large or virtualised editors. Key events are relative and the editor resolves
 * them itself.
 */
object EditorActions {

    fun moveCursor(ic: InputConnection, steps: Int, select: Boolean = false) {
        if (steps == 0) return
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) { sendKey(ic, code, select) }
    }

    fun moveLine(ic: InputConnection, steps: Int, select: Boolean = false) {
        if (steps == 0) return
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
        repeat(kotlin.math.abs(steps)) { sendKey(ic, code, select) }
    }

    fun moveWord(ic: InputConnection, steps: Int, select: Boolean = false) {
        if (steps == 0) return
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        var meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (select) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        repeat(kotlin.math.abs(steps)) { sendKeyWithMeta(ic, code, meta) }
    }

    /**
     * Delegates to the editor's own undo stack via the standard context-menu action, rather
     * than reimplementing one. The editor knows its own history; a keyboard-side stack would
     * desynchronise the moment anything else edits the field.
     */
    fun undo(ic: InputConnection): Boolean = ic.performContextMenuAction(android.R.id.undo)

    fun redo(ic: InputConnection): Boolean = ic.performContextMenuAction(android.R.id.redo)

    fun cut(ic: InputConnection): Boolean = ic.performContextMenuAction(android.R.id.cut)
    fun copy(ic: InputConnection): Boolean = ic.performContextMenuAction(android.R.id.copy)
    fun paste(ic: InputConnection): Boolean = ic.performContextMenuAction(android.R.id.paste)
    fun selectAll(ic: InputConnection): Boolean = ic.performContextMenuAction(android.R.id.selectAll)

    fun clearSelection(ic: InputConnection) {
        // Collapse to the right edge of whatever is selected.
        sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT, select = false)
        sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT, select = false)
    }

    private fun sendKey(ic: InputConnection, keyCode: Int, select: Boolean) {
        val meta = if (select) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        sendKeyWithMeta(ic, keyCode, meta)
    }

    private fun sendKeyWithMeta(ic: InputConnection, keyCode: Int, meta: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(
            KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta),
        )
        ic.sendKeyEvent(
            KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta),
        )
    }
}
