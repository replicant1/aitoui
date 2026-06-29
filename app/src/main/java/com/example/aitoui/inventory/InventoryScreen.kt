package com.example.aitoui.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.data.MedicationFormatDetails
import com.example.aitoui.ui.theme.AitouiTheme

@Composable
fun InventoryRoot(
    onBack: () -> Unit,
    viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InventoryScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    state: InventoryState,
    onAction: (InventoryAction) -> Unit,
    onBack: () -> Unit,
) {
    // Keep the last shown format so the panel still renders its details while sliding out.
    var lastShown by remember { mutableStateOf<MedicationFormatDetails?>(null) }
    state.selectedFormat?.let { lastShown = it }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Inventory") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = "Medications in hand:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.formats, key = { it.formatId }) { format ->
                    MedicationRow(
                        format = format,
                        selected = format.formatId == state.selectedId,
                        onClick = { onAction(InventoryAction.FormatSelected(format.formatId)) },
                    )
                    HorizontalDivider()
                }
            }

            // The panel sits below the weighted list, so the list shrinks above it when shown.
            AnimatedVisibility(
                visible = state.selectedFormat != null,
                enter = slideInVertically { it } + expandVertically() + fadeIn(),
                exit = slideOutVertically { it } + shrinkVertically() + fadeOut(),
            ) {
                lastShown?.let { format ->
                    MedicationDetailSheet(
                        format = format,
                        onClose = { onAction(InventoryAction.SheetDismissed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationRow(
    format: MedicationFormatDetails,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background =
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = format.brandName,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = format.activeIngredient,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MedicationDetailSheet(
    format: MedicationFormatDetails,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Drag-handle bar.
            Surface(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {}

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = format.brandName,
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onClose) { Text("Close") }
            }

            DetailRow("Active ingredient", format.activeIngredient)
            DetailRow("Dose per tablet", format.dosePerTablet)
            DetailRow("Tablets per box", format.tabletsPerBox)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InventoryScreenPreview() {
    AitouiTheme {
        InventoryScreen(
            state = InventoryState(
                formats = listOf(
                    MedicationFormatDetails(1, 1, "Panadol", "Paracetamol", "500", "24"),
                    MedicationFormatDetails(2, 2, "Nurofen", "Ibuprofen", "200", "16"),
                ),
                selectedId = 1,
            ),
            onAction = {},
            onBack = {},
        )
    }
}
