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

    // --- Grid adjust (manual pan / spacing) ---

    @Test
    fun `no adjust leaves cell centres exactly where they were`() {
        for (a in 0 until alongLong) for (c in 0 until alongShort) {
            val base = cellCenter(region, alongLong, alongShort, a, c)
            val same = adjustedCellCenter(region, alongLong, alongShort, a, c, GridAdjust.None)
            assertEquals(base.x, same.x, 1e-4f)
            assertEquals(base.y, same.y, 1e-4f)
        }
    }

    @Test
    fun `panning translates every centre by the same offset`() {
        val adjust = GridAdjust(dx = 12f, dy = -7f)
        for (a in 0 until alongLong) for (c in 0 until alongShort) {
            val base = cellCenter(region, alongLong, alongShort, a, c)
            val moved = adjustedCellCenter(region, alongLong, alongShort, a, c, adjust)
            assertEquals(base.x + 12f, moved.x, 1e-4f)
            assertEquals(base.y - 7f, moved.y, 1e-4f)
        }
    }

    @Test
    fun `spacing scales gaps between centres but keeps the grid centred on the pack`() {
        val adjust = GridAdjust(spacing = 2f)
        // The centre-most pair of adjacent rows should end up twice as far apart.
        val a0 = cellCenter(region, alongLong, alongShort, 0, 0)
        val a1 = cellCenter(region, alongLong, alongShort, 1, 0)
        val s0 = adjustedCellCenter(region, alongLong, alongShort, 0, 0, adjust)
        val s1 = adjustedCellCenter(region, alongLong, alongShort, 1, 0, adjust)
        assertEquals(2f * (a1.y - a0.y), s1.y - s0.y, 1e-3f)
        // The four cells stay symmetric about the pack centroid, so their mean is still the centroid.
        var mx = 0f; var my = 0f; var count = 0
        for (a in 0 until alongLong) for (c in 0 until alongShort) {
            val p = adjustedCellCenter(region, alongLong, alongShort, a, c, adjust)
            mx += p.x; my += p.y; count++
        }
        assertEquals(region.cx, mx / count, 1e-2f)
        assertEquals(region.cy, my / count, 1e-2f)
    }

    @Test
    fun `a hit lands on the circle under it, and empty space between circles misses`() {
        val c = adjustedCellCenter(region, alongLong, alongShort, 2, 1, GridAdjust.None)
        // Small radius: dead-centre hits, but a point midway to a neighbour is off every circle.
        assertEquals(CellRef(2, 1), adjustedCellHit(region, alongLong, alongShort, c.x, c.y, GridAdjust.None, 8f))
        assertNull(adjustedCellHit(region, alongLong, alongShort, c.x, c.y + 25f, GridAdjust.None, 8f))
    }

    @Test
    fun `hit testing follows the grid once it has been panned`() {
        val adjust = GridAdjust(dx = 30f, dy = 40f)
        val moved = adjustedCellCenter(region, alongLong, alongShort, 0, 0, adjust)
        // The circle moved with the grid; testing at its new centre still identifies the cell.
        assertEquals(CellRef(0, 0), adjustedCellHit(region, alongLong, alongShort, moved.x, moved.y, adjust, 8f))
        // ...and the old (un-panned) location is now empty.
        val old = cellCenter(region, alongLong, alongShort, 0, 0)
        assertNull(adjustedCellHit(region, alongLong, alongShort, old.x, old.y, adjust, 8f))
    }
}
