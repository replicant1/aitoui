package com.example.aitoui.script

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.DoseUnit
import com.example.aitoui.data.Medication
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.R
import com.example.aitoui.dispensableunit.abbreviation
import com.example.aitoui.ui.AppTextField
import com.example.aitoui.ui.FieldRequirement
import com.example.aitoui.ui.UnsavedChangesDialog
import com.example.aitoui.ui.heading
import com.example.aitoui.ui.requiredFieldsNote
import com.example.aitoui.ui.theme.AitouiTheme
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun AddScriptRoot(
    onBack: () -> Unit,
    viewModel: AddScriptViewModel = viewModel(factory = AddScriptViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // After a script is saved (and both resolution dialogs are done), return to the Scripts screen.
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }
    AddScriptScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScriptScreen(
    state: AddScriptState,
    onAction: (AddScriptAction) -> Unit,
    onBack: () -> Unit,
) {
    var showIssuePicker by remember { mutableStateOf(false) }
    var showValidToPicker by remember { mutableStateOf(false) }
    var showLeavePrompt by remember { mutableStateOf(false) }
    // Guard both back affordances (arrow + system back): prompt to save when there are unsaved edits.
    val attemptBack = { if (state.hasUnsavedChanges) showLeavePrompt = true else onBack() }

    BackHandler(enabled = state.hasUnsavedChanges) { showLeavePrompt = true }
    if (showLeavePrompt) {
        UnsavedChangesDialog(
            canSave = state.canSave,
            onSave = { showLeavePrompt = false; onAction(AddScriptAction.Save) },
            onDiscard = { showLeavePrompt = false; onBack() },
            onCancel = { showLeavePrompt = false },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_script_appbar_title), modifier = Modifier.heading()) },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.add_script_back_button_cd),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onAction(AddScriptAction.Save) },
                        enabled = state.canSave,
                    ) {
                        Text(stringResource(R.string.add_script_save_button_label))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.add_script_description_text, requiredFieldsNote()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // The medication + dispensable unit, as raw fields — resolved to records on Save.
            AppTextField(
                value = state.brandName,
                onValueChange = { onAction(AddScriptAction.BrandNameChanged(it)) },
                label = stringResource(R.string.add_script_brand_name_label),
            )
            AppTextField(
                value = state.activeIngredient,
                onValueChange = { onAction(AddScriptAction.ActiveIngredientChanged(it)) },
                label = stringResource(R.string.add_script_active_ingredient_label),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTextField(
                    modifier = Modifier.weight(1f),
                    value = state.dosePerTablet,
                    onValueChange = { onAction(AddScriptAction.DosePerTabletChanged(it)) },
                    label = stringResource(R.string.add_script_dose_per_tablet_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                ExposedDropdownMenuBox(
                    modifier = Modifier.width(110.dp),
                    expanded = state.doseUnitMenuExpanded,
                    onExpandedChange = { onAction(AddScriptAction.ToggleDoseUnitMenu) },
                ) {
                    AppTextField(
                        value = state.selectedDoseUnit.abbreviation(),
                        onValueChange = {},
                        label = stringResource(R.string.app_dose_unit_label),
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = state.doseUnitMenuExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = state.doseUnitMenuExpanded,
                        onDismissRequest = { onAction(AddScriptAction.DismissDoseUnitMenu) },
                    ) {
                        DoseUnit.values().forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.abbreviation()) },
                                onClick = {
                                    onAction(AddScriptAction.DoseUnitSelected(unit))
                                    onAction(AddScriptAction.DismissDoseUnitMenu)
                                },
                            )
                        }
                    }
                }
            }
            AppTextField(
                value = state.tabletsPerUnit,
                onValueChange = { onAction(AddScriptAction.TabletsPerUnitChanged(it)) },
                label = stringResource(R.string.add_script_tablets_per_unit_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            AppTextField(
                value = state.serialNo,
                onValueChange = { onAction(AddScriptAction.SerialNoChanged(it)) },
                label = stringResource(R.string.add_script_prescription_number_label),
                requirement = FieldRequirement.Optional,
            )
            AppTextField(
                value = state.serialNo2,
                onValueChange = { onAction(AddScriptAction.SerialNo2Changed(it)) },
                label = stringResource(R.string.add_script_erx_token_label),
                requirement = FieldRequirement.Optional,
            )

            // Date of issue — read-only field that opens a date picker on tap.
            Box {
                AppTextField(
                    value = state.dateOfIssue?.let { formatDate(it) } ?: "",
                    onValueChange = {},
                    label = stringResource(R.string.add_script_date_of_issue_label),
                    readOnly = true,
                    placeholder = stringResource(R.string.add_script_select_date_placeholder),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showIssuePicker = true },
                )
            }

            AppTextField(
                value = state.priorDispensed,
                onValueChange = { onAction(AddScriptAction.PriorDispensedChanged(it)) },
                label = stringResource(R.string.add_script_times_dispensed_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            AppTextField(
                value = state.repeats,
                onValueChange = { onAction(AddScriptAction.RepeatsChanged(it)) },
                label = stringResource(R.string.add_script_repeats_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            // Valid to — read-only field that opens a date picker on tap.
            Box {
                AppTextField(
                    value = state.validToMillis?.let { formatDate(it) } ?: "",
                    onValueChange = {},
                    label = stringResource(R.string.add_script_valid_to_label),
                    readOnly = true,
                    placeholder = stringResource(R.string.add_script_select_date_placeholder),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showValidToPicker = true },
                )
            }

            AppTextField(
                value = state.instructions,
                onValueChange = { onAction(AddScriptAction.InstructionsChanged(it)) },
                label = stringResource(R.string.add_script_instructions_label),
                requirement = FieldRequirement.Optional,
                singleLine = false,
                placeholder = stringResource(R.string.add_script_instructions_placeholder),
            )
        }
    }

    // --- Save-time resolution dialogs ---

    // Serial-number clash — a blocking error, checked before any resolution begins.
    if (state.duplicateSerial) {
        AlertDialog(
            onDismissRequest = { onAction(AddScriptAction.DismissDuplicateSerial) },
            title = { Text(stringResource(R.string.add_script_duplicate_serial_title)) },
            text = {
                Text(stringResource(R.string.add_script_duplicate_serial_message))
            },
            confirmButton = {
                TextButton(onClick = { onAction(AddScriptAction.DismissDuplicateSerial) }) {
                    Text(stringResource(R.string.add_script_ok_button_label))
                }
            },
        )
    }

    state.medicationStep?.let { step ->
        MedicationResolutionDialog(
            knownMedications = step.candidates,
            newBrandName = state.brandName.trim(),
            newActiveIngredient = state.activeIngredient.trim(),
            blocked = step.blocked,
            onPickKnown = { onAction(AddScriptAction.PickMedication(it)) },
            onCreateNew = { onAction(AddScriptAction.CreateMedication) },
            onCancel = { onAction(AddScriptAction.CancelResolution) },
        )
    }

    state.dispensableUnitStep?.let { step ->
        DispensableUnitResolutionDialog(
            medicationBrandName = step.medicationBrandName,
            medicationActiveIngredient = step.medicationActiveIngredient,
            existingUnits = step.candidates,
            newDosePerTablet = state.dosePerTablet.trim(),
            newDoseUnit = state.selectedDoseUnit.abbreviation(),
            newTabletsPerUnit = state.tabletsPerUnit.trim(),
            blocked = step.blocked,
            onPickExisting = { onAction(AddScriptAction.PickDispensableUnit(it)) },
            onCreateNew = { onAction(AddScriptAction.CreateDispensableUnit) },
            onCancel = { onAction(AddScriptAction.CancelResolution) },
        )
    }

    if (showIssuePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = state.dateOfIssue)
        DatePickerDialog(
            onDismissRequest = { showIssuePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onAction(AddScriptAction.DateOfIssueChanged(dpState.selectedDateMillis))
                    showIssuePicker = false
                }) { Text(stringResource(R.string.add_script_ok_button_label)) }
            },
            dismissButton = {
                TextButton(onClick = { showIssuePicker = false }) {
                    Text(stringResource(R.string.add_script_cancel_button_label))
                }
            },
        ) {
            DatePicker(state = dpState)
        }
    }

    if (showValidToPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = state.validToMillis)
        DatePickerDialog(
            onDismissRequest = { showValidToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onAction(AddScriptAction.ValidToChanged(dpState.selectedDateMillis))
                    showValidToPicker = false
                }) { Text(stringResource(R.string.add_script_ok_button_label)) }
            },
            dismissButton = {
                TextButton(onClick = { showValidToPicker = false }) {
                    Text(stringResource(R.string.add_script_cancel_button_label))
                }
            },
        ) {
            DatePicker(state = dpState)
        }
    }
}

