package com.example.aitoui.script

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.DispensableUnitRepository
import com.example.aitoui.data.Script
import com.example.aitoui.data.ScriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form state for adding a Script. A script is for a single dispensable unit (many scripts → one unit). */
data class AddScriptState(
    val dispensableUnits: List<DispensableUnitDetails> = emptyList(),
    val selectedFormatId: Long? = null,
    val serialNo: String = "",
    val dateOfIssue: Long? = null,
    val repeats: String = "",
    val validToMillis: Long? = null,
) {
    val selectedFormatLabel: String
        get() = dispensableUnits.firstOrNull { it.formatId == selectedFormatId }?.label ?: ""

    // Basic field validation.
    val formatValid: Boolean get() = selectedFormatId != null
    val dateOfIssueValid: Boolean get() = dateOfIssue != null
    val repeatsValid: Boolean get() = repeats.toIntOrNull() != null
    val validToValid: Boolean get() = validToMillis != null

    val canSave: Boolean
        get() = formatValid && dateOfIssueValid && repeatsValid && validToValid
}

sealed interface AddScriptAction {
    data class DispensableUnitSelected(val id: Long) : AddScriptAction
    data class SerialNoChanged(val value: String) : AddScriptAction
    data class DateOfIssueChanged(val millis: Long?) : AddScriptAction
    data class RepeatsChanged(val value: String) : AddScriptAction
    data class ValidToChanged(val millis: Long?) : AddScriptAction
    data object Save : AddScriptAction
}

class AddScriptViewModel(
    private val scriptRepository: ScriptRepository,
    dispensableUnitRepository: DispensableUnitRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddScriptState())
    val state: StateFlow<AddScriptState> = _state.asStateFlow()

    init {
        // Keep the dropdown's options in sync with the dispensable units.
        dispensableUnitRepository.formatsWithMedication
            .onEach { formats ->
                _state.update { current ->
                    val stillExists = formats.any { it.formatId == current.selectedFormatId }
                    current.copy(
                        dispensableUnits = formats,
                        selectedFormatId = current.selectedFormatId.takeIf { stillExists },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: AddScriptAction) {
        when (action) {
            is AddScriptAction.DispensableUnitSelected ->
                _state.update { it.copy(selectedFormatId = action.id) }

            is AddScriptAction.SerialNoChanged ->
                _state.update { it.copy(serialNo = action.value) }

            is AddScriptAction.DateOfIssueChanged ->
                _state.update { it.copy(dateOfIssue = action.millis) }

            is AddScriptAction.RepeatsChanged ->
                _state.update { it.copy(repeats = action.value.digitsOnly()) }

            is AddScriptAction.ValidToChanged ->
                _state.update { it.copy(validToMillis = action.millis) }

            AddScriptAction.Save -> save()
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            scriptRepository.add(
                Script(
                    dispensableUnitId = current.selectedFormatId!!,
                    serialNo = current.serialNo.trim(),
                    dateOfIssue = current.dateOfIssue!!,
                    repeats = current.repeats.toInt(),
                    validToMillis = current.validToMillis!!,
                )
            )
        }
        // Clear the form for the next entry (keep the loaded dispensable units).
        _state.update { AddScriptState(dispensableUnits = it.dispensableUnits) }
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                AddScriptViewModel(app.scriptRepository, app.dispensableUnitRepository)
            }
        }
    }
}
