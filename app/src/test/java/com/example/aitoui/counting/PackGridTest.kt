package com.example.aitoui.counting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PackGridTest {

    // A vertical 2x5 pack: long axis = +y, short axis = +x, centred at (100, 200),
    // spanning y in [50, 350] (height 300) and x in [60, 140] (width 80).
    private val region = PackRegion(
        cx = 100f, cy = 200f,
        longX = 0f, longY = 1f, shortX = 1f, shortY = 0f,
        longMin = -150f, longMax = 150f, shortMin = -40f, shortMax = 40f,
    )
    private val alongLong = 5
    private val alongShort = 2

    @Test
    fun `cell centres stay inside the pack bounds`() {
        for (a in 0 until alongLong) for (c in 0 until alongShort) {
            val p = cellCenter(region, alongLong, alongShort, a, c)
            assertTrue(p.y in 50f..350f && p.x in 60f..140f)
        }
    }

    @Test
    fun `cells advance down the long axis and across the short axis`() {
        val topLeft = cellCenter(region, alongLong, alongShort, 0, 0)
        val bottomLeft = cellCenter(region, alongLong, alongShort, 4, 0)
        val topRight = cellCenter(region, alongLong, alongShort, 0, 1)
        assertTrue("rows increase in y", bottomLeft.y > topLeft.y)
        assertTrue("columns increase in x", topRight.x > topLeft.x)
        assertTrue("same row shares y", abs(topLeft.y - topRight.y) < 0.01f)
    }

    @Test
    fun `tapping a cell centre returns that cell`() {
        for (a in 0 until alongLong) for (c in 0 until alongShort) {
            val p = cellCenter(region, alongLong, alongShort, a, c)
            assertEquals(CellRef(a, c), tapToCell(region, alongLong, alongShort, p.x, p.y))
        }
    }

    @Test
    fun `a tap just off a cell centre still snaps to that cell`() {
        val p = cellCenter(region, alongLong, alongShort, 2, 1)
        assertEquals(CellRef(2, 1), tapToCell(region, alongLong, alongShort, p.x + 8f, p.y - 9f))
    }

    @Test
    fun `a tap on the pack maps somewhere, a tap far away is rejected`() {
        assertNotNull(tapToCell(region, alongLong, alongShort, 100f, 200f)) // pack centre
        assertNull(tapToCell(region, alongLong, alongShort, 900f, 900f))    // far outside
        assertNull(tapToCell(region, alongLong, alongShort, 100f, 600f))    // past the long end
    }
}
