package com.example.aitoui.counting

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * An editable, oriented rectangle used to frame one blister pack by hand. Held in a form that's easy to
 * drag: a centre, half-width/half-height along the box's own axes, and a rotation. All values are in image
 * pixels. Converts to/from [PackRegion] (which the grid/counter consume) at the edges of the framing step.
 *
 * The local axes are u = (cos θ, sin θ) for the width and v = (−sin θ, cos θ) for the height. Pure and
 * JVM-testable — no Android/Compose types.
 */
data class FrameBox(
    val cx: Float,
    val cy: Float,
    val halfW: Float,
    val halfH: Float,
    val angleRad: Float,
)

/** Smallest allowed half-extent (image px), so a box can never invert or collapse while resizing. */
const val FRAME_MIN_HALF = 8f

/** Where a [FrameBox]'s corner hit or handle landed. */
sealed interface FrameHit {
    data object Rotate : FrameHit
    /** Corner [index] in the order top-left, top-right, bottom-right, bottom-left. */
    data class Corner(val index: Int) : FrameHit
    data object Body : FrameHit
    data object None : FrameHit
}

private fun FrameBox.uAxis() = CountPoint(cos(angleRad.toDouble()).toFloat(), sin(angleRad.toDouble()).toFloat())
private fun FrameBox.vAxis() = CountPoint((-sin(angleRad.toDouble())).toFloat(), cos(angleRad.toDouble()).toFloat())

/** The four corners in image pixels, ordered top-left, top-right, bottom-right, bottom-left (local frame). */
fun FrameBox.corners(): List<CountPoint> {
    val u = uAxis()
    val v = vAxis()
    fun corner(su: Float, sv: Float) = CountPoint(
        cx + su * halfW * u.x + sv * halfH * v.x,
        cy + su * halfW * u.y + sv * halfH * v.y,
    )
    return listOf(corner(-1f, -1f), corner(1f, -1f), corner(1f, 1f), corner(-1f, 1f))
}

/** Midpoint of the box's screen-topmost edge (the one whose middle sits highest, i.e. smallest y). */
fun FrameBox.topEdgeMidpoint(): CountPoint {
    val c = corners()
    val mids = listOf(mid(c[0], c[1]), mid(c[1], c[2]), mid(c[2], c[3]), mid(c[3], c[0]))
    return mids.minByOrNull { it.y }!!
}

/**
 * The rotation handle's centre: [gap] image pixels outward from the box's top edge, where "top" is the edge
 * that sits highest on screen — so the handle stays above the box whatever its orientation.
 */
fun FrameBox.rotationHandle(gap: Float): CountPoint {
    val anchor = topEdgeMidpoint()
    val dx = anchor.x - cx
    val dy = anchor.y - cy
    val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1e-3f)
    return CountPoint(anchor.x + dx / len * gap, anchor.y + dy / len * gap)
}

private fun mid(a: CountPoint, b: CountPoint) = CountPoint((a.x + b.x) / 2f, (a.y + b.y) / 2f)

/** True if image point ([x], [y]) is inside the box. */
fun FrameBox.contains(x: Float, y: Float): Boolean {
    val (lx, ly) = toLocal(x, y)
    return abs(lx) <= halfW && abs(ly) <= halfH
}

private fun FrameBox.toLocal(x: Float, y: Float): Pair<Float, Float> {
    val dx = x - cx
    val dy = y - cy
    val u = uAxis()
    val v = vAxis()
    return (dx * u.x + dy * u.y) to (dx * v.x + dy * v.y)
}

/**
 * Classifies an image-pixel touch: the rotation handle first, then a corner within [handleRadius], then the
 * body, else nothing. [rotationGap] is the handle's distance beyond the top edge (same units as the box).
 */
fun FrameBox.hitTest(x: Float, y: Float, handleRadius: Float, rotationGap: Float): FrameHit {
    val rh = rotationHandle(rotationGap)
    if (dist(rh.x, rh.y, x, y) <= handleRadius) return FrameHit.Rotate
    corners().forEachIndexed { i, c -> if (dist(c.x, c.y, x, y) <= handleRadius) return FrameHit.Corner(i) }
    if (contains(x, y)) return FrameHit.Body
    return FrameHit.None
}

