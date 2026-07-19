package com.example.aitoui.counting

/**
 * An axis-aligned pixel rectangle within a [CountImage] — used to restrict tablet detection to a region the
 * user has framed (excluding a cluttered or textured surround). Pure and JVM-testable.
 */
data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

/** Copy the [rect] sub-region into a new [CountImage]. [rect] must lie within this image's bounds. */
fun CountImage.cropped(rect: PixelRect): CountImage {
    val out = IntArray(rect.width * rect.height)
    for (y in 0 until rect.height) {
        System.arraycopy(pixels, (rect.top + y) * width + rect.left, out, y * rect.width, rect.width)
    }
    return CountImage(rect.width, rect.height, out)
}

/** Clamp to lie fully within [imageWidth] × [imageHeight], with each side at least [minSize] px. */
fun PixelRect.clampedTo(imageWidth: Int, imageHeight: Int, minSize: Int = 8): PixelRect {
    val w = width.coerceIn(minSize.coerceAtMost(imageWidth), imageWidth)
    val h = height.coerceIn(minSize.coerceAtMost(imageHeight), imageHeight)
    val l = left.coerceIn(0, imageWidth - w)
    val t = top.coerceIn(0, imageHeight - h)
    return PixelRect(l, t, w, h)
}
