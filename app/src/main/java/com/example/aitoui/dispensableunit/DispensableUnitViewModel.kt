package com.example.aitoui.dispensableunit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.Medication
import com.example.aitoui.data.DispensableUnit
import com.example.aitoui.data.DispensableUnitRepository
import com.example.aitoui.data.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form state for the Dispensable Unit entry screen. */
data class DispensableUnitState(
    val medications: List<Medication> = emptyList(),
    val selectedMedicationId: Long? = null,
    val dosePerTablet: String = "",
    val tabletsPerUnit: String = "",
) {
    val selectedMedicationName: String
        get() = medications.firstOrNull { it.id == selectedMedicationId }?.brandName ?: ""

    val canSave: Boolean
        get() = selectedMedicationId != null && dosePerTablet.isNotBlank() && tabletsPerUnit.isNotBlank()

    /** True once the user has picked a medication or typed into this blank entry form. */
    val hasUnsavedChanges: Boolean
        get() = selectedMedicationId != null || dosePerTablet.isNotBlank() || tabletsPerUnit.isNotBlank()
}

sealed interface DispensableUnitAction {
    data class MedicationSelected(val id: Long) : DispensableUnitAction
    data class DosePerTabletChanged(val value: String) : DispensableUnitAction
    data class TabletsPerUnitChanged(val value: String) : DispensableUnitAction
    data object Save : DispensableUnitAction
}

class DispensableUnitViewModel(
    private val formatRepository: DispensableUnitRepository,
    medicationRepository: MedicationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DispensableUnitState())
    val state: StateFlow<DispensableUnitState> = _state.asStateFlow()

    /** One-shot flag: flips true once a dispensable unit is saved, so the screen can pop back to the list. */
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun consumeSaved() { _saved.value = false }

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

    fun onAction(action: DispensableUnitAction) {
        when (action) {
            is DispensableUnitAction.MedicationSelected ->
                _state.update { it.copy(selectedMedicationId = action.id) }

            is DispensableUnitAction.DosePerTabletChanged ->
                _state.update { it.copy(dosePerTablet = action.value.digitsOnly()) }

            is DispensableUnitAction.TabletsPerUnitChanged ->
                _state.update { it.copy(tabletsPerUnit = action.value.digitsOnly()) }

            DispensableUnitAction.Save -> save()
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            formatRepository.add(
                DispensableUnit(
                    medicationId = current.selectedMedicationId!!,
                    dosePerTablet = current.dosePerTablet,
                    tabletsPerUnit = current.tabletsPerUnit,
                )
            )
            // Clear the form for the next entry (keep the loaded medications).
            _state.update { DispensableUnitState(medications = it.medications) }
            _saved.value = true                // signal the screen to return to the list
        }
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                DispensableUnitViewModel(app.dispensableUnitRepository, app.medicationRepository)
            }
        }
    }
}