/** Translate by ([dx], [dy]) image pixels, keeping the centre within the image. */
fun FrameBox.movedBy(dx: Float, dy: Float, imageWidth: Int, imageHeight: Int): FrameBox =
    copy(
        cx = (cx + dx).coerceIn(0f, imageWidth.toFloat()),
        cy = (cy + dy).coerceIn(0f, imageHeight.toFloat()),
    )

/**
 * Drag [cornerIndex] to image point ([px], [py]) while the opposite corner stays put. The box keeps its
 * angle; half-extents come from projecting the diagonal onto the box axes, clamped to [FRAME_MIN_HALF] — so
 * the rectangle can't invert (this is the "corner can't cross the opposite edge" rule).
 */
fun FrameBox.resizedByCorner(cornerIndex: Int, px: Float, py: Float): FrameBox {
    val opp = corners()[(cornerIndex + 2) % 4]
    val u = uAxis()
    val v = vAxis()
    val dx = px - opp.x
    val dy = py - opp.y
    val hw = abs(dx * u.x + dy * u.y) / 2f
    val hh = abs(dx * v.x + dy * v.y) / 2f
    return copy(
        cx = (opp.x + px) / 2f,
        cy = (opp.y + py) / 2f,
        halfW = max(hw, FRAME_MIN_HALF),
        halfH = max(hh, FRAME_MIN_HALF),
    )
}

/** The oriented box the grid/counter consume. The longer side becomes the pack's long (blister) axis. */
fun FrameBox.toPackRegion(): PackRegion {
    val u = uAxis()
    val v = vAxis()
    return if (halfW >= halfH) {
        PackRegion(cx, cy, u.x, u.y, v.x, v.y, -halfW, halfW, -halfH, halfH)
    } else {
        PackRegion(cx, cy, v.x, v.y, u.x, u.y, -halfH, halfH, -halfW, halfW)
    }
}

/** Seed an editable box from a detected [PackRegion], centred on the region's box (not its centroid). */
fun PackRegion.toFrameBox(): FrameBox {
    val lMid = (longMin + longMax) / 2f
    val sMid = (shortMin + shortMax) / 2f
    return FrameBox(
        cx = cx + lMid * longX + sMid * shortX,
        cy = cy + lMid * longY + sMid * shortY,
        halfW = abs(longMax - longMin) / 2f,
        halfH = abs(shortMax - shortMin) / 2f,
        angleRad = atan2(longY.toDouble(), longX.toDouble()).toFloat(),
    )
}

/** A default axis-aligned box in the middle of the image (for "add pack" or when nothing was detected). */
fun centeredFrame(imageWidth: Int, imageHeight: Int, halfW: Float, halfH: Float): FrameBox =
    FrameBox(imageWidth / 2f, imageHeight / 2f, halfW, halfH, 0f)

/**
 * The order the framed packs should be numbered and popped in: row-major — top rows first, then left-to-right
 * within a row. Boxes whose centres sit within a (median) pack half-height of each other count as one row, so
 * a slightly uneven row still reads left-to-right rather than zig-zagging; well-separated rows fall through to
 * an approximately top-to-bottom, clockwise-ish order. Returns the frame indices in that order (empty in,
 * empty out).
 */
fun rowMajorOrder(boxes: List<FrameBox>): List<Int> {
    if (boxes.isEmpty()) return emptyList()
    // Rotation-aware vertical half-extent: how far the box reaches above/below its centre on screen.
    fun verticalHalf(b: FrameBox): Float =
        abs(b.halfW * sin(b.angleRad.toDouble()).toFloat()) + abs(b.halfH * cos(b.angleRad.toDouble()).toFloat())

    val band = boxes.map { verticalHalf(it) }.sorted().let { it[it.size / 2] } // median → the row-grouping tolerance
    val topToBottom = boxes.indices.sortedBy { boxes[it].cy }
    val rows = mutableListOf<MutableList<Int>>()
    for (i in topToBottom) {
        val row = rows.lastOrNull()
        if (row == null || boxes[i].cy - boxes[row.first()].cy > band) rows.add(mutableListOf(i))
        else row.add(i)
    }
    return rows.flatMap { row -> row.sortedBy { boxes[it].cx } }
}

private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float =
    hypot((ax - bx).toDouble(), (ay - by).toDouble()).toFloat()
