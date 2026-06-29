package com.example.aitoui.dispense

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.aitoui.data.MedicationFormatDetails
import com.example.aitoui.ui.theme.AitouiTheme

@Composable
fun DispenseRoot(
    onBack: () -> Unit,
    viewModel: DispenseViewModel = viewModel(factory = DispenseViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DispenseScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispenseScreen(
    state: DispenseState,
    onAction: (DispenseAction) -> Unit,
    onBack: () -> Unit,
) {
    var formatExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Dispense") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onAction(DispenseAction.Save) }) {
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Record a trip to the pharmacy where your scripts are filled. Add each " +
                    "medication format dispensed and the number received, then Save.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Medication Format — dropdown of existing Medication Format records.
            ExposedDropdownMenuBox(
                expanded = formatExpanded,
                onExpandedChange = { formatExpanded = !formatExpanded },
            ) {
                OutlinedTextField(
                    value = state.selectedFormatLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Medication Format") },
                    placeholder = { Text("Select a medication format") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                    supportingText = if (state.formats.isEmpty()) {
                        { Text("No medication formats yet — add one first") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = formatExpanded,
                    onDismissRequest = { formatExpanded = false },
                ) {
                    state.formats.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(format.label) },
                            onClick = {
                                onAction(DispenseAction.FormatSelected(format.formatId))
                                formatExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.number,
                onValueChange = { onAction(DispenseAction.NumberChanged(it)) },
                label = { Text("Number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onAction(DispenseAction.Add) },
                enabled = state.canAdd,
            ) {
                Text("ADD")
            }

            Text(
                text = "Dispensations:",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.dispensations, key = { it.id }) { entry ->
                        val selected = entry.id == state.selectedId
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAction(DispenseAction.RowSelected(entry.id)) }
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
                onClick = { onAction(DispenseAction.Delete) },
                enabled = state.canDelete,
            ) {
                Text("DELETE")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DispenseScreenPreview() {
    AitouiTheme {
        DispenseScreen(
            state = DispenseState(
                formats = listOf(
                    MedicationFormatDetails(1, 1, "Panadol", "Paracetamol", "500", "24", 2, 6),
                ),
                dispensations = listOf(
                    DispensationEntry(0, 1, "Panadol (500mg)", "3"),
                    DispensationEntry(1, 1, "Amoxil (500mg)", "1"),
                ),
                selectedId = 1,
            ),
            onAction = {},
            onBack = {},
        )
    }
}
