package com.example.aitoui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** State for the Settings screen: the editable "warning window" field, in days. */
data class SettingsState(
    /** The warning-window value as shown in the text field (digits only; may be blank mid-edit). */
    val warningWindowDays: String = "",
)

/** User intents from the Settings screen. */
sealed interface SettingsAction {
    /** The user edited the warning-window field. */
    data class WarningWindowChanged(val value: String) : SettingsAction
}

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsState(warningWindowDays = settingsRepository.currentWarningWindowDays().toString()),
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.WarningWindowChanged -> {
                // The field is numeric: keep only digits, and allow it to be blank mid-edit.
                val digits = action.value.filter(Char::isDigit).take(MAX_DIGITS)
                _state.update { it.copy(warningWindowDays = digits) }
                // Persist only a sensible positive value; a blank or 0 leaves the stored setting untouched.
                digits.toIntOrNull()?.takeIf { it > 0 }?.let(settingsRepository::setWarningWindowDays)
            }
        }
    }

    companion object {
        // Up to 9999 days; guards against absurdly long input while allowing any realistic window.
        private const val MAX_DIGITS = 4

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                SettingsViewModel(app.settingsRepository)
            }
        }
    }
}
