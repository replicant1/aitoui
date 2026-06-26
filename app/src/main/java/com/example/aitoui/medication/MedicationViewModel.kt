package com.example.aitoui.medication

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

class MedicationViewModel : ViewModel() {

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

            MedicationAction.Save -> {
                // TODO: persist the medication once a data layer exists. Stub for now.
            }
        }
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }
}
