package com.example.aitoui.dailyschedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.aitoui.R
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.image.ImageStore
import com.example.aitoui.ui.AppTextField
import com.example.aitoui.ui.UnsavedChangesDialog
import com.example.aitoui.ui.heading
import com.example.aitoui.ui.requiredFieldsNote
import com.example.aitoui.ui.selectableRow
import com.example.aitoui.ui.theme.AitouiTheme
import com.example.aitoui.ui.theme.ThumbnailShape

internal const val DS_TABLETS_FIELD_TAG = "ds_number_of_tablets"

@Composable
fun DailyScheduleRoot(
    onBack: () -> Unit,
    viewModel: DailyScheduleViewModel = viewModel(factory = DailyScheduleViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // After the schedule is saved, return to the Main screen.
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }
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
    var showLeavePrompt by remember { mutableStateOf(false) }
    // Guard both back affordances (arrow + system back): prompt to save when there are unsaved edits.
    val attemptBack = { if (state.hasUnsavedChanges) showLeavePrompt = true else onBack() }
    // Shared width so ADD and DELETE are the same size (DELETE is the wider label).
    val actionButtonWidth = 120.dp

    BackHandler(enabled = state.hasUnsavedChanges) { showLeavePrompt = true }
    if (showLeavePrompt) {
        UnsavedChangesDialog(
            canSave = true,
            onSave = { showLeavePrompt = false; onAction(DailyScheduleAction.Save) },
            onDiscard = { showLeavePrompt = false; onBack() },
            onCancel = { showLeavePrompt = false },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.daily_schedule_appbar_title), modifier = Modifier.heading()) },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.daily_schedule_back_button_cd),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onAction(DailyScheduleAction.Save) }) {
                        Text(stringResource(R.string.daily_schedule_save_button_label))
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
                text = stringResource(
                    R.string.daily_schedule_description_text,
                    requiredFieldsNote(),
                ),
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
                    label = stringResource(R.string.daily_schedule_unit_label),
                    readOnly = true,
                    placeholder = stringResource(R.string.daily_schedule_unit_placeholder),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = medicationExpanded) },
                    supportingText = if (state.units.isEmpty()) {
                        stringResource(R.string.daily_schedule_no_units_supporting_text)
                    } else null,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
                                            .clip(ThumbnailShape),
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

            AppTextField(
                modifier = Modifier.testTag(DS_TABLETS_FIELD_TAG),
                value = state.numberOfTablets,
                onValueChange = { onAction(DailyScheduleAction.NumberOfTabletsChanged(it)) },
                label = stringResource(R.string.daily_schedule_number_of_tablets_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Button(
                onClick = { onAction(DailyScheduleAction.Add) },
                enabled = state.canAdd,
                modifier = Modifier.width(actionButtonWidth),
            ) {
                Text(stringResource(R.string.daily_schedule_add_button_label))
            }

            // Total tablets taken daily, appended to the title (omitted when zero).
            val totalTablets = state.tabletsTaken.sumOf { it.number.toDoubleOrNull() ?: 0.0 }
            val totalLabel =
                if (totalTablets == totalTablets.toLong().toDouble()) totalTablets.toLong().toString()
                else totalTablets.toString()
            Text(
                text = if (totalTablets > 0.0) {
                    stringResource(R.string.daily_schedule_tablets_with_total_label, totalLabel)
                } else {
                    stringResource(R.string.daily_schedule_tablets_label)
                },
                // Polite live region so a screen reader hears the updated total after Add/Delete.
                modifier = Modifier
                    .heading()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val context = LocalContext.current
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.tabletsTaken, key = { it.id }) { entry ->
                        val selected = entry.id == state.selectedId
                        // Dose and photo come from the medication's dispensable unit, looked up live.
                        val unit = state.units.firstOrNull { it.formatId == entry.dispensableUnitId }
                        val dose = unit?.dosePerTablet?.let {
                            stringResource(R.string.daily_schedule_dose_suffix, it, unit.doseUnit)
                        } ?: ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableRow(selected = selected) {
                                    onAction(DailyScheduleAction.RowSelected(entry.id))
                                }
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 16.dp, vertical = 6.dp),
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
                                        .clip(ThumbnailShape),
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.daily_schedule_tablet_entry_format,
                                    entry.brand,
                                    dose,
                                    entry.number,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { onAction(DailyScheduleAction.Delete) },
                enabled = state.canDelete,
                modifier = Modifier.width(actionButtonWidth),
            ) {
                Text(stringResource(R.string.daily_schedule_delete_button_label))
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
