package com.example.aitoui.inventory

import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.ScriptDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InventorySupplyTest {

    private fun unit(formatId: Long, medicationId: Long, tabletsPerUnit: String) =
        DispensableUnitDetails(
            formatId = formatId,
            medicationId = medicationId,
            brandName = "Brand$formatId",
            activeIngredient = "Ingredient$formatId",
            dosePerTablet = "500",
            tabletsPerUnit = tabletsPerUnit,
            imagePath = null,
        )

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

    // --- in-hand supply (taken from the in_hand table) ---

    @Test
    fun `in-hand tablets convert to whole days`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = mapOf(1L to 80.0),
        )
        assertEquals(80, r[1]?.inHandTablets)
        assertEquals(16, r[1]?.inHandDays)               // 80 / 5
        assertEquals(16, r[1]?.totalDays)
    }

    @Test
    fun `in-hand days floor down`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 3.0),
            inHandByMedication = mapOf(1L to 70.0),
        )
        assertEquals(23, r[1]?.inHandDays)               // 70 / 3 = 23.33 -> 23
        assertEquals(23, r[1]?.totalDays)
    }

    @Test
    fun `fractional in-hand rounds the tablet count but floors the days`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = mapOf(1L to 12.5),
        )
        assertEquals(13, r[1]?.inHandTablets)            // 12.5 rounds to 13 for display
        assertEquals(2, r[1]?.inHandDays)                // floor(12.5 / 5) = 2
    }

    @Test
    fun `in-hand is looked up by medication, shared across that medication's units`() {
        val r = computeSupply(
            units = listOf(unit(1, 7, "10"), unit(2, 7, "20")),
            scripts = emptyList(),
            dailyByMedication = mapOf(7L to 5.0),
            inHandByMedication = mapOf(7L to 50.0),
        )
        assertEquals(50, r[1]?.inHandTablets)            // both units of medication 7 see the same total
        assertEquals(50, r[2]?.inHandTablets)
        assertEquals(10, r[1]?.inHandDays)               // 50 / 5
    }

    @Test
    fun `no in-hand entry yields zero in-hand tablets`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = emptyMap(),
        )
        assertEquals(0, r[1]?.inHandTablets)
        assertEquals(0, r[1]?.totalDays)
    }

    @Test
    fun `no schedule entry yields null`() {
        val r = computeSupply(
            units = listOf(unit(1, 2, "10")),
            scripts = emptyList(),
            dailyByMedication = emptyMap(),
            inHandByMedication = mapOf(2L to 100.0),
        )
        assertNull(r[1])
    }

    @Test
    fun `non-positive rate yields null`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 0.0),
            inHandByMedication = mapOf(1L to 100.0),
        )
        assertNull(r[1])
    }

    // --- in-hand decay since the figures were gathered ---

    @Test
    fun `in-hand decays at the daily rate over the days since it was gathered`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = mapOf(1L to 80.0),       // gathered 80 tablets
            daysSinceGathered = 4.0,                       // 4 days * 5/day = 20 consumed
        )
        assertEquals(60, r[1]?.inHandTablets)             // 80 - 20
        assertEquals(12, r[1]?.inHandDays)                // 60 / 5
    }

    @Test
    fun `in-hand decay floors at zero and does not touch undispensed fills`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            scripts = listOf(script(unitId = 1, dispensed = 0, repeats = 5)),  // 6 fills * 10 = 60
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = mapOf(1L to 40.0),       // gathered 40 tablets
            daysSinceGathered = 20.0,                      // would consume 100 -> clamps in-hand to 0
        )
        assertEquals(0, r[1]?.inHandTablets)              // never negative
        assertEquals(0, r[1]?.inHandDays)
        assertEquals(60, r[1]?.undispensedTablets)        // fills untouched by the decay
        assertEquals(12, r[1]?.totalDays)                 // 0 in-hand days + 12 undispensed days
    }

    // --- undispensed (future) supply from script repeats ---

    @Test
    fun `remaining repeats give the undispensed breakdown`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            scripts = listOf(script(unitId = 1, dispensed = 0, repeats = 5)),
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = emptyMap(),
        )
        val s = r[1]!!
        assertEquals(6, s.undispensedFills)              // repeats 5 + 1 - dispensed 0
        assertEquals(10, s.tabletsPerUnit)
        assertEquals(60, s.undispensedTablets)           // 6 * 10
        assertEquals(12, s.undispensedDays)              // 60 / 5
        assertEquals(12, s.totalDays)
    }

    @Test
    fun `future repeats combine with in-hand supply`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            scripts = listOf(script(unitId = 1, dispensed = 2, repeats = 5)),
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = mapOf(1L to 40.0),      // 40 in hand
        )
        val s = r[1]!!
        assertEquals(8, s.inHandDays)                    // 40 / 5
        assertEquals(8, s.undispensedDays)               // (5 + 1 - 2) fills * 10 = 40 / 5
        assertEquals(16, s.totalDays)
    }

    @Test
    fun `a fully finished script adds no future supply`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            // dispensed exceeds repeats (repeats + 1 fills all used) -> nothing left.
            scripts = listOf(script(unitId = 1, dispensed = 6, repeats = 5)),
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = emptyMap(),
        )
        assertEquals(0, r[1]?.undispensedFills)
        assertEquals(0, r[1]?.totalDays)
    }

    @Test
    fun `dispensed equal to repeats still leaves one fill`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            scripts = listOf(script(unitId = 1, dispensed = 5, repeats = 5)),
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = emptyMap(),
        )
        assertEquals(1, r[1]?.undispensedFills)           // repeats + 1 - dispensed = 1
        assertEquals(2, r[1]?.totalDays)                  // 1 * 10 / 5
    }

    @Test
    fun `script for an unknown unit is skipped`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "10")),
            scripts = listOf(script(unitId = 999, dispensed = 0, repeats = 5)),
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = emptyMap(),
        )
        assertEquals(0, r[1]?.totalDays)
    }

    @Test
    fun `blank tabletsPerUnit contributes zero undispensed tablets`() {
        val r = computeSupply(
            units = listOf(unit(1, 1, "")),
            scripts = listOf(script(unitId = 1, dispensed = 0, repeats = 5)),
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = mapOf(1L to 25.0),
        )
        assertEquals(0, r[1]?.undispensedTablets)         // blank pack size -> 0 tablets per fill
        assertEquals(5, r[1]?.inHandDays)                 // in-hand is unaffected: 25 / 5
    }

    // --- humanizeDuration ---

    @Test
    fun `humanizeDuration uses days, weeks, months and years with one decimal`() {
        assertEquals("0 days", humanizeDuration(0))
        assertEquals("1 day", humanizeDuration(1))
        assertEquals("6 days", humanizeDuration(6))
        assertEquals("1 week", humanizeDuration(7))
        assertEquals("1.9 weeks", humanizeDuration(13))    // 13 / 7 = 1.857 -> 1.9
        assertEquals("2 weeks", humanizeDuration(14))
        assertEquals("1 month", humanizeDuration(30))
        assertEquals("1.5 months", humanizeDuration(45))   // 45 / 30 = 1.5
        assertEquals("3 months", humanizeDuration(90))
        assertEquals("1 year", humanizeDuration(365))
        assertEquals("1.1 years", humanizeDuration(400))   // 400 / 365 = 1.096 -> 1.1
        assertEquals("2.2 years", humanizeDuration(800))   // 800 / 365 = 2.19 -> 2.2
    }
}
