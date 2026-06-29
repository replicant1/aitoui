package com.example.aitoui.taketablets

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

/** A single row in the "Tablets Taken" list. */
data class TabletEntry(
    val id: Long,
    val brand: String,
    val number: String,
) {
    /** Display label, e.g. "Panadol × 2". */
    val label: String get() = "$brand × $number"
}

/** Screen state for "Take Tablets". */
data class TakeTabletsState(
    val medications: List<Medication> = emptyList(),
    val selectedMedicationId: Long? = null,
    val numberOfTablets: String = "",
    val tabletsTaken: List<TabletEntry> = emptyList(),
    val selectedId: Long? = null,
) {
    val selectedMedicationName: String
        get() = medications.firstOrNull { it.id == selectedMedicationId }?.brandName ?: ""

    val canAdd: Boolean get() = selectedMedicationId != null && numberOfTablets.isNotBlank()
    val canDelete: Boolean get() = selectedId != null
}

/** User intents emitted by the "Take Tablets" screen. */
sealed interface TakeTabletsAction {
    data class MedicationSelected(val id: Long) : TakeTabletsAction
    data class NumberOfTabletsChanged(val value: String) : TakeTabletsAction
    data object Add : TakeTabletsAction
    data class RowSelected(val id: Long) : TakeTabletsAction
    data object Delete : TakeTabletsAction
    data object Save : TakeTabletsAction
}

class TakeTabletsViewModel(
    medicationRepository: MedicationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TakeTabletsState())
    val state: StateFlow<TakeTabletsState> = _state.asStateFlow()

    private var nextId = 0L

    init {
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

    fun onAction(action: TakeTabletsAction) {
        when (action) {
            is TakeTabletsAction.MedicationSelected ->
                _state.update { it.copy(selectedMedicationId = action.id) }

            is TakeTabletsAction.NumberOfTabletsChanged ->
                _state.update { it.copy(numberOfTablets = action.value.digitsOnly()) }

            TakeTabletsAction.Add -> _state.update { current ->
                if (!current.canAdd) return@update current
                val brand = current.medications.firstOrNull { it.id == current.selectedMedicationId }
                    ?.brandName ?: return@update current
                val entry = TabletEntry(id = nextId++, brand = brand, number = current.numberOfTablets)
                // Append the new row and clear the inputs.
                current.copy(
                    selectedMedicationId = null,
                    numberOfTablets = "",
                    tabletsTaken = current.tabletsTaken + entry,
                )
            }

            is TakeTabletsAction.RowSelected ->
                _state.update { it.copy(selectedId = action.id) }

            TakeTabletsAction.Delete -> _state.update { current ->
                val selected = current.selectedId ?: return@update current
                current.copy(
                    tabletsTaken = current.tabletsTaken.filterNot { it.id == selected },
                    selectedId = null,
                )
            }

            TakeTabletsAction.Save -> {
                // TODO: persist the tablets-taken list once a data layer exists. Stub for now.
            }
        }
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                TakeTabletsViewModel(app.medicationRepository)
            }
        }
    }
}
