package com.example.aitoui.dailyschedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.image.ImageStore
import com.example.aitoui.ui.theme.AitouiTheme

@Composable
fun DailyScheduleRoot(
    onBack: () -> Unit,
    viewModel: DailyScheduleViewModel = viewModel(factory = DailyScheduleViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DailyScheduleScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyScheduleScreen(
    state: DailyScheduleState,
    onAction: (DailyScheduleAction) -> Unit,
    onBack: () -> Unit,
) {
    var medicationExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Daily Schedule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onAction(DailyScheduleAction.Save) }) {
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
                text = "This is the number and type of tablets that you take every day.",
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
                    supportingText = if (state.units.isEmpty()) {
                        { Text("No dispensable units yet — add one first") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = medicationExpanded,
                    onDismissRequest = { medicationExpanded = false },
                ) {
                    val context = LocalContext.current
                    state.units.forEach { unit ->
                        DropdownMenuItem(
                            leadingIcon = {
                                unit.imagePath?.let { imagePath ->
                                    AsyncImage(
                                        model = ImageStore.fileFor(context, imagePath),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                    )
                                }
                            },
                            text = { Text(unit.label, fontWeight = FontWeight.Normal) },
                            onClick = {
                                onAction(DailyScheduleAction.MedicationSelected(unit.formatId))
                                medicationExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.numberOfTablets,
                onValueChange = { onAction(DailyScheduleAction.NumberOfTabletsChanged(it)) },
                label = { Text("Number of tablets") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onAction(DailyScheduleAction.Add) },
                enabled = state.canAdd,
            ) {
                Text("ADD")
            }

            Text(
                text = "Tablets taken daily:",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val context = LocalContext.current
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.tabletsTaken, key = { it.id }) { entry ->
                        val selected = entry.id == state.selectedId
                        // Dose and photo come from the medication's dispensable unit, looked up live.
                        val unit = state.units.firstOrNull { it.medicationId == entry.medicationId }
                        val dose = unit?.dosePerTablet?.let { " ($it" + "mg)" } ?: ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAction(DailyScheduleAction.RowSelected(entry.id)) }
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            unit?.imagePath?.let { imagePath ->
                                AsyncImage(
                                    model = ImageStore.fileFor(context, imagePath),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                )
                            }
                            Text(
                                text = "${entry.brand}$dose × ${entry.number}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { onAction(DailyScheduleAction.Delete) },
                enabled = state.canDelete,
            ) {
                Text("DELETE")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyScheduleScreenPreview() {
    AitouiTheme {
        DailyScheduleScreen(
            state = DailyScheduleState(
                units = listOf(
                    DispensableUnitDetails(1, 1, "Panadol", "Paracetamol", "500", "24", null),
                    DispensableUnitDetails(2, 2, "Nurofen", "Ibuprofen", "200", "16", null),
                ),
                tabletsTaken = listOf(
                    DailyScheduleEntry(0, 1, "Panadol", "2"),
                    DailyScheduleEntry(1, 2, "Nurofen", "0.5"),
                ),
                selectedId = 1,
            ),
            onAction = {},
            onBack = {},
        )
    }
}
