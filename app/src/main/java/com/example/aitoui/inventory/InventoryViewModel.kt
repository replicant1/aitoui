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
import com.example.aitoui.data.inHandDaysElapsed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class InventoryState(
    val items: List<InventoryItem> = emptyList(),
)

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
            inHandRepository.gatheredDate,
        ) { formats, inHand, schedule, scripts, gatheredDate ->
            // A medication can have several schedule rows (e.g. AM + PM), so sum them per medication.
            val dailyByMedication = schedule
                .groupBy { it.medicationId }
                .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
            // A medication can have several in-hand rows (e.g. separate counts/stashes), so sum them per medication.
            val inHandByMedication = inHand
                .groupBy { it.medicationId }
                .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
            val supply = computeSupply(
                units = formats,
                scripts = scripts,
                dailyByMedication = dailyByMedication,
                inHandByMedication = inHandByMedication,
                daysSinceGathered = inHandDaysElapsed(gatheredDate, System.currentTimeMillis()),
            )
            formats.map { InventoryItem(it, supply[it.formatId]) }
        }
            .onEach { items -> _state.update { it.copy(items = items) } }
            .launchIn(viewModelScope)
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
