package com.example.aitoui.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.Medication
import com.example.aitoui.data.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State for the Medications list screen — one [Medication] per row in the medications table. */
data class MedicationsState(
    val medications: List<Medication> = emptyList(),
    /** Medication awaiting the user's confirmation to delete it, if any. */
    val pendingDeleteMedicationId: Long? = null,
) {
    val pendingDeleteMedication: Medication?
        get() = medications.firstOrNull { it.id == pendingDeleteMedicationId }
}

sealed interface MedicationsAction {
    /** The user tapped the delete (cross) icon on a medication row. */
    data class DeleteTapped(val id: Long) : MedicationsAction
    /** Confirm deleting the pending medication. */
    data object ConfirmDelete : MedicationsAction
    /** Dismiss the delete confirmation without deleting. */
    data object CancelDelete : MedicationsAction
}

class MedicationsViewModel(
    private val repository: MedicationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MedicationsState())
    val state: StateFlow<MedicationsState> = _state.asStateFlow()

    init {
        repository.medications
            .onEach { medications ->
                _state.update { current ->
                    // Drop the pending delete if that medication no longer exists.
                    current.copy(
                        medications = medications,
                        pendingDeleteMedicationId = current.pendingDeleteMedicationId
                            ?.takeIf { id -> medications.any { it.id == id } },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: MedicationsAction) {
        when (action) {
            is MedicationsAction.DeleteTapped ->
                _state.update { it.copy(pendingDeleteMedicationId = action.id) }

            MedicationsAction.CancelDelete ->
                _state.update { it.copy(pendingDeleteMedicationId = null) }

            MedicationsAction.ConfirmDelete -> deleteMedication()
        }
    }

    /** Deletes the pending medication (its units/scripts/dispensations cascade). List updates reactively. */
    private fun deleteMedication() {
        val medication = _state.value.pendingDeleteMedication ?: return
        viewModelScope.launch {
            repository.deleteById(medication.id)
            _state.update { it.copy(pendingDeleteMedicationId = null) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                MedicationsViewModel(app.medicationRepository)
            }
        }
    }
}
