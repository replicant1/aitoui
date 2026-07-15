package com.example.aitoui.counting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PackSegmentationTest {

    private val dark = 0xFF202020.toInt()
    private val bright = 0xFFF0F0F0.toInt()

    @Test
    fun `an empty image yields no packs`() {
        assertEquals(0, segmentPacks(CountImage(0, 0, IntArray(0))).size)
    }

    @Test
    fun `a plain surface with no packs yields no packs`() {
        assertEquals(0, segmentPacks(solid(300, 300)).size)
    }

    @Test
    fun `one pack is found, centred, with the long axis on its long side`() {
        val img = solid(300, 400)
        fillRect(img, cx = 150, cy = 200, halfW = 45, halfH = 150) // tall: 90 x 300
        val packs = segmentPacks(img)
        assertEquals(1, packs.size)
        val p = packs[0]
        assertTrue("centroid x", abs(p.cx - 150) < 3)
        assertTrue("centroid y", abs(p.cy - 200) < 3)
        // Long axis is (nearly) vertical, and its extent matches the taller side.
        assertTrue("long axis vertical", abs(p.longY) > abs(p.longX))
        assertTrue("long extent ~300", abs((p.longMax - p.longMin) - 300) < 12)
        assertTrue("short extent ~90", abs((p.shortMax - p.shortMin) - 90) < 12)
    }

    @Test
    fun `two separated packs are found and ordered left to right`() {
        val img = solid(500, 300)
        fillRect(img, cx = 110, cy = 150, halfW = 40, halfH = 110)
        fillRect(img, cx = 380, cy = 150, halfW = 40, halfH = 110)
        val packs = segmentPacks(img)
        assertEquals(2, packs.size)
        assertTrue(packs[0].cx < packs[1].cx)
        assertTrue(abs(packs[0].cx - 110) < 4)
        assertTrue(abs(packs[1].cx - 380) < 4)
    }

    @Test
    fun `a rotated pack reports an angled long axis`() {
        val img = solid(400, 400)
        fillOrientedRect(img, cx = 200, cy = 200, halfLong = 130, halfShort = 45, angle = Math.PI / 4)
        val packs = segmentPacks(img)
        assertEquals(1, packs.size)
        val p = packs[0]
        // Long axis ~45 degrees: |longX| ~ |longY| ~ 0.707 (sign irrelevant).
        assertTrue("long axis ~45deg", abs(abs(p.longX) - 0.707f) < 0.12f && abs(abs(p.longY) - 0.707f) < 0.12f)
        assertTrue("long extent ~260", abs((p.longMax - p.longMin) - 260) < 16)
    }

    @Test
    fun `small speckle is ignored`() {
        val img = solid(300, 400)
        fillRect(img, cx = 150, cy = 200, halfW = 45, halfH = 150)
        fillRect(img, cx = 20, cy = 20, halfW = 3, halfH = 3)   // below the area floor
        fillRect(img, cx = 280, cy = 380, halfW = 2, halfH = 2)
        assertEquals(1, segmentPacks(img).size)
    }

    private fun solid(w: Int, h: Int) = CountImage(w, h, IntArray(w * h) { dark })

    private fun fillRect(img: CountImage, cx: Int, cy: Int, halfW: Int, halfH: Int) {
        for (y in (cy - halfH)..(cy + halfH)) {
            if (y < 0 || y >= img.height) continue
            for (x in (cx - halfW)..(cx + halfW)) {
                if (x < 0 || x >= img.width) continue
                img.pixels[y * img.width + x] = bright
            }
        }
    }

    private fun fillOrientedRect(img: CountImage, cx: Int, cy: Int, halfLong: Int, halfShort: Int, angle: Double) {
        val ux = cos(angle); val uy = sin(angle) // long axis
        val vx = -uy; val vy = ux                 // short axis
        val r = halfLong + halfShort
        for (y in (cy - r)..(cy + r)) {
            if (y < 0 || y >= img.height) continue
            for (x in (cx - r)..(cx + r)) {
                if (x < 0 || x >= img.width) continue
                val dx = (x - cx).toDouble(); val dy = (y - cy).toDouble()
                val alongLong = abs(dx * ux + dy * uy)
                val alongShort = abs(dx * vx + dy * vy)
                if (alongLong <= halfLong && alongShort <= halfShort) img.pixels[y * img.width + x] = bright
            }
        }
    }
}
