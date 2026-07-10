package com.example.aitoui.inventory

import com.example.aitoui.data.Dispensation
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.ScriptDetails
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

    private fun script(unitId: Long, dispensed: Int, repeats: Int) =
        ScriptDetails(
            scriptId = 0,
            dispensableUnitId = unitId,
            medicationId = 0,
            brandName = "Brand",
            activeIngredient = "Ingredient",
            dosePerTablet = "500",
            tabletsPerUnit = "0",   // unused by computeSupply (it uses the unit's tabletsPerUnit)
            dispensed = dispensed,
            repeats = repeats,
            dateOfIssue = 0L,
        )

    // --- on-hand (dispensed) supply; no scripts ---

    @Test
    fun `single dispensation depleted exactly to zero`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 10, 80)),   // 100 tablets, 20 days ago
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(0, r[1]?.dispensedTablets)          // 100 - 20*5 = 0
        assertEquals(0, r[1]?.totalDays)
    }

    @Test
    fun `single dispensation with surplus floors down`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 10, 96)),     // 100 tablets, 4 days ago
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(80, r[1]?.dispensedTablets)
        assertEquals(16, r[1]?.dispensedDays)            // 80 / 5
        assertEquals(16, r[1]?.totalDays)
    }

    @Test
    fun `fractional days are floored`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 10, 90)),     // 100 tablets, 10 days ago
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 3.0),
            nowMillis = now,
        )
        assertEquals(23, r[1]?.totalDays)                // (100 - 30) / 3 = 23.33 -> 23
    }

    @Test
    fun `multiple dispensations use a running balance`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 10, 80), disp(1, 10, 90)),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 10.0),
            nowMillis = now,
        )
        assertEquals(0, r[1]?.totalDays)
    }

    @Test
    fun `stock cannot carry negative across a stockout gap`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 10, 50), disp(1, 10, 95)),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 10.0),
            nowMillis = now,
        )
        assertEquals(5, r[1]?.totalDays)                 // day50 pack lapses; day95 +100 -> 50 by day100
    }

    @Test
    fun `no schedule entry yields null`() {
        val r = computeSupply(
            units = listOf(unit(1, 2, "10")),
            dispensations = listOf(disp(1, 10, 90)),
            scripts = emptyList(),
            dailyByMedication = emptyMap(),
            nowMillis = now,
        )
        assertNull(r[1])
    }

    @Test
    fun `non-positive rate yields null`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 10, 90)),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 0.0),
            nowMillis = now,
        )
        assertNull(r[1])
    }

    @Test
    fun `unit with no dispensations or scripts yields zero`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = emptyList(),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(0, r[1]?.totalDays)
    }

    @Test
    fun `dispensation for an unknown unit is skipped`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(999, 10, 96), disp(1, 10, 96)),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(16, r[1]?.totalDays)                // only the valid dispensation counts
        assertNull(r[999L])
    }

    @Test
    fun `blank tabletsPerUnit contributes zero tablets`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "")),
            dispensations = listOf(disp(1, 10, 90)),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(0, r[1]?.totalDays)
    }

    @Test
    fun `future dated dispensation is ignored`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "1")),
            dispensations = listOf(disp(1, 200, 110), disp(1, 10, 98)),  // first is in the future
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(0, r[1]?.totalDays)                 // only day-98 (10 tablets): 10 - 2*5 = 0
    }

    // --- undispensed (future) supply from script repeats ---

    @Test
    fun `remaining repeats give the undispensed breakdown`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = emptyList(),
            scripts = listOf(script(unitId = 1, dispensed = 0, repeats = 5)),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        val s = r[1]!!
        assertEquals(6, s.undispensedFills)              // repeats 5 + 1 - dispensed 0
        assertEquals(10, s.tabletsPerUnit)
        assertEquals(60, s.undispensedTablets)           // 6 * 10
        assertEquals(12, s.undispensedDays)              // 60 / 5
        assertEquals(12, s.totalDays)
    }

    @Test
    fun `future repeats combine with on-hand supply`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = listOf(disp(1, 2, 96)),      // 20 tablets, 4 days ago -> depletes to 0
            scripts = listOf(script(unitId = 1, dispensed = 2, repeats = 5)),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        val s = r[1]!!
        assertEquals(0, s.dispensedDays)                 // 20 - 4*5 = 0
        assertEquals(8, s.undispensedDays)               // (5 + 1 - 2) fills * 10 = 40 / 5
        assertEquals(8, s.totalDays)
    }

    @Test
    fun `a fully finished script adds no future supply`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = emptyList(),
            // dispensed exceeds repeats (repeats + 1 fills all used) -> nothing left.
            scripts = listOf(script(unitId = 1, dispensed = 6, repeats = 5)),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(0, r[1]?.undispensedFills)
        assertEquals(0, r[1]?.totalDays)
    }

    @Test
    fun `dispensed equal to repeats still leaves one fill`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = emptyList(),
            scripts = listOf(script(unitId = 1, dispensed = 5, repeats = 5)),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(1, r[1]?.undispensedFills)           // repeats + 1 - dispensed = 1
        assertEquals(2, r[1]?.totalDays)                  // 1 * 10 / 5
    }

    @Test
    fun `script for an unknown unit is skipped`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            dispensations = emptyList(),
            scripts = listOf(script(unitId = 999, dispensed = 0, repeats = 5)),
            dailyByMedication = mapOf(1L to 5.0),
            nowMillis = now,
        )
        assertEquals(0, r[1]?.totalDays)
    }

    // --- humanizeDuration ---

    @Test
    fun `humanizeDuration uses only days and weeks`() {
        assertEquals("0 days", humanizeDuration(0))
        assertEquals("1 day", humanizeDuration(1))
        assertEquals("6 days", humanizeDuration(6))
        assertEquals("1 week", humanizeDuration(7))
        assertEquals("1 week", humanizeDuration(13))
        assertEquals("2 weeks", humanizeDuration(14))
        assertEquals("4 weeks", humanizeDuration(30))
        assertEquals("12 weeks", humanizeDuration(90))
        assertEquals("52 weeks", humanizeDuration(365))
        assertEquals("114 weeks", humanizeDuration(800))
    }
}
