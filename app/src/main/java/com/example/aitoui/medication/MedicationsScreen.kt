package com.example.aitoui.medication

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
import com.example.aitoui.data.Medication
import com.example.aitoui.ui.theme.AitouiTheme

@Composable
fun MedicationsRoot(
    onBack: () -> Unit,
    onAddMedication: () -> Unit,
    viewModel: MedicationsViewModel = viewModel(factory = MedicationsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MedicationsScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onAddMedication = onAddMedication,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationsScreen(
    state: MedicationsState,
    onAction: (MedicationsAction) -> Unit,
    onBack: () -> Unit,
    onAddMedication: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Medications") },
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
            FloatingActionButton(onClick = onAddMedication) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add medication")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = "These are all the medications you've recorded — each identified by its " +
                    "brand name and active ingredient.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.medications, key = { it.id }) { medication ->
                    MedicationRow(
                        medication = medication,
                        onDeleteClick = { onAction(MedicationsAction.DeleteTapped(medication.id)) },
                    )
                }
            }
        }
    }

    // Confirm before deleting a medication.
    state.pendingDeleteMedication?.let { medication ->
        AlertDialog(
            onDismissRequest = { onAction(MedicationsAction.CancelDelete) },
            title = { Text("Delete medication?") },
            text = {
                Text(
                    "Delete ${medication.brandName}? This also removes its dispensable units, " +
                        "scripts and dispensation history, and cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(MedicationsAction.ConfirmDelete) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(MedicationsAction.CancelDelete) }) { Text("Cancel") }
            },
        )
    }
}

/** A single medication: brand name (prominent) over active ingredient (muted), with a delete icon. */
@Composable
private fun MedicationRow(
    medication: Medication,
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
                Text(
                    text = medication.brandName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = medication.activeIngredient,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Delete medication")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MedicationsScreenPreview() {
    AitouiTheme {
        MedicationsScreen(
            state = MedicationsState(
                medications = listOf(
                    Medication(1, "Panadol", "Paracetamol"),
                    Medication(2, "Nurofen", "Ibuprofen"),
                    Medication(3, "Amoxil", "Amoxicillin"),
                ),
            ),
            onAction = {},
            onBack = {},
            onAddMedication = {},
        )
    }
}
