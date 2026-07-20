package com.example.aitoui.counting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class FrameEditingTest {

    private fun assertClose(expected: Float, actual: Float, eps: Float = 1e-3f) {
        assertTrue("expected $expected but was $actual", abs(expected - actual) <= eps)
    }

    @Test
    fun `corners of an axis-aligned box are its four extremes`() {
        val box = FrameBox(cx = 100f, cy = 50f, halfW = 20f, halfH = 10f, angleRad = 0f)
        val c = box.corners()
        // top-left, top-right, bottom-right, bottom-left
        assertClose(80f, c[0].x); assertClose(40f, c[0].y)
        assertClose(120f, c[1].x); assertClose(40f, c[1].y)
        assertClose(120f, c[2].x); assertClose(60f, c[2].y)
        assertClose(80f, c[3].x); assertClose(60f, c[3].y)
    }

    @Test
    fun `contains distinguishes inside from outside`() {
        val box = FrameBox(100f, 50f, 20f, 10f, 0f)
        assertTrue(box.contains(100f, 50f))
        assertTrue(box.contains(119f, 59f))
        assertTrue(!box.contains(121f, 50f))
        assertTrue(!box.contains(100f, 61f))
    }

    @Test
    fun `hitTest finds the rotation handle, then a corner, then the body`() {
        val box = FrameBox(100f, 50f, 20f, 10f, 0f)
        // rotation handle sits above the top edge by the gap
        val rh = box.rotationHandle(15f)
        assertEquals(FrameHit.Rotate, box.hitTest(rh.x, rh.y, handleRadius = 6f, rotationGap = 15f))
        // top-right corner
        assertEquals(FrameHit.Corner(1), box.hitTest(120f, 40f, handleRadius = 6f, rotationGap = 15f))
        // centre is body
        assertEquals(FrameHit.Body, box.hitTest(100f, 50f, handleRadius = 6f, rotationGap = 15f))
        // far away is nothing
        assertEquals(FrameHit.None, box.hitTest(1000f, 1000f, handleRadius = 6f, rotationGap = 15f))
    }

    @Test
    fun `resizing a corner keeps the opposite corner fixed`() {
        val box = FrameBox(100f, 50f, 20f, 10f, 0f)
        val opposite = box.corners()[3] // bottom-left, opposite of top-right (index 1)
        val resized = box.resizedByCorner(cornerIndex = 1, px = 140f, py = 20f)
        val stillThere = resized.corners()[3]
        assertClose(opposite.x, stillThere.x)
        assertClose(opposite.y, stillThere.y)
        // the dragged corner now sits at the pointer
        val dragged = resized.corners()[1]
        assertClose(140f, dragged.x)
        assertClose(20f, dragged.y)
    }

    @Test
    fun `resizing cannot invert the box below the minimum`() {
        val box = FrameBox(100f, 50f, 20f, 10f, 0f)
        // drag the top-right corner onto the opposite corner
        val opp = box.corners()[3]
        val resized = box.resizedByCorner(cornerIndex = 1, px = opp.x, py = opp.y)
        assertTrue(resized.halfW >= FRAME_MIN_HALF)
        assertTrue(resized.halfH >= FRAME_MIN_HALF)
    }

    @Test
    fun `rotation handle sits above the box on screen`() {
        val box = FrameBox(100f, 50f, 20f, 10f, 0f)
        val rh = box.rotationHandle(15f)
        assertTrue("handle should be above centre", rh.y < box.cy)
        assertClose(box.cx, rh.x, eps = 0.5f) // centred over the top edge
    }

    @Test
    fun `rotation handle stays above a rotated box`() {
        // A portrait box turned 90 degrees: its top edge is still the screen-topmost one.
        val box = FrameBox(100f, 100f, 40f, 10f, angleRad = (PI / 2).toFloat())
        val rh = box.rotationHandle(15f)
        assertTrue("handle should be above centre after rotation", rh.y < box.cy)
    }

    @Test
    fun `movedBy clamps the centre to the image`() {
        val box = FrameBox(100f, 50f, 20f, 10f, 0f)
        val moved = box.movedBy(dx = -500f, dy = 5000f, imageWidth = 300, imageHeight = 400)
        assertClose(0f, moved.cx)
        assertClose(400f, moved.cy)
    }

    @Test
    fun `PackRegion round-trips through FrameBox`() {
        val region = PackRegion(
            cx = 100f, cy = 200f, longX = 0f, longY = 1f, shortX = 1f, shortY = 0f,
            longMin = -150f, longMax = 150f, shortMin = -40f, shortMax = 40f,
        )
        val back = region.toFrameBox().toPackRegion()
        assertClose(region.cx, back.cx)
        assertClose(region.cy, back.cy)
        assertClose(region.longMax - region.longMin, back.longMax - back.longMin)
        assertClose(region.shortMax - region.shortMin, back.shortMax - back.shortMin)
        // long axis stays the longer one
        assertTrue(abs(back.longMax - back.longMin) >= abs(back.shortMax - back.shortMin))
    }

    @Test
    fun `toFrameBox recovers a 90-degree pack angle`() {
        val region = PackRegion(
            cx = 0f, cy = 0f, longX = 0f, longY = 1f, shortX = 1f, shortY = 0f,
            longMin = -150f, longMax = 150f, shortMin = -40f, shortMax = 40f,
        )
        val box = region.toFrameBox()
        assertClose((PI / 2).toFloat(), box.angleRad, eps = 1e-3f)
        assertClose(150f, box.halfW) // long axis becomes width
        assertClose(40f, box.halfH)
    }

    // --- Row-major pack ordering ---

    /** A tall, portrait pack (long axis vertical) centred at ([cx], [cy]). */
    private fun pack(cx: Float, cy: Float) = FrameBox(cx, cy, halfW = 30f, halfH = 100f, angleRad = 0f)

    @Test
    fun `empty input yields empty order`() {
        assertEquals(emptyList<Int>(), rowMajorOrder(emptyList()))
    }

    @Test
    fun `a single row of packs is ordered left to right`() {
        // Three side-by-side packs at roughly the same height, given out of order.
        val boxes = listOf(pack(400f, 100f), pack(100f, 110f), pack(250f, 95f))
        assertEquals(listOf(1, 2, 0), rowMajorOrder(boxes)) // x = 100, 250, 400
    }

    @Test
    fun `stacked rows go top-to-bottom, left-to-right within each row`() {
        // Two rows of two. Row 1 near y=100, row 2 near y=500 (well past the ~100px half-height band).
        val topLeft = pack(100f, 100f)
        val topRight = pack(300f, 90f)
        val bottomLeft = pack(120f, 500f)
        val bottomRight = pack(310f, 510f)
        val boxes = listOf(bottomRight, topLeft, bottomLeft, topRight) // shuffled
        // Expected visual order: topLeft, topRight, bottomLeft, bottomRight → their indices 1, 3, 2, 0.
        assertEquals(listOf(1, 3, 2, 0), rowMajorOrder(boxes))
    }

    @Test
    fun `slight vertical jitter within a row does not split it`() {
        // Centres differ in y by less than half a pack-height (200px tall → ~100px band): still one row.
        val boxes = listOf(pack(100f, 100f), pack(300f, 150f), pack(500f, 80f))
        assertEquals(listOf(0, 1, 2), rowMajorOrder(boxes)) // stays left-to-right, not reordered by y
    }
}
