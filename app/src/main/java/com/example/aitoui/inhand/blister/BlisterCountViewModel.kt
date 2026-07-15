package com.example.aitoui.inhand.blister

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.counting.CellRef
import com.example.aitoui.counting.CountImage
import com.example.aitoui.counting.PackRegion
import com.example.aitoui.counting.segmentPacks
import com.example.aitoui.counting.tapToCell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Steps of the blister-counting flow. */
enum class BlisterPhase { CAPTURE, CONFIRM_LAYOUT, POP, SUMMARY }

/** Outcome of a correction tap, so the screen can play the right feedback. */
enum class PopResult { POPPED, UNPOPPED, NONE }

/**
 * One pack being counted: its squared-up [region], the user-confirmed layout, and which pockets have been
 * popped (emptied). Every pocket starts full, so [popped] starts empty and [fullCount] starts at the total.
 */
@Stable
data class PackState(
    val region: PackRegion,
    val cols: Int = 2,
    val rows: Int = 5,
    val popped: Set<CellRef> = emptySet(),
) {
    /** More pockets run down the pack's long axis; fewer across the short axis. */
    val alongLong: Int get() = maxOf(cols, rows)
    val alongShort: Int get() = minOf(cols, rows)
    val pocketCount: Int get() = cols * rows
    val fullCount: Int get() = pocketCount - popped.size
}

/**
 * @property capturePath retained frame path so the review survives configuration changes (as in the
 *   loose-tablet counter); the screen re-decodes it for display.
 */
@Stable
data class BlisterCountState(
    val phase: BlisterPhase = BlisterPhase.CAPTURE,
    val capturePath: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val analysing: Boolean = false,
    val packs: List<PackState> = emptyList(),
    val currentPackIndex: Int = 0,
) {
    val currentPack: PackState? get() = packs.getOrNull(currentPackIndex)
    val isLastPack: Boolean get() = currentPackIndex >= packs.lastIndex
    /** Tablets remaining = full pockets summed across every pack. */
    val total: Int get() = packs.sumOf { it.fullCount }
}

/**
 * Drives the blister-counting screen. The machine only does geometry ([segmentPacks]); every pocket defaults
 * to full and the user pops the empties. All state transitions here are synchronous and pure except
 * [analyse], which segments off the main thread.
 */
class BlisterCountViewModel : ViewModel() {

    private val _state = MutableStateFlow(BlisterCountState())
    val state: StateFlow<BlisterCountState> = _state.asStateFlow()

    /** A frame was captured at [path] and decoded to [image]: segment packs, then confirm the first layout. */
    fun analyse(path: String, image: CountImage) {
        _state.update {
            it.copy(capturePath = path, imageWidth = image.width, imageHeight = image.height, analysing = true)
        }
        viewModelScope.launch {
            val regions = withContext(Dispatchers.Default) { segmentPacks(image) }
            onPacksSegmented(regions)
        }
    }

    /** Enter layout confirmation with one [PackState] per detected pack (or back to capture if none found). */
    internal fun onPacksSegmented(regions: List<PackRegion>) {
        _state.update {
            it.copy(
                analysing = false,
                packs = regions.map { region -> PackState(region = region) },
                currentPackIndex = 0,
                phase = if (regions.isEmpty()) BlisterPhase.CAPTURE else BlisterPhase.CONFIRM_LAYOUT,
            )
        }
    }

    fun setColumns(cols: Int) = updateLayout(cols = cols)
    fun setRows(rows: Int) = updateLayout(rows = rows)

    /** Changing the grid discards any pops for this pack (their addresses may no longer exist). */
    private fun updateLayout(cols: Int? = null, rows: Int? = null) {
        updateCurrentPack {
            it.copy(
                cols = (cols ?: it.cols).coerceIn(1, MAX_DIM),
                rows = (rows ?: it.rows).coerceIn(1, MAX_DIM),
                popped = emptySet(),
            )
        }
    }

    /** Accept the current pack's grid and start popping its empties. */
    fun confirmLayout() {
        _state.update { it.copy(phase = BlisterPhase.POP) }
    }

    /** Toggle the pocket under image-pixel ([x], [y]); returns what happened so the screen can give feedback. */
    fun popAt(x: Float, y: Float): PopResult {
        val pack = _state.value.currentPack ?: return PopResult.NONE
        val cell = tapToCell(pack.region, pack.alongLong, pack.alongShort, x, y) ?: return PopResult.NONE
        val popping = cell !in pack.popped
        updateCurrentPack {
            it.copy(popped = if (popping) it.popped + cell else it.popped - cell)
        }
        return if (popping) PopResult.POPPED else PopResult.UNPOPPED
    }

    /** Restore every pocket in the current pack to full. */
    fun resetCurrentPops() = updateCurrentPack { it.copy(popped = emptySet()) }

    /** Advance to the next pack's layout confirmation, or to the summary after the last pack. */
    fun nextPack() {
        _state.update {
            if (it.isLastPack) it.copy(phase = BlisterPhase.SUMMARY)
            else it.copy(currentPackIndex = it.currentPackIndex + 1, phase = BlisterPhase.CONFIRM_LAYOUT)
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

    private fun updateCurrentPack(transform: (PackState) -> PackState) {
        _state.update { s ->
            val i = s.currentPackIndex
            s.copy(packs = s.packs.mapIndexed { index, pack -> if (index == i) transform(pack) else pack })
        }
    }

    private fun deleteCaptureFile() {
        _state.value.capturePath?.let { File(it).delete() }
    }

    companion object {
        private const val MAX_DIM = 12
        val Factory = viewModelFactory { initializer { BlisterCountViewModel() } }
    }
}
