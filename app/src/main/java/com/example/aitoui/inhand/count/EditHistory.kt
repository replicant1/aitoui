package com.example.aitoui.inhand.count

/**
 * A bounded undo/redo stack of immutable snapshots of type [T] (here, the marker list). The caller
 * [record]s the state *before* each edit; [undo]/[redo] swap the current state for the neighbouring
 * snapshot. Pure and JVM-testable — no Android or ViewModel dependencies.
 *
 * Recording a new edit clears the redo stack (the usual branch-on-new-edit behaviour). The undo stack is
 * capped at [limit] entries so a long session can't grow without bound; the oldest snapshots are dropped.
 */
class EditHistory<T>(private val limit: Int = 30) {

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Snapshot [current] before an edit is applied. Clears any redo history. */
    fun record(current: T) {
        undoStack.addLast(current)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    /** Step back: push [current] onto the redo stack and return the previous snapshot, or null if none. */
    fun undo(current: T): T? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        return undoStack.removeLast()
    }

    /** Step forward: push [current] onto the undo stack and return the next snapshot, or null if none. */
    fun redo(current: T): T? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current)
        return redoStack.removeLast()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
