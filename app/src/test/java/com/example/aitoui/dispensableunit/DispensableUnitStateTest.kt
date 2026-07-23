package com.example.aitoui.dispensableunit

import com.example.aitoui.data.DoseUnit
import com.example.aitoui.data.Medication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DispensableUnitStateTest {

    @Test
    fun `a blank form has no unsaved changes`() {
        assertFalse(DispensableUnitState().hasUnsavedChanges)
    }

    @Test
    fun `loaded dropdown options alone are not an unsaved change`() {
        val withOptions = DispensableUnitState(
            medications = listOf(Medication(id = 1, brandName = "Panadol", activeIngredient = "Paracetamol")),
        )
        assertFalse(withOptions.hasUnsavedChanges)
    }

    @Test
    fun `picking a medication or typing a field counts as an unsaved change`() {
        assertTrue(DispensableUnitState(selectedMedicationId = 1).hasUnsavedChanges)
        assertTrue(DispensableUnitState(dosePerTablet = "500").hasUnsavedChanges)
        assertTrue(DispensableUnitState(tabletsPerUnit = "24").hasUnsavedChanges)
        assertTrue(DispensableUnitState(selectedDoseUnit = DoseUnit.GRAMS).hasUnsavedChanges)
    }
}
