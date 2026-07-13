package com.example.aitoui.runout

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Feeds the Run-out Graph from the same four sources as the Inventory screen (dispensable units,
 * in-hand tablets, daily schedule, scripts). The time cursor is UI state and lives in the composable.
 */
class RunOutGraphViewModel(
    dispensableUnitRepository: DispensableUnitRepository,
    inHandRepository: InHandRepository,
    dailyScheduleRepository: DailyScheduleRepository,
    scriptRepository: ScriptRepository,
) : ViewModel() {

    val state: StateFlow<RunOutGraphData> = combine(
        dispensableUnitRepository.formatsWithMedication,
        inHandRepository.inHand,
        dailyScheduleRepository.dailySchedule,
        scriptRepository.scriptsWithDetails,
    ) { formats, inHand, schedule, scripts ->
        // A medication can have several schedule rows (e.g. AM + PM), so sum them per medication.
        val dailyByMedication = schedule
            .groupBy { it.medicationId }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
        val inHandByMedication = inHand.associate { it.medicationId to it.quantity }
        computeRunOutGraph(formats, scripts, dailyByMedication, inHandByMedication, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunOutGraphData())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                RunOutGraphViewModel(
                    dispensableUnitRepository = app.dispensableUnitRepository,
                    inHandRepository = app.inHandRepository,
                    dailyScheduleRepository = app.dailyScheduleRepository,
                    scriptRepository = app.scriptRepository,
                )
            }
        }
    }
}
