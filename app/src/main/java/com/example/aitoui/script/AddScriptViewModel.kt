package com.example.aitoui.script

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.aitoui.AitouiApp
import com.example.aitoui.data.Dispensation
import com.example.aitoui.data.DispensableUnit
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.DispensableUnitRepository
import com.example.aitoui.data.DispensationRepository
import com.example.aitoui.data.FuzzyMatcher
import com.example.aitoui.data.Medication
import com.example.aitoui.data.MedicationRepository
import com.example.aitoui.data.Script
import com.example.aitoui.data.ScriptRepository
import com.example.aitoui.navigation.ScriptRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Form state for adding a Script. The medication and dispensable unit are entered as raw fields (brand,
 * active ingredient, mg/tablet, tablets/unit) — pre-filled from a PB038 scan where available — and resolved
 * to existing-or-new `medications` / `dispensable_units` rows on Save via [medicationStep]/[dispensableUnitStep].
 */
data class AddScriptState(
    val brandName: String = "",
    val activeIngredient: String = "",
    val dosePerTablet: String = "",
    val tabletsPerUnit: String = "",
    val serialNo: String = "",
    val serialNo2: String = "",
    val dateOfIssue: Long? = null,
    val repeats: String = "",
    val validToMillis: Long? = null,
    val instructions: String = "",
    /** "No. of times already dispensed" (scanned or entered manually); a positive value is applied as a
     *  prior dispensation on save. Required. */
    val priorDispensed: String = "",
    /** Non-null while the medication-resolution dialog is shown. */
    val medicationStep: MedicationResolution? = null,
    /** Non-null while the dispensable-unit-resolution dialog is shown. */
    val dispensableUnitStep: DispensableUnitResolution? = null,
    /** True while the "serial number already used" error dialog is shown; blocks the save. */
    val duplicateSerial: Boolean = false,
) {
    private val brandValid get() = brandName.isNotBlank()
    private val activeValid get() = activeIngredient.isNotBlank()
    private val doseValid get() = dosePerTablet.isNotBlank()
    private val tabletsValid get() = tabletsPerUnit.isNotBlank()
    private val dateOfIssueValid get() = dateOfIssue != null
    private val repeatsValid get() = repeats.toIntOrNull() != null
    private val validToValid get() = validToMillis != null
    private val priorDispensedValid get() = priorDispensed.toIntOrNull() != null

    val canSave: Boolean
        get() = brandValid && activeValid && doseValid && tabletsValid && priorDispensedValid &&
            dateOfIssueValid && repeatsValid && validToValid
}

/** Medication-resolution dialog: existing candidates to pick, and whether creating a new one is refused. */
data class MedicationResolution(
    val exact: List<Medication>,
    val similar: List<Medication>,
    val blocked: Boolean,
) {
    val candidates: List<Medication> get() = exact + similar
}

/** Dispensable-unit-resolution dialog for the [resolvedMedication]: its existing dispensable units + blocked flag. */
data class DispensableUnitResolution(
    val resolvedMedication: ResolvedMedication,
    /** The resolved medication's brand/active, shown on every dispensable-unit card. */
    val medicationBrandName: String,
    val medicationActiveIngredient: String,
    val candidates: List<DispensableUnitDetails>,
    val blocked: Boolean,
)

/** Either an existing medication id, or a new medication to create from (brand, active). */
sealed interface ResolvedMedication {
    data class Existing(val id: Long) : ResolvedMedication
    data class New(val brandName: String, val activeIngredient: String) : ResolvedMedication
}

sealed interface AddScriptAction {
    data class BrandNameChanged(val value: String) : AddScriptAction
    data class ActiveIngredientChanged(val value: String) : AddScriptAction
    data class DosePerTabletChanged(val value: String) : AddScriptAction
    data class TabletsPerUnitChanged(val value: String) : AddScriptAction
    data class SerialNoChanged(val value: String) : AddScriptAction
    data class SerialNo2Changed(val value: String) : AddScriptAction
    data class DateOfIssueChanged(val millis: Long?) : AddScriptAction
    data class PriorDispensedChanged(val value: String) : AddScriptAction
    data class RepeatsChanged(val value: String) : AddScriptAction
    data class ValidToChanged(val millis: Long?) : AddScriptAction
    data class InstructionsChanged(val value: String) : AddScriptAction
    data object Save : AddScriptAction

    // Medication-resolution dialog.
    data class PickMedication(val id: Long) : AddScriptAction
    data object CreateMedication : AddScriptAction

