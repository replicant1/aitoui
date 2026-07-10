package com.example.aitoui.inventory

import com.example.aitoui.data.Dispensation
import com.example.aitoui.data.DispensableUnitDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InventorySupplyTest {

    private val day = 86_400_000L
    private val now = 100 * day

    private fun unit(formatId: Long, medicationId: Long, tabletsPerUnit: String) =
        DispensableUnitDetails(
            formatId = formatId,
            medicationId = medicationId,
            brandName = "Brand$formatId",
            activeIngredient = "Ingredient$formatId",
            dosePerTablet = "500",
            tabletsPerUnit = tabletsPerUnit,
        )

    private fun disp(unitId: Long, number: Int, dayNo: Long) =
        Dispensation(scriptId = 0, dispensableUnitId = unitId, number = number, dispensedAtMillis = dayNo * day)

    @Test
    fun `single dispensation depleted exactly to zero`() {
        val result = computeDaysRemaining(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 10, 80)),   // 100 tablets, 20 days ago
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(0, result[1])                       // 100 - 20*5 = 0
    }

    @Test
    fun `single dispensation with surplus floors down`() {
        val result = computeDaysRemaining(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 10, 96)),     // 100 tablets, 4 days ago
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(16, result[1])                      // (100 - 4*5) / 5 = 16
    }

    @Test
    fun `fractional days remaining are floored`() {
        val result = computeDaysRemaining(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 10, 90)),     // 100 tablets, 10 days ago
            dailyByMedication = mapOf(1L to 3.0),
            nowMillis = now,
        )
        assertEquals(23, result[1])                      // (100 - 30) / 3 = 23.33 -> 23
    }

    @Test
    fun `multiple dispensations use a running balance`() {
        val result = computeDaysRemaining(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 10, 80), disp(1, 10, 90)),
            dailyByMedication = mapOf(1L to 10.0),
            nowMillis = now,
        )
        assertEquals(0, result[1])                       // +100@80, deplete 100 by 90, +100, deplete 100 to now
    }

    @Test
    fun `stock cannot carry negative across a stockout gap`() {
        val result = computeDaysRemaining(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 10, 50), disp(1, 10, 95)),
            dailyByMedication = mapOf(1L to 10.0),
            nowMillis = now,
        )
        assertEquals(5, result[1])                       // day50 pack runs out; day95 +100 -> 50 by day100
    }

    @Test
    fun `no schedule entry yields null`() {
        val result = computeDaysRemaining(
            units = listOf(unit(1, 2, "10")),
            dispensations = listOf(disp(1, 10, 90)),
            dailyByMedication = emptyMap(),
            nowMillis = now,
        )
        assertNull(result[2])
    }

    @Test
    fun `non-positive rate yields null`() {
        val result = computeDaysRemaining(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 10, 90)),
            dailyByMedication = mapOf(1L to 0.0),
            nowMillis = now,
        )
        assertNull(result[1])
    }

    @Test
    fun `units present but no dispensations yields zero`() {
        val result = computeDaysRemaining(
            units = listOf(unit(1, 1, "10")),
            dispensations = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(0, result[1])
    }

    @Test
    fun `dispensation for an unknown unit is skipped`() {
        val result = computeDaysRemaining(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(999, 10, 96), disp(1, 10, 96)),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(16, result[1])                      // only the valid dispensation counts
        assertNull(result[999L])                          // unknown unit never enters the result
    }

    @Test
    fun `blank tabletsPerUnit contributes zero tablets`() {
        val result = computeDaysRemaining(
            units = listOf(unit(1, 1, "")),
            dispensations = listOf(disp(1, 10, 90)),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(0, result[1])
    }

    @Test
    fun `future dated dispensation is ignored`() {
        val result = computeDaysRemaining(
            units = listOf(unit(1, 1, "1")),
            dispensations = listOf(disp(1, 200, 110), disp(1, 10, 98)),  // first is in the future
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(0, result[1])                       // only day-98 (10 tablets) counts: 10 - 2*5 = 0
    }
}
