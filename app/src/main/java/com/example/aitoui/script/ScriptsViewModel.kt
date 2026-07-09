package com.example.aitoui.script

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.Dispensation
import com.example.aitoui.data.DispensationRepository
import com.example.aitoui.data.ScriptDetails
import com.example.aitoui.data.ScriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State for the Scripts list screen — one [ScriptDetails] per row in the scripts table. */
data class ScriptsState(
    val scripts: List<ScriptDetails> = emptyList(),
    /** Script awaiting the user's confirmation to dispense one unit, if any. */
    val pendingDispenseScriptId: Long? = null,
) {
    val pendingDispenseScript: ScriptDetails?
        get() = scripts.firstOrNull { it.scriptId == pendingDispenseScriptId }
}

sealed interface ScriptsAction {
    /** The user tapped the "dispensed" area of a script card. */
    data class DispensedTapped(val scriptId: Long) : ScriptsAction
    /** Confirm dispensing one unit for the pending script. */
    data object ConfirmDispense : ScriptsAction
    /** Dismiss the confirmation without dispensing. */
    data object CancelDispense : ScriptsAction
}

class ScriptsViewModel(
    scriptRepository: ScriptRepository,
    private val dispensationRepository: DispensationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScriptsState())
    val state: StateFlow<ScriptsState> = _state.asStateFlow()

    init {
        scriptRepository.scriptsWithDetails
            .onEach { scripts ->
                _state.update { current ->
                    // Drop the pending confirmation if that script no longer exists.
                    val stillThere = scripts.any { it.scriptId == current.pendingDispenseScriptId }
                    current.copy(
                        scripts = scripts,
                        pendingDispenseScriptId = current.pendingDispenseScriptId.takeIf { stillThere },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ScriptsAction) {
        when (action) {
            is ScriptsAction.DispensedTapped ->
                _state.update { it.copy(pendingDispenseScriptId = action.scriptId) }

            ScriptsAction.CancelDispense ->
                _state.update { it.copy(pendingDispenseScriptId = null) }

            ScriptsAction.ConfirmDispense -> dispenseOne()
        }
    }

    /** Records a single dispensation for the pending script. The derived dispensed count follows. */
    private fun dispenseOne() {
        val script = _state.value.pendingDispenseScript ?: return
        viewModelScope.launch {
            dispensationRepository.add(
                Dispensation(
                    scriptId = script.scriptId,
                    dispensableUnitId = script.dispensableUnitId,
                    number = 1,
                    dispensedAtMillis = System.currentTimeMillis(),
                )
            )
            _state.update { it.copy(pendingDispenseScriptId = null) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                ScriptsViewModel(app.scriptRepository, app.dispensationRepository)
            }
        }
    }
}