    // Dispensable-unit-resolution dialog.
    data class PickDispensableUnit(val id: Long) : AddScriptAction
    data object CreateDispensableUnit : AddScriptAction

    data object CancelResolution : AddScriptAction

    // "Serial number already used" error dialog.
    data object DismissDuplicateSerial : AddScriptAction
}

class AddScriptViewModel(
    private val scriptRepository: ScriptRepository,
    private val medicationRepository: MedicationRepository,
    private val dispensableUnitRepository: DispensableUnitRepository,
    private val dispensationRepository: DispensationRepository,
    prefill: ScriptRoute,
) : ViewModel() {

    // Seed the form from a scanned PB038 (or all-null args for manual entry).
    private val _state = MutableStateFlow(
        AddScriptState(
            brandName = prefill.brandName.orEmpty(),
            activeIngredient = prefill.activeIngredient.orEmpty(),
            dosePerTablet = prefill.dosePerTablet.orEmpty(),
            tabletsPerUnit = prefill.tabletsPerUnit.orEmpty(),
            serialNo = prefill.serialNo.orEmpty(),
            serialNo2 = prefill.serialNo2.orEmpty(),
            dateOfIssue = prefill.dateOfIssueMillis,
            repeats = prefill.repeats?.toString() ?: "",
            validToMillis = prefill.validToMillis,
            instructions = prefill.instructions.orEmpty(),
            priorDispensed = if (prefill.priorDispensed != 0) prefill.priorDispensed.toString() else "",
        )
    )
    val state: StateFlow<AddScriptState> = _state.asStateFlow()

    /** One-shot flag: flips true once a script is fully saved, so the screen can pop back to Scripts. */
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun consumeSaved() { _saved.value = false }

    fun onAction(action: AddScriptAction) {
        when (action) {
            is AddScriptAction.BrandNameChanged -> _state.update { it.copy(brandName = action.value) }
            is AddScriptAction.ActiveIngredientChanged -> _state.update { it.copy(activeIngredient = action.value) }
            is AddScriptAction.DosePerTabletChanged -> _state.update { it.copy(dosePerTablet = action.value) }
            is AddScriptAction.TabletsPerUnitChanged -> _state.update { it.copy(tabletsPerUnit = action.value.digitsOnly()) }
            is AddScriptAction.SerialNoChanged -> _state.update { it.copy(serialNo = action.value) }
            is AddScriptAction.SerialNo2Changed -> _state.update { it.copy(serialNo2 = action.value) }
            is AddScriptAction.DateOfIssueChanged -> _state.update { it.copy(dateOfIssue = action.millis) }
            is AddScriptAction.PriorDispensedChanged ->
                _state.update { it.copy(priorDispensed = action.value.digitsOnly()) }
            is AddScriptAction.RepeatsChanged -> _state.update { it.copy(repeats = action.value.digitsOnly()) }
            is AddScriptAction.ValidToChanged -> _state.update { it.copy(validToMillis = action.millis) }
            is AddScriptAction.InstructionsChanged -> _state.update { it.copy(instructions = action.value) }

            AddScriptAction.Save -> save()

            is AddScriptAction.PickMedication ->
                onMedicationResolved(ResolvedMedication.Existing(action.id))

            AddScriptAction.CreateMedication -> {
                val step = _state.value.medicationStep ?: return
                if (step.blocked) return
                val s = _state.value
                onMedicationResolved(ResolvedMedication.New(s.brandName.trim(), s.activeIngredient.trim()))
            }

            is AddScriptAction.PickDispensableUnit -> {
                val step = _state.value.dispensableUnitStep ?: return
                _state.update { it.copy(dispensableUnitStep = null) }
                viewModelScope.launch { persist(step.resolvedMedication, ChosenUnit.Existing(action.id)) }
            }

            AddScriptAction.CreateDispensableUnit -> {
                val step = _state.value.dispensableUnitStep ?: return
                if (step.blocked) return
                _state.update { it.copy(dispensableUnitStep = null) }
                viewModelScope.launch { persist(step.resolvedMedication, ChosenUnit.New) }
            }

            AddScriptAction.CancelResolution ->
                _state.update { it.copy(medicationStep = null, dispensableUnitStep = null) }

            AddScriptAction.DismissDuplicateSerial ->
                _state.update { it.copy(duplicateSerial = false) }
        }
    }

    /**
     * Save entry point. The very first check is serial-number uniqueness: no two scripts may share a serial
     * number, so a clash shows a blocking error and stops here. Otherwise the medication/dispensable-unit
     * resolution begins. A blank serial is treated as "no serial" and is not subject to the uniqueness rule.
     */
    private fun save() {
        val s = _state.value
        if (!s.canSave) return
        val serials = listOf(s.serialNo, s.serialNo2).map { it.trim() }.filter { it.isNotEmpty() }
        if (serials.isEmpty()) {
            resolveMedication()
            return
        }
        viewModelScope.launch {
            if (scriptRepository.anySerialInUse(serials)) {
                _state.update { it.copy(duplicateSerial = true) }
            } else {
                resolveMedication()
            }
        }
    }

    /** Step 1: match the entered brand/active against existing medications. */
    private fun resolveMedication() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val existing = medicationRepository.medications.first()
            val m = FuzzyMatcher.classifyMedications(s.brandName.trim(), s.activeIngredient.trim(), existing)
            // Always confirm via the dialog — even with no similar medications, the user picks the new one.
            _state.update { it.copy(medicationStep = MedicationResolution(m.exact, m.similar, m.blocked)) }
        }
    }

    /** Step 2: with the medication resolved, match the entered dose/pack against its dispensable units. */
    private fun onMedicationResolved(resolved: ResolvedMedication) {
        _state.update { it.copy(medicationStep = null) }
        val s = _state.value
        viewModelScope.launch {
            // Always confirm via the dispensable-unit dialog, even with no existing units to match against.
            val step = when (resolved) {
                is ResolvedMedication.Existing -> {
                    val med = medicationRepository.medications.first().find { it.id == resolved.id }
                    val units = dispensableUnitRepository.formatsWithMedication.first()
                    val du = FuzzyMatcher.classifyDispensableUnits(
                        resolved.id, s.dosePerTablet.trim(), s.tabletsPerUnit.trim(), units,
                    )
                    DispensableUnitResolution(
                        resolved,
                        med?.brandName.orEmpty(),
                        med?.activeIngredient.orEmpty(),
                        du.candidates,
                        du.blocked,
                    )
                }
                // A brand-new medication has no existing dispensable units to match against.
                is ResolvedMedication.New -> DispensableUnitResolution(
                    resolved, resolved.brandName, resolved.activeIngredient, emptyList(), blocked = false,
                )
            }
            _state.update { it.copy(dispensableUnitStep = step) }
        }
    }

    /** Either an existing dispensable-unit id, or a request to create a new one. */
    private sealed interface ChosenUnit {
        data class Existing(val id: Long) : ChosenUnit
        data object New : ChosenUnit
    }

    /** Step 3: create the medication / dispensable unit as needed, then insert the script (+ prior dispensation). */
    private suspend fun persist(resolvedMedication: ResolvedMedication, chosenUnit: ChosenUnit) {
        val s = _state.value
        val medicationId = when (resolvedMedication) {
            is ResolvedMedication.Existing -> resolvedMedication.id
            is ResolvedMedication.New -> medicationRepository.add(
                Medication(
                    brandName = resolvedMedication.brandName,
                    activeIngredient = resolvedMedication.activeIngredient,
                ),
            )
        }
        val dispensableUnitId = when (chosenUnit) {
            is ChosenUnit.Existing -> chosenUnit.id
            ChosenUnit.New -> dispensableUnitRepository.add(
                DispensableUnit(
                    medicationId = medicationId,
                    dosePerTablet = s.dosePerTablet.trim(),
                    tabletsPerUnit = s.tabletsPerUnit.trim(),
                ),
            )
        }
        val scriptId = scriptRepository.add(
            Script(
                dispensableUnitId = dispensableUnitId,
                serialNo = s.serialNo.trim(),
                serialNo2 = s.serialNo2.trim(),
                dateOfIssue = s.dateOfIssue!!,
                repeats = s.repeats.toInt(),
                validToMillis = s.validToMillis!!,
                instructions = s.instructions.trim(),
            ),
        )
        val dispensed = s.priorDispensed.toInt()
        if (dispensed > 0) {
            dispensationRepository.add(
                Dispensation(
                    scriptId = scriptId,
                    dispensableUnitId = dispensableUnitId,
                    number = dispensed,
                    dispensedAtMillis = s.dateOfIssue,
                ),
            )
        }
        _state.update { AddScriptState() }   // clear the form for the next entry
        _saved.value = true                  // signal the screen to return to Scripts
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AitouiApp
                AddScriptViewModel(
                    app.scriptRepository,
                    app.medicationRepository,
                    app.dispensableUnitRepository,
                    app.dispensationRepository,
                    createSavedStateHandle().toRoute<ScriptRoute>(),
                )
            }
        }
    }
}