/** A single selection within [MedicationResolutionDialog]: an existing medication, or the entered "new" one. */
private sealed interface MedSelection {
    data class Known(val id: Long) : MedSelection
    data object New : MedSelection
}

/**
 * Resolve the entered medication against similar known ones. Every option — each known medication and the
 * entered "new" one — is a single-select radio card; the user picks one and presses Continue. When creating
 * a new medication is refused ([blocked]) the new card is omitted, so only a known one can be chosen.
 */
@Composable
private fun MedicationResolutionDialog(
    knownMedications: List<Medication>,
    newBrandName: String,
    newActiveIngredient: String,
    blocked: Boolean,
    onPickKnown: (Long) -> Unit,
    onCreateNew: () -> Unit,
    onCancel: () -> Unit,
) {
    var selection by remember { mutableStateOf<MedSelection?>(null) }

    val hasKnown = knownMedications.isNotEmpty()
    val showNew = !blocked

    val knownPrompt = stringResource(medResolutionKnownPrompt(knownMedications.size))
    val actionPrompt = stringResource(medResolutionActionPrompt(knownMedications.size, blocked))

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.add_script_med_resolution_title)) },
        text = {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (hasKnown) {
                    Text(knownPrompt, style = MaterialTheme.typography.bodyMedium)
                    knownMedications.forEach { med ->
                        MedicationChoiceCard(
                            brandName = med.brandName,
                            activeIngredient = med.activeIngredient,
                            isNew = false,
                            selected = selection == MedSelection.Known(med.id),
                            onSelect = { selection = MedSelection.Known(med.id) },
                        )
                    }
                }
                Text(actionPrompt, style = MaterialTheme.typography.bodyMedium)
                if (showNew) {
                    MedicationChoiceCard(
                        brandName = newBrandName,
                        activeIngredient = newActiveIngredient,
                        isNew = true,
                        selected = selection == MedSelection.New,
                        onSelect = { selection = MedSelection.New },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val sel = selection) {
                        is MedSelection.Known -> onPickKnown(sel.id)
                        MedSelection.New -> onCreateNew()
                        null -> Unit
                    }
                },
                enabled = selection != null,
            ) { Text(stringResource(R.string.add_script_continue_button_label)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.add_script_cancel_button_label)) }
        },
    )
}

