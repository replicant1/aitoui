package com.example.aitoui.counting

import kotlin.math.hypot
import kotlin.math.min

/** Fraction of the image's smaller side within which a correction tap counts as hitting an existing marker. */
const val MARKER_HIT_RADIUS_FRACTION = 0.04f

/**
 * Applies a correction tap at image-pixel ([tapX], [tapY]) to [markers]: removes the nearest marker within
 * the hit radius (a false positive), or, if none is close enough, adds a new marker there (a missed tablet).
 * Pure and JVM-testable.
 */
fun editMarkers(
    markers: List<CountPoint>,
    imageWidth: Int,
    imageHeight: Int,
    tapX: Float,
    tapY: Float,
): List<CountPoint> {
    if (imageWidth == 0 || imageHeight == 0) return markers
    val hitRadius = min(imageWidth, imageHeight) * MARKER_HIT_RADIUS_FRACTION
    val nearest = markers.withIndex().minByOrNull { distance(it.value, tapX, tapY) }
    return if (nearest != null && distance(nearest.value, tapX, tapY) <= hitRadius) {
        markers.filterIndexed { index, _ -> index != nearest.index }
    } else {
        markers + CountPoint(tapX, tapY)
    }
}

private fun distance(p: CountPoint, x: Float, y: Float): Double =
    hypot((p.x - x).toDouble(), (p.y - y).toDouble())
