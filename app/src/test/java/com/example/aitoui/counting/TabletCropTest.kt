package com.example.aitoui.counting

import org.junit.Assert.assertEquals
import org.junit.Test

class TabletCropTest {

    @Test
    fun `cropped copies the requested sub-region`() {
        // A 4x3 image where each pixel encodes its (x,y) as x*10 + y.
        val w = 4; val h = 3
        val pixels = IntArray(w * h) { (it % w) * 10 + (it / w) }
        val image = CountImage(w, h, pixels)

        val sub = image.cropped(PixelRect(left = 1, top = 1, width = 2, height = 2))
        assertEquals(2, sub.width)
        assertEquals(2, sub.height)
        // top-left of the crop is original (1,1) = 11; then (2,1)=21, (1,2)=12, (2,2)=22
        assertEquals(listOf(11, 21, 12, 22), sub.pixels.toList())
    }

    @Test
    fun `clampedTo keeps the rect inside the image`() {
        val r = PixelRect(left = -5, top = -5, width = 100, height = 100).clampedTo(40, 30)
        assertEquals(0, r.left)
        assertEquals(0, r.top)
        assertEquals(40, r.width)
        assertEquals(30, r.height)
    }

    @Test
    fun `clampedTo enforces a minimum size and shifts to stay in bounds`() {
        val r = PixelRect(left = 39, top = 29, width = 1, height = 1).clampedTo(40, 30, minSize = 8)
        assertEquals(8, r.width)
        assertEquals(8, r.height)
        assertEquals(32, r.left) // 40 - 8
        assertEquals(22, r.top)  // 30 - 8
    }

    @Test
    fun `detection within a crop finds the tablet in that region`() {
        // A tablet only inside the right half; cropping to the left half should find nothing.
        val image = CountImage(200, 100, IntArray(200 * 100) { 0xFF202020.toInt() })
        for (y in 40..60) for (x in 150..170) image.pixels[y * 200 + x] = 0xFFF0F0F0.toInt()
        val counter = PeakTabletCounter()
        assertEquals(1, counter.count(image).size)
        assertEquals(0, counter.count(image.cropped(PixelRect(0, 0, 100, 100))).size) // left half only
        assertEquals(1, counter.count(image.cropped(PixelRect(100, 0, 100, 100))).size) // right half
    }
}
