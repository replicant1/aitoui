package com.example.aitoui.medicationformat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.MedicationFormat
import com.example.aitoui.data.MedicationFormatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form state for the medication template entry screen. All fields are raw text input. */
data class MedicationFormatState(
    val brandName: String = "",
    val activeIngredient: String = "",
    val dosePerTablet: String = "",
    val tabletsPerBox: String = "",
)

/** User intents emitted by the medication template entry screen. */
sealed interface MedicationFormatAction {
    data class BrandNameChanged(val value: String) : MedicationFormatAction
    data class ActiveIngredientChanged(val value: String) : MedicationFormatAction
    data class DosePerTabletChanged(val value: String) : MedicationFormatAction
    data class TabletsPerBoxChanged(val value: String) : MedicationFormatAction
    data object Save : MedicationFormatAction
}

class MedicationFormatViewModel(
    private val repository: MedicationFormatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MedicationFormatState())
    val state: StateFlow<MedicationFormatState> = _state.asStateFlow()

    fun onAction(action: MedicationFormatAction) {
        when (action) {
            is MedicationFormatAction.BrandNameChanged ->
                _state.update { it.copy(brandName = action.value) }

            is MedicationFormatAction.ActiveIngredientChanged ->
                _state.update { it.copy(activeIngredient = action.value) }

            is MedicationFormatAction.DosePerTabletChanged ->
                _state.update { it.copy(dosePerTablet = action.value.digitsOnly()) }

            is MedicationFormatAction.TabletsPerBoxChanged ->
                _state.update { it.copy(tabletsPerBox = action.value.digitsOnly()) }

            MedicationFormatAction.Save -> save()
        }
    }

    private fun save() {
        val current = _state.value
        if (current.brandName.isBlank()) return
        viewModelScope.launch {
            repository.add(
                MedicationFormat(
                    brandName = current.brandName.trim(),
                    activeIngredient = current.activeIngredient.trim(),
                    dosePerTablet = current.dosePerTablet,
                    tabletsPerBox = current.tabletsPerBox,
                )
            )
        }
        // Clear the form for the next entry.
        _state.value = MedicationFormatState()
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                MedicationFormatViewModel(app.medicationFormatRepository)
            }
        }
    }
}
