package com.example.timestream

import android.widget.EditText

class UndoRedoManager(private val maxHistorySize: Int = 50) {

    // A snapshot of text + selection
    data class State(val text: CharSequence, val selectionStart: Int, val selectionEnd: Int)

    private val undoStack = ArrayDeque<State>()
    private val redoStack = ArrayDeque<State>()

    /**
     * Call this after you modify the EditText to store a new state in the undo stack.
     */
    fun captureState(editText: EditText) {
        // Store the new text + selection as a State in the undo stack
        val state = State(
            text = editText.text.toString(),
            selectionStart = editText.selectionStart,
            selectionEnd = editText.selectionEnd
        )
        undoStack.addLast(state)

        // If we exceed max history, trim the oldest
        if (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }

        // Once a new change is made, we usually clear the redo stack
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.size > 1
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Undo: move top of undoStack -> redoStack, revert to previous.
     */
    fun undo(editText: EditText) {
        if (!canUndo()) return

        // Current state is top of undoStack
        val currentState = undoStack.removeLast()

        // The next top of undoStack is the "previous" state
        val previousState = undoStack.lastOrNull() ?: return

        // Push the current state to redoStack (for Redo)
        redoStack.addLast(currentState)

        // Restore the previous state to the EditText
        applyState(editText, previousState)
    }

    /**
     * Redo: move top of redoStack -> undoStack, apply it.
     */
    fun redo(editText: EditText) {
        if (!canRedo()) return

        val stateToRestore = redoStack.removeLast()
        // Current state becomes top of undoStack
        val currentState = State(
            text = editText.text.toString(),
            selectionStart = editText.selectionStart,
            selectionEnd = editText.selectionEnd
        )
        undoStack.addLast(currentState)

        applyState(editText, stateToRestore)
    }

    private fun applyState(editText: EditText, state: State) {
        editText.setText(state.text)
        val length = editText.text.length
        val start = state.selectionStart.coerceIn(0, length)
        val end = state.selectionEnd.coerceIn(0, length)
        editText.setSelection(start, end)
    }
}
