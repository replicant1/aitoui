package com.example.aitoui.counting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeakTabletCounterTest {

    private val counter = PeakTabletCounter()
    private val background = 0xFF202020.toInt()

    @Test
    fun `no tablets on a plain background yields no points`() {
        assertEquals(0, counter.count(solid(200, 200)).size)
    }

    @Test
    fun `a single elongated tablet yields exactly one point, not one per end`() {
        // The distance-transform ridge of an elongated (capsule) tablet must collapse to one marker.
        val image = solid(200, 200)
        fillCapsule(image, cx = 100, cy = 100, halfLen = 12, radius = 14)
        val points = counter.count(image)
        assertEquals(1, points.size)
        assertTrue(points[0].x in 88f..112f && points[0].y in 88f..112f)
    }

    @Test
    fun `three well-separated tablets yield three points`() {
        val image = solid(300, 300)
        fillCapsule(image, 70, 70, 12, 14)
        fillCapsule(image, 220, 90, 12, 14)
        fillCapsule(image, 150, 230, 12, 14)
        assertEquals(3, counter.count(image).size)
    }

    @Test
    fun `two touching tablets are separated into two points`() {
        // Blob detection would merge these into one; the distance transform keeps two peaks.
        val image = solid(260, 200)
        fillCapsule(image, cx = 90, cy = 100, halfLen = 12, radius = 14)
        fillCapsule(image, cx = 140, cy = 100, halfLen = 12, radius = 14) // caps overlap -> connected
        assertEquals(2, counter.count(image).size)
    }

    @Test
    fun `a lone speckle is ignored`() {
        val image = solid(200, 200)
        fillCapsule(image, cx = 100, cy = 100, halfLen = 12, radius = 14) // sets the size scale
        image.pixels[30 * image.width + 30] = 0xFFF0F0F0.toInt()          // 1px speck below the floor
        assertEquals(1, counter.count(image).size)
    }

    @Test
    fun `analyse then select at the default floor matches count`() {
        val image = solid(300, 300)
        fillCapsule(image, 70, 70, 12, 14)
        fillCapsule(image, 220, 90, 12, 14)
        fillCapsule(image, 150, 230, 12, 14)
        assertEquals(counter.count(image).size, counter.analyse(image).select(0.30).size)
    }

    @Test
    fun `a higher sensitivity floor drops a faint (smaller) tablet`() {
        // Two full-size tablets set the median height; a much smaller one has a shallow peak.
        val image = solid(300, 300)
        fillCapsule(image, 70, 70, 12, 14)
        fillCapsule(image, 220, 90, 12, 14)
        fillCapsule(image, 150, 230, 4, 6) // small -> peak ~6 vs median ~14
        val field = counter.analyse(image)
        assertEquals(3, field.select(0.10).size) // low floor keeps the faint one
        assertEquals(2, field.select(0.60).size) // high floor drops it
    }

    @Test
    fun `selecting is monotonic — a higher floor never yields more markers`() {
        val image = solid(300, 300)
        fillCapsule(image, 70, 70, 12, 14)
        fillCapsule(image, 220, 90, 12, 14)
        fillCapsule(image, 150, 230, 4, 6)
        val field = counter.analyse(image)
        assertTrue(field.select(0.10).size >= field.select(0.35).size)
        assertTrue(field.select(0.35).size >= field.select(0.60).size)
    }

    private fun solid(w: Int, h: Int) = CountImage(w, h, IntArray(w * h) { background })

    /** Paint a filled light capsule (stadium): all pixels within [radius] of the horizontal centre segment. */
    private fun fillCapsule(
        image: CountImage,
        cx: Int,
        cy: Int,
        halfLen: Int,
        radius: Int,
        color: Int = 0xFFF0F0F0.toInt(),
    ) {
        val r2 = radius * radius
        for (y in (cy - radius)..(cy + radius)) {
            if (y < 0 || y >= image.height) continue
            for (x in (cx - halfLen - radius)..(cx + halfLen + radius)) {
                if (x < 0 || x >= image.width) continue
                val nearestX = x.coerceIn(cx - halfLen, cx + halfLen)
                val dx = x - nearestX
                val dy = y - cy
                if (dx * dx + dy * dy <= r2) image.pixels[y * image.width + x] = color
            }
        }
    }
}
