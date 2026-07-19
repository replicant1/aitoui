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
import com.example.aitoui.counting.editMarkers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Steps of the review: tune the auto-detection, then hand-correct the markers. */
enum class CountPhase { DETECT, EDIT }

/**
 * @property capturePath absolute path of the captured JPEG being reviewed, or null while in live preview.
 *   Held here (rather than the decoded Bitmap in composition) so the review survives configuration changes
 *   such as rotation; the composable re-decodes the file for display.
 * @property sensitivity detection slider in 0..1; higher raises the peak-height floor, so faint false peaks
 *   (glare, clutter) drop out and the count falls. See [CountTabletsViewModel.minHeightFractionFor].
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
) {
    /** true = reviewing a captured frame; false = live camera preview. */
    val captured: Boolean get() = capturePath != null
    val count: Int get() = markers.size

    companion object {
        /** Default slider position, chosen so the initial count matches the counter's default floor (0.30). */
        const val DEFAULT_SENSITIVITY = 0.4f
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

    /** Cached distance-transform peaks for the current capture, so the slider can re-select without redoing it. */
    private var peaks: PeakField? = null

    /**
     * Enter review of the frame saved at [path] and analyse its pixels ([image]), placing a marker per
     * detected tablet at the current sensitivity. The path is retained so the review survives config changes.
     */
    fun analyse(path: String, image: CountImage) {
        _state.update {
            it.copy(
                capturePath = path, analysing = true, phase = CountPhase.DETECT,
                imageWidth = image.width, imageHeight = image.height,
            )
        }
        viewModelScope.launch {
            val field = withContext(Dispatchers.Default) { counter.analyse(image) }
            peaks = field
            _state.update {
                it.copy(analysing = false, markers = field.select(minHeightFractionFor(it.sensitivity)))
            }
        }
    }

    /** Re-tune the detection: raise/lower the peak-height floor and re-select markers (cheap; no re-analyse). */
    fun setSensitivity(value: Float) {
        val v = value.coerceIn(0f, 1f)
        val field = peaks ?: run { _state.update { it.copy(sensitivity = v) }; return }
        _state.update { it.copy(sensitivity = v, markers = field.select(minHeightFractionFor(v))) }
    }

    /** Accept the detected count and move on to hand-correction. */
    fun confirmDetection() {
        _state.update { it.copy(phase = CountPhase.EDIT) }
    }

    /** A tap at image-pixel ([x], [y]) removes the nearest marker within the hit radius, else adds one. */
    fun onTapAt(x: Float, y: Float) {
        _state.update {
            it.copy(markers = editMarkers(it.markers, it.imageWidth, it.imageHeight, x, y))
        }
    }

    /** Discard the capture (deleting its file) and return to the live preview. */
    fun retake() {
        deleteCaptureFile()
        peaks = null
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
        /** Maps the slider (0..1) to the counter's min-height fraction: higher slider ⇒ higher floor ⇒ fewer. */
        internal fun minHeightFractionFor(sensitivity: Float): Double =
            (MIN_FLOOR + sensitivity.coerceIn(0f, 1f) * (MAX_FLOOR - MIN_FLOOR)).toDouble()

        private const val MIN_FLOOR = 0.10f
        private const val MAX_FLOOR = 0.60f

        val Factory = viewModelFactory {
            initializer { CountTabletsViewModel() }
        }
    }
}
