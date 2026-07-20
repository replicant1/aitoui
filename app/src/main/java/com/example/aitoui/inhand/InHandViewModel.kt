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
import com.example.aitoui.data.Medication
import com.example.aitoui.data.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/** A single row in the in-hand list. */
data class InHandEntry(
    val id: Long,
    val medicationId: Long,
    val brand: String,
    val number: String,
) {
    /** Display label, e.g. "Panadol × 24". */
    val label: String get() = "$brand × $number"
}

/** Screen state for the In Hand screen. */
data class InHandState(
    val medications: List<Medication> = emptyList(),
    /** Dispensable units keyed by medication, used to show each list row's photo and dose. */
    val units: List<DispensableUnitDetails> = emptyList(),
    val selectedMedicationId: Long? = null,
    val numberOfTablets: String = "",
    val tabletsInHand: List<InHandEntry> = emptyList(),
    val selectedId: Long? = null,
    /** When the persisted in-hand figures were gathered (UTC start-of-day millis), or null if never saved. */
    val gatheredDate: Long? = null,
    /** Sorted signature of the list as last persisted, to detect unsaved add/deletes. See [hasUnsavedChanges]. */
    val savedSignature: List<String> = emptyList(),
) {
    val selectedMedicationName: String
        get() = medications.firstOrNull { it.id == selectedMedicationId }?.brandName ?: ""

    val canAdd: Boolean
        get() = selectedMedicationId != null && (numberOfTablets.toDoubleOrNull() ?: 0.0) > 0.0
    val canDelete: Boolean get() = selectedId != null

    /** True when a medication appears in more than one row, so merging would collapse them. */
    val canMerge: Boolean
        get() = tabletsInHand.groupingBy { it.medicationId }.eachCount().any { it.value > 1 }

    /** True when rows have been added, removed or merged since the list was last loaded/saved. */
    val hasUnsavedChanges: Boolean get() = listSignature(tabletsInHand) != savedSignature
}

/** Order-independent signature of a saved list: one "medicationId:number" per row, sorted. */
internal fun listSignature(rows: List<InHandEntry>): List<String> =
    rows.map { "${it.medicationId}:${it.number}" }.sorted()

/**
 * Collapses in-hand rows for the same medication into a single row, summing their quantities. Dose per
 * tablet is a function of the medication (it comes from the medication's dispensable unit), so grouping by
 * medication is equivalent to grouping by medication and dose. First-appearance order is preserved, and each
 * merged row keeps the first grouped row's id and brand.
 */
internal fun collateInHand(rows: List<InHandEntry>): List<InHandEntry> {
    val groups = LinkedHashMap<Long, MutableList<InHandEntry>>()
    for (row in rows) groups.getOrPut(row.medicationId) { mutableListOf() }.add(row)
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
    data class MedicationSelected(val id: Long) : InHandAction
    data class NumberOfTabletsChanged(val value: String) : InHandAction
    /** A camera-counted tablet total, dropped into the "Number of tablets" field for the user to ADD. */
    data class TabletsCounted(val count: Int) : InHandAction
    data object Add : InHandAction
    data class RowSelected(val id: Long) : InHandAction
    data object Delete : InHandAction
    /** Collapse rows for the same medication into one, summing their quantities. */
    data object Merge : InHandAction
    data object Save : InHandAction
}

class InHandViewModel(
    medicationRepository: MedicationRepository,
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
                        medicationId = item.medicationId,
                        brand = item.brandName,
                        number = item.quantity.formatQuantity(),
                    )
                }
                current.copy(tabletsInHand = loaded, savedSignature = listSignature(loaded))
            }
        }

        medicationRepository.medications
            .onEach { meds ->
                _state.update { current ->
                    val stillExists = meds.any { it.id == current.selectedMedicationId }
                    current.copy(
                        medications = meds,
                        selectedMedicationId = current.selectedMedicationId.takeIf { stillExists },
                    )
                }
            }
            .launchIn(viewModelScope)

        // Keep the dispensable units on hand so each list row can show its photo and dose.
        dispensableUnitRepository.formatsWithMedication
            .onEach { units -> _state.update { it.copy(units = units) } }
            .launchIn(viewModelScope)

        // Track the date the persisted figures were gathered, for the list title.
        inHandRepository.gatheredDate
            .onEach { date -> _state.update { it.copy(gatheredDate = date) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: InHandAction) {
        when (action) {
            is InHandAction.MedicationSelected ->
                _state.update { it.copy(selectedMedicationId = action.id) }

            is InHandAction.NumberOfTabletsChanged ->
                _state.update { it.copy(numberOfTablets = action.value.decimalOnly()) }

            is InHandAction.TabletsCounted ->
                _state.update { it.copy(numberOfTablets = action.count.toString()) }

            InHandAction.Add -> _state.update { current ->
                if (!current.canAdd) return@update current
                val medication = current.medications.firstOrNull { it.id == current.selectedMedicationId }
                    ?: return@update current
                val entry = InHandEntry(
                    id = nextId++,
                    medicationId = medication.id,
                    brand = medication.brandName,
                    number = current.numberOfTablets,
                )
                // Append the new row and clear the inputs.
                current.copy(
                    selectedMedicationId = null,
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
            InHandItem(medicationId = entry.medicationId, quantity = quantity)
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
                InHandViewModel(
                    app.medicationRepository,
                    app.dispensableUnitRepository,
                    app.inHandRepository,
                )
            }
        }
    }
}
