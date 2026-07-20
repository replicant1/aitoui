package com.example.aitoui.dailyschedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.DailyScheduleItem
import com.example.aitoui.data.DailyScheduleRepository
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.DispensableUnitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A single row in the daily-schedule list: [number] tablets of a specific dispensable unit taken each day. */
data class DailyScheduleEntry(
    val id: Long,
    val dispensableUnitId: Long,
    val brand: String,
    val number: String,
) {
    /** Display label, e.g. "Panadol × 0.5". */
    val label: String get() = "$brand × $number"
}

/** Screen state for the Daily Schedule screen. */
data class DailyScheduleState(
    /** Dispensable units offered in the medication dropdown, each with its photo and dose. */
    val units: List<DispensableUnitDetails> = emptyList(),
    /** The chosen unit's formatId, if any. */
    val selectedUnitId: Long? = null,
    val numberOfTablets: String = "",
    val tabletsTaken: List<DailyScheduleEntry> = emptyList(),
    val selectedId: Long? = null,
    /** Sorted signature of the list as last persisted, to detect unsaved add/deletes. See [hasUnsavedChanges]. */
    val savedSignature: List<String> = emptyList(),
) {
    val selectedUnit: DispensableUnitDetails?
        get() = units.firstOrNull { it.formatId == selectedUnitId }

    /** Text shown in the closed dropdown, e.g. "Panadol (500mg)". */
    val selectedMedicationName: String
        get() = selectedUnit?.label ?: ""

    val canAdd: Boolean
        get() = selectedUnit != null && (numberOfTablets.toDoubleOrNull() ?: 0.0) > 0.0
    val canDelete: Boolean get() = selectedId != null

    /** True when rows have been added or removed since the list was last loaded/saved (staging fields ignored). */
    val hasUnsavedChanges: Boolean get() = scheduleSignature(tabletsTaken) != savedSignature
}

/** Order-independent signature of a saved list: one "dispensableUnitId:number" per row, sorted. */
internal fun scheduleSignature(rows: List<DailyScheduleEntry>): List<String> =
    rows.map { "${it.dispensableUnitId}:${it.number}" }.sorted()

/** User intents emitted by the Daily Schedule screen. */
sealed interface DailyScheduleAction {
    data class MedicationSelected(val formatId: Long) : DailyScheduleAction
    data class NumberOfTabletsChanged(val value: String) : DailyScheduleAction
    data object Add : DailyScheduleAction
    data class RowSelected(val id: Long) : DailyScheduleAction
    data object Delete : DailyScheduleAction
    data object Save : DailyScheduleAction
}

class DailyScheduleViewModel(
    dispensableUnitRepository: DispensableUnitRepository,
    private val dailyScheduleRepository: DailyScheduleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DailyScheduleState())
    val state: StateFlow<DailyScheduleState> = _state.asStateFlow()

    /** One-shot flag: flips true once the schedule is saved, so the screen can pop back to Main. */
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun consumeSaved() { _saved.value = false }

    private var nextId = 0L

    init {
        // Initialise the list from the persisted daily schedule.
        viewModelScope.launch {
            val saved = dailyScheduleRepository.getAll()
            _state.update { current ->
                val loaded = saved.map { item ->
                    DailyScheduleEntry(
                        id = nextId++,
                        dispensableUnitId = item.dispensableUnitId,
                        brand = item.brandName,
                        number = item.quantity.formatQuantity(),
                    )
                }
                current.copy(tabletsTaken = loaded, savedSignature = scheduleSignature(loaded))
            }
        }

        dispensableUnitRepository.formatsWithMedication
            .onEach { units ->
                _state.update { current ->
                    val stillExists = units.any { it.formatId == current.selectedUnitId }
                    current.copy(
                        units = units,
                        selectedUnitId = current.selectedUnitId.takeIf { stillExists },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: DailyScheduleAction) {
        when (action) {
            is DailyScheduleAction.MedicationSelected ->
                _state.update { it.copy(selectedUnitId = action.formatId) }

            is DailyScheduleAction.NumberOfTabletsChanged ->
                _state.update { it.copy(numberOfTablets = action.value.decimalOnly()) }

            DailyScheduleAction.Add -> _state.update { current ->
                if (!current.canAdd) return@update current
                val unit = current.selectedUnit ?: return@update current
                val entry = DailyScheduleEntry(
                    id = nextId++,
                    dispensableUnitId = unit.formatId,
                    brand = unit.brandName,
                    number = current.numberOfTablets,
                )
                // Append the new row and clear the inputs.
                current.copy(
                    selectedUnitId = null,
                    numberOfTablets = "",
                    tabletsTaken = current.tabletsTaken + entry,
                )
            }

            is DailyScheduleAction.RowSelected ->
                _state.update { it.copy(selectedId = action.id) }

            DailyScheduleAction.Delete -> _state.update { current ->
                val selected = current.selectedId ?: return@update current
                current.copy(
                    tabletsTaken = current.tabletsTaken.filterNot { it.id == selected },
                    selectedId = null,
                )
            }

            DailyScheduleAction.Save -> save()
        }
    }

    /** Persists the current list to the daily_schedule table (replacing its contents). */
    private fun save() {
        val items = _state.value.tabletsTaken.mapNotNull { entry ->
            val quantity = entry.number.toDoubleOrNull() ?: return@mapNotNull null
            DailyScheduleItem(dispensableUnitId = entry.dispensableUnitId, quantity = quantity)
        }
        viewModelScope.launch {
            dailyScheduleRepository.save(items)
            _saved.value = true              // signal the screen to return to Main
        }
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
                DailyScheduleViewModel(app.dispensableUnitRepository, app.dailyScheduleRepository)
            }
        }
    }
}
