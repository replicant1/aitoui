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
    /** Script whose dispensed count has already reached its repeats maximum, if the user tapped it. */
    val maxedOutScriptId: Long? = null,
    /** Script awaiting the user's confirmation to delete it, if any. */
    val pendingDeleteScriptId: Long? = null,
) {
    val pendingDispenseScript: ScriptDetails?
        get() = scripts.firstOrNull { it.scriptId == pendingDispenseScriptId }

    val maxedOutScript: ScriptDetails?
        get() = scripts.firstOrNull { it.scriptId == maxedOutScriptId }

    val pendingDeleteScript: ScriptDetails?
        get() = scripts.firstOrNull { it.scriptId == pendingDeleteScriptId }
}

sealed interface ScriptsAction {
    /** The user tapped the "dispensed" area of a script card. */
    data class DispensedTapped(val scriptId: Long) : ScriptsAction
    /** Confirm dispensing one unit for the pending script. */
    data object ConfirmDispense : ScriptsAction
    /** Dismiss the confirmation without dispensing. */
    data object CancelDispense : ScriptsAction
    /** Dismiss the "already at maximum" error. */
    data object DismissMaxedOut : ScriptsAction
    /** The user tapped the delete (cross) icon on a script card. */
    data class DeleteTapped(val scriptId: Long) : ScriptsAction
    /** Confirm deleting the pending script. */
    data object ConfirmDelete : ScriptsAction
    /** Dismiss the delete confirmation without deleting. */
    data object CancelDelete : ScriptsAction
}

class ScriptsViewModel(
    private val scriptRepository: ScriptRepository,
    private val dispensationRepository: DispensationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScriptsState())
    val state: StateFlow<ScriptsState> = _state.asStateFlow()

    init {
        scriptRepository.scriptsWithDetails
            .onEach { scripts ->
                _state.update { current ->
                    // Drop any open dialog whose script no longer exists.
                    current.copy(
                        scripts = scripts,
                        pendingDispenseScriptId = current.pendingDispenseScriptId
                            ?.takeIf { id -> scripts.any { it.scriptId == id } },
                        maxedOutScriptId = current.maxedOutScriptId
                            ?.takeIf { id -> scripts.any { it.scriptId == id } },
                        pendingDeleteScriptId = current.pendingDeleteScriptId
                            ?.takeIf { id -> scripts.any { it.scriptId == id } },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ScriptsAction) {
        when (action) {
            is ScriptsAction.DispensedTapped -> _state.update { current ->
                val script = current.scripts.firstOrNull { it.scriptId == action.scriptId }
                // Already dispensed the maximum number of times → show an error instead of confirming.
                if (script != null && script.dispensed >= script.repeats) {
                    current.copy(maxedOutScriptId = action.scriptId)
                } else {
                    current.copy(pendingDispenseScriptId = action.scriptId)
                }
            }

            ScriptsAction.CancelDispense ->
                _state.update { it.copy(pendingDispenseScriptId = null) }

            ScriptsAction.ConfirmDispense -> dispenseOne()

            ScriptsAction.DismissMaxedOut ->
                _state.update { it.copy(maxedOutScriptId = null) }

            is ScriptsAction.DeleteTapped ->
                _state.update { it.copy(pendingDeleteScriptId = action.scriptId) }

            ScriptsAction.CancelDelete ->
                _state.update { it.copy(pendingDeleteScriptId = null) }

            ScriptsAction.ConfirmDelete -> deleteScript()
        }
    }

    /** Deletes the pending script (its dispensations cascade). The list updates reactively. */
    private fun deleteScript() {
        val script = _state.value.pendingDeleteScript ?: return
        viewModelScope.launch {
            scriptRepository.deleteById(script.scriptId)
            _state.update { it.copy(pendingDeleteScriptId = null) }
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
