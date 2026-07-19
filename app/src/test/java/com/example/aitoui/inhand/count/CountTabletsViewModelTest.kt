package com.example.aitoui.inhand.count

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountTabletsViewModelTest {

    @Test
    fun `the default sensitivity maps to the counter's default floor`() {
        // 0.4 on the slider must reproduce the counter's built-in 0.30 min-height fraction, so the
        // initial count is unchanged from before the slider existed.
        val floor = CountTabletsViewModel.minHeightFractionFor(CountTabletsState.DEFAULT_SENSITIVITY)
        assertEquals(0.30, floor, 1e-6)
    }

    @Test
    fun `the slider spans the intended floor range`() {
        assertEquals(0.10, CountTabletsViewModel.minHeightFractionFor(0f), 1e-6)
        assertEquals(0.60, CountTabletsViewModel.minHeightFractionFor(1f), 1e-6)
    }

    @Test
    fun `a higher slider value maps to a higher floor and is clamped`() {
        assertTrue(
            CountTabletsViewModel.minHeightFractionFor(0.7f) > CountTabletsViewModel.minHeightFractionFor(0.3f),
        )
        assertEquals(0.60, CountTabletsViewModel.minHeightFractionFor(5f), 1e-6) // clamped to 1.0
        assertEquals(0.10, CountTabletsViewModel.minHeightFractionFor(-5f), 1e-6) // clamped to 0.0
    }
}