/**
 * One selectable medication in [MedicationResolutionDialog], styled like a Medications-screen card (bold brand
 * over muted active ingredient) fronted by a radio button. Both known medications and the entered ("new") one
 * are filled cards; the new one carries a sparkle badge at the top-right to mark it as create-new.
 */
@Composable
private fun MedicationChoiceCard(
    brandName: String,
    activeIngredient: String,
    isNew: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = brandName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = activeIngredient,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isNew) {
                // Sparkle badge marks this as the "create a new medication" option.
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = stringResource(R.string.add_script_new_medication_badge_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

/** A single selection within [DispensableUnitResolutionDialog]: an existing dispensable unit, or the entered "new" one. */
private sealed interface DuSelection {
    data class Existing(val id: Long) : DuSelection
    data object New : DuSelection
}

/**
 * Resolve the entered dispensable unit against the medication's existing ones. Every option — each existing
 * dispensable unit and the entered "new" one — is a single-select radio card; the user picks one and presses
 * Continue. When creating a new dispensable unit is refused ([blocked] — a duplicate dose/pack already exists)
 * the new card is omitted, so only an existing one can be chosen.
 */
@Composable
private fun DispensableUnitResolutionDialog(
    medicationBrandName: String,
    medicationActiveIngredient: String,
    existingUnits: List<DispensableUnitDetails>,
    newDosePerTablet: String,
    newDoseUnit: String,
    newTabletsPerUnit: String,
    blocked: Boolean,
    onPickExisting: (Long) -> Unit,
    onCreateNew: () -> Unit,
    onCancel: () -> Unit,
) {
    var selection by remember { mutableStateOf<DuSelection?>(null) }

    val hasExisting = existingUnits.isNotEmpty()
    val showNew = !blocked

    val existingPrompt = stringResource(duResolutionExistingPrompt(existingUnits.size))
    val actionPrompt = stringResource(duResolutionActionPrompt(existingUnits.size, blocked))

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.add_script_du_resolution_title)) },
        text = {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (hasExisting) {
                    Text(existingPrompt, style = MaterialTheme.typography.bodyMedium)
                    existingUnits.forEach { unit ->
                        DispensableUnitChoiceCard(
                            brandName = medicationBrandName,
                            activeIngredient = medicationActiveIngredient,
                            dosePerTablet = unit.dosePerTablet,
                            doseUnit = unit.doseUnit,
                            tabletsPerUnit = unit.tabletsPerUnit,
                            isNew = false,
                            selected = selection == DuSelection.Existing(unit.formatId),
                            onSelect = { selection = DuSelection.Existing(unit.formatId) },
                        )
                    }
                }
                Text(actionPrompt, style = MaterialTheme.typography.bodyMedium)
                if (showNew) {
                    DispensableUnitChoiceCard(
                        brandName = medicationBrandName,
                        activeIngredient = medicationActiveIngredient,
                        dosePerTablet = newDosePerTablet,
                        doseUnit = newDoseUnit,
                        tabletsPerUnit = newTabletsPerUnit,
                        isNew = true,
                        selected = selection == DuSelection.New,
                        onSelect = { selection = DuSelection.New },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val sel = selection) {
                        is DuSelection.Existing -> onPickExisting(sel.id)
                        DuSelection.New -> onCreateNew()
                        null -> Unit
                    }
                },
                enabled = selection != null,
            ) { Text(stringResource(R.string.add_script_continue_button_label)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.add_script_cancel_button_label)) }
        },
    )
}

