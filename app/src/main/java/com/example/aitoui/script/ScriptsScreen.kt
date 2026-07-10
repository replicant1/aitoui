package com.example.aitoui.script

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.data.ScriptDetails
import com.example.aitoui.ui.theme.AitouiTheme

/** The yellow band across the top of each script "card". */
private val CardYellow = Color(0xFFFFF176)

@Composable
fun ScriptsRoot(
    onBack: () -> Unit,
    onAddScript: () -> Unit,
    viewModel: ScriptsViewModel = viewModel(factory = ScriptsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScriptsScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onAddScript = onAddScript,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptsScreen(
    state: ScriptsState,
    onAction: (ScriptsAction) -> Unit,
    onBack: () -> Unit,
    onAddScript: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Scripts") },
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
            FloatingActionButton(onClick = onAddScript) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add script")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = "These are all the scripts you currently have in hand — one for each " +
                    "medication your doctor has prescribed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.scripts, key = { it.scriptId }) { script ->
                    ScriptCard(
                        script = script,
                        onDispensedClick = { onAction(ScriptsAction.DispensedTapped(script.scriptId)) },
                        onDeleteClick = { onAction(ScriptsAction.DeleteTapped(script.scriptId)) },
                    )
                }
            }
        }
    }

    // Confirm before recording a dispensation.
    state.pendingDispenseScript?.let { script ->
        AlertDialog(
            onDismissRequest = { onAction(ScriptsAction.CancelDispense) },
            title = { Text("Dispense one unit?") },
            text = {
                Text(
                    "Record one dispensation of ${script.medicationLabel}. This adds " +
                        "${script.tabletsPerUnit} tablets to your In Hand tally.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(ScriptsAction.ConfirmDispense) }) { Text("Dispense") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ScriptsAction.CancelDispense) }) { Text("Cancel") }
            },
        )
    }

    // Error when the script has already been dispensed its maximum number of times.
    state.maxedOutScript?.let { script ->
        AlertDialog(
            onDismissRequest = { onAction(ScriptsAction.DismissMaxedOut) },
            title = { Text("Cannot dispense") },
            text = {
                Text(
                    "${script.medicationLabel} has already been dispensed the maximum number of " +
                        "times (${script.repeats}).",
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(ScriptsAction.DismissMaxedOut) }) { Text("OK") }
            },
        )
    }

    // Confirm before deleting a script.
    state.pendingDeleteScript?.let { script ->
        AlertDialog(
            onDismissRequest = { onAction(ScriptsAction.CancelDelete) },
            title = { Text("Delete script?") },
            text = {
                Text(
                    "Delete the script for ${script.medicationLabel}? This also removes its " +
                        "dispensation history and cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(ScriptsAction.ConfirmDelete) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ScriptsAction.CancelDelete) }) { Text("Cancel") }
            },
        )
    }
}

/**
 * A single script rendered like a small credit card: a yellow header (medication name + dosage) above
 * a horizontal divider, then two vertically-partitioned white cells — dispensed count and repeats.
 */
@Composable
private fun ScriptCard(
    script: ScriptDetails,
    onDispensedClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Yellow header: two-line medication label on the left, delete (cross) icon top-right.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardYellow),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    Text(
                        text = "${script.brandName} (${script.activeIngredient})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${script.dosePerTablet}mg × Qty ${script.tabletsPerUnit}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Black.copy(alpha = 0.6f),
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Delete script",
                        tint = Color.Black,
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = Color.Black)

            // Two white cells, split by a vertical divider.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                ScriptCardCell(
                    modifier = Modifier.weight(1f),
                    caption = "Dispensed",
                    value = script.dispensed.toString(),
                    onClick = onDispensedClick,
                )
                VerticalDivider(thickness = 1.dp, color = Color.Black)
                ScriptCardCell(
                    modifier = Modifier.weight(1f),
                    caption = "Repeats",
                    value = script.repeats.toString(),
                )
            }
        }
    }
}

@Composable
private fun ScriptCardCell(
    modifier: Modifier,
    caption: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .background(Color.White)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScriptsScreenPreview() {
    AitouiTheme {
        ScriptsScreen(
            state = ScriptsState(
                scripts = listOf(
                    ScriptDetails(1, 1, 1, "Panadol", "Paracetamol", "500", "24", dispensed = 2, repeats = 5),
                    ScriptDetails(2, 2, 2, "Amoxil", "Amoxicillin", "500", "20", dispensed = 1, repeats = 0),
                    ScriptDetails(3, 3, 3, "Ventolin", "Salbutamol", "100", "200", dispensed = 1, repeats = 3),
                ),
            ),
            onAction = {},
            onBack = {},
            onAddScript = {},
        )
    }
}
