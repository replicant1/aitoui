package com.example.aitoui.inhand.blister

import com.example.aitoui.counting.CellRef
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

    private fun vmWith(vararg regions: PackRegion) = BlisterCountViewModel().apply { onPacksSegmented(regions.toList()) }

    @Test
    fun `no packs found stays on capture`() {
        val vm = BlisterCountViewModel().apply { onPacksSegmented(emptyList()) }
        assertEquals(BlisterPhase.CAPTURE, vm.state.value.phase)
        assertEquals(0, vm.state.value.packs.size)
    }

    @Test
    fun `packs found enter layout confirmation, all pockets full by default`() {
        val vm = vmWith(region())
        val s = vm.state.value
        assertEquals(BlisterPhase.CONFIRM_LAYOUT, s.phase)
        assertEquals(1, s.packs.size)
        assertEquals(2 to 5, s.currentPack!!.cols to s.currentPack!!.rows)
        assertEquals(10, s.currentPack!!.fullCount)
        assertEquals(10, s.total)
    }

    @Test
    fun `changing the layout updates the pocket count and clears pops`() {
        val vm = vmWith(region())
        vm.confirmLayout()
        vm.popAt(popPoint(vm, 0, 0).first, popPoint(vm, 0, 0).second)
        vm.setColumns(3) // 3x5 = 15, and pops reset
        assertEquals(15, vm.state.value.currentPack!!.pocketCount)
        assertEquals(15, vm.state.value.total)
        assertEquals(emptySet<CellRef>(), vm.state.value.currentPack!!.popped)
    }

    @Test
    fun `confirmLayout moves to popping`() {
        val vm = vmWith(region())
        vm.confirmLayout()
        assertEquals(BlisterPhase.POP, vm.state.value.phase)
    }

    @Test
    fun `popping a pocket empties it and un-popping refills it`() {
        val vm = vmWith(region())
        vm.confirmLayout()
        val (x, y) = popPoint(vm, 0, 0)

        assertEquals(PopResult.POPPED, vm.popAt(x, y))
        assertEquals(9, vm.state.value.currentPack!!.fullCount)
        assertEquals(setOf(CellRef(0, 0)), vm.state.value.currentPack!!.popped)

        assertEquals(PopResult.UNPOPPED, vm.popAt(x, y))
        assertEquals(10, vm.state.value.currentPack!!.fullCount)
    }

    @Test
    fun `a tap off the pack does nothing`() {
        val vm = vmWith(region())
        vm.confirmLayout()
        assertEquals(PopResult.NONE, vm.popAt(5000f, 5000f))
        assertEquals(10, vm.state.value.total)
    }

    @Test
    fun `reset restores every pocket`() {
        val vm = vmWith(region())
        vm.confirmLayout()
        vm.popAt(popPoint(vm, 1, 0).first, popPoint(vm, 1, 0).second)
        vm.popAt(popPoint(vm, 2, 1).first, popPoint(vm, 2, 1).second)
        assertEquals(8, vm.state.value.total)
        vm.resetCurrentPops()
        assertEquals(10, vm.state.value.total)
    }

    @Test
    fun `nextPack walks through each pack then summarises, totalling across packs`() {
        val vm = vmWith(region(cx = 100f), region(cx = 400f))
        vm.confirmLayout()
        vm.popAt(popPoint(vm, 0, 0).first, popPoint(vm, 0, 0).second) // pack 1: pop one -> 9

        vm.nextPack()
        assertEquals(BlisterPhase.CONFIRM_LAYOUT, vm.state.value.phase)
        assertEquals(1, vm.state.value.currentPackIndex)
        vm.confirmLayout()
        vm.popAt(popPoint(vm, 3, 1).first, popPoint(vm, 3, 1).second) // pack 2: pop one -> 9

        vm.nextPack()
        assertEquals(BlisterPhase.SUMMARY, vm.state.value.phase)
        assertEquals(18, vm.state.value.total) // 9 + 9
    }

    @Test
    fun `retake clears everything back to capture`() {
        val vm = vmWith(region())
        vm.retake()
        assertEquals(BlisterPhase.CAPTURE, vm.state.value.phase)
        assertEquals(0, vm.state.value.packs.size)
        assertNull(vm.state.value.capturePath)
    }

    /** Image-pixel coordinate of the centre of pocket ([along], [across]) on the current pack. */
    private fun popPoint(vm: BlisterCountViewModel, along: Int, across: Int): Pair<Float, Float> {
        val pack = vm.state.value.currentPack!!
        val p = cellCenter(pack.region, pack.alongLong, pack.alongShort, along, across)
        return p.x to p.y
    }
}
