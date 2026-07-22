package com.example.aitoui.inventory

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Pins the default locale/time zone so the [runOutDateLabel] formatting is deterministic. */
class RunOutDateLabelTest {

    private val originalLocale = Locale.getDefault()
    private val originalZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalZone)
    }

    private fun millis(year: Int, month0: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month0, day, 12, 0, 0) }.timeInMillis

    @Test
    fun `same calendar year omits the year`() {
        // 2026-07-22 + 14 days = 2026-08-05
        assertEquals("5 Aug", runOutDateLabel(14, millis(2026, Calendar.JULY, 22)))
    }

    @Test
    fun `zero days remaining is today, same year`() {
        assertEquals("1 Jan", runOutDateLabel(0, millis(2026, Calendar.JANUARY, 1)))
    }

    @Test
    fun `run-out in a later calendar year includes the year`() {
        // 2026-12-20 + 30 days = 2027-01-19
        assertEquals("19 Jan 2027", runOutDateLabel(30, millis(2026, Calendar.DECEMBER, 20)))
    }

    @Test
    fun `late-in-year run-out that stays in the same year still omits the year`() {
        // 2026-11-15 + 10 days = 2026-11-25
        assertEquals("25 Nov", runOutDateLabel(10, millis(2026, Calendar.NOVEMBER, 15)))
    }
}
