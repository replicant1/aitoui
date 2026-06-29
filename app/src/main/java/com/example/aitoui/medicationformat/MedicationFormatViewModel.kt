package com.example.aitoui.medicationformat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.Medication
import com.example.aitoui.data.MedicationFormat
import com.example.aitoui.data.MedicationFormatRepository
import com.example.aitoui.data.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form state for the Medication Format entry screen. */
data class MedicationFormatState(
    val medications: List<Medication> = emptyList(),
    val selectedMedicationId: Long? = null,
    val dosePerTablet: String = "",
    val tabletsPerBox: String = "",
) {
    val selectedMedicationName: String
        get() = medications.firstOrNull { it.id == selectedMedicationId }?.brandName ?: ""

    val canSave: Boolean
        get() = selectedMedicationId != null && dosePerTablet.isNotBlank() && tabletsPerBox.isNotBlank()
}

sealed interface MedicationFormatAction {
    data class MedicationSelected(val id: Long) : MedicationFormatAction
    data class DosePerTabletChanged(val value: String) : MedicationFormatAction
    data class TabletsPerBoxChanged(val value: String) : MedicationFormatAction
    data object Save : MedicationFormatAction
}

class MedicationFormatViewModel(
    private val formatRepository: MedicationFormatRepository,
    medicationRepository: MedicationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MedicationFormatState())
    val state: StateFlow<MedicationFormatState> = _state.asStateFlow()

    init {
        // Keep the Medication dropdown's options in sync with the medications table.
        medicationRepository.medications
            .onEach { meds ->
                _state.update { current ->
                    val stillExists = meds.any { it.id == current.selectedMedicationId }
                    current.copy(
                        medications = meds,
                        selectedMedicationId = current.selectedMedicationId.takeIf { stillExists },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: MedicationFormatAction) {
        when (action) {
            is MedicationFormatAction.MedicationSelected ->
                _state.update { it.copy(selectedMedicationId = action.id) }

            is MedicationFormatAction.DosePerTabletChanged ->
                _state.update { it.copy(dosePerTablet = action.value.digitsOnly()) }

            is MedicationFormatAction.TabletsPerBoxChanged ->
                _state.update { it.copy(tabletsPerBox = action.value.digitsOnly()) }

            MedicationFormatAction.Save -> save()
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            formatRepository.add(
                MedicationFormat(
                    medicationId = current.selectedMedicationId!!,
                    dosePerTablet = current.dosePerTablet,
                    tabletsPerBox = current.tabletsPerBox,
                )
            )
        }
        // Clear the form for the next entry (keep the loaded medications).
        _state.update { MedicationFormatState(medications = it.medications) }
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                MedicationFormatViewModel(app.medicationFormatRepository, app.medicationRepository)
            }
        }
    }
}
