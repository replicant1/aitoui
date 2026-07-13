package com.example.aitoui.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InHandDateTest {

    private val day = 86_400_000L
    private val gathered = 1_700_000_000_000L   // an arbitrary fixed instant

    @Test
    fun `null gathered date yields zero elapsed days`() {
        assertEquals(0.0, inHandDaysElapsed(null, gathered + 5 * day), 1e-9)
    }

    @Test
    fun `same day yields zero elapsed days`() {
        assertEquals(0.0, inHandDaysElapsed(gathered, gathered), 1e-9)
    }

    @Test
    fun `part of a day floors to zero elapsed days`() {
        assertEquals(0.0, inHandDaysElapsed(gathered, gathered + day - 1), 1e-9)
    }

    @Test
    fun `whole days elapsed are counted, extra hours floored off`() {
        assertEquals(3.0, inHandDaysElapsed(gathered, gathered + 3 * day + day / 2), 1e-9)
    }

    @Test
    fun `a clock before the gathered date yields zero, never negative`() {
        assertEquals(0.0, inHandDaysElapsed(gathered, gathered - 5 * day), 1e-9)
    }
}
