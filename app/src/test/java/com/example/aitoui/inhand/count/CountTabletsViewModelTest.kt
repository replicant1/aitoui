package com.example.aitoui.inhand.count

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountTabletsViewModelTest {

    @Test
    fun `the default sensitivity is a low absolute floor, near the counter's original noise floor`() {
        // ~2px keeps the initial count essentially unchanged from before the slider existed.
        val floor = CountTabletsViewModel.absoluteFloorFor(CountTabletsState.DEFAULT_SENSITIVITY)
        assertEquals(2.0, floor, 1e-6)
    }

    @Test
    fun `the slider spans zero to the maximum floor`() {
        assertEquals(0.0, CountTabletsViewModel.absoluteFloorFor(0f), 1e-6)
        assertEquals(20.0, CountTabletsViewModel.absoluteFloorFor(1f), 1e-6)
    }

    @Test
    fun `a higher slider value maps to a taller floor and is clamped`() {
        assertTrue(
            CountTabletsViewModel.absoluteFloorFor(0.7f) > CountTabletsViewModel.absoluteFloorFor(0.3f),
        )
        assertEquals(20.0, CountTabletsViewModel.absoluteFloorFor(5f), 1e-6) // clamped to 1.0
        assertEquals(0.0, CountTabletsViewModel.absoluteFloorFor(-5f), 1e-6) // clamped to 0.0
    }
}
