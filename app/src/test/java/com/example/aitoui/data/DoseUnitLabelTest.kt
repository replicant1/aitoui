package com.example.aitoui.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DoseUnitLabelTest {

    @Test
    fun `dispensable unit label uses stored dose unit`() {
        val details = DispensableUnitDetails(
            formatId = 1,
            medicationId = 1,
            brandName = "Ventolin",
            activeIngredient = "Salbutamol",
            dosePerTablet = "100",
            tabletsPerUnit = "200",
            imagePath = null,
            doseUnit = "μg",
        )

        assertEquals("Ventolin (100μg)", details.label)
    }

    @Test
    fun `script medication label uses stored dose unit`() {
        val details = ScriptDetails(
            scriptId = 1,
            dispensableUnitId = 1,
            medicationId = 1,
            brandName = "Insulin",
            activeIngredient = "Human insulin",
            dosePerTablet = "100",
            tabletsPerUnit = "1",
            dispensed = 0,
            repeats = 5,
            dateOfIssue = 0L,
            doseUnit = "IU",
        )

        assertEquals("Insulin (100IU)", details.medicationLabel)
    }
}
