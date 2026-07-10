package com.example.aitoui.taketablets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.DailyScheduleItem
import com.example.aitoui.data.DailyScheduleRepository
import com.example.aitoui.data.Medication
import com.example.aitoui.data.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A single row in the daily-schedule list. */
data class TabletEntry(
    val id: Long,
    val medicationId: Long,
    val brand: String,
    val number: String,
) {
    /** Display label, e.g. "Panadol × 0.5". */
    val label: String get() = "$brand × $number"
}

/** Screen state for the Daily Schedule screen. */
data class TakeTabletsState(
    val medications: List<Medication> = emptyList(),
    val selectedMedicationId: Long? = null,
    val numberOfTablets: String = "",
    val tabletsTaken: List<TabletEntry> = emptyList(),
    val selectedId: Long? = null,
) {
    val selectedMedicationName: String
        get() = medications.firstOrNull { it.id == selectedMedicationId }?.brandName ?: ""

    val canAdd: Boolean
        get() = selectedMedicationId != null && (numberOfTablets.toDoubleOrNull() ?: 0.0) > 0.0
    val canDelete: Boolean get() = selectedId != null
}

/** User intents emitted by the Daily Schedule screen. */
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
    private val dailyScheduleRepository: DailyScheduleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TakeTabletsState())
    val state: StateFlow<TakeTabletsState> = _state.asStateFlow()

    private var nextId = 0L

    init {
        // Initialise the list from the persisted daily schedule.
        viewModelScope.launch {
            val saved = dailyScheduleRepository.getAll()
            _state.update { current ->
                current.copy(
                    tabletsTaken = saved.map { item ->
                        TabletEntry(
                            id = nextId++,
                            medicationId = item.medicationId,
                            brand = item.brandName,
                            number = item.quantity.formatQuantity(),
                        )
                    },
                )
            }
        }

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
                _state.update { it.copy(numberOfTablets = action.value.decimalOnly()) }

            TakeTabletsAction.Add -> _state.update { current ->
                if (!current.canAdd) return@update current
                val medication = current.medications.firstOrNull { it.id == current.selectedMedicationId }
                    ?: return@update current
                val entry = TabletEntry(
                    id = nextId++,
                    medicationId = medication.id,
                    brand = medication.brandName,
                    number = current.numberOfTablets,
                )
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

            TakeTabletsAction.Save -> save()
        }
    }

    /** Persists the current list to the daily_schedule table (replacing its contents). */
    private fun save() {
        val items = _state.value.tabletsTaken.mapNotNull { entry ->
            val quantity = entry.number.toDoubleOrNull() ?: return@mapNotNull null
            DailyScheduleItem(medicationId = entry.medicationId, quantity = quantity)
        }
        viewModelScope.launch { dailyScheduleRepository.save(items) }
    }

    /** Keeps digits and a single decimal point, e.g. so "0.5" is accepted but "0.5.2" is not. */
    private fun String.decimalOnly(): String {
        val filtered = filter { it.isDigit() || it == '.' }
        val firstDot = filtered.indexOf('.')
        return if (firstDot == -1) filtered
        else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
    }

    /** Drops a trailing ".0" so whole quantities show as "2" rather than "2.0". */
    private fun Double.formatQuantity(): String =
        if (this == toLong().toDouble()) toLong().toString() else toString()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                TakeTabletsViewModel(app.medicationRepository, app.dailyScheduleRepository)
            }
        }
    }
}
