package com.example.aitoui.counting

import kotlin.math.max
import kotlin.math.min

/** A local maximum of the distance transform: a candidate tablet centre at ([x], [y]) of height [d]. */
internal class Peak(val x: Float, val y: Float, val d: Float)

/**
 * The result of [PeakTabletCounter.analyse] — the distance-transform peaks and their median height, from
 * which a marker set is chosen by [select]. Splitting the pipeline this way lets a caller run the expensive
 * analysis once (mask + distance transform + local maxima) and then re-[select] cheaply as the user drags a
 * sensitivity slider, since sensitivity only affects the final height filter and suppression.
 */
class PeakField internal constructor(
    private val maxima: List<Peak>,
    private val medianHeight: Double,
    private val width: Int,
    private val minHeightPx: Double,
) {
    val peakCount: Int get() = maxima.size

    /**
     * Choose tablet centres from the peaks. Peaks shorter than [minHeightFraction] of the median height (or
     * [minHeightPx], whichever is larger) are dropped as glare/speckle; the survivors are thinned by
     * non-maximum suppression at a radius of [suppressionFactor] × median height.
     */
    fun select(minHeightFraction: Double, suppressionFactor: Double = 2.0): List<CountPoint> {
        if (maxima.isEmpty()) return emptyList()
        val floor = max(minHeightPx, minHeightFraction * medianHeight).toFloat()
        val radius = max(3.0, suppressionFactor * medianHeight)
        val candidates = maxima.filter { it.d >= floor }.sortedByDescending { it.d }
        return suppress(candidates, radius, width).map { CountPoint(it.x, it.y) }
    }

    /**
     * Greedy non-maximum suppression: taking peaks tallest-first, keep one only if no already-kept peak lies
     * within [radius]. A uniform grid (cell = radius) keeps the neighbour search near O(n).
     */
    private fun suppress(candidates: List<Peak>, radius: Double, w: Int): List<Peak> {
        val cell = radius
        val gridW = (w / cell).toInt() + 1
        val grid = HashMap<Int, MutableList<Peak>>()
        val kept = ArrayList<Peak>()
        val r2 = radius * radius

        for (p in candidates) {
            val gx = (p.x / cell).toInt()
            val gy = (p.y / cell).toInt()
            var ok = true
            neighbours@ for (cy in gy - 1..gy + 1) {
                for (cx in gx - 1..gx + 1) {
                    val bucket = grid[cy * gridW + cx] ?: continue
                    for (q in bucket) {
                        val dx = (p.x - q.x).toDouble()
                        val dy = (p.y - q.y).toDouble()
                        if (dx * dx + dy * dy <= r2) {
                            ok = false
                            break@neighbours
                        }
                    }
                }
            }
            if (ok) {
                kept.add(p)
                grid.getOrPut(gy * gridW + gx) { ArrayList() }.add(p)
            }
        }
        return kept
    }
}

/**
 * Counts tablets by finding peaks in a distance transform — a lightweight watershed that separates touching
 * tablets, which plain blob detection ([BlobTabletCounter]) merges into a single count.
 *
 * Each foreground (tablet) pixel is assigned its distance to the nearest background pixel. A tablet's centre
 * is a local maximum of that map — the point farthest from its edge — and two touching tablets produce two
 * distinct peaks separated by a lower "valley" at the neck where they meet. The peaks are thinned by
 * non-maximum suppression at a radius derived from the typical tablet size, and each survivor becomes one
 * tablet centre.
 *
 * Pure Kotlin, dependency-free (no OpenCV / ML Kit). Callers should down-scale large camera frames (e.g. to
 * ~1200px on the long edge) before constructing the [CountImage]. Remaining errors — heavily overlapping
 * tablets that merge, or glare that splits one — are handled by the user's tap-to-add / tap-to-remove.
 *
 * The pipeline is split into [analyse] (expensive, sensitivity-independent) and [PeakField.select] (cheap),
 * so the UI can re-tune the count live with a slider without recomputing the distance transform.
 */
