package com.example.aitoui.alerts

import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.ScriptDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationSuppliesTest {

    private fun unit(medId: Long, formatId: Long, tabletsPerUnit: String, brand: String = "Brand$medId") =
        DispensableUnitDetails(
            formatId = formatId, medicationId = medId, brandName = brand, activeIngredient = "Active",
            dosePerTablet = "10", tabletsPerUnit = tabletsPerUnit, imagePath = null,
        )

    private fun script(medId: Long, formatId: Long, tabletsPerUnit: String, repeats: Int, dispensed: Int) =
        ScriptDetails(
            scriptId = 0, dispensableUnitId = formatId, medicationId = medId, brandName = "Brand$medId",
            activeIngredient = "Active", dosePerTablet = "10", tabletsPerUnit = tabletsPerUnit,
            dispensed = dispensed, repeats = repeats, dateOfIssue = 0L,
        )

    @Test
    fun `aggregates in-hand days, undispensed fills and total days per medication`() {
        val supplies = medicationSupplies(
            units = listOf(unit(medId = 1, formatId = 10, tabletsPerUnit = "30", brand = "Cartia")),
            scripts = listOf(script(medId = 1, formatId = 10, tabletsPerUnit = "30", repeats = 2, dispensed = 0)),
            dailyByMedication = mapOf(1L to 1.0),
            inHandByMedication = mapOf(1L to 10.0),
            daysSinceGathered = 0.0,
        )
        val s = supplies.single()
        assertEquals("Cartia", s.brandName)
        assertEquals(10, s.inHandDays)        // 10 tablets / 1 per day
        assertEquals(3, s.undispensedFills)   // repeats + 1 - dispensed
        assertEquals(100, s.totalDays)        // 10 in hand + (3 * 30) undispensed
    }

    @Test
    fun `medications with no daily rate are excluded`() {
        val supplies = medicationSupplies(
            units = listOf(unit(medId = 1, formatId = 10, tabletsPerUnit = "30")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 0.0),
            inHandByMedication = mapOf(1L to 30.0),
            daysSinceGathered = 0.0,
        )
        assertTrue(supplies.isEmpty())
    }

    @Test
    fun `a scheduled medication with no dispensable unit is skipped`() {
        val supplies = medicationSupplies(
            units = emptyList(),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 1.0),
            inHandByMedication = mapOf(1L to 30.0),
            daysSinceGathered = 0.0,
        )
        assertTrue(supplies.isEmpty())
    }

    @Test
    fun `in-hand supply decays by the days elapsed since it was gathered`() {
        val supplies = medicationSupplies(
            units = listOf(unit(medId = 1, formatId = 10, tabletsPerUnit = "30")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 1.0),
            inHandByMedication = mapOf(1L to 10.0),
            daysSinceGathered = 3.0,   // three days already consumed
        )
        assertEquals(7, supplies.single().inHandDays) // 10 - 3*1 = 7
    }
}
