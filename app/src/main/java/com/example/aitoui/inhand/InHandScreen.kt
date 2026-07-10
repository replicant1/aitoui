package com.example.aitoui.inhand

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedCard
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
fun InHandRoot(
    onBack: () -> Unit,
    viewModel: InHandViewModel = viewModel(factory = InHandViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InHandScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InHandScreen(
    state: InHandState,
    onAction: (InHandAction) -> Unit,
    onBack: () -> Unit,
) {
    var medicationExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("In Hand") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onAction(InHandAction.Save) }) {
                        Text("SAVE")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "These are the tablets currently in your hand — dispensed, but not yet taken.",
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
                                onAction(InHandAction.MedicationSelected(medication.id))
                                medicationExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.numberOfTablets,
                onValueChange = { onAction(InHandAction.NumberOfTabletsChanged(it)) },
                label = { Text("Number of tablets") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onAction(InHandAction.Add) },
                enabled = state.canAdd,
            ) {
                Text("ADD")
            }

            Text(
                text = "Tablets in hand:",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.tabletsInHand, key = { it.id }) { entry ->
                        val selected = entry.id == state.selectedId
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAction(InHandAction.RowSelected(entry.id)) }
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            Button(
                onClick = { onAction(InHandAction.Delete) },
                enabled = state.canDelete,
            ) {
                Text("DELETE")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InHandScreenPreview() {
    AitouiTheme {
        InHandScreen(
            state = InHandState(
                medications = listOf(
                    Medication(1, "Panadol", "Paracetamol"),
                    Medication(2, "Nurofen", "Ibuprofen"),
                ),
                tabletsInHand = listOf(
                    InHandEntry(0, 1, "Panadol", "24"),
                    InHandEntry(1, 2, "Nurofen", "16"),
                ),
                selectedId = 1,
            ),
            onAction = {},
            onBack = {},
        )
    }
}
