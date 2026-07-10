package com.example.aitoui.script

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.example.aitoui.data.DispensableUnitDetails
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
    var typeExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Script") },
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
                    "dispense to you a particular medication in a particular format.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Dispensable Unit — the single dispensable unit this script is for.
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = !typeExpanded },
            ) {
                OutlinedTextField(
                    value = state.selectedFormatLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Dispensable Unit") },
                    placeholder = { Text("Select a dispensable unit") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    supportingText = if (state.dispensableUnits.isEmpty()) {
                        { Text("No dispensable units yet — add one first") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false },
                ) {
                    state.dispensableUnits.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(format.label) },
                            onClick = {
                                onAction(AddScriptAction.DispensableUnitSelected(format.formatId))
                                typeExpanded = false
                            },
                        )
                    }
                }
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
                        .clickable { showDatePicker = true },
                )
            }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = state.validToMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onAction(AddScriptAction.ValidToChanged(dpState.selectedDateMillis))
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dpState)
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
                dispensableUnits = listOf(
                    DispensableUnitDetails(1, 1, "Panadol", "Paracetamol", "500", "24"),
                ),
                selectedFormatId = 1,
                repeats = "2",
                validToMillis = 0L,
            ),
            onAction = {},
            onBack = {},
        )
    }
}
