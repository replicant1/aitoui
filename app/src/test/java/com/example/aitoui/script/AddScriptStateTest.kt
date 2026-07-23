package com.example.aitoui.script

import com.example.aitoui.data.DoseUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddScriptStateTest {

    /** Mirror the ViewModel's seed step: whatever the form is first shown with becomes the baseline. */
    private fun seeded(state: AddScriptState) = state.copy(baseline = state.fields)

    @Test
    fun `a blank form has no unsaved changes`() {
        assertFalse(seeded(AddScriptState()).hasUnsavedChanges)
    }

    @Test
    fun `a scan-prefilled form has no unsaved changes until a field is edited`() {
        val prefilled = seeded(AddScriptState(brandName = "Dytrex", dosePerTablet = "60", repeats = "5"))
        assertFalse(prefilled.hasUnsavedChanges)
        assertTrue(prefilled.copy(dosePerTablet = "90").hasUnsavedChanges)
    }

    @Test
    fun `changing the selected dose unit counts as an edit`() {
        val prefilled = seeded(AddScriptState(dosePerTablet = "1", tabletsPerUnit = "30"))
        assertTrue(prefilled.copy(selectedDoseUnit = DoseUnit.GRAMS).hasUnsavedChanges)
    }

    @Test
    fun `transient resolution-dialog state is not counted as an edit`() {
        val seed = seeded(AddScriptState(brandName = "Dytrex"))
        assertFalse(seed.copy(duplicateSerial = true).hasUnsavedChanges)
        assertFalse(
            seed.copy(
                medicationStep = MedicationResolution(exact = emptyList(), similar = emptyList(), blocked = false),
            ).hasUnsavedChanges,
        )
    }
}
