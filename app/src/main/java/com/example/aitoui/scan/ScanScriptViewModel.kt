package com.example.aitoui.scan

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Progress state while a captured form is being read. */
data class ScanState(
    val busy: Boolean = false,
    val error: String? = null,
)

class ScanScriptViewModel(
    private val app: Application,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** One-shot: the parsed fields once a scan succeeds; cleared by the screen after it navigates on. */
    private val _result = MutableStateFlow<ParsedScript?>(null)
    val result: StateFlow<ParsedScript?> = _result.asStateFlow()

    /** Runs OCR on the captured [file] and parses the PB038 fields. */
    fun scan(file: File) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val parsed = PbsScriptParser.parse(recognise(file))
                    if (parsed.serialNo2 != null) {
                        parsed
                    } else {
                        // OCR missed the eRx label — decode the form's barcodes/QR (the same [file], still
                        // present) and take the token from there. Best-effort: a decode failure must not
                        // turn an otherwise-good OCR parse into an error, so it's caught separately.
                        val token = runCatching {
                            PbsScriptParser.erxFromBarcodes(recogniseBarcodes(file))
                        }.getOrNull()
                        if (token != null) parsed.copy(serialNo2 = token) else parsed
                    }
                }
            }
            file.delete()
            outcome.fold(
                onSuccess = { parsed ->
                    _state.update { it.copy(busy = false) }
                    _result.value = parsed
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

    /** Decodes the CODE-128 barcodes and QR code on the PB038 into their raw strings. */
    private fun recogniseBarcodes(file: File): List<String> {
        val image = InputImage.fromFilePath(app, Uri.fromFile(file))
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        return Tasks.await(scanner.process(image)).mapNotNull { it.rawValue }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                ScanScriptViewModel(app)
            }
        }
    }
}
