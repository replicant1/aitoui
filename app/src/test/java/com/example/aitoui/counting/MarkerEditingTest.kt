package com.example.aitoui.counting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerEditingTest {

    private val w = 1000
    private val h = 800
    // Hit radius = min(1000, 800) * 0.04 = 32 px.

    @Test
    fun `tapping empty space adds a marker there`() {
        val result = editMarkers(emptyList(), w, h, 100f, 200f)
        assertEquals(listOf(CountPoint(100f, 200f)), result)
    }

    @Test
    fun `the eraser removes every marker within its radius and keeps the rest`() {
        val markers = listOf(
            CountPoint(100f, 100f),  // inside
            CountPoint(115f, 108f),  // inside (~17px away)
            CountPoint(140f, 100f),  // just outside (40px away, radius 25)
            CountPoint(500f, 500f),  // far away
        )
        val result = eraseMarkersNear(markers, x = 100f, y = 100f, radius = 25f)
        assertEquals(listOf(CountPoint(140f, 100f), CountPoint(500f, 500f)), result)
    }

    @Test
    fun `the eraser returns the same list when nothing is within reach`() {
        val markers = listOf(CountPoint(500f, 500f))
        assertTrue(eraseMarkersNear(markers, 100f, 100f, 25f) === markers)
    }

    @Test
    fun `tapping on an existing marker removes it`() {
        val markers = listOf(CountPoint(100f, 100f), CountPoint(500f, 500f))
        val result = editMarkers(markers, w, h, 105f, 98f) // within 32px of the first
        assertEquals(listOf(CountPoint(500f, 500f)), result)
    }

    @Test
    fun `tapping just outside the hit radius adds rather than removes`() {
        val markers = listOf(CountPoint(100f, 100f))
        val result = editMarkers(markers, w, h, 140f, 100f) // 40px away, outside the 32px radius
        assertEquals(2, result.size)
        assertTrue(result.contains(CountPoint(140f, 100f)))
    }

    @Test
    fun `removal targets the nearest marker`() {
        val markers = listOf(CountPoint(100f, 100f), CountPoint(120f, 100f))
        val result = editMarkers(markers, w, h, 118f, 100f) // nearest is the second
        assertEquals(listOf(CountPoint(100f, 100f)), result)
    }

    @Test
    fun `taps are ignored until an image size is known`() {
        val markers = listOf(CountPoint(1f, 1f))
        assertEquals(markers, editMarkers(markers, 0, 0, 10f, 10f))
    }
}
