package com.example.aitoui.inhand.count

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.counting.CountImage
import com.example.aitoui.counting.CountPoint
import com.example.aitoui.counting.PeakField
import com.example.aitoui.counting.PeakTabletCounter
import com.example.aitoui.counting.PixelRect
import com.example.aitoui.counting.clampedTo
import com.example.aitoui.counting.cropped
import com.example.aitoui.counting.editMarkers
import com.example.aitoui.counting.eraseMarkersNear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Steps of the review: tune the auto-detection (optionally cropping to it), then hand-correct the markers. */
enum class CountPhase { DETECT, CROP, EDIT }

/**
 * @property capturePath absolute path of the captured JPEG being reviewed, or null while in live preview.
 *   Held here (rather than the decoded Bitmap in composition) so the review survives configuration changes
 *   such as rotation; the composable re-decodes the file for display.
 * @property sensitivity detection slider in 0..1; higher raises an *absolute* peak-height floor (in image
 *   pixels), so small false peaks — glare speckle, woven-cloth texture — drop out and the count falls, even
 *   when they dominate the median. See [CountTabletsViewModel.absoluteFloorFor].
 * @property markers detected/edited tablet centres in image-pixel coordinates; the count is [count].
 */
@Stable
data class CountTabletsState(
    val capturePath: String? = null,
    val analysing: Boolean = false,
    val phase: CountPhase = CountPhase.DETECT,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val sensitivity: Float = DEFAULT_SENSITIVITY,
    val markers: List<CountPoint> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** The applied crop in full-image pixels, or null for the whole frame. Detection runs within it. */
    val cropRect: PixelRect? = null,
    /** In the EDIT phase: true = the eraser tool is active (drag wipes markers); false = tap add/remove. */
    val erasing: Boolean = false,
) {
    /** true = reviewing a captured frame; false = live camera preview. */
    val captured: Boolean get() = capturePath != null
    val count: Int get() = markers.size

    companion object {
        /** Default slider position: a ~2px absolute floor, matching the counter's original behaviour. */
        const val DEFAULT_SENSITIVITY = 0.1f
    }
}

/**
 * Drives the tablet-counting camera screen. It runs the [PeakTabletCounter] in two stages: the expensive
 * [PeakTabletCounter.analyse] once per capture, then [PeakField.select] each time the sensitivity slider
 * moves — cheap, so the count re-tunes live. The user then hand-corrects the markers; the confirmed count is
 * `markers.size`.
 *
 * The captured Bitmap itself lives in the composable (transient camera state); this ViewModel owns only the
 * count-relevant, testable state.
 */
