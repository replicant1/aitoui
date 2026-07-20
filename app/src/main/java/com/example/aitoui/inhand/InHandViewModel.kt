package com.example.aitoui.inhand

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.DispensableUnitRepository
import com.example.aitoui.data.InHandItem
import com.example.aitoui.data.InHandRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/** A single row in the in-hand list: [number] tablets of a specific dispensable unit (dose/format). */
data class InHandEntry(
    val id: Long,
    val dispensableUnitId: Long,
    val brand: String,
    val number: String,
) {
    /** Display label, e.g. "Panadol × 24". */
    val label: String get() = "$brand × $number"
}

/** Screen state for the In Hand screen. */
data class InHandState(
    /** Dispensable units offered in the dropdown, each with its photo, dose and brand. */
    val units: List<DispensableUnitDetails> = emptyList(),
    /** The chosen unit's formatId, if any. */
    val selectedUnitId: Long? = null,
    val numberOfTablets: String = "",
    val tabletsInHand: List<InHandEntry> = emptyList(),
    val selectedId: Long? = null,
    /** When the persisted in-hand figures were gathered (UTC start-of-day millis), or null if never saved. */
    val gatheredDate: Long? = null,
    /** Sorted signature of the list as last persisted, to detect unsaved add/deletes. See [hasUnsavedChanges]. */
    val savedSignature: List<String> = emptyList(),
) {
    val selectedUnit: DispensableUnitDetails?
        get() = units.firstOrNull { it.formatId == selectedUnitId }

    /** Text shown in the closed dropdown, e.g. "Panadol (500mg)". */
    val selectedUnitLabel: String get() = selectedUnit?.label ?: ""

    val canAdd: Boolean
        get() = selectedUnit != null && (numberOfTablets.toDoubleOrNull() ?: 0.0) > 0.0
    val canDelete: Boolean get() = selectedId != null

    /** True when a dispensable unit appears in more than one row, so merging would collapse them. */
    val canMerge: Boolean
        get() = tabletsInHand.groupingBy { it.dispensableUnitId }.eachCount().any { it.value > 1 }

    /** True when rows have been added, removed or merged since the list was last loaded/saved. */
    val hasUnsavedChanges: Boolean get() = listSignature(tabletsInHand) != savedSignature
}

/** Order-independent signature of a saved list: one "dispensableUnitId:number" per row, sorted. */
internal fun listSignature(rows: List<InHandEntry>): List<String> =
    rows.map { "${it.dispensableUnitId}:${it.number}" }.sorted()

/**
 * Collapses in-hand rows for the same dispensable unit into a single row, summing their quantities.
 * First-appearance order is preserved, and each merged row keeps the first grouped row's id and brand.
 */
internal fun collateInHand(rows: List<InHandEntry>): List<InHandEntry> {
    val groups = LinkedHashMap<Long, MutableList<InHandEntry>>()
    for (row in rows) groups.getOrPut(row.dispensableUnitId) { mutableListOf() }.add(row)
    return groups.values.map { group ->
        val total = group.sumOf { it.number.toDoubleOrNull() ?: 0.0 }
        group.first().copy(number = total.formatQuantity())
    }
}

