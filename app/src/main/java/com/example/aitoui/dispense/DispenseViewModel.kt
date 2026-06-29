package com.example.aitoui.dispense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.Dispensation
import com.example.aitoui.data.DispensationRepository
import com.example.aitoui.data.MedicationFormatDetails
import com.example.aitoui.data.MedicationFormatRepository
import com.example.aitoui.data.ScriptDetails
import com.example.aitoui.data.ScriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A single captured dispensation: a unit (under a script) dispensed [number] times. */
data class DispensationEntry(
    val id: Long,
    val scriptId: Long,
    val formatId: Long,
    val formatLabel: String,
    val number: String,
) {
    /** Display label, e.g. "Panadol (500mg) × 1". */
    val label: String get() = "$formatLabel × $number"
}

/** Screen state for "Dispense" — capturing a pharmacy trip. */
data class DispenseState(
    val scripts: List<ScriptDetails> = emptyList(),
    val selectedScriptId: Long? = null,
    val allFormats: List<MedicationFormatDetails> = emptyList(),
    val selectedFormatId: Long? = null,
    val number: String = "1",
    val dispensations: List<DispensationEntry> = emptyList(),
    val selectedRowId: Long? = null,
) {
    val selectedScript: ScriptDetails? get() = scripts.firstOrNull { it.scriptId == selectedScriptId }
    val selectedScriptLabel: String get() = selectedScript?.label ?: ""

    /** The dispensable unit the selected script is for (the script's single unit). */
    val availableFormats: List<MedicationFormatDetails>
        get() {
            val unitId = selectedScript?.dispensableUnitId ?: return emptyList()
            return allFormats.filter { it.formatId == unitId }
        }

    val selectedFormatLabel: String
        get() = availableFormats.firstOrNull { it.formatId == selectedFormatId }?.label ?: ""

    val canAdd: Boolean
        get() = selectedScriptId != null && selectedFormatId != null && number.isNotBlank()
    val canDelete: Boolean get() = selectedRowId != null
}

sealed interface DispenseAction {
    data class ScriptSelected(val id: Long) : DispenseAction
    data class FormatSelected(val id: Long) : DispenseAction
    data class NumberChanged(val value: String) : DispenseAction
    data object Add : DispenseAction
    data class RowSelected(val id: Long) : DispenseAction
    data object Delete : DispenseAction
    data object Save : DispenseAction
}

class DispenseViewModel(
    private val scriptRepository: ScriptRepository,
    formatRepository: MedicationFormatRepository,
    private val dispensationRepository: DispensationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DispenseState())
    val state: StateFlow<DispenseState> = _state.asStateFlow()

    private var nextId = 0L

    init {
        combine(
            scriptRepository.scriptsWithDetails,
            formatRepository.formatsWithMedication,
        ) { scripts, formats -> scripts to formats }
            .onEach { (scripts, formats) ->
                _state.update { current ->
                    val scriptStillExists = scripts.any { it.scriptId == current.selectedScriptId }
                    current.copy(
                        scripts = scripts,
                        allFormats = formats,
                        selectedScriptId = current.selectedScriptId.takeIf { scriptStillExists },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: DispenseAction) {
        when (action) {
            is DispenseAction.ScriptSelected ->
                // Changing the script changes which unit is offered, so reset the format.
                _state.update { it.copy(selectedScriptId = action.id, selectedFormatId = null) }

            is DispenseAction.FormatSelected ->
                _state.update { it.copy(selectedFormatId = action.id) }

            is DispenseAction.NumberChanged ->
                _state.update { it.copy(number = action.value.digitsOnly()) }

            DispenseAction.Add -> _state.update { current ->
                if (!current.canAdd) return@update current
                val format = current.availableFormats.firstOrNull { it.formatId == current.selectedFormatId }
                    ?: return@update current
                val entry = DispensationEntry(
                    id = nextId++,
                    scriptId = current.selectedScriptId!!,
                    formatId = format.formatId,
                    formatLabel = format.label,
                    number = current.number,
                )
                current.copy(
                    selectedScriptId = null,
                    selectedFormatId = null,
                    number = "1",
                    dispensations = current.dispensations + entry,
                )
            }

            is DispenseAction.RowSelected ->
                _state.update { it.copy(selectedRowId = action.id) }

            DispenseAction.Delete -> _state.update { current ->
                val selected = current.selectedRowId ?: return@update current
                current.copy(
                    dispensations = current.dispensations.filterNot { it.id == selected },
                    selectedRowId = null,
                )
            }

            DispenseAction.Save -> save()
        }
    }

    private fun save() {
        val entries = _state.value.dispensations
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            for (entry in entries) {
                val number = entry.number.toIntOrNull() ?: continue
                dispensationRepository.add(
                    Dispensation(
                        scriptId = entry.scriptId,
                        dispensableUnitId = entry.formatId,
                        number = number,
                        dispensedAtMillis = now,
                    )
                )
            }
            _state.update { it.copy(dispensations = emptyList(), selectedRowId = null) }
        }
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                DispenseViewModel(
                    scriptRepository = app.scriptRepository,
                    formatRepository = app.medicationFormatRepository,
                    dispensationRepository = app.dispensationRepository,
                )
            }
        }
    }
}
