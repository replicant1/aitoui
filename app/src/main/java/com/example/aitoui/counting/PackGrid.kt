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

/**
 * A manual nudge of the whole blister grid over one pack, so the user can slide the circles onto the real
 * blisters and stretch/squeeze their spacing to fit. [dx]/[dy] translate every centre in image pixels;
 * [spacing] scales every centre's distance from the pack centroid equally along both axes (1 = unchanged).
 * The blister radius is deliberately unaffected — only the gaps between centres change.
 */
data class GridAdjust(val dx: Float = 0f, val dy: Float = 0f, val spacing: Float = 1f) {
    companion object {
        val None = GridAdjust()

        /** Sensible bounds so a pinch can't collapse the grid to a point or fling it apart. */
        const val MIN_SPACING = 0.4f
        const val MAX_SPACING = 3f
    }
}

/** Centre of blister ([along], [across]) with a manual [adjust] applied, in image pixels. */
fun adjustedCellCenter(
    region: PackRegion,
    alongLong: Int,
    alongShort: Int,
    along: Int,
    across: Int,
    adjust: GridAdjust,
): CountPoint {
    val base = cellCenter(region, alongLong, alongShort, along, across)
    return CountPoint(
        region.cx + (base.x - region.cx) * adjust.spacing + adjust.dx,
        region.cy + (base.y - region.cy) * adjust.spacing + adjust.dy,
    )
}

/**
 * The blister whose [adjust]ed centre lies within [radius] image-pixels of ([x], [y]), or null when the point
 * is off every circle. Unlike [tapToCell] (which snaps a tap anywhere on the pack to the nearest blister),
 * this is a strict on-a-circle hit test — it's what tells a "pop" (a touch that lands on a blister) from a
 * "pan" (a touch that starts on empty space and drags the grid).
 */
fun adjustedCellHit(
    region: PackRegion,
    alongLong: Int,
    alongShort: Int,
    x: Float,
    y: Float,
    adjust: GridAdjust,
    radius: Float,
): CellRef? {
    var best: CellRef? = null
    var bestD2 = radius * radius
    for (along in 0 until alongLong) for (across in 0 until alongShort) {
        val c = adjustedCellCenter(region, alongLong, alongShort, along, across, adjust)
        val d2 = (c.x - x) * (c.x - x) + (c.y - y) * (c.y - y)
        if (d2 <= bestD2) {
            bestD2 = d2
            best = CellRef(along, across)
        }
    }
    return best
}

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
