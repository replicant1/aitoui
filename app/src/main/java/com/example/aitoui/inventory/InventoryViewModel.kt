package com.example.aitoui.inventory

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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class InventoryState(
    val medications: List<MedicationTemplate> = emptyList(),
    val selectedId: Long? = null,
) {
    val selectedMedication: MedicationTemplate? get() = medications.firstOrNull { it.id == selectedId }
}

sealed interface InventoryAction {
    data class MedicationSelected(val id: Long) : InventoryAction
    data object SheetDismissed : InventoryAction
}

class InventoryViewModel(
    repository: MedicationTemplateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InventoryState())
    val state: StateFlow<InventoryState> = _state.asStateFlow()

    init {
        repository.medicationTemplates
            .onEach { meds ->
                _state.update { current ->
                    // Drop selection if the selected item no longer exists.
                    val stillThere = meds.any { it.id == current.selectedId }
                    current.copy(
                        medications = meds,
                        selectedId = current.selectedId.takeIf { stillThere },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: InventoryAction) {
        when (action) {
            is InventoryAction.MedicationSelected -> _state.update {
                // Re-tapping the selected row closes the sheet.
                it.copy(selectedId = if (it.selectedId == action.id) null else action.id)
            }

            InventoryAction.SheetDismissed -> _state.update { it.copy(selectedId = null) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                InventoryViewModel(app.medicationTemplateRepository)
            }
        }
    }
}
