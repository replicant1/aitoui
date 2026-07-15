package com.example.aitoui.script

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aitoui.data.Medication
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.ui.heading
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Script", modifier = Modifier.heading()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onAction(AddScriptAction.Save) },
                        enabled = state.canSave,
                    ) {
                        Text("Save")
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
                text = "A Script is an authorization from your doctor for the chemist to " +
                    "dispense to you a particular medication in a particular format. On Save, the app " +
                    "matches the medication and dispensable unit to your existing records.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // The medication + dispensable unit, as raw fields — resolved to records on Save.
            OutlinedTextField(
                value = state.brandName,
                onValueChange = { onAction(AddScriptAction.BrandNameChanged(it)) },
                label = { Text("Brand name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.activeIngredient,
                onValueChange = { onAction(AddScriptAction.ActiveIngredientChanged(it)) },
                label = { Text("Active ingredient") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.dosePerTablet,
                onValueChange = { onAction(AddScriptAction.DosePerTabletChanged(it)) },
                label = { Text("Milligrams per tablet") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.tabletsPerUnit,
                onValueChange = { onAction(AddScriptAction.TabletsPerUnitChanged(it)) },
                label = { Text("Tablets per unit") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.serialNo,
                onValueChange = { onAction(AddScriptAction.SerialNoChanged(it)) },
                label = { Text("Serial number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.serialNo2,
                onValueChange = { onAction(AddScriptAction.SerialNo2Changed(it)) },
                label = { Text("Serial number 2") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Date of issue — read-only field that opens a date picker on tap.
            Box {
                OutlinedTextField(
                    value = state.dateOfIssue?.let { formatDate(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date of issue") },
                    placeholder = { Text("Select a date") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showIssuePicker = true },
                )
            }

            OutlinedTextField(
                value = state.repeats,
                onValueChange = { onAction(AddScriptAction.RepeatsChanged(it)) },
                label = { Text("Number of repeats") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            // Valid to — read-only field that opens a date picker on tap.
            Box {
                OutlinedTextField(
                    value = state.validToMillis?.let { formatDate(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Valid to") },
                    placeholder = { Text("Select a date") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showValidToPicker = true },
                )
            }

            OutlinedTextField(
                value = state.instructions,
                onValueChange = { onAction(AddScriptAction.InstructionsChanged(it)) },
                label = { Text("Instructions") },
                placeholder = { Text("e.g. Take ONE tablet TWICE a day as directed") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // --- Save-time resolution dialogs ---

    // Serial-number clash — a blocking error, checked before any resolution begins.
    if (state.duplicateSerial) {
        AlertDialog(
            onDismissRequest = { onAction(AddScriptAction.DismissDuplicateSerial) },
            title = { Text("Serial number already used") },
            text = {
                Text(
                    "A script with this serial number already exists. Every script must have a unique " +
                        "serial number, so this one can't be saved. Change the serial number and try again.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(AddScriptAction.DismissDuplicateSerial) }) { Text("OK") }
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
        ResolutionDialog(
            title = "Is this the dispensable unit?",
            body = "This medication already has dispensable units. Pick one, or create a new dispensable unit.",
            candidateLabels = step.candidates.map { it.formatId to "${it.dosePerTablet}mg × ${it.tabletsPerUnit} per unit" },
            blocked = step.blocked,
            blockedMessage = "A dispensable unit with this dose and pack size already exists — pick it above.",
            onPick = { onAction(AddScriptAction.PickDispensableUnit(it)) },
            onCreate = { onAction(AddScriptAction.CreateDispensableUnit) },
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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showIssuePicker = false }) { Text("Cancel") }
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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showValidToPicker = false }) { Text("Cancel") }
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
    val single = knownMedications.size == 1
    val showNew = !blocked

    val knownPrompt = if (single) {
        "The medication you entered is similar to the following known medication:"
    } else {
        "The medication you entered is similar to the following known medications:"
    }
    val actionPrompt = when {
        !hasKnown -> "Select the new medication below:"
        blocked && single -> "Select the known medication above."
        blocked -> "Select a known medication above."
        single -> "Select the known medication above or select the new medication below:"
        else -> "Select a known medication above or select the new medication below:"
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Resolve medication") },
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
            ) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
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
                    contentDescription = "New medication",
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

/** A pick-an-existing-or-create-new dialog used for the dispensable-unit step. */
@Composable
private fun ResolutionDialog(
    title: String,
    body: String,
    candidateLabels: List<Pair<Long, String>>,
    blocked: Boolean,
    blockedMessage: String,
    onPick: (Long) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                candidateLabels.forEach { (id, label) ->
                    Surface(
                        onClick = { onPick(id) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label, modifier = Modifier.padding(12.dp))
                    }
                }
                if (blocked) {
                    Text(
                        text = blockedMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate, enabled = !blocked) { Text("Create new") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
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
