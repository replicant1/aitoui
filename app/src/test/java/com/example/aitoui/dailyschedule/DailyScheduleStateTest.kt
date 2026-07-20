package com.example.aitoui.dailyschedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyScheduleStateTest {

    private fun entry(id: Long, medId: Long, number: String) =
        DailyScheduleEntry(id = id, medicationId = medId, brand = "Brand$medId", number = number)

    private fun loaded(vararg rows: DailyScheduleEntry) =
        DailyScheduleState(tabletsTaken = rows.toList(), savedSignature = scheduleSignature(rows.toList()))

    @Test
    fun `a freshly loaded schedule has no unsaved changes`() {
        assertFalse(loaded(entry(0, 1, "1"), entry(1, 2, "0.5")).hasUnsavedChanges)
    }

    @Test
    fun `the initial empty state has no unsaved changes`() {
        assertFalse(DailyScheduleState().hasUnsavedChanges)
    }

    @Test
    fun `adding or deleting a row is an unsaved change`() {
        val base = loaded(entry(0, 1, "1"))
        assertTrue(base.copy(tabletsTaken = base.tabletsTaken + entry(1, 2, "0.5")).hasUnsavedChanges)

        val two = loaded(entry(0, 1, "1"), entry(1, 2, "0.5"))
        assertTrue(two.copy(tabletsTaken = two.tabletsTaken.drop(1)).hasUnsavedChanges)
    }

    @Test
    fun `staging fields on their own are not unsaved changes`() {
        val staged = loaded(entry(0, 1, "1")).copy(selectedUnitId = 7, numberOfTablets = "2")
        assertFalse(staged.hasUnsavedChanges)
    }
}
