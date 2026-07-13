package com.example.aitoui.runout

import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.ScriptDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunOutGraphTest {

    private fun unit(formatId: Long, medicationId: Long, tabletsPerUnit: String, brand: String = "Brand$formatId") =
        DispensableUnitDetails(
            formatId = formatId,
            medicationId = medicationId,
            brandName = brand,
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
            tabletsPerUnit = "0",
            dispensed = dispensed,
            repeats = repeats,
            dateOfIssue = 0L,
        )

    @Test
    fun `series total is in-hand plus remaining fills, and run-out is total over rate`() {
        val data = computeRunOutGraph(
            units = listOf(unit(1, 1, "10")),
            scripts = listOf(script(unitId = 1, dispensed = 2, repeats = 5)),  // 5+1-2 = 4 fills
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = mapOf(1L to 20.0),
        )
        val s = data.series.single()
        assertEquals(60, s.totalTablets)             // 20 in hand + 4 fills * 10
        assertEquals(12.0, s.runOutDay, 1e-9)        // 60 / 5
    }

    @Test
    fun `in-hand decays by the days since it was gathered, leaving fills intact`() {
        val data = computeRunOutGraph(
            units = listOf(unit(1, 1, "10")),
            scripts = listOf(script(unitId = 1, dispensed = 2, repeats = 5)),  // 4 fills * 10 = 40
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = mapOf(1L to 20.0),   // 20 in hand, gathered 2 days ago
            daysSinceGathered = 2.0,                   // 2 * 5 = 10 consumed -> 10 in hand now
        )
        val s = data.series.single()
        assertEquals(50, s.totalTablets)              // 10 in hand + 40 fills
    }

    @Test
    fun `in-hand decay floors at zero`() {
        val data = computeRunOutGraph(
            units = listOf(unit(1, 1, "10")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = mapOf(1L to 20.0),
            daysSinceGathered = 10.0,                  // would consume 50 -> clamps to 0
        )
        val s = data.series.single()
        assertEquals(0, s.totalTablets)
    }

    @Test
    fun `units with no schedule are omitted`() {
        val data = computeRunOutGraph(
            units = listOf(unit(1, 1, "10"), unit(2, 2, "10")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),    // medication 2 has no rate
            inHandByMedication = mapOf(1L to 10.0, 2L to 50.0),
        )
        assertEquals(listOf(1L), data.series.map { it.unitId })
    }

    @Test
    fun `tabletsAt and daysRemainingAt decline then clamp at zero`() {
        val data = computeRunOutGraph(
            units = listOf(unit(1, 1, "10")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = mapOf(1L to 50.0),  // 50 tablets, 10 days
        )
        val s = data.series.single()
        assertEquals(50.0, s.tabletsAt(0.0), 1e-9)
        assertEquals(25.0, s.tabletsAt(5.0), 1e-9)
        assertEquals(0.0, s.tabletsAt(10.0), 1e-9)
        assertEquals(0.0, s.tabletsAt(20.0), 1e-9)   // clamped, not negative
        assertEquals(10, s.daysRemainingAt(0.0))
        assertEquals(6, s.daysRemainingAt(4.0))
        assertEquals(0, s.daysRemainingAt(10.0))
        assertEquals(0, s.daysRemainingAt(15.0))     // clamped
    }

    @Test
    fun `domains round out to whole months and a round tablet step`() {
        val data = computeRunOutGraph(
            units = listOf(unit(1, 1, "10")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 1.0),
            inHandByMedication = mapOf(1L to 40.0),  // 40 tablets, 40 days -> 2 months
        )
        assertEquals(60.0, data.domainDays, 1e-9)    // ceil(40/30)=2 months = 60 days
        assertEquals(10, data.tabletTickStep)        // <= 100 tablets -> step 10
        assertEquals(40, data.domainTablets)         // ceil(40/10)*10
    }

    @Test
    fun `large supplies use a tablet step of fifty`() {
        val data = computeRunOutGraph(
            units = listOf(unit(1, 1, "50")),
            scripts = listOf(script(unitId = 1, dispensed = 0, repeats = 5)),  // 6 fills * 50 = 300
            dailyByMedication = mapOf(1L to 5.0),
            inHandByMedication = emptyMap(),
        )
        assertEquals(50, data.tabletTickStep)
        assertEquals(300, data.domainTablets)
    }

    @Test
    fun `series are ordered by label with ascending colour indices`() {
        val data = computeRunOutGraph(
            units = listOf(
                unit(1, 1, "10", brand = "Zinc"),
                unit(2, 2, "10", brand = "Aspirin"),
            ),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 5.0, 2L to 5.0),
            inHandByMedication = mapOf(1L to 10.0, 2L to 10.0),
        )
        assertEquals(listOf("Aspirin (500mg)", "Zinc (500mg)"), data.series.map { it.label })
        assertEquals(listOf(0, 1), data.series.map { it.colorIndex })
    }

    @Test
    fun `month ticks fall on calendar boundaries within the domain`() {
        // 100 tablets at 1/day -> 100 days -> ceil to 4 months (120-day domain).
        val data = computeRunOutGraph(
            units = listOf(unit(1, 1, "10")),
            scripts = emptyList(),
            dailyByMedication = mapOf(1L to 1.0),
            inHandByMedication = mapOf(1L to 100.0),
            nowMillis = 1_700_000_000_000L,          // an arbitrary fixed instant
        )
        val ticks = data.monthTicks
        assertTrue("expected several month ticks", ticks.size >= 3)
        // All ticks lie within the domain, strictly increasing, and roughly a month apart.
        var previous = 0.0
        for (t in ticks) {
            assertTrue(t.dayOffset > 0.0 && t.dayOffset <= data.domainDays)
            assertTrue(t.dayOffset > previous)
            assertEquals(3, t.label.length)          // abbreviated month name, e.g. "Aug"
            previous = t.dayOffset
        }
        assertTrue("first boundary within a month", ticks.first().dayOffset <= 31.0)
        for (i in 1 until ticks.size) {
            val gap = ticks[i].dayOffset - ticks[i - 1].dayOffset
            // 28–31 days per month, with a little slack for float rounding and DST-shifted days.
            assertTrue("consecutive months ~28-31 days apart, was $gap", gap in 27.5..31.5)
        }
    }

    @Test
    fun `empty inputs produce an empty graph with sane defaults`() {
        val data = computeRunOutGraph(emptyList(), emptyList(), emptyMap(), emptyMap())
        assertTrue(data.isEmpty)
        assertEquals(MONTH_DAYS, data.domainDays, 1e-9)   // at least one month
        assertTrue(data.domainTablets >= data.tabletTickStep)
    }
}
