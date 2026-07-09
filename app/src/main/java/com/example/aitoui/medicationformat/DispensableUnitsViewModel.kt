package com.example.aitoui.medicationformat

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
import kotlinx.coroutines.launch

/** State for the Dispensable Units list — one [MedicationFormatDetails] per dispensable_units row. */
data class DispensableUnitsState(
    val units: List<MedicationFormatDetails> = emptyList(),
    /** Unit awaiting the user's confirmation to delete it, if any. */
    val pendingDeleteUnitId: Long? = null,
) {
    val pendingDeleteUnit: MedicationFormatDetails?
        get() = units.firstOrNull { it.formatId == pendingDeleteUnitId }
}

sealed interface DispensableUnitsAction {
    /** The user tapped the delete (cross) icon on a dispensable unit row. */
    data class DeleteTapped(val id: Long) : DispensableUnitsAction
    /** Confirm deleting the pending unit. */
    data object ConfirmDelete : DispensableUnitsAction
    /** Dismiss the delete confirmation without deleting. */
    data object CancelDelete : DispensableUnitsAction
}

class DispensableUnitsViewModel(
    private val repository: MedicationFormatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DispensableUnitsState())
    val state: StateFlow<DispensableUnitsState> = _state.asStateFlow()

    init {
        repository.formatsWithMedication
            .onEach { units ->
                // Alphabetical (case-insensitive) by brand name — independent of the source query.
                val sorted = units.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.brandName })
                _state.update { current ->
                    // Drop the pending delete if that unit no longer exists.
                    current.copy(
                        units = sorted,
                        pendingDeleteUnitId = current.pendingDeleteUnitId
                            ?.takeIf { id -> sorted.any { it.formatId == id } },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: DispensableUnitsAction) {
        when (action) {
            is DispensableUnitsAction.DeleteTapped ->
                _state.update { it.copy(pendingDeleteUnitId = action.id) }

            DispensableUnitsAction.CancelDelete ->
                _state.update { it.copy(pendingDeleteUnitId = null) }

            DispensableUnitsAction.ConfirmDelete -> deleteUnit()
        }
    }

    /** Deletes the pending unit (its scripts/dispensations cascade). The list updates reactively. */
    private fun deleteUnit() {
        val unit = _state.value.pendingDeleteUnit ?: return
        viewModelScope.launch {
            repository.deleteById(unit.formatId)
            _state.update { it.copy(pendingDeleteUnitId = null) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                DispensableUnitsViewModel(app.medicationFormatRepository)
            }
        }
    }
}
