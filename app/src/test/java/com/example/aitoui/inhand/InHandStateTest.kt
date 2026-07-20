package com.example.aitoui.inhand

import org.junit.Assert.assertEquals
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

    // --- Merge / collate ---

    @Test
    fun `canMerge is true only when a medication appears more than once`() {
        assertFalse(loaded(entry(0, 1, "10"), entry(1, 2, "5")).canMerge)
        assertTrue(loaded(entry(0, 1, "10"), entry(1, 1, "5")).canMerge)
        assertFalse(InHandState().canMerge)
    }

    @Test
    fun `collate sums quantities of the same medication into one row`() {
        val merged = collateInHand(
            listOf(entry(0, 1, "27"), entry(1, 1, "24"), entry(2, 2, "5"), entry(3, 1, "20")),
        )
        // Medication 1's three rows (27 + 24 + 20 = 71) collapse to one; medication 2 stays.
        assertEquals(2, merged.size)
        val med1 = merged.single { it.medicationId == 1L }
        assertEquals("71", med1.number)
        assertEquals(0L, med1.id)          // keeps the first grouped row's id
        assertEquals("Brand1", med1.brand)
        assertEquals("5", merged.single { it.medicationId == 2L }.number)
    }

    @Test
    fun `collate preserves first-appearance order and is idempotent`() {
        val rows = listOf(entry(0, 2, "5"), entry(1, 1, "10"), entry(2, 2, "3"))
        val merged = collateInHand(rows)
        assertEquals(listOf(2L, 1L), merged.map { it.medicationId }) // 2 first (it appeared first)
        assertEquals(merged, collateInHand(merged))                  // merging again changes nothing
    }

    @Test
    fun `collate sums fractional quantities`() {
        val merged = collateInHand(listOf(entry(0, 1, "0.5"), entry(1, 1, "0.5")))
        assertEquals("1", merged.single().number)
    }
}
