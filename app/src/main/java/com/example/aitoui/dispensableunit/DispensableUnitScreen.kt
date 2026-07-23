package com.example.aitoui.dispensableunit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.aitoui.R
import com.example.aitoui.data.Medication
import com.example.aitoui.data.DoseUnit
import com.example.aitoui.ui.AppTextField
import com.example.aitoui.ui.UnsavedChangesDialog
import com.example.aitoui.ui.heading
import com.example.aitoui.ui.requiredFieldsNote
import com.example.aitoui.ui.theme.AitouiTheme
import androidx.compose.ui.res.stringResource

@Composable
fun DispensableUnitRoot(
    onBack: () -> Unit,
    viewModel: DispensableUnitViewModel = viewModel(factory = DispensableUnitViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // After a dispensable unit is saved, return to the Dispensable Units screen.
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }
    DispensableUnitScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispensableUnitScreen(
    state: DispensableUnitState,
    onAction: (DispensableUnitAction) -> Unit,
    onBack: () -> Unit,
) {
    var medicationExpanded by remember { mutableStateOf(false) }
    var showLeavePrompt by remember { mutableStateOf(false) }
    // Guard both back affordances (arrow + system back): prompt to save when there are unsaved edits.
    val attemptBack = { if (state.hasUnsavedChanges) showLeavePrompt = true else onBack() }

    BackHandler(enabled = state.hasUnsavedChanges) { showLeavePrompt = true }
    if (showLeavePrompt) {
        UnsavedChangesDialog(
            canSave = state.canSave,
            onSave = { showLeavePrompt = false; onAction(DispensableUnitAction.Save) },
            onDiscard = { showLeavePrompt = false; onBack() },
            onCancel = { showLeavePrompt = false },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dispensable_unit_appbar_title), modifier = Modifier.heading()) },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dispensable_unit_back_button_cd),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onAction(DispensableUnitAction.Save) },
                        enabled = state.canSave,
                    ) {
                        Text(stringResource(R.string.dispensable_unit_save_button_label))
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
                text = stringResource(R.string.dispensable_unit_description_text, requiredFieldsNote()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Medication — dropdown of existing Medication records.
            ExposedDropdownMenuBox(
                expanded = medicationExpanded,
                onExpandedChange = { medicationExpanded = !medicationExpanded },
            ) {
                AppTextField(
                    value = state.selectedMedicationName,
                    onValueChange = {},
                    label = stringResource(R.string.dispensable_unit_medication_label),
                    readOnly = true,
                    placeholder = stringResource(R.string.dispensable_unit_medication_placeholder),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = medicationExpanded) },
                    supportingText = if (state.medications.isEmpty()) {
                        stringResource(R.string.dispensable_unit_no_medications_supporting_text)
                    } else null,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = medicationExpanded,
                    onDismissRequest = { medicationExpanded = false },
                ) {
                    state.medications.forEach { medication ->
                        DropdownMenuItem(
                            text = { Text("${medication.brandName} (${medication.activeIngredient})") },
                            onClick = {
                                onAction(DispensableUnitAction.MedicationSelected(medication.id))
                                medicationExpanded = false
                            },
                        )
                    }
                }
            }

            // Dose per tablet with unit selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTextField(
                    modifier = Modifier.weight(1f),
                    value = state.dosePerTablet,
                    onValueChange = { onAction(DispensableUnitAction.DosePerTabletChanged(it)) },
                    label = stringResource(R.string.dispensable_unit_dose_per_tablet_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )

                ExposedDropdownMenuBox(
                    modifier = Modifier.width(110.dp),
                    expanded = state.doseUnitMenuExpanded,
                    onExpandedChange = {
                        onAction(DispensableUnitAction.ToggleDoseUnitMenu)
                    },
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
                        onDismissRequest = { onAction(DispensableUnitAction.DismissDoseUnitMenu) },
                    ) {
                        DoseUnit.values().forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.abbreviation()) },
                                onClick = {
                                    onAction(DispensableUnitAction.DoseUnitSelected(unit))
                                    onAction(DispensableUnitAction.DismissDoseUnitMenu)
                                },
                            )
                        }
                    }
                }
            }

            AppTextField(
                value = state.tabletsPerUnit,
                onValueChange = { onAction(DispensableUnitAction.TabletsPerUnitChanged(it)) },
                label = stringResource(R.string.dispensable_unit_tablets_per_unit_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DispensableUnitScreenPreview() {
    AitouiTheme {
        DispensableUnitScreen(
            state = DispensableUnitState(
                medications = listOf(Medication(1, "Panadol", "Paracetamol")),
                selectedMedicationId = 1,
                dosePerTablet = "500",
                tabletsPerUnit = "24",
            ),
            onAction = {},
            onBack = {},
        )
    }
}
