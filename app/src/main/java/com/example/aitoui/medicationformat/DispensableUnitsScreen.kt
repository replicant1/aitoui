package com.example.aitoui.medicationformat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.data.MedicationFormatDetails
import com.example.aitoui.ui.theme.AitouiTheme

@Composable
fun DispensableUnitsRoot(
    onBack: () -> Unit,
    onAddDispensableUnit: () -> Unit,
    viewModel: DispensableUnitsViewModel = viewModel(factory = DispensableUnitsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DispensableUnitsScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onAddDispensableUnit = onAddDispensableUnit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispensableUnitsScreen(
    state: DispensableUnitsState,
    onAction: (DispensableUnitsAction) -> Unit,
    onBack: () -> Unit,
    onAddDispensableUnit: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Dispensable Units") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDispensableUnit) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add dispensable unit")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = "These are all the dispensable units — the specific formats (dose and pack " +
                    "size) in which your medications come.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.units, key = { it.formatId }) { unit ->
                    DispensableUnitRow(
                        unit = unit,
                        onDeleteClick = { onAction(DispensableUnitsAction.DeleteTapped(unit.formatId)) },
                    )
                }
            }
        }
    }

    // Confirm before deleting a dispensable unit.
    state.pendingDeleteUnit?.let { unit ->
        AlertDialog(
            onDismissRequest = { onAction(DispensableUnitsAction.CancelDelete) },
            title = { Text("Delete dispensable unit?") },
            text = {
                Text(
                    "Delete ${unit.brandName} (${unit.dosePerTablet}mg)? This also removes its " +
                        "scripts and dispensation history, and cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(DispensableUnitsAction.ConfirmDelete) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(DispensableUnitsAction.CancelDelete) }) { Text("Cancel") }
            },
        )
    }
}

/**
 * A single dispensable unit. The medication component (brand name over active ingredient) mirrors the
 * Medications screen; a third line adds the unit's dose and pack size. Delete icon at top-right.
 */
@Composable
private fun DispensableUnitRow(
    unit: MedicationFormatDetails,
    onDeleteClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            ) {
                // Medication component — as presented on the Medications screen.
                Text(
                    text = unit.brandName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = unit.activeIngredient,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Dispensable-unit specifics.
                Text(
                    text = "${unit.dosePerTablet}mg × Qty ${unit.tabletsPerUnit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Delete dispensable unit")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DispensableUnitsScreenPreview() {
    AitouiTheme {
        DispensableUnitsScreen(
            state = DispensableUnitsState(
                units = listOf(
                    MedicationFormatDetails(1, 1, "Panadol", "Paracetamol", "500", "24"),
                    MedicationFormatDetails(2, 2, "Nurofen", "Ibuprofen", "200", "16"),
                    MedicationFormatDetails(3, 3, "Ventolin", "Salbutamol", "100", "200"),
                ),
            ),
            onAction = {},
            onBack = {},
            onAddDispensableUnit = {},
        )
    }
}
