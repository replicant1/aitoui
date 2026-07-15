package com.example.aitoui.inhand.count

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.counting.CountImage
import com.example.aitoui.counting.CountPoint
import com.example.aitoui.counting.PeakTabletCounter
import com.example.aitoui.counting.TabletCounter
import com.example.aitoui.counting.editMarkers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * @property capturePath absolute path of the captured JPEG being reviewed, or null while in live preview.
 *   Held here (rather than the decoded Bitmap in composition) so the review survives configuration changes
 *   such as rotation; the composable re-decodes the file for display.
 * @property markers detected/edited tablet centres in image-pixel coordinates; the count is [count].
 */
@Stable
data class CountTabletsState(
    val capturePath: String? = null,
    val analysing: Boolean = false,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val markers: List<CountPoint> = emptyList(),
) {
    /** true = reviewing a captured frame; false = live camera preview. */
    val captured: Boolean get() = capturePath != null
    val count: Int get() = markers.size
}

/**
 * Drives the tablet-counting camera screen: runs the [TabletCounter] on a captured frame and holds the
 * editable set of tablet markers. The count the user confirms is `markers.size`.
 *
 * The captured Bitmap itself lives in the composable (transient camera state); this ViewModel owns only the
 * count-relevant, testable state.
 */
class CountTabletsViewModel(
    private val counter: TabletCounter = PeakTabletCounter(),
) : ViewModel() {

    private val _state = MutableStateFlow(CountTabletsState())
    val state: StateFlow<CountTabletsState> = _state.asStateFlow()

    /**
     * Enter review of the frame saved at [path] and run the counter on its pixels ([image]), placing a marker
     * per detected tablet. The path is retained so the review survives configuration changes.
     */
    fun analyse(path: String, image: CountImage) {
        _state.update {
            it.copy(capturePath = path, analysing = true, imageWidth = image.width, imageHeight = image.height)
        }
        viewModelScope.launch {
            val points = withContext(Dispatchers.Default) { counter.count(image) }
            _state.update { it.copy(analysing = false, markers = points) }
        }
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
        val Factory = viewModelFactory {
            initializer { CountTabletsViewModel() }
        }
    }
}
