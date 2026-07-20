package com.example.aitoui.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationEntityTest {

    @Test
    fun `requiresPrescription defaults to true on a new medication`() {
        assertTrue(Medication(brandName = "Panadol", activeIngredient = "Paracetamol").requiresPrescription)
    }

    @Test
    fun `entity and domain mappings carry requiresPrescription both ways`() {
        val overTheCounter = Medication(
            brandName = "Cartia", activeIngredient = "Aspirin", requiresPrescription = false,
        )
        assertFalse(overTheCounter.toEntity().requiresPrescription)
        assertFalse(overTheCounter.toEntity().toDomain().requiresPrescription)

        val prescription = Medication(brandName = "Lipitor", activeIngredient = "Atorvastatin")
        assertTrue(prescription.toEntity().toDomain().requiresPrescription)
    }
}
