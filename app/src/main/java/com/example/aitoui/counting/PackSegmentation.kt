package com.example.aitoui.counting

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A segmented blister pack, squared up for gridding: its centroid, two orthogonal principal axes (unit
 * vectors), and the pack's extent along each. [longX]/[longY] is the pack's longer side (which carries more
 * pockets), [shortX]/[shortY] the shorter. Extents are signed distances of pixels from the centroid when
 * projected onto each axis, so a pocket at fraction f along the long axis sits at `longMin + f*(longMax-longMin)`.
 * All values are in image pixels. See [cellCenter] / [tapToCell] in PackGrid.kt.
 */
data class PackRegion(
    val cx: Float, val cy: Float,
    val longX: Float, val longY: Float,
    val shortX: Float, val shortY: Float,
    val longMin: Float, val longMax: Float,
    val shortMin: Float, val shortMax: Float,
)

/**
 * Finds blister packs in [image] — bright islands on a dark surface — and returns each as a squared-up
 * [PackRegion], ordered left-to-right by centroid x.
 *
 * Reuses [foregroundMask] (Otsu, tablets/packs = the bright minority), labels 8-connected components, keeps
 * those at least [minAreaFraction] of the image (drops speckle), and derives each pack's orientation and
 * extent by PCA of its pixels. Packs must not touch or overlap — each must be one connected blob. Pure and
 * JVM-testable; the on-device pipeline downscales the frame before calling this.
 */
fun segmentPacks(image: CountImage, minAreaFraction: Float = 0.02f): List<PackRegion> {
    val w = image.width
    val h = image.height
    val n = w * h
    if (n == 0) return emptyList()

    val mask = foregroundMask(image)

    // 8-connected component labelling.
    val label = IntArray(n) { -1 }
    val stack = IntArray(n)
    val areas = ArrayList<Int>()
    var next = 0
    for (start in 0 until n) {
        if (!mask[start] || label[start] != -1) continue
        var top = 0
        stack[top++] = start
        label[start] = next
        var area = 0
        while (top > 0) {
            val idx = stack[--top]
            area++
            val px = idx % w
            val py = idx / w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = px + dx
                val ny = py + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val ni = ny * w + nx
                if (mask[ni] && label[ni] == -1) { label[ni] = next; stack[top++] = ni }
            }
        }
        areas.add(area)
        next++
    }

    val minArea = (minAreaFraction * n).toInt().coerceAtLeast(1)
    val valid = areas.indices.filter { areas[it] >= minArea }
    if (valid.isEmpty()) return emptyList()
    val idOf = HashMap<Int, Int>()
    valid.forEachIndexed { i, c -> idOf[c] = i }
    val m = valid.size

    // Pass 1: first/second moments per pack -> centroid + covariance -> principal axes.
    val cnt = DoubleArray(m); val sx = DoubleArray(m); val sy = DoubleArray(m)
    val sxx = DoubleArray(m); val sxy = DoubleArray(m); val syy = DoubleArray(m)
    for (i in 0 until n) {
        val k = idOf[label[i]] ?: continue
        val x = (i % w).toDouble(); val y = (i / w).toDouble()
        cnt[k]++; sx[k] += x; sy[k] += y; sxx[k] += x * x; sxy[k] += x * y; syy[k] += y * y
    }
    val mx = DoubleArray(m); val my = DoubleArray(m)
    val ax = DoubleArray(m); val ay = DoubleArray(m) // principal axis A
    val bx = DoubleArray(m); val by = DoubleArray(m) // orthogonal axis B
    for (k in 0 until m) {
        val c = cnt[k]
        mx[k] = sx[k] / c; my[k] = sy[k] / c
        val cxx = sxx[k] / c - mx[k] * mx[k]
        val cxy = sxy[k] / c - mx[k] * my[k]
        val cyy = syy[k] / c - my[k] * my[k]
        val theta = 0.5 * atan2(2 * cxy, cxx - cyy)
        ax[k] = cos(theta); ay[k] = sin(theta); bx[k] = -ay[k]; by[k] = ax[k]
    }

    // Pass 2: extent of each pack along its two axes.
    val aMin = DoubleArray(m) { Double.MAX_VALUE }; val aMax = DoubleArray(m) { -Double.MAX_VALUE }
    val bMin = DoubleArray(m) { Double.MAX_VALUE }; val bMax = DoubleArray(m) { -Double.MAX_VALUE }
    for (i in 0 until n) {
        val k = idOf[label[i]] ?: continue
        val dx = i % w - mx[k]; val dy = i / w - my[k]
        val pa = dx * ax[k] + dy * ay[k]; val pb = dx * bx[k] + dy * by[k]
        if (pa < aMin[k]) aMin[k] = pa; if (pa > aMax[k]) aMax[k] = pa
        if (pb < bMin[k]) bMin[k] = pb; if (pb > bMax[k]) bMax[k] = pb
    }

    val regions = ArrayList<PackRegion>(m)
    for (k in 0 until m) {
        // Long axis = the one with the greater extent (more pockets).
        val region = if ((aMax[k] - aMin[k]) >= (bMax[k] - bMin[k])) {
            PackRegion(
                mx[k].toFloat(), my[k].toFloat(),
                ax[k].toFloat(), ay[k].toFloat(), bx[k].toFloat(), by[k].toFloat(),
                aMin[k].toFloat(), aMax[k].toFloat(), bMin[k].toFloat(), bMax[k].toFloat(),
            )
        } else {
            PackRegion(
                mx[k].toFloat(), my[k].toFloat(),
                bx[k].toFloat(), by[k].toFloat(), ax[k].toFloat(), ay[k].toFloat(),
                bMin[k].toFloat(), bMax[k].toFloat(), aMin[k].toFloat(), aMax[k].toFloat(),
            )
        }
        regions.add(region)
    }
    return regions.sortedBy { it.cx }
}
