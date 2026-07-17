package com.example.aitoui.inhand.blister

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.counting.CellRef
import com.example.aitoui.counting.CountImage
import com.example.aitoui.counting.FrameBox
import com.example.aitoui.counting.PackRegion
import com.example.aitoui.counting.centeredFrame
import com.example.aitoui.counting.movedBy
import com.example.aitoui.counting.resizedByCorner
import com.example.aitoui.counting.segmentPacks
import com.example.aitoui.counting.tapToCell
import com.example.aitoui.counting.toFrameBox
import com.example.aitoui.counting.toPackRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Steps of the blister-counting flow: capture a photo, hand-frame the packs, set the one shared grid format,
 * pop the empties (per pack), then total.
 */
enum class BlisterPhase { CAPTURE, FRAME, FORMAT, POP, SUMMARY }

/** Outcome of a correction tap, so the screen can play the right feedback. */
enum class PopResult { POPPED, UNPOPPED, NONE }

/**
 * One pack being counted: its squared-up [region] and which blisters have been popped (emptied). The grid
 * (rows × columns) is shared across all packs, so it lives on [BlisterCountState], not here. Every blister
 * starts full, so [popped] starts empty.
 */
@Stable
data class PackState(
    val region: PackRegion,
    val popped: Set<CellRef> = emptySet(),
)

/**
 * @property frames the editable rectangles during the FRAME step (one per pack); converted to [packs] on
 *   confirmation.
 * @property cols @property rows the single grid format shared by every pack (all packs in a photo are
 *   assumed to be the same format, as they're already assumed to be the same medication).
 * @property capturePath retained frame path so the review survives configuration changes; the screen
 *   re-decodes it for display.
 */
@Stable
data class BlisterCountState(
    val phase: BlisterPhase = BlisterPhase.CAPTURE,
    val capturePath: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val analysing: Boolean = false,
    val frames: List<FrameBox> = emptyList(),
    val selectedFrame: Int? = null,
    val cols: Int = 2,
    val rows: Int = 5,
    val packs: List<PackState> = emptyList(),
    val currentPackIndex: Int = 0,
) {
    /** More blisters run down the pack's long axis; fewer across the short axis. */
    val alongLong: Int get() = maxOf(cols, rows)
    val alongShort: Int get() = minOf(cols, rows)
    val blisterCount: Int get() = cols * rows
    fun fullCountOf(pack: PackState): Int = blisterCount - pack.popped.size

    val currentPack: PackState? get() = packs.getOrNull(currentPackIndex)
    val isLastPack: Boolean get() = currentPackIndex >= packs.lastIndex
    /** Tablets remaining = full blisters summed across every pack. */
    val total: Int get() = packs.sumOf { blisterCount - it.popped.size }
}

/**
 * Drives the blister-counting screen. Detection ([segmentPacks]) only *seeds* the frames; the user then
 * frames each pack by hand, sets one shared grid, and pops the empties. All transitions here are synchronous
 * and pure except [analyse], which segments off the main thread.
 */
class BlisterCountViewModel : ViewModel() {

    private val _state = MutableStateFlow(BlisterCountState())
    val state: StateFlow<BlisterCountState> = _state.asStateFlow()

    /** A frame was captured at [path] and decoded to [image]: segment packs to seed the framing step. */
    fun analyse(path: String, image: CountImage) {
        _state.update {
            it.copy(capturePath = path, imageWidth = image.width, imageHeight = image.height, analysing = true)
        }
        viewModelScope.launch {
            val regions = withContext(Dispatchers.Default) { segmentPacks(image) }
            onFramesSeeded(regions)
        }
    }

    /** Enter the framing step with one editable [FrameBox] per detected pack, or one default box if none. */
    internal fun onFramesSeeded(regions: List<PackRegion>) {
        _state.update { s ->
            val frames = if (regions.isEmpty()) {
                listOf(centeredFrame(s.imageWidth, s.imageHeight, s.imageWidth * 0.18f, s.imageHeight * 0.22f))
            } else {
                regions.map { it.toFrameBox() }
            }
            s.copy(
                analysing = false,
                frames = frames,
                selectedFrame = 0,
                packs = emptyList(),
                currentPackIndex = 0,
                phase = BlisterPhase.FRAME,
            )
        }
    }

    // --- Framing step ---

    fun selectFrame(index: Int?) {
        _state.update { s -> s.copy(selectedFrame = index?.takeIf { it in s.frames.indices }) }
    }

    fun moveSelectedFrame(dx: Float, dy: Float) =
        updateSelectedFrame { it.movedBy(dx, dy, _state.value.imageWidth, _state.value.imageHeight) }

    fun resizeSelectedFrame(cornerIndex: Int, x: Float, y: Float) =
        updateSelectedFrame { it.resizedByCorner(cornerIndex, x, y) }

