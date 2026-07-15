package com.example.aitoui.counting

/**
 * Pure geometry for laying a confirmed blister grid over a [PackRegion] and hit-testing taps back to blisters.
 * The grid has [alongLong] blisters down the pack's long axis and [alongShort] across its short axis (for a
 * 2x5 pack: alongLong = 5, alongShort = 2). The blister band is inset from the pack edge by these fractions,
 * since blisters don't reach the sealed rim.
 */
const val PACK_GRID_MARGIN_LONG = 0.06f
const val PACK_GRID_MARGIN_SHORT = 0.10f

/** A blister address: [along] indexes the long axis (0 until alongLong), [across] the short axis. */
data class CellRef(val along: Int, val across: Int)

/** Centre of blister ([along], [across]) in image pixels. */
fun cellCenter(region: PackRegion, alongLong: Int, alongShort: Int, along: Int, across: Int): CountPoint {
    val f = cellFraction(along, alongLong, PACK_GRID_MARGIN_LONG)
    val g = cellFraction(across, alongShort, PACK_GRID_MARGIN_SHORT)
    val lc = region.longMin + f * (region.longMax - region.longMin)
    val sc = region.shortMin + g * (region.shortMax - region.shortMin)
    return CountPoint(
        region.cx + lc * region.longX + sc * region.shortX,
        region.cy + lc * region.longY + sc * region.shortY,
    )
}

/**
 * The blister a tap at image ([x], [y]) falls on, or null if the tap is well outside the pack. Taps within
 * the pack snap to the nearest blister (so a tap anywhere on a blister, including its margin, still registers).
 */
fun tapToCell(region: PackRegion, alongLong: Int, alongShort: Int, x: Float, y: Float): CellRef? {
    val dx = x - region.cx
    val dy = y - region.cy
    val lc = dx * region.longX + dy * region.longY
    val sc = dx * region.shortX + dy * region.shortY
    val f = (lc - region.longMin) / (region.longMax - region.longMin)
    val g = (sc - region.shortMin) / (region.shortMax - region.shortMin)
    if (f < -0.15f || f > 1.15f || g < -0.20f || g > 1.20f) return null
    return CellRef(
        cellIndex(f, alongLong, PACK_GRID_MARGIN_LONG).coerceIn(0, alongLong - 1),
        cellIndex(g, alongShort, PACK_GRID_MARGIN_SHORT).coerceIn(0, alongShort - 1),
    )
}

/** Fractional position (0..1 across the pack) of blister [index]'s centre, given the band [margin]. */
private fun cellFraction(index: Int, count: Int, margin: Float): Float =
    margin + (index + 0.5f) / count * (1 - 2 * margin)

/** Inverse of [cellFraction]: which blister a [fraction] (0..1 across the pack) lands in. */
private fun cellIndex(fraction: Float, count: Int, margin: Float): Int {
    val inner = (fraction - margin) / (1 - 2 * margin) // 0..1 across the blister band
    return (inner * count).toInt()
}
