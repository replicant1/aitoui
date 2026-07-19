package com.example.aitoui.inhand.count

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    @Test
    fun `a fresh history can neither undo nor redo`() {
        val h = EditHistory<String>()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertNull(h.undo("a"))
        assertNull(h.redo("a"))
    }

    @Test
    fun `undo returns the recorded snapshot and enables redo`() {
        val h = EditHistory<String>()
        h.record("v0")            // before editing to v1
        assertTrue(h.canUndo)
        assertEquals("v0", h.undo("v1"))
        assertFalse(h.canUndo)
        assertTrue(h.canRedo)
    }

    @Test
    fun `redo re-applies the undone snapshot`() {
        val h = EditHistory<String>()
        h.record("v0")
        assertEquals("v0", h.undo("v1")) // now at v0, current = v1 on redo stack
        assertEquals("v1", h.redo("v0"))
        assertFalse(h.canRedo)
        assertTrue(h.canUndo)
    }

    @Test
    fun `walk back through several edits in order`() {
        val h = EditHistory<Int>()
        h.record(0); h.record(1); h.record(2) // edited 0->1->2->3, current = 3
        assertEquals(2, h.undo(3))
        assertEquals(1, h.undo(2))
        assertEquals(0, h.undo(1))
        assertNull(h.undo(0))
    }

    @Test
    fun `recording a new edit clears the redo stack`() {
        val h = EditHistory<String>()
        h.record("v0")
        h.undo("v1")              // redo now has v1
        assertTrue(h.canRedo)
        h.record("v0")            // a new edit branches, discarding redo
        assertFalse(h.canRedo)
    }

    @Test
    fun `the undo stack is capped, dropping the oldest`() {
        val h = EditHistory<Int>(limit = 2)
        h.record(0); h.record(1); h.record(2) // only the two newest (1, 2) survive
        assertEquals(2, h.undo(3))
        assertEquals(1, h.undo(2))
        assertNull(h.undo(1)) // 0 was dropped
    }

    @Test
    fun `clear empties both stacks`() {
        val h = EditHistory<String>()
        h.record("v0")
        h.undo("v1")
        h.clear()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
    }
}
