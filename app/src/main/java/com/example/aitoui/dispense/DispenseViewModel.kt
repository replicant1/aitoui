package com.example.aitoui.dispense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.MedicationFormatDetails
import com.example.aitoui.data.MedicationFormatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/** A single captured dispensation: a medication format and how many were dispensed. */
data class DispensationEntry(
    val id: Long,
    val formatId: Long,
    val formatLabel: String,
    val number: String,
) {
    /** Display label, e.g. "Panadol (500mg) × 3". */
    val label: String get() = "$formatLabel × $number"
}

/** Screen state for "Dispense" — capturing a pharmacy trip. */
data class DispenseState(
    val formats: List<MedicationFormatDetails> = emptyList(),
    val selectedFormatId: Long? = null,
    val number: String = "",
    val dispensations: List<DispensationEntry> = emptyList(),
    val selectedId: Long? = null,
) {
    val selectedFormatLabel: String
        get() = formats.firstOrNull { it.formatId == selectedFormatId }?.label ?: ""

    val canAdd: Boolean get() = selectedFormatId != null && number.isNotBlank()
    val canDelete: Boolean get() = selectedId != null
}

sealed interface DispenseAction {
    data class FormatSelected(val id: Long) : DispenseAction
    data class NumberChanged(val value: String) : DispenseAction
    data object Add : DispenseAction
    data class RowSelected(val id: Long) : DispenseAction
    data object Delete : DispenseAction
    data object Save : DispenseAction
}

class DispenseViewModel(
    formatRepository: MedicationFormatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DispenseState())
    val state: StateFlow<DispenseState> = _state.asStateFlow()

    private var nextId = 0L

    init {
        // Keep the Medication Format dropdown's options in sync.
        formatRepository.formatsWithMedication
            .onEach { formats ->
                _state.update { current ->
                    val stillExists = formats.any { it.formatId == current.selectedFormatId }
                    current.copy(
                        formats = formats,
                        selectedFormatId = current.selectedFormatId.takeIf { stillExists },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: DispenseAction) {
        when (action) {
            is DispenseAction.FormatSelected ->
                _state.update { it.copy(selectedFormatId = action.id) }

            is DispenseAction.NumberChanged ->
                _state.update { it.copy(number = action.value.digitsOnly()) }

            DispenseAction.Add -> _state.update { current ->
                if (!current.canAdd) return@update current
                val format = current.formats.firstOrNull { it.formatId == current.selectedFormatId }
                    ?: return@update current
                val entry = DispensationEntry(
                    id = nextId++,
                    formatId = format.formatId,
                    formatLabel = format.label,
                    number = current.number,
                )
                // Append the new dispensation and clear the inputs.
                current.copy(
                    selectedFormatId = null,
                    number = "",
                    dispensations = current.dispensations + entry,
                )
            }

            is DispenseAction.RowSelected ->
                _state.update { it.copy(selectedId = action.id) }

            DispenseAction.Delete -> _state.update { current ->
                val selected = current.selectedId ?: return@update current
                current.copy(
                    dispensations = current.dispensations.filterNot { it.id == selected },
                    selectedId = null,
                )
            }

            DispenseAction.Save -> save()
        }
    }

    private fun save() {
        // TODO: persist the captured dispensations to the database (mechanism to be specified).
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                DispenseViewModel(app.medicationFormatRepository)
            }
        }
    }
}