class PeakTabletCounter(
    /** Suppression radius as a multiple of the median peak height (≈ a tablet's half-width). Larger merges
     *  more aggressively (fewer, safer against splitting one elongated tablet along its distance-transform
     *  ridge); smaller separates closer tablets. ~2.0 keeps 2:1 oval pills whole while still splitting most
     *  touching pairs. */
    private val suppressionFactor: Double = 2.0,
    /** Peaks shorter than this fraction of the median peak height are dropped as glare/speckle. */
    private val minHeightFraction: Double = 0.30,
    /** Absolute floor (px) on peak height, to ignore 1–2px noise regardless of the median. */
    private val minHeightPx: Double = 2.0,
) : TabletCounter {

    override fun count(image: CountImage, reference: ReferenceImage?): List<CountPoint> =
        analyse(image).select(minHeightFraction, suppressionFactor)

    /**
     * The expensive, sensitivity-independent stage: build the foreground mask, its distance transform, and
     * the local maxima. Cache the returned [PeakField] and call [PeakField.select] to (re)choose markers.
     */
    fun analyse(image: CountImage): PeakField {
        val w = image.width
        val h = image.height
        val n = w * h
        if (n == 0) return PeakField(emptyList(), 0.0, w, minHeightPx)

        val dist = distanceTransform(foregroundMask(image), w, h)
        val maxima = localMaxima(dist, w, h)
        val median = if (maxima.isEmpty()) 0.0 else medianOf(maxima)
        return PeakField(maxima, median, w, minHeightPx)
    }

    /**
     * Chamfer distance transform: distance from each foreground pixel to the nearest background pixel, via
     * two passes (forward top-left→bottom-right, backward bottom-right→top-left) with 3-4 style weights.
     */
    private fun distanceTransform(mask: BooleanArray, w: Int, h: Int): FloatArray {
        val inf = (w + h).toFloat()
        val d = FloatArray(mask.size) { if (mask[it]) inf else 0f }

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (!mask[i]) continue
                var best = d[i]
                if (x > 0) best = min(best, d[i - 1] + ORTHOGONAL)
                if (y > 0) best = min(best, d[i - w] + ORTHOGONAL)
                if (x > 0 && y > 0) best = min(best, d[i - w - 1] + DIAGONAL)
                if (x < w - 1 && y > 0) best = min(best, d[i - w + 1] + DIAGONAL)
                d[i] = best
            }
        }
        for (y in h - 1 downTo 0) {
            for (x in w - 1 downTo 0) {
                val i = y * w + x
                if (!mask[i]) continue
                var best = d[i]
                if (x < w - 1) best = min(best, d[i + 1] + ORTHOGONAL)
                if (y < h - 1) best = min(best, d[i + w] + ORTHOGONAL)
                if (x < w - 1 && y < h - 1) best = min(best, d[i + w + 1] + DIAGONAL)
                if (x > 0 && y < h - 1) best = min(best, d[i + w - 1] + DIAGONAL)
                d[i] = best
            }
        }
        return d
    }

    /** Foreground pixels that are >= all 8 neighbours (plateaus included; thinned later by suppression). */
    private fun localMaxima(d: FloatArray, w: Int, h: Int): List<Peak> {
        val peaks = ArrayList<Peak>()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val v = d[i]
                if (v < minHeightPx) continue
                var isMax = true
                loop@ for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) continue
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        if (nx < 0 || nx >= w) continue
                        if (d[ny * w + nx] > v) {
                            isMax = false
                            break@loop
                        }
                    }
                }
                if (isMax) peaks.add(Peak(x.toFloat(), y.toFloat(), v))
            }
        }
        return peaks
    }

    private fun medianOf(peaks: List<Peak>): Double {
        val sorted = peaks.map { it.d }.sorted()
        val m = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[m].toDouble() else (sorted[m - 1] + sorted[m]) / 2.0
    }

    private companion object {
        const val ORTHOGONAL = 1f
        const val DIAGONAL = 1.41421356f
    }
}
