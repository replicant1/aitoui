package com.example.aitoui.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.Medication
import com.example.aitoui.data.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form state for the Medication screen. All fields are raw text input. */
data class MedicationState(
    val brandName: String = "",
    val activeIngredient: String = "",
    val dosePerTablet: String = "",
    val tabletsPerBox: String = "",
    val boxes: String = "",
)

/** User intents emitted by the Medication screen. */
sealed interface MedicationAction {
    data class BrandNameChanged(val value: String) : MedicationAction
    data class ActiveIngredientChanged(val value: String) : MedicationAction
    data class DosePerTabletChanged(val value: String) : MedicationAction
    data class TabletsPerBoxChanged(val value: String) : MedicationAction
    data class BoxesChanged(val value: String) : MedicationAction
    data object Save : MedicationAction
}

class MedicationViewModel(
    private val repository: MedicationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MedicationState())
    val state: StateFlow<MedicationState> = _state.asStateFlow()

    fun onAction(action: MedicationAction) {
        when (action) {
            is MedicationAction.BrandNameChanged ->
                _state.update { it.copy(brandName = action.value) }

            is MedicationAction.ActiveIngredientChanged ->
                _state.update { it.copy(activeIngredient = action.value) }

            is MedicationAction.DosePerTabletChanged ->
                _state.update { it.copy(dosePerTablet = action.value.digitsOnly()) }

            is MedicationAction.TabletsPerBoxChanged ->
                _state.update { it.copy(tabletsPerBox = action.value.digitsOnly()) }

            is MedicationAction.BoxesChanged ->
                _state.update { it.copy(boxes = action.value.digitsOnly()) }

            MedicationAction.Save -> save()
        }
    }

    private fun save() {
        val current = _state.value
        if (current.brandName.isBlank()) return
        viewModelScope.launch {
            repository.add(
                Medication(
                    brandName = current.brandName.trim(),
                    activeIngredient = current.activeIngredient.trim(),
                    dosePerTablet = current.dosePerTablet,
                    tabletsPerBox = current.tabletsPerBox,
                    boxes = current.boxes,
                )
            )
        }
        // Clear the form for the next entry.
        _state.value = MedicationState()
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                MedicationViewModel(app.medicationRepository)
            }
        }
    }
}