    fun rotateSelectedFrame(angleRad: Float) = updateSelectedFrame { it.copy(angleRad = angleRad) }

    /** Add a new frame at the image centre, sized like the existing ones, and select it. */
    fun addFrame() {
        _state.update { s ->
            val box = if (s.frames.isEmpty()) {
                centeredFrame(s.imageWidth, s.imageHeight, s.imageWidth * 0.18f, s.imageHeight * 0.22f)
            } else {
                centeredFrame(s.imageWidth, s.imageHeight, median(s.frames.map { it.halfW }), median(s.frames.map { it.halfH }))
            }
            s.copy(frames = s.frames + box, selectedFrame = s.frames.size)
        }
    }

    fun deleteSelectedFrame() {
        _state.update { s ->
            val i = s.selectedFrame ?: return@update s
            val frames = s.frames.toMutableList().also { it.removeAt(i) }
            s.copy(frames = frames, selectedFrame = if (frames.isEmpty()) null else i.coerceAtMost(frames.lastIndex))
        }
    }

    /** Turn the framed rectangles into packs and move on to setting the shared format. */
    fun confirmFrames() {
        _state.update { s ->
            if (s.frames.isEmpty()) return@update s
            s.copy(
                packs = s.frames.map { PackState(region = it.toPackRegion()) },
                currentPackIndex = 0,
                phase = BlisterPhase.FORMAT,
            )
        }
    }

    // --- Shared format ---

    fun setColumns(cols: Int) = updateFormat(cols = cols)
    fun setRows(rows: Int) = updateFormat(rows = rows)

    /** Changing the shared grid clears every pack's pops (their addresses may no longer exist). */
    private fun updateFormat(cols: Int? = null, rows: Int? = null) {
        _state.update {
            it.copy(
                cols = (cols ?: it.cols).coerceIn(1, MAX_DIM),
                rows = (rows ?: it.rows).coerceIn(1, MAX_DIM),
                packs = it.packs.map { pack -> pack.copy(popped = emptySet()) },
            )
        }
    }

    /** Accept the shared grid and start popping the first pack's empties. */
    fun confirmFormat() {
        _state.update { it.copy(phase = BlisterPhase.POP, currentPackIndex = 0) }
    }

    // --- Popping ---

    /** Toggle the blister under image-pixel ([x], [y]); returns what happened so the screen can give feedback. */
    fun popAt(x: Float, y: Float): PopResult {
        val s = _state.value
        val pack = s.currentPack ?: return PopResult.NONE
        val cell = tapToCell(pack.region, s.alongLong, s.alongShort, x, y) ?: return PopResult.NONE
        return toggleCell(cell)
    }

    /** Toggle a specific blister [cell] (used by the accessible grid); returns what happened. */
    fun toggleCell(cell: CellRef): PopResult {
        val s = _state.value
        val pack = s.currentPack ?: return PopResult.NONE
        if (cell.along !in 0 until s.alongLong || cell.across !in 0 until s.alongShort) return PopResult.NONE
        val popping = cell !in pack.popped
        updateCurrentPack {
            it.copy(popped = if (popping) it.popped + cell else it.popped - cell)
        }
        return if (popping) PopResult.POPPED else PopResult.UNPOPPED
    }

    /** Restore every blister in the current pack to full. */
    fun resetCurrentPops() = updateCurrentPack { it.copy(popped = emptySet()) }

    /** Advance to the next pack (still popping), or to the summary after the last pack. */
    fun nextPack() {
        _state.update {
            if (it.isLastPack) it.copy(phase = BlisterPhase.SUMMARY)
            else it.copy(currentPackIndex = it.currentPackIndex + 1)
        }
    }

    /** Discard the capture (deleting its file) and return to the live preview. */
    fun retake() {
        deleteCaptureFile()
        _state.value = BlisterCountState()
    }

    override fun onCleared() {
        deleteCaptureFile()
        super.onCleared()
    }

    private fun updateSelectedFrame(transform: (FrameBox) -> FrameBox) {
        _state.update { s ->
            val i = s.selectedFrame ?: return@update s
            s.copy(frames = s.frames.mapIndexed { index, f -> if (index == i) transform(f) else f })
        }
    }

    private fun updateCurrentPack(transform: (PackState) -> PackState) {
        _state.update { s ->
            val i = s.currentPackIndex
            s.copy(packs = s.packs.mapIndexed { index, pack -> if (index == i) transform(pack) else pack })
        }
    }

    private fun deleteCaptureFile() {
        _state.value.capturePath?.let { File(it).delete() }
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    companion object {
        private const val MAX_DIM = 12
        val Factory = viewModelFactory { initializer { BlisterCountViewModel() } }
    }
}
