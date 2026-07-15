package com.example.aitoui.counting

/**
 * Counts tablets by classical blob detection: greyscale, an automatic (Otsu) threshold to separate the
 * tablets from a plain background, then connected-component labelling. Each surviving blob becomes one
 * tablet centre point.
 *
 * Deliberately simple and dependency-free (no OpenCV / ML Kit). It assumes the MVP capture guidance — a
 * single layer of well-separated tablets on a plain, contrasting surface. Touching tablets merge into one
 * blob (under-count) and glare/shadow can add spurious blobs (over-count); both are fixed by the user's
 * tap-to-add / tap-to-remove correction. Smarter de-clumping is deferred (see docs/tablet-counting-mvp.md).
 *
 * For speed and stable thresholds, callers should down-scale large camera frames (e.g. to ~1000px on the
 * long edge) before constructing the [CountImage].
 */
class BlobTabletCounter(
    /** Blobs smaller than this fraction of the image are treated as speckle/noise and dropped. */
    private val minAreaFraction: Double = 0.0004,
    /** Blobs larger than this fraction of the image are treated as background leaks / merged mass and dropped. */
    private val maxAreaFraction: Double = 0.25,
) : TabletCounter {

    override fun count(image: CountImage, reference: ReferenceImage?): List<CountPoint> {
        val w = image.width
        val h = image.height
        val n = w * h
        if (n == 0) return emptyList()

        val gray = toGrayscale(image.pixels, n)
        val threshold = otsuThreshold(gray)

        // Tablets are the minority class after thresholding (background dominates a spread-out layer).
        var darkCount = 0
        for (v in gray) if (v <= threshold) darkCount++
        val foregroundIsDark = darkCount <= n - darkCount

        val foreground = BooleanArray(n) { i ->
            val isDark = gray[i] <= threshold
            if (foregroundIsDark) isDark else !isDark
        }

        return blobCentres(foreground, w, h, n)
    }

    private fun toGrayscale(pixels: IntArray, n: Int): IntArray {
        val gray = IntArray(n)
        for (i in 0 until n) {
            val p = pixels[i]
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 luma, integer-scaled.
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        return gray
    }

    /** Otsu's method: the threshold maximising between-class variance of the intensity histogram. */
    private fun otsuThreshold(gray: IntArray): Int {
        val hist = IntArray(256)
        for (v in gray) hist[v]++
        val total = gray.size

        var sumAll = 0.0
        for (i in 0 until 256) sumAll += (i.toDouble() * hist[i])

        var sumB = 0.0
        var weightB = 0
        var maxBetween = -1.0
        var threshold = 127
        for (i in 0 until 256) {
            weightB += hist[i]
            if (weightB == 0) continue
            val weightF = total - weightB
            if (weightF == 0) break
            sumB += i.toDouble() * hist[i]
            val meanB = sumB / weightB
            val meanF = (sumAll - sumB) / weightF
            val between = weightB.toDouble() * weightF.toDouble() * (meanB - meanF) * (meanB - meanF)
            if (between > maxBetween) {
                maxBetween = between
                threshold = i
            }
        }
        return threshold
    }

    /** Iterative 8-connected flood fill over the foreground mask; returns the centroid of each blob that
     *  falls within the area window. */
    private fun blobCentres(foreground: BooleanArray, w: Int, h: Int, n: Int): List<CountPoint> {
        val minArea = (minAreaFraction * n).toInt().coerceAtLeast(1)
        val maxArea = (maxAreaFraction * n).toInt()

        val visited = BooleanArray(n)
        val stack = IntArray(n)
        val points = ArrayList<CountPoint>()

        for (start in 0 until n) {
            if (!foreground[start] || visited[start]) continue

            var top = 0
            stack[top++] = start
            visited[start] = true

            var area = 0
            var sumX = 0L
            var sumY = 0L
            while (top > 0) {
                val idx = stack[--top]
                val cx = idx % w
                val cy = idx / w
                area++
                sumX += cx
                sumY += cy

                for (dy in -1..1) {
                    val ny = cy + dy
                    if (ny < 0 || ny >= h) continue
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = cx + dx
                        if (nx < 0 || nx >= w) continue
                        val nIdx = ny * w + nx
                        if (foreground[nIdx] && !visited[nIdx]) {
                            visited[nIdx] = true
                            stack[top++] = nIdx
                        }
                    }
                }
            }

            if (area in minArea..maxArea) {
                points.add(CountPoint(sumX.toFloat() / area, sumY.toFloat() / area))
            }
        }
        return points
    }
}
