package com.example.aitoui.taketablets

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A single row in the "Tablets Taken" list. */
data class TabletEntry(
    val id: Long,
    val brand: String,
    val number: String,
) {
    /** Display label, e.g. "Panadol × 2". */
    val label: String get() = "$brand × $number"
}

/** Screen state for "Take Tablets". */
data class TakeTabletsState(
    val brandName: String = "",
    val numberOfTablets: String = "",
    val tabletsTaken: List<TabletEntry> = emptyList(),
    val selectedId: Long? = null,
) {
    val canAdd: Boolean get() = brandName.isNotBlank() && numberOfTablets.isNotBlank()
    val canDelete: Boolean get() = selectedId != null
}

/** User intents emitted by the "Take Tablets" screen. */
sealed interface TakeTabletsAction {
    data class BrandNameChanged(val value: String) : TakeTabletsAction
    data class NumberOfTabletsChanged(val value: String) : TakeTabletsAction
    data object Add : TakeTabletsAction
    data class RowSelected(val id: Long) : TakeTabletsAction
    data object Delete : TakeTabletsAction
    data object Save : TakeTabletsAction
}

class TakeTabletsViewModel : ViewModel() {

    private val _state = MutableStateFlow(TakeTabletsState())
    val state: StateFlow<TakeTabletsState> = _state.asStateFlow()

    private var nextId = 0L

    fun onAction(action: TakeTabletsAction) {
        when (action) {
            is TakeTabletsAction.BrandNameChanged ->
                _state.update { it.copy(brandName = action.value) }

            is TakeTabletsAction.NumberOfTabletsChanged ->
                _state.update { it.copy(numberOfTablets = action.value.digitsOnly()) }

            TakeTabletsAction.Add -> _state.update { current ->
                if (!current.canAdd) return@update current
                val entry = TabletEntry(
                    id = nextId++,
                    brand = current.brandName.trim(),
                    number = current.numberOfTablets,
                )
                // Append the new row and clear the input fields.
                current.copy(
                    brandName = "",
                    numberOfTablets = "",
                    tabletsTaken = current.tabletsTaken + entry,
                )
            }

            is TakeTabletsAction.RowSelected ->
                _state.update { it.copy(selectedId = action.id) }

            TakeTabletsAction.Delete -> _state.update { current ->
                val selected = current.selectedId ?: return@update current
                current.copy(
                    tabletsTaken = current.tabletsTaken.filterNot { it.id == selected },
                    selectedId = null,
                )
            }

            TakeTabletsAction.Save -> {
                // TODO: persist the tablets-taken list once a data layer exists. Stub for now.
            }
        }
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }
}
