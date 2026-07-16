package com.example.aitoui.inhand

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.Medication
import com.example.aitoui.image.ImageStore
import com.example.aitoui.ui.AppTextField
import com.example.aitoui.ui.REQUIRED_FIELDS_NOTE
import com.example.aitoui.ui.heading
import com.example.aitoui.ui.selectableRow
import com.example.aitoui.ui.theme.AitouiTheme
import com.example.aitoui.ui.theme.ThumbnailShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun InHandRoot(
    onBack: () -> Unit,
    onCountTablets: () -> Unit,
    onCountBlisters: () -> Unit,
    countedTablets: Int?,
    onCountedConsumed: () -> Unit,
    viewModel: InHandViewModel = viewModel(factory = InHandViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current

    // A count returned from the camera screen lands in the "Number of tablets" field.
    LaunchedEffect(countedTablets) {
        countedTablets?.let {
            viewModel.onAction(InHandAction.TabletsCounted(it))
            // The field is populated without the user typing, so a screen reader would otherwise get no
            // cue. A one-shot announcement suits this discrete event better than a liveRegion on the field,
            // which would also fire on every manual keystroke and when the field resets after Add.
            view.announceForAccessibility("Counted $it ${if (it == 1) "tablet" else "tablets"}")
            onCountedConsumed()
        }
    }

    InHandScreen(
        state = state,
        onAction = viewModel::onAction,
        onCountTablets = onCountTablets,
        onCountBlisters = onCountBlisters,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InHandScreen(
    state: InHandState,
    onAction: (InHandAction) -> Unit,
    onCountTablets: () -> Unit,
    onCountBlisters: () -> Unit,
    onBack: () -> Unit,
) {
    var medicationExpanded by remember { mutableStateOf(false) }
    // Shared width so ADD and DELETE are the same size (DELETE is the wider label).
    val actionButtonWidth = 120.dp

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("In Hand", modifier = Modifier.heading()) },
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
                text = "These are the tablets currently in your hand — dispensed, but not yet taken. " +
                    REQUIRED_FIELDS_NOTE,
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
                    label = "Medication",
                    readOnly = true,
                    placeholder = "Select a medication",
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = medicationExpanded) },
                    supportingText = if (state.medications.isEmpty()) {
                        "No medications yet — add one first"
                    } else null,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = medicationExpanded,
                    onDismissRequest = { medicationExpanded = false },
                ) {
                    val context = LocalContext.current
                    state.medications.forEach { medication ->
                        // Photo and dose come from the medication's dispensable unit, looked up live.
                        val unit = state.units.firstOrNull { it.medicationId == medication.id }
                        val dose = unit?.dosePerTablet?.let { " ($it" + "mg)" } ?: ""
                        DropdownMenuItem(
                            leadingIcon = {
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
                            },
                            text = { Text("${medication.brandName}$dose", fontWeight = FontWeight.Normal) },
                            onClick = {
                                onAction(InHandAction.MedicationSelected(medication.id))
                                medicationExpanded = false
                            },
                        )
                    }
                }
            }

            AppTextField(
                value = state.numberOfTablets,
                onValueChange = { onAction(InHandAction.NumberOfTabletsChanged(it)) },
                label = "Number of tablets",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    var countMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { countMenuExpanded = true },
                            enabled = state.selectedMedicationId != null,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = "Count with camera",
                            )
                        }
                        DropdownMenu(
                            expanded = countMenuExpanded,
                            onDismissRequest = { countMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Loose tablets") },
                                onClick = { countMenuExpanded = false; onCountTablets() },
                            )
                            DropdownMenuItem(
                                text = { Text("Blister packs") },
                                onClick = { countMenuExpanded = false; onCountBlisters() },
                            )
                        }
                    }
                },
            )
            Button(
                onClick = { onAction(InHandAction.Add) },
                enabled = state.canAdd,
                modifier = Modifier.width(actionButtonWidth),
            ) {
                Text("ADD")
            }

            // Once saved, the title records the date the figures were gathered. It's a polite live
            // region so a screen reader hears the updated title after Save records the date.
            Text(
                text = state.gatheredDate?.let { "Tablets in hand as of ${formatInHandDate(it)}:" }
                    ?: "Tablets in hand:",
                modifier = Modifier
                    .heading()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val context = LocalContext.current
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.tabletsInHand, key = { it.id }) { entry ->
                        val selected = entry.id == state.selectedId
                        // Dose and photo come from the medication's dispensable unit, looked up live.
                        val unit = state.units.firstOrNull { it.medicationId == entry.medicationId }
                        val dose = unit?.dosePerTablet?.let { " ($it" + "mg)" } ?: ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableRow(selected = selected) {
                                    onAction(InHandAction.RowSelected(entry.id))
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
                                text = "${entry.brand}$dose × ${entry.number}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { onAction(InHandAction.Delete) },
                enabled = state.canDelete,
                modifier = Modifier.width(actionButtonWidth),
            ) {
                Text("DELETE")
            }
        }
    }
}

/** Formats a gathered-date (UTC start-of-day millis) as dd/MM/yyyy, matching how scripts show dates. */
private fun formatInHandDate(millis: Long): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(millis))
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
                units = listOf(
                    DispensableUnitDetails(1, 1, "Panadol", "Paracetamol", "500", "24", null),
                    DispensableUnitDetails(2, 2, "Nurofen", "Ibuprofen", "200", "16", null),
                ),
                tabletsInHand = listOf(
                    InHandEntry(0, 1, "Panadol", "24"),
                    InHandEntry(1, 2, "Nurofen", "16"),
                ),
                selectedId = 1,
                gatheredDate = 1_700_000_000_000L,
            ),
            onAction = {},
            onCountTablets = {},
            onCountBlisters = {},
            onBack = {},
        )
    }
}
