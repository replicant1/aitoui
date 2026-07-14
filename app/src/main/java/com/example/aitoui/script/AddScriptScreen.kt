package com.example.aitoui.script

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
        ResolutionDialog(
            title = "Is this the medication?",
            body = "Similar medications already exist. Pick one, or create a new medication.",
            candidateLabels = step.candidates.map { it.id to "${it.brandName} (${it.activeIngredient})" },
            blocked = step.blocked,
            blockedMessage = "Your entry is too similar to an existing medication — pick one above.",
            onPick = { onAction(AddScriptAction.PickMedication(it)) },
            onCreate = { onAction(AddScriptAction.CreateMedication) },
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

/** A pick-an-existing-or-create-new dialog used for both the medication and dispensable-unit steps. */
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
