package com.example.aitoui.medication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationStateTest {

    @Test
    fun `a blank form has no unsaved changes`() {
        assertFalse(MedicationState().hasUnsavedChanges)
    }

    @Test
    fun `typing into either field counts as an unsaved change`() {
        assertTrue(MedicationState(brandName = "Panadol").hasUnsavedChanges)
        assertTrue(MedicationState(activeIngredient = "Paracetamol").hasUnsavedChanges)
    }
}
