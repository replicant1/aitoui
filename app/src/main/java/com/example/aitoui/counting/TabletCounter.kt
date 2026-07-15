package com.example.aitoui.counting

/**
 * A platform-independent still image for [TabletCounter]: row-major 32-bit ARGB pixels (as produced by
 * `Bitmap.getPixels`). Kept free of Android types so the counting algorithm is pure and JVM-testable.
 */
class CountImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(pixels.size == width * height) {
            "pixels.size=${pixels.size} does not match width*height=${width * height}"
        }
    }
}

/** A detected tablet location, in pixel coordinates of the analysed [CountImage]. */
data class CountPoint(val x: Float, val y: Float)

/**
 * A reference photo of the medication's tablet. Reserved for future accuracy improvements (colour/size
 * pre-filtering, appearance-based de-clumping); accepted by [TabletCounter] but ignored in the MVP.
 */
class ReferenceImage(val image: CountImage)

/**
 * Counts discrete tablets in a still image — intended for a single layer of tablets spread on a plain,
 * contrasting surface. Pure and JVM-testable.
 *
 * The MVP is medication-agnostic: [reference] is part of the contract for forward compatibility but is
 * ignored today. The result is a list of tablet centre points so the UI can draw a correctable marker on
 * each; the count is `result.size`.
 */
interface TabletCounter {
    fun count(image: CountImage, reference: ReferenceImage? = null): List<CountPoint>
}