/** Drops a trailing ".0" so whole quantities show as "2" rather than "2.0". */
internal fun Double.formatQuantity(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()

/** User intents emitted by the In Hand screen. */
sealed interface InHandAction {
    data class UnitSelected(val formatId: Long) : InHandAction
    data class NumberOfTabletsChanged(val value: String) : InHandAction
    /** A camera-counted tablet total, dropped into the "Number of tablets" field for the user to ADD. */
    data class TabletsCounted(val count: Int) : InHandAction
    data object Add : InHandAction
    data class RowSelected(val id: Long) : InHandAction
    data object Delete : InHandAction
    /** Collapse rows for the same dispensable unit into one, summing their quantities. */
    data object Merge : InHandAction
    data object Save : InHandAction
}

class InHandViewModel(
    dispensableUnitRepository: DispensableUnitRepository,
    private val inHandRepository: InHandRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InHandState())
    val state: StateFlow<InHandState> = _state.asStateFlow()

    /** One-shot flag: flips true once the in-hand figures are saved, so the screen can pop back to Main. */
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun consumeSaved() { _saved.value = false }

    private var nextId = 0L

    init {
        // Initialise the list from the persisted in-hand tablets.
        viewModelScope.launch {
            val saved = inHandRepository.getAll()
            _state.update { current ->
                val loaded = saved.map { item ->
                    InHandEntry(
                        id = nextId++,
                        dispensableUnitId = item.dispensableUnitId,
                        brand = item.brandName,
                        number = item.quantity.formatQuantity(),
                    )
                }
                current.copy(tabletsInHand = loaded, savedSignature = listSignature(loaded))
            }
        }

        // Dispensable units offered in the dropdown (with photo/dose); drop a selection whose unit is gone.
        dispensableUnitRepository.formatsWithMedication
            .onEach { units ->
                _state.update { current ->
                    val stillExists = units.any { it.formatId == current.selectedUnitId }
                    current.copy(units = units, selectedUnitId = current.selectedUnitId.takeIf { stillExists })
                }
            }
            .launchIn(viewModelScope)

        // Track the date the persisted figures were gathered, for the list title.
        inHandRepository.gatheredDate
            .onEach { date -> _state.update { it.copy(gatheredDate = date) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: InHandAction) {
        when (action) {
            is InHandAction.UnitSelected ->
                _state.update { it.copy(selectedUnitId = action.formatId) }

            is InHandAction.NumberOfTabletsChanged ->
                _state.update { it.copy(numberOfTablets = action.value.decimalOnly()) }

            is InHandAction.TabletsCounted ->
                _state.update { it.copy(numberOfTablets = action.count.toString()) }

            InHandAction.Add -> _state.update { current ->
                if (!current.canAdd) return@update current
                val unit = current.selectedUnit ?: return@update current
                val entry = InHandEntry(
                    id = nextId++,
                    dispensableUnitId = unit.formatId,
                    brand = unit.brandName,
                    number = current.numberOfTablets,
                )
                // Append the new row and clear the inputs.
                current.copy(
                    selectedUnitId = null,
                    numberOfTablets = "",
                    tabletsInHand = current.tabletsInHand + entry,
                )
            }

            is InHandAction.RowSelected ->
                _state.update { it.copy(selectedId = action.id) }

            InHandAction.Delete -> _state.update { current ->
                val selected = current.selectedId ?: return@update current
                current.copy(
                    tabletsInHand = current.tabletsInHand.filterNot { it.id == selected },
                    selectedId = null,
                )
            }

            InHandAction.Merge -> _state.update { current ->
                current.copy(tabletsInHand = collateInHand(current.tabletsInHand), selectedId = null)
            }

            InHandAction.Save -> save()
        }
    }

    /**
     * Persists the current list to the in_hand table (replacing its contents) and records today as the
     * date the figures were gathered.
     */
    private fun save() {
        val items = _state.value.tabletsInHand.mapNotNull { entry ->
            val quantity = entry.number.toDoubleOrNull() ?: return@mapNotNull null
            InHandItem(dispensableUnitId = entry.dispensableUnitId, quantity = quantity)
        }
        viewModelScope.launch {
            inHandRepository.save(items, todayStartOfDayUtcMillis())
            _saved.value = true              // signal the screen to return to Main
        }
    }

    /** Now, normalised to the start of the day in UTC — the project's convention for stored dates. */
    private fun todayStartOfDayUtcMillis(): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = System.currentTimeMillis()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Keeps digits and a single decimal point, e.g. so "0.5" is accepted but "0.5.2" is not. */
    private fun String.decimalOnly(): String {
        val filtered = filter { it.isDigit() || it == '.' }
        val firstDot = filtered.indexOf('.')
        return if (firstDot == -1) filtered
        else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                InHandViewModel(app.dispensableUnitRepository, app.inHandRepository)
            }
        }
    }
}
