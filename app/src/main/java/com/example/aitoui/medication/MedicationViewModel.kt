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

/** Form state for the Medication entry screen. */
data class MedicationState(
    val brandName: String = "",
    val activeIngredient: String = "",
) {
    val canSave: Boolean get() = brandName.isNotBlank() && activeIngredient.isNotBlank()
}

sealed interface MedicationAction {
    data class BrandNameChanged(val value: String) : MedicationAction
    data class ActiveIngredientChanged(val value: String) : MedicationAction
    data object Save : MedicationAction
}

class MedicationViewModel(
    private val repository: MedicationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MedicationState())
    val state: StateFlow<MedicationState> = _state.asStateFlow()

    /** One-shot flag: flips true once a medication is saved, so the screen can pop back to Medications. */
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun consumeSaved() { _saved.value = false }

    fun onAction(action: MedicationAction) {
        when (action) {
            is MedicationAction.BrandNameChanged ->
                _state.update { it.copy(brandName = action.value) }

            is MedicationAction.ActiveIngredientChanged ->
                _state.update { it.copy(activeIngredient = action.value) }

            MedicationAction.Save -> save()
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            repository.add(
                Medication(
                    brandName = current.brandName.trim(),
                    activeIngredient = current.activeIngredient.trim(),
                )
            )
            _state.value = MedicationState()   // clear the form for the next entry
            _saved.value = true                // signal the screen to return to Medications
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                MedicationViewModel(app.medicationRepository)
            }
        }
    }
}