/**
 * One selectable dispensable unit in [DispensableUnitResolutionDialog], styled like a Dispensable Units screen
 * card minus the tablet-photo thumbnail: brand (bold) over active ingredient (muted) over the dose/pack line,
 * fronted by a radio button. Both existing units and the entered ("new") one are filled cards; the new one
 * carries a sparkle badge at the top-right.
 */
@Composable
private fun DispensableUnitChoiceCard(
    brandName: String,
    activeIngredient: String,
    dosePerTablet: String,
    doseUnit: String,
    tabletsPerUnit: String,
    isNew: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = brandName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = activeIngredient,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.add_script_du_format_text,
                            dosePerTablet,
                            doseUnit,
                            tabletsPerUnit,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (isNew) {
                // Sparkle badge marks this as the "create a new dispensable unit" option.
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = stringResource(R.string.add_script_new_dispensable_unit_badge_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

/** Format a UTC epoch-millis date (as produced by the Material date picker) as "dd MMM yyyy". */
private fun formatDate(millis: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(millis)
}

@Preview(showBackground = true)
@Composable
private fun AddScriptScreenPreview() {
    AitouiTheme {
        AddScriptScreen(
            state = AddScriptState(
                brandName = "Tensig",
                activeIngredient = "Atenolol",
                dosePerTablet = "50",
                tabletsPerUnit = "60",
                serialNo = "PW2048022",
                serialNo2 = "1TV4J832WYJHBHY3D8",
                dateOfIssue = 0L,
                repeats = "5",
                validToMillis = 0L,
                instructions = "Take ONE tablet TWICE a day as directed",
            ),
            onAction = {},
            onBack = {},
        )
    }
}

private val previewKnownMedications = listOf(
    Medication(id = 1, brandName = "Tensig", activeIngredient = "Atenolol"),
    Medication(id = 2, brandName = "Noten", activeIngredient = "Atenolol"),
)

@Preview(name = "Med dialog · many matches, not blocked", showBackground = true)
@Composable
private fun MedicationResolutionDialogManyPreview() {
    AitouiTheme {
        MedicationResolutionDialog(
            knownMedications = previewKnownMedications,
            newBrandName = "Tenoret",
            newActiveIngredient = "Atenolol",
            blocked = false,
            onPickKnown = {},
            onCreateNew = {},
            onCancel = {},
        )
    }
}

@Preview(name = "Med dialog · no matches", showBackground = true)
@Composable
private fun MedicationResolutionDialogNoMatchPreview() {
    AitouiTheme {
        MedicationResolutionDialog(
            knownMedications = emptyList(),
            newBrandName = "Ventolin",
            newActiveIngredient = "Salbutamol",
            blocked = false,
            onPickKnown = {},
            onCreateNew = {},
            onCancel = {},
        )
    }
}

@Preview(name = "Med dialog · one match, blocked", showBackground = true)
@Composable
private fun MedicationResolutionDialogBlockedPreview() {
    AitouiTheme {
        MedicationResolutionDialog(
            knownMedications = previewKnownMedications.take(1),
            newBrandName = "Tensig",
            newActiveIngredient = "Atenolol",
            blocked = true,
            onPickKnown = {},
            onCreateNew = {},
            onCancel = {},
        )
    }
}

private val previewExistingUnits = listOf(
    DispensableUnitDetails(
        formatId = 1, medicationId = 1, brandName = "Tensig", activeIngredient = "Atenolol",
        dosePerTablet = "50", tabletsPerUnit = "60", imagePath = null,
    ),
    DispensableUnitDetails(
        formatId = 2, medicationId = 1, brandName = "Tensig", activeIngredient = "Atenolol",
        dosePerTablet = "100", tabletsPerUnit = "30", imagePath = null,
    ),
)

@Preview(name = "DU dialog · many existing, not blocked", showBackground = true)
@Composable
private fun DispensableUnitResolutionDialogManyPreview() {
    AitouiTheme {
        DispensableUnitResolutionDialog(
            medicationBrandName = "Tensig",
            medicationActiveIngredient = "Atenolol",
            existingUnits = previewExistingUnits,
            newDosePerTablet = "25",
            newDoseUnit = DoseUnit.MILLIGRAMS.abbreviation(),
            newTabletsPerUnit = "90",
            blocked = false,
            onPickExisting = {},
            onCreateNew = {},
            onCancel = {},
        )
    }
}

@Preview(name = "DU dialog · no existing units", showBackground = true)
@Composable
private fun DispensableUnitResolutionDialogNonePreview() {
    AitouiTheme {
        DispensableUnitResolutionDialog(
            medicationBrandName = "Ventolin",
            medicationActiveIngredient = "Salbutamol",
            existingUnits = emptyList(),
            newDosePerTablet = "50",
            newDoseUnit = DoseUnit.MICROGRAMS.abbreviation(),
            newTabletsPerUnit = "60",
            blocked = false,
            onPickExisting = {},
            onCreateNew = {},
            onCancel = {},
        )
    }
}

@Preview(name = "DU dialog · one existing, blocked", showBackground = true)
@Composable
private fun DispensableUnitResolutionDialogBlockedPreview() {
    AitouiTheme {
        DispensableUnitResolutionDialog(
            medicationBrandName = "Tensig",
            medicationActiveIngredient = "Atenolol",
            existingUnits = previewExistingUnits.take(1),
            newDosePerTablet = "50",
            newDoseUnit = DoseUnit.MILLIGRAMS.abbreviation(),
            newTabletsPerUnit = "60",
            blocked = true,
            onPickExisting = {},
            onCreateNew = {},
            onCancel = {},
        )
    }
}
