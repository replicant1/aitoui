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

/** Sort order for the inventory list. */
enum class SortOption(val label: String) {
    BrandName("Brand name"),
    TimeRemaining("Time remaining"),
}

data class InventoryState(
    val items: List<InventoryItem> = emptyList(),
    /** How [items] is ordered. */
    val sortOrder: SortOption = SortOption.TimeRemaining,
)

/** User intents from the Inventory screen. */
sealed interface InventoryAction {
    /** Change how the list is sorted. */
    data class SortOrderChanged(val order: SortOption) : InventoryAction
}

class InventoryViewModel(
    dispensableUnitRepository: DispensableUnitRepository,
    inHandRepository: InHandRepository,
    dailyScheduleRepository: DailyScheduleRepository,
    scriptRepository: ScriptRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InventoryState())
    val state: StateFlow<InventoryState> = _state.asStateFlow()

    /** The items as delivered by the repositories, before applying the chosen sort order. */
    private var rawItems: List<InventoryItem> = emptyList()

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
            .onEach { items ->
                rawItems = items
                _state.update { it.copy(items = sortItems(items, it.sortOrder)) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: InventoryAction) {
        when (action) {
            is InventoryAction.SortOrderChanged -> _state.update {
                it.copy(sortOrder = action.order, items = sortItems(rawItems, action.order))
            }
        }
    }

    private fun sortItems(items: List<InventoryItem>, order: SortOption): List<InventoryItem> =
        when (order) {
            // Case-insensitive, ascending alphabetical.
            SortOption.BrandName -> items.sortedBy { it.unit.brandName.lowercase() }
            // Smallest time-remaining first; rows with no daily dose (null supply) sink to the bottom.
            SortOption.TimeRemaining -> items.sortedBy { it.supply?.totalDays ?: Int.MAX_VALUE }
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
