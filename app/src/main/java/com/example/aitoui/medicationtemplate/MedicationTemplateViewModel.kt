package com.example.aitoui.medicationtemplate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.MedicationTemplate
import com.example.aitoui.data.MedicationTemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form state for the medication template entry screen. All fields are raw text input. */
data class MedicationTemplateState(
    val brandName: String = "",
    val activeIngredient: String = "",
    val dosePerTablet: String = "",
    val tabletsPerBox: String = "",
)

/** User intents emitted by the medication template entry screen. */
sealed interface MedicationTemplateAction {
    data class BrandNameChanged(val value: String) : MedicationTemplateAction
    data class ActiveIngredientChanged(val value: String) : MedicationTemplateAction
    data class DosePerTabletChanged(val value: String) : MedicationTemplateAction
    data class TabletsPerBoxChanged(val value: String) : MedicationTemplateAction
    data object Save : MedicationTemplateAction
}

class MedicationTemplateViewModel(
    private val repository: MedicationTemplateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MedicationTemplateState())
    val state: StateFlow<MedicationTemplateState> = _state.asStateFlow()

    fun onAction(action: MedicationTemplateAction) {
        when (action) {
            is MedicationTemplateAction.BrandNameChanged ->
                _state.update { it.copy(brandName = action.value) }

            is MedicationTemplateAction.ActiveIngredientChanged ->
                _state.update { it.copy(activeIngredient = action.value) }

            is MedicationTemplateAction.DosePerTabletChanged ->
                _state.update { it.copy(dosePerTablet = action.value.digitsOnly()) }

            is MedicationTemplateAction.TabletsPerBoxChanged ->
                _state.update { it.copy(tabletsPerBox = action.value.digitsOnly()) }

            MedicationTemplateAction.Save -> save()
        }
    }

    private fun save() {
        val current = _state.value
        if (current.brandName.isBlank()) return
        viewModelScope.launch {
            repository.add(
                MedicationTemplate(
                    brandName = current.brandName.trim(),
                    activeIngredient = current.activeIngredient.trim(),
                    dosePerTablet = current.dosePerTablet,
                    tabletsPerBox = current.tabletsPerBox,
                )
            )
        }
        // Clear the form for the next entry.
        _state.value = MedicationTemplateState()
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                MedicationTemplateViewModel(app.medicationTemplateRepository)
            }
        }
    }
}
