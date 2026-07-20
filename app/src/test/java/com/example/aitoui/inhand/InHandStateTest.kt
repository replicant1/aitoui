package com.example.aitoui.inhand

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InHandStateTest {

    private fun entry(id: Long, medId: Long, number: String) =
        InHandEntry(id = id, medicationId = medId, brand = "Brand$medId", number = number)

    private fun loaded(vararg rows: InHandEntry) =
        InHandState(tabletsInHand = rows.toList(), savedSignature = listSignature(rows.toList()))

    @Test
    fun `a freshly loaded list has no unsaved changes`() {
        assertFalse(loaded(entry(0, 1, "10"), entry(1, 2, "5")).hasUnsavedChanges)
    }

    @Test
    fun `the initial empty state has no unsaved changes`() {
        assertFalse(InHandState().hasUnsavedChanges)
    }

    @Test
    fun `adding or deleting a row is an unsaved change`() {
        val base = loaded(entry(0, 1, "10"))
        assertTrue(base.copy(tabletsInHand = base.tabletsInHand + entry(1, 2, "5")).hasUnsavedChanges)

        val two = loaded(entry(0, 1, "10"), entry(1, 2, "5"))
        assertTrue(two.copy(tabletsInHand = two.tabletsInHand.drop(1)).hasUnsavedChanges)
    }

    @Test
    fun `the same rows in a different order are not an unsaved change`() {
        val rows = listOf(entry(0, 1, "10"), entry(1, 2, "5"))
        val reordered = InHandState(tabletsInHand = rows.reversed(), savedSignature = listSignature(rows))
        assertFalse(reordered.hasUnsavedChanges)
    }

    @Test
    fun `staging fields on their own are not unsaved changes`() {
        val staged = loaded(entry(0, 1, "10")).copy(selectedMedicationId = 7, numberOfTablets = "99")
        assertFalse(staged.hasUnsavedChanges)
    }
}
