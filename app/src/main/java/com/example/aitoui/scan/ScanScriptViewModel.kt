package com.example.aitoui.scan

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.DispensableUnitRepository
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Progress state while a captured form is being read. */
data class ScanState(
    val busy: Boolean = false,
    val error: String? = null,
)

/** The outcome of a scan: the parsed fields plus the id of the auto-matched dispensable unit, if any. */
data class ScanResult(
    val parsed: ParsedScript,
    val matchedFormatId: Long?,
)

class ScanScriptViewModel(
    private val app: Application,
    private val dispensableUnitRepository: DispensableUnitRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** One-shot: set when a scan succeeds, cleared by the screen once it has navigated on. */
    private val _result = MutableStateFlow<ScanResult?>(null)
    val result: StateFlow<ScanResult?> = _result.asStateFlow()

    /** Runs OCR on the captured [file], parses the PB038 fields, and matches the medication. */
    fun scan(file: File) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val outcome = runCatching {
                val lines = withContext(Dispatchers.IO) { recognise(file) }
                val parsed = PbsScriptParser.parse(lines)
                ScanResult(parsed, matchFormat(parsed))
            }
            file.delete()
            outcome.fold(
                onSuccess = { res ->
                    _state.update { it.copy(busy = false) }
                    _result.value = res
                },
                onFailure = { e ->
                    _state.update { it.copy(busy = false, error = "Couldn't read the form: ${e.message}") }
                },
            )
        }
    }

    fun consumeResult() { _result.value = null }

    fun dismissError() { _state.update { it.copy(error = null) } }

    private fun recognise(file: File): List<OcrLine> {
        val image = InputImage.fromFilePath(app, Uri.fromFile(file))
        val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = Tasks.await(recogniser.process(image))
        return text.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(line.text, box.left, box.top, box.right, box.bottom)
            }
    }

    /** Matches the parsed brand + dose + pack size to an existing dispensable unit, or null. */
    private suspend fun matchFormat(p: ParsedScript): Long? {
        val brand = p.brand ?: return null
        return dispensableUnitRepository.formatsWithMedication.first().firstOrNull { unit ->
            unit.brandName.equals(brand, ignoreCase = true) &&
                (p.dosePerTablet == null || unit.dosePerTablet == p.dosePerTablet) &&
                (p.tabletsPerUnit == null || unit.tabletsPerUnit == p.tabletsPerUnit)
        }?.formatId
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                ScanScriptViewModel(app, app.dispensableUnitRepository)
            }
        }
    }
}
