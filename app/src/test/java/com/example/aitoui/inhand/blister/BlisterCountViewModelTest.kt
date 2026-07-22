package com.example.aitoui.inhand.blister

import com.example.aitoui.counting.CellRef
import com.example.aitoui.counting.GridAdjust
import com.example.aitoui.counting.PackRegion
import com.example.aitoui.counting.cellCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlisterCountViewModelTest {

    // A vertical 2x5 pack (long axis = +y), enough for cellCenter/tapToCell to round-trip.
    private fun region(cx: Float = 100f) = PackRegion(
        cx = cx, cy = 200f, longX = 0f, longY = 1f, shortX = 1f, shortY = 0f,
        longMin = -150f, longMax = 150f, shortMin = -40f, shortMax = 40f,
    )

    /** Seed frames from [regions], then confirm framing and the default format to reach the pop step. */
    private fun vmPopping(vararg regions: PackRegion) = BlisterCountViewModel().apply {
        onFramesSeeded(regions.toList())
        confirmFrames()
        confirmFormat()
    }

    @Test
    fun `no packs detected still lets you frame one manually`() {
        val vm = BlisterCountViewModel().apply { onFramesSeeded(emptyList()) }
        assertEquals(BlisterPhase.FRAME, vm.state.value.phase)
        assertEquals(1, vm.state.value.frames.size)
        assertEquals(0, vm.state.value.packs.size)
    }

    @Test
    fun `seeding enters the framing step with one frame per detected pack`() {
        val vm = BlisterCountViewModel().apply { onFramesSeeded(listOf(region(100f), region(400f))) }
        assertEquals(BlisterPhase.FRAME, vm.state.value.phase)
        assertEquals(2, vm.state.value.frames.size)
        assertEquals(0, vm.state.value.selectedFrame)
        assertEquals(0, vm.state.value.packs.size)
    }

    @Test
    fun `confirming the frames builds one pack each and moves to the shared format`() {
        val vm = BlisterCountViewModel().apply { onFramesSeeded(listOf(region())) }
        vm.confirmFrames()
        val s = vm.state.value
        assertEquals(BlisterPhase.FORMAT, s.phase)
        assertEquals(1, s.packs.size)
        assertEquals(2 to 5, s.cols to s.rows)
        assertEquals(10, s.fullCountOf(s.currentPack!!))
        assertEquals(10, s.total)
    }

    @Test
    fun `deleting the selected frame removes it`() {
        val vm = BlisterCountViewModel().apply { onFramesSeeded(listOf(region(100f), region(400f))) }
        vm.selectFrame(0)
        vm.deleteSelectedFrame()
        assertEquals(1, vm.state.value.frames.size)
    }

    @Test
    fun `add frame appends and selects a new frame`() {
        val vm = BlisterCountViewModel().apply { onFramesSeeded(listOf(region())) }
        vm.addFrame()
        assertEquals(2, vm.state.value.frames.size)
        assertEquals(1, vm.state.value.selectedFrame)
    }

    @Test
    fun `changing the shared format updates the blister count for every pack`() {
        val vm = BlisterCountViewModel().apply { onFramesSeeded(listOf(region())); confirmFrames() }
        vm.setColumns(3) // 3x5 = 15
        assertEquals(15, vm.state.value.blisterCount)
        vm.confirmFormat()
        assertEquals(15, vm.state.value.total)
    }

    @Test
    fun `confirmFormat moves to popping`() {
        val vm = BlisterCountViewModel().apply { onFramesSeeded(listOf(region())); confirmFrames() }
        vm.confirmFormat()
        assertEquals(BlisterPhase.POP, vm.state.value.phase)
    }

    @Test
    fun `stepBack returns false from capture so caller can leave the screen`() {
        val vm = BlisterCountViewModel()
        assertEquals(false, vm.stepBack())
        assertEquals(BlisterPhase.CAPTURE, vm.state.value.phase)
    }

    @Test
    fun `stepBack retraces the whole flow one step at a time`() {
        // Three packs, so stepping back through POP has somewhere to go other than straight out.
        val vm = vmPopping(region(100f), region(400f), region(700f))

        // Walk forward to the summary: POP(0) -> POP(1) -> POP(2) -> SUMMARY.
        repeat(3) { vm.nextPack() }
        assertEquals(BlisterPhase.SUMMARY, vm.state.value.phase)

        // SUMMARY -> POP, resuming at the pack the user was last on.
        assertEquals(true, vm.stepBack())
        assertEquals(BlisterPhase.POP, vm.state.value.phase)
        assertEquals(2, vm.state.value.currentPackIndex)

        // POP retreats a pack at a time rather than leaving the step.
        assertEquals(true, vm.stepBack())
        assertEquals(BlisterPhase.POP, vm.state.value.phase)
        assertEquals(1, vm.state.value.currentPackIndex)

        assertEquals(true, vm.stepBack())
        assertEquals(BlisterPhase.POP, vm.state.value.phase)
        assertEquals(0, vm.state.value.currentPackIndex)

        // Only from the first pack does it fall through to the format step.
        assertEquals(true, vm.stepBack())
        assertEquals(BlisterPhase.FORMAT, vm.state.value.phase)

        // FORMAT -> FRAME, then FRAME -> a fresh capture.
        assertEquals(true, vm.stepBack())
        assertEquals(BlisterPhase.FRAME, vm.state.value.phase)

        assertEquals(true, vm.stepBack())
        assertEquals(BlisterPhase.CAPTURE, vm.state.value.phase)

        // And from the capture there is nothing left to step back to.
        assertEquals(false, vm.stepBack())
    }

    @Test
    fun `stepping back to an earlier pack keeps the pops already made on it`() {
        val vm = vmPopping(region(100f), region(400f))
        val (x, y) = popPoint(vm, 0, 0)
        vm.popAt(x, y)
        assertEquals(setOf(CellRef(0, 0)), vm.state.value.packs[0].popped)

        vm.nextPack()
        assertEquals(1, vm.state.value.currentPackIndex)

        vm.stepBack()
        assertEquals(0, vm.state.value.currentPackIndex)
        assertEquals(setOf(CellRef(0, 0)), vm.state.value.currentPack!!.popped)
    }

    @Test
    fun `popping a blister empties it and un-popping refills it`() {
        val vm = vmPopping(region())
        val (x, y) = popPoint(vm, 0, 0)

        assertEquals(PopResult.POPPED, vm.popAt(x, y))
        assertEquals(9, vm.state.value.fullCountOf(vm.state.value.currentPack!!))
        assertEquals(setOf(CellRef(0, 0)), vm.state.value.currentPack!!.popped)

        assertEquals(PopResult.UNPOPPED, vm.popAt(x, y))
        assertEquals(10, vm.state.value.fullCountOf(vm.state.value.currentPack!!))
    }

    @Test
    fun `a tap off the pack does nothing`() {
        val vm = vmPopping(region())
        assertEquals(PopResult.NONE, vm.popAt(5000f, 5000f))
        assertEquals(10, vm.state.value.total)
    }

    @Test
    fun `reset restores every blister`() {
        val vm = vmPopping(region())
        vm.popAt(popPoint(vm, 1, 0).first, popPoint(vm, 1, 0).second)
        vm.popAt(popPoint(vm, 2, 1).first, popPoint(vm, 2, 1).second)
        assertEquals(8, vm.state.value.total)
        vm.resetCurrentPops()
        assertEquals(10, vm.state.value.total)
    }

    @Test
    fun `nextPack walks through each pack then summarises, totalling across packs`() {
        val vm = vmPopping(region(cx = 100f), region(cx = 400f))
        vm.popAt(popPoint(vm, 0, 0).first, popPoint(vm, 0, 0).second) // pack 1: pop one -> 9

        vm.nextPack()
        assertEquals(BlisterPhase.POP, vm.state.value.phase)
        assertEquals(1, vm.state.value.currentPackIndex)
        vm.popAt(popPoint(vm, 3, 1).first, popPoint(vm, 3, 1).second) // pack 2: pop one -> 9

        vm.nextPack()
        assertEquals(BlisterPhase.SUMMARY, vm.state.value.phase)
        assertEquals(18, vm.state.value.total) // 9 + 9
    }

    @Test
    fun `panning accumulates onto the current pack's grid, leaving other packs untouched`() {
        val vm = vmPopping(region(cx = 100f), region(cx = 400f))
        vm.panCurrentGrid(10f, -4f)
        vm.panCurrentGrid(5f, 1f)
        val adjust = vm.state.value.currentPack!!.adjust
        assertEquals(15f, adjust.dx, 1e-4f)
        assertEquals(-3f, adjust.dy, 1e-4f)
        assertEquals(GridAdjust.None, vm.state.value.packs[1].adjust) // second pack unchanged
    }

    @Test
    fun `scaling multiplies the current pack's spacing and clamps to bounds`() {
        val vm = vmPopping(region())
        vm.scaleCurrentGridSpacing(1.5f)
        assertEquals(1.5f, vm.state.value.currentPack!!.adjust.spacing, 1e-4f)
        // Pinch far past the ceiling — spacing saturates at MAX_SPACING rather than running away.
        repeat(10) { vm.scaleCurrentGridSpacing(2f) }
        assertEquals(GridAdjust.MAX_SPACING, vm.state.value.currentPack!!.adjust.spacing, 1e-4f)
    }

    @Test
    fun `changing the format resets any manual grid alignment`() {
        val vm = vmPopping(region())
        vm.panCurrentGrid(20f, 20f)
        vm.scaleCurrentGridSpacing(1.4f)
        vm.setRows(6) // re-formatting invalidates a nudge tuned for the old spacing
        assertEquals(GridAdjust.None, vm.state.value.packs[0].adjust)
    }

    @Test
    fun `retake clears everything back to capture`() {
        val vm = vmPopping(region())
        vm.retake()
        assertEquals(BlisterPhase.CAPTURE, vm.state.value.phase)
        assertEquals(0, vm.state.value.packs.size)
        assertEquals(0, vm.state.value.frames.size)
        assertNull(vm.state.value.capturePath)
    }

    /** Image-pixel coordinate of the centre of blister ([along], [across]) on the current pack. */
    private fun popPoint(vm: BlisterCountViewModel, along: Int, across: Int): Pair<Float, Float> {
        val s = vm.state.value
        val pack = s.currentPack!!
        val p = cellCenter(pack.region, s.alongLong, s.alongShort, along, across)
        return p.x to p.y
    }
}
