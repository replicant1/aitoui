package com.example.aitoui.counting

/**
 * Shared image-segmentation primitives for the pure-Kotlin tablet counters: greyscale conversion, an
 * automatic Otsu threshold, and the resulting foreground (tablet) mask. Dependency-free (no OpenCV / ML Kit).
 */

/** Rec. 601 luma greyscale of the ARGB [pixels] (length [n]). */
internal fun toGrayscale(pixels: IntArray, n: Int): IntArray {
    val gray = IntArray(n)
    for (i in 0 until n) {
        val p = pixels[i]
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        gray[i] = (r * 299 + g * 587 + b * 114) / 1000
    }
    return gray
}

/** Otsu's method: the threshold maximising between-class variance of the intensity histogram. */
internal fun otsuThreshold(gray: IntArray): Int {
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

/**
 * Foreground (tablet) mask: true where tablets are. Tablets are taken to be the minority intensity class
 * after an Otsu split, since a spread-out layer leaves the background as the dominant class.
 */
internal fun foregroundMask(image: CountImage): BooleanArray {
    val n = image.width * image.height
    val gray = toGrayscale(image.pixels, n)
    val threshold = otsuThreshold(gray)

    var darkCount = 0
    for (v in gray) if (v <= threshold) darkCount++
    val foregroundIsDark = darkCount <= n - darkCount

    return BooleanArray(n) { i ->
        val isDark = gray[i] <= threshold
        if (foregroundIsDark) isDark else !isDark
    }
}
