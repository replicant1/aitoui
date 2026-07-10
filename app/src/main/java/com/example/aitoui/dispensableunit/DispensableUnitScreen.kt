package com.example.aitoui.dispensableunit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.example.aitoui.data.Medication
import com.example.aitoui.ui.theme.AitouiTheme

@Composable
fun DispensableUnitRoot(
    onBack: () -> Unit,
    viewModel: DispensableUnitViewModel = viewModel(factory = DispensableUnitViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Dispensable Unit") },
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
                        onClick = { onAction(DispensableUnitAction.Save) },
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
                text = "A Dispensable Unit is a particular packaging and presentation of a " +
                    "medication. Typically a box or bottle of a capsule or tablet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Medication — dropdown of existing Medication records.
            ExposedDropdownMenuBox(
                expanded = medicationExpanded,
                onExpandedChange = { medicationExpanded = !medicationExpanded },
            ) {
                OutlinedTextField(
                    value = state.selectedMedicationName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Medication") },
                    placeholder = { Text("Select a medication") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = medicationExpanded) },
                    supportingText = if (state.medications.isEmpty()) {
                        { Text("No medications yet — add one first") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
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

            OutlinedTextField(
                value = state.dosePerTablet,
                onValueChange = { onAction(DispensableUnitAction.DosePerTabletChanged(it)) },
                label = { Text("Dose per tablet") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.tabletsPerUnit,
                onValueChange = { onAction(DispensableUnitAction.TabletsPerUnitChanged(it)) },
                label = { Text("Tablets per unit") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
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
