package com.example.aitoui.script

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.MedicationFormat
import com.example.aitoui.data.MedicationFormatRepository
import com.example.aitoui.data.Script
import com.example.aitoui.data.ScriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form state for adding a Script. Numeric inputs are held as raw (digit-filtered) text. */
data class AddScriptState(
    val medicationFormats: List<MedicationFormat> = emptyList(),
    val selectedMedicationFormatId: Long? = null,
    val directions: String = "",
    val quantity: String = "",
    val repeats: String = "",
    val validToMillis: Long? = null,
) {
    val selectedMedicationFormatName: String
        get() = medicationFormats.firstOrNull { it.id == selectedMedicationFormatId }?.brandName ?: ""

    // Basic field validation.
    val medicationFormatValid: Boolean get() = selectedMedicationFormatId != null
    val directionsValid: Boolean get() = directions.isNotBlank()
    val quantityValid: Boolean get() = (quantity.toIntOrNull() ?: 0) > 0
    val repeatsValid: Boolean get() = repeats.toIntOrNull() != null
    val validToValid: Boolean get() = validToMillis != null

    val canSave: Boolean
        get() = medicationFormatValid && directionsValid && quantityValid && repeatsValid && validToValid
}

sealed interface AddScriptAction {
    data class MedicationFormatSelected(val id: Long) : AddScriptAction
    data class DirectionsChanged(val value: String) : AddScriptAction
    data class QuantityChanged(val value: String) : AddScriptAction
    data class RepeatsChanged(val value: String) : AddScriptAction
    data class ValidToChanged(val millis: Long?) : AddScriptAction
    data object Save : AddScriptAction
}

class AddScriptViewModel(
    private val scriptRepository: ScriptRepository,
    medicationFormatRepository: MedicationFormatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddScriptState())
    val state: StateFlow<AddScriptState> = _state.asStateFlow()

    init {
        // Keep the dropdown's options in sync with the Medication Format table.
        medicationFormatRepository.medicationFormats
            .onEach { types ->
                _state.update { current ->
                    val stillExists = types.any { it.id == current.selectedMedicationFormatId }
                    current.copy(
                        medicationFormats = types,
                        selectedMedicationFormatId = current.selectedMedicationFormatId.takeIf { stillExists },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: AddScriptAction) {
        when (action) {
            is AddScriptAction.MedicationFormatSelected ->
                _state.update { it.copy(selectedMedicationFormatId = action.id) }

            is AddScriptAction.DirectionsChanged ->
                _state.update { it.copy(directions = action.value) }

            is AddScriptAction.QuantityChanged ->
                _state.update { it.copy(quantity = action.value.digitsOnly()) }

            is AddScriptAction.RepeatsChanged ->
                _state.update { it.copy(repeats = action.value.digitsOnly()) }

            is AddScriptAction.ValidToChanged ->
                _state.update { it.copy(validToMillis = action.millis) }

            AddScriptAction.Save -> save()
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            scriptRepository.add(
                Script(
                    medicationFormatId = current.selectedMedicationFormatId!!,
                    directions = current.directions.trim(),
                    quantity = current.quantity.toInt(),
                    repeats = current.repeats.toInt(),
                    validToMillis = current.validToMillis!!,
                )
            )
        }
        // Clear the form for the next entry (keep the loaded medication formats).
        _state.update { AddScriptState(medicationFormats = it.medicationFormats) }
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                AddScriptViewModel(app.scriptRepository, app.medicationFormatRepository)
            }
        }
    }
}
