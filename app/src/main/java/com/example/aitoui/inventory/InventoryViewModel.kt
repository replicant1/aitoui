package com.example.aitoui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.DailyScheduleRepository
import com.example.aitoui.data.DispensableUnitRepository
import com.example.aitoui.data.InHandRepository
import com.example.aitoui.data.ScriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class InventoryState(
    val items: List<InventoryItem> = emptyList(),
    val selectedId: Long? = null,
) {
    val selectedItem: InventoryItem? get() = items.firstOrNull { it.unit.formatId == selectedId }
}

sealed interface InventoryAction {
    data class FormatSelected(val id: Long) : InventoryAction
    data object SheetDismissed : InventoryAction
}

class InventoryViewModel(
    dispensableUnitRepository: DispensableUnitRepository,
    inHandRepository: InHandRepository,
    dailyScheduleRepository: DailyScheduleRepository,
    scriptRepository: ScriptRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InventoryState())
    val state: StateFlow<InventoryState> = _state.asStateFlow()

    init {
        combine(
            dispensableUnitRepository.formatsWithMedication,
            inHandRepository.inHand,
            dailyScheduleRepository.dailySchedule,
            scriptRepository.scriptsWithDetails,
        ) { formats, inHand, schedule, scripts ->
            // A medication can have several schedule rows (e.g. AM + PM), so sum them per medication.
            val dailyByMedication = schedule
                .groupBy { it.medicationId }
                .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
            // In-hand tablets are keyed by medication (one row per medication).
            val inHandByMedication = inHand.associate { it.medicationId to it.quantity }
            val supply = computeSupply(
                units = formats,
                scripts = scripts,
                dailyByMedication = dailyByMedication,
                inHandByMedication = inHandByMedication,
            )
            formats.map { InventoryItem(it, supply[it.formatId]) }
        }
            .onEach { items ->
                _state.update { current ->
                    // Drop selection if the selected item no longer exists.
                    val stillThere = items.any { it.unit.formatId == current.selectedId }
                    current.copy(
                        items = items,
                        selectedId = current.selectedId.takeIf { stillThere },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: InventoryAction) {
        when (action) {
            is InventoryAction.FormatSelected -> _state.update {
                // Re-tapping the selected row closes the sheet.
                it.copy(selectedId = if (it.selectedId == action.id) null else action.id)
            }

            InventoryAction.SheetDismissed -> _state.update { it.copy(selectedId = null) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                InventoryViewModel(
                    dispensableUnitRepository = app.dispensableUnitRepository,
                    inHandRepository = app.inHandRepository,
                    dailyScheduleRepository = app.dailyScheduleRepository,
                    scriptRepository = app.scriptRepository,
                )
            }
        }
    }
}