class CountTabletsViewModel(
    private val counter: PeakTabletCounter = PeakTabletCounter(),
) : ViewModel() {

    private val _state = MutableStateFlow(CountTabletsState())
    val state: StateFlow<CountTabletsState> = _state.asStateFlow()

    /** Cached distance-transform peaks for the current (possibly cropped) region, so the slider re-selects cheaply. */
    private var peaks: PeakField? = null

    /** The full captured image, retained so a crop can re-run detection on a sub-region. */
    private var original: CountImage? = null

    /** Undo/redo over the marker set during hand-correction. */
    private val history = EditHistory<List<CountPoint>>()

    /** Whether the current erase drag has already snapshotted its pre-stroke markers (one undo step / stroke). */
    private var eraseStrokeRecorded = false

    /**
     * Enter review of the frame saved at [path] and analyse its pixels ([image]), placing a marker per
     * detected tablet at the current sensitivity. The path is retained so the review survives config changes.
     */
    fun analyse(path: String, image: CountImage) {
        original = image
        _state.update {
            it.copy(
                capturePath = path, analysing = true, phase = CountPhase.DETECT, cropRect = null,
                imageWidth = image.width, imageHeight = image.height,
            )
        }
        history.clear()
        viewModelScope.launch {
            val field = withContext(Dispatchers.Default) { counter.analyse(image) }
            peaks = field
            _state.update {
                it.copy(analysing = false, markers = selectAt(field, it.sensitivity, null), canUndo = false, canRedo = false)
            }
        }
    }

    /** Re-tune the detection: raise/lower the peak-height floor and re-select markers (cheap; no re-analyse). */
    fun setSensitivity(value: Float) {
        val v = value.coerceIn(0f, 1f)
        val field = peaks ?: run { _state.update { it.copy(sensitivity = v) }; return }
        _state.update { it.copy(sensitivity = v, markers = selectAt(field, v, it.cropRect)) }
    }

    /** Enter the crop step: draw a rectangle over the full frame to restrict detection to it. */
    fun beginCrop() {
        if (original != null) _state.update { it.copy(phase = CountPhase.CROP) }
    }

    /** Leave the crop step without changing anything. */
    fun cancelCrop() {
        _state.update { it.copy(phase = CountPhase.DETECT) }
    }

    /**
     * Apply [rect] (in full-image pixels): re-run detection on just that region — recomputing the threshold
     * locally, which is what lets tablets be counted on a busy or textured surface — then offset the markers
     * back into full-image coordinates. Returns to the detection step so the slider can re-tune.
     */
    fun applyCrop(rect: PixelRect) {
        val src = original ?: return
        val clamped = rect.clampedTo(src.width, src.height)
        _state.update { it.copy(phase = CountPhase.DETECT, analysing = true, cropRect = clamped) }
        history.clear()
        viewModelScope.launch {
            val field = withContext(Dispatchers.Default) { counter.analyse(src.cropped(clamped)) }
            peaks = field
            _state.update {
                it.copy(analysing = false, markers = selectAt(field, it.sensitivity, clamped), canUndo = false, canRedo = false)
            }
        }
    }

    /** Markers from [field] at [sensitivity]'s absolute floor, offset into full-image coords by [crop]. */
    private fun selectAt(field: PeakField, sensitivity: Float, crop: PixelRect?): List<CountPoint> {
        val pts = field.select(absoluteFloorPx = absoluteFloorFor(sensitivity))
        return if (crop == null) pts else pts.map { CountPoint(it.x + crop.left, it.y + crop.top) }
    }

    /** Accept the detected count and move on to hand-correction, starting a fresh undo history. */
    fun confirmDetection() {
        history.clear()
        _state.update { it.copy(phase = CountPhase.EDIT, erasing = false, canUndo = false, canRedo = false) }
    }

    /** Toggle the eraser tool on/off (EDIT phase). */
    fun toggleErase() {
        _state.update { it.copy(erasing = !it.erasing) }
    }

    /** Begin an erase drag: the next removal in this stroke snapshots the pre-stroke markers for undo. */
    fun beginEraseStroke() {
        eraseStrokeRecorded = false
    }

    /** Erase markers within [radius] image-pixels of ([x], [y]); the whole drag stroke is one undo step. */
    fun eraseAt(x: Float, y: Float, radius: Float) {
        val cur = _state.value
        val after = eraseMarkersNear(cur.markers, x, y, radius)
        if (after === cur.markers) return
        if (!eraseStrokeRecorded) {
            history.record(cur.markers)
            eraseStrokeRecorded = true
        }
        _state.value = cur.copy(markers = after, canUndo = true, canRedo = history.canRedo)
    }

    /** A tap at image-pixel ([x], [y]) removes the nearest marker within the hit radius, else adds one. */
    fun onTapAt(x: Float, y: Float) {
        val cur = _state.value
        val edited = editMarkers(cur.markers, cur.imageWidth, cur.imageHeight, x, y)
        if (edited === cur.markers) return
        history.record(cur.markers)
        _state.value = cur.copy(markers = edited, canUndo = true, canRedo = history.canRedo)
    }

    /** Revert the last edit. */
    fun undo() {
        val cur = _state.value
        val prev = history.undo(cur.markers) ?: return
        _state.value = cur.copy(markers = prev, canUndo = history.canUndo, canRedo = history.canRedo)
    }

    /** Re-apply the last undone edit. */
    fun redo() {
        val cur = _state.value
        val next = history.redo(cur.markers) ?: return
        _state.value = cur.copy(markers = next, canUndo = history.canUndo, canRedo = history.canRedo)
    }

    /** Discard the capture (deleting its file) and return to the live preview. */
    fun retake() {
        deleteCaptureFile()
        peaks = null
        original = null
        history.clear()
        _state.value = CountTabletsState()
    }

    override fun onCleared() {
        deleteCaptureFile()
        super.onCleared()
    }

    private fun deleteCaptureFile() {
        _state.value.capturePath?.let { File(it).delete() }
    }

    companion object {
        /**
         * Maps the slider (0..1) to an absolute peak-height floor in image pixels: higher slider ⇒ taller
         * floor ⇒ fewer markers. Linear from 0 to [MAX_FLOOR_PX], so the default (0.1) ≈ 2px — the counter's
         * original noise floor — and the top end drops anything shorter than a real tablet's distance peak.
         */
        internal fun absoluteFloorFor(sensitivity: Float): Double =
            (sensitivity.coerceIn(0f, 1f) * MAX_FLOOR_PX).toDouble()

        private const val MAX_FLOOR_PX = 20f

        val Factory = viewModelFactory {
            initializer { CountTabletsViewModel() }
        }
    }
}
