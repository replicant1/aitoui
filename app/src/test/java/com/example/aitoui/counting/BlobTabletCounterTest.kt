package com.example.aitoui.counting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlobTabletCounterTest {

    private val counter = BlobTabletCounter()

    private val white = 0xFFFFFFFF.toInt()
    private val dark = 0xFF303030.toInt()

    private fun blank(w: Int, h: Int, color: Int = white) = CountImage(w, h, IntArray(w * h) { color })

    /** Fill the half-open rectangle [x0,x1) x [y0,y1) with [color]. */
    private fun CountImage.fillRect(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        for (y in y0 until y1) for (x in x0 until x1) pixels[y * width + x] = color
    }

    @Test
    fun `a plain background counts zero tablets`() {
        assertEquals(0, counter.count(blank(200, 200)).size)
    }

    @Test
    fun `three well-separated tablets are counted as three`() {
        val img = blank(200, 200)
        img.fillRect(20, 90, 45, 115, dark)
        img.fillRect(90, 90, 115, 115, dark)
        img.fillRect(160, 90, 185, 115, dark)
        assertEquals(3, counter.count(img).size)
    }

    @Test
    fun `five well-separated tablets are counted as five`() {
        val img = blank(240, 240)
        val spots = listOf(20 to 20, 120 to 20, 20 to 120, 120 to 120, 200 to 200)
        for ((x, y) in spots) img.fillRect(x, y, x + 22, y + 22, dark)
        assertEquals(5, counter.count(img).size)
    }

    @Test
    fun `light tablets on a dark background are also counted (polarity handled)`() {
        val img = blank(200, 200, color = dark)
        img.fillRect(40, 40, 65, 65, white)
        img.fillRect(130, 130, 155, 155, white)
        assertEquals(2, counter.count(img).size)
    }

    @Test
    fun `touching tablets merge into one blob (documents the MVP under-count the user corrects)`() {
        val img = blank(200, 200)
        // Two adjacent squares sharing the x=80 boundary -> one connected blob.
        img.fillRect(60, 90, 80, 110, dark)
        img.fillRect(80, 90, 100, 110, dark)
        assertEquals(1, counter.count(img).size)
    }

    @Test
    fun `sub-threshold speckle is ignored`() {
        val img = blank(200, 200)
        img.fillRect(100, 100, 102, 102, dark) // 2x2 = 4 px, below the min-area floor
        assertEquals(0, counter.count(img).size)
    }

    @Test
    fun `a detected centre lands inside the tablet`() {
        val img = blank(200, 200)
        img.fillRect(80, 80, 120, 120, dark)
        val points = counter.count(img)
        assertEquals(1, points.size)
        val p = points.single()
        assertTrue("centre x within tablet", p.x in 80f..120f)
        assertTrue("centre y within tablet", p.y in 80f..120f)
    }

    @Test
    fun `passing a reference image does not change the MVP count (reference is ignored)`() {
        val img = blank(200, 200)
        img.fillRect(30, 30, 55, 55, dark)
        img.fillRect(140, 140, 165, 165, dark)
        val reference = ReferenceImage(blank(30, 30, color = dark))
        assertEquals(
            counter.count(img).size,
            counter.count(img, reference).size,
        )
    }
}
