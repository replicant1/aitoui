package com.example.aitoui.inventory

import com.example.aitoui.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.image.FullImageDialog
import com.example.aitoui.image.ImageStore
import com.example.aitoui.ui.heading
import com.example.aitoui.ui.theme.AitouiTheme
import com.example.aitoui.ui.theme.ThumbnailShape

@Composable
fun InventoryRoot(
    onBack: () -> Unit,
    onRunOutGraph: () -> Unit,
    viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InventoryScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onRunOutGraph = onRunOutGraph,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    state: InventoryState,
    onAction: (InventoryAction) -> Unit,
    onBack: () -> Unit,
    onRunOutGraph: () -> Unit,
) {
    // The filename of a photo being viewed full-screen (via tapping its thumbnail), if any.
    var viewingFullImage by remember { mutableStateOf<String?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // A single "now" for every row's run-out date, captured once when the screen opens.
    val nowMillis = remember { System.currentTimeMillis() }
    // Jump back to the top whenever the sort order changes, so the new first item is shown.
    LaunchedEffect(state.sortOrder) { listState.scrollToItem(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_appbar_title), modifier = Modifier.heading()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.inventory_back_button_cd),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.inventory_sort_order_button_cd),
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            SortOption.entries.forEach { order ->
                                val isSelected = order == state.sortOrder
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = order.label,
                                            modifier = Modifier.semantics { selected = isSelected },
                                        )
                                    },
                                    onClick = {
                                        onAction(InventoryAction.SortOrderChanged(order))
                                        sortMenuExpanded = false
                                    },
                                    trailingIcon = if (isSelected) {
                                        { Icon(Icons.Filled.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onRunOutGraph) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = stringResource(R.string.inventory_run_out_graph_button_cd),
                        )
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
                text = stringResource(R.string.inventory_description_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(state.items, key = { it.unit.formatId }) { item ->
                    MedicationRow(
                        item = item,
                        nowMillis = nowMillis,
                        onViewImage = { path -> viewingFullImage = path },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    viewingFullImage?.let { fileName ->
        FullImageDialog(fileName = fileName, onDismiss = { viewingFullImage = null })
    }
}

@Composable
private fun MedicationRow(
    item: InventoryItem,
    nowMillis: Long,
    onViewImage: (String) -> Unit,
) {
    // A single gap so the whitespace beside the thumbnail equals the whitespace below it. Rows with no
    // thumbnail keep the supply lines tucked tight under the brand/dose block.
    val gap = 12.dp
    val hasThumbnail = item.unit.imagePath != null
    Column(
        // Read the row as one stop — brand, dose, runway and the supply lines together — instead of 5+
        // separate stops (the runway "—"/"5 months" no longer reads in isolation). The clickable thumbnail
        // is its own merge boundary, so it stays a separate focusable button.
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(if (hasThumbnail) gap else 2.dp),
    ) {
        // Top block: the tablet photo, with the brand name and dose/pack size wrapped to its right,
        // and the total runway figure at the far right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Top,
        ) {
            item.unit.imagePath?.let { imagePath ->
                val context = LocalContext.current
                AsyncImage(
                    model = ImageStore.fileFor(context, imagePath),
                    contentDescription = stringResource(R.string.inventory_photo_button_cd, item.unit.brandName),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        // Reserve a >=48dp touch target around the 44dp visual (as Material's IconButton does).
                        .minimumInteractiveComponentSize()
                        .size(44.dp)
                        .clip(ThumbnailShape)
                        .clickable(role = Role.Button) { onViewImage(imagePath) },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.unit.brandName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(
                        R.string.dispensable_units_dose_format,
                        item.unit.dosePerTablet,
                        item.unit.doseUnit,
                        item.unit.tabletsPerUnit,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.supply != null) {
                // The runway figure, with the calendar run-out date right-justified beneath it.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = humanizeDuration(item.supply.totalDays),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = runOutDateLabel(item.supply.totalDays, nowMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                // No daily_schedule entry ⇒ no consumption rate ⇒ nothing to compute a run-out from.
                Text(
                    text = stringResource(R.string.inventory_no_daily_dose_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Bottom block: the two supply lines, full width, left-justified below the thumbnail.
        item.supply?.let { supply ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (supply.undispensedFills == 0) {
                        // A non-prescription medication has no scripts by nature — say so plainly rather
                        // than flagging "No scripts" as if something were missing.
                        if (item.unit.requiresPrescription) stringResource(R.string.inventory_no_scripts_label) else stringResource(R.string.inventory_over_the_counter_label)
                    } else {
                        "${supply.undispensedFills} scripts × " +
                            "${supply.tabletsPerUnit} tabs = ${supply.undispensedTablets} tabs = " +
                            humanizeDuration(supply.undispensedDays)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (supply.inHandTablets == 0) {
                        stringResource(R.string.inventory_none_in_hand_label)
                    } else {
                        stringResource(R.string.inventory_in_hand_format, supply.inHandTablets, humanizeDuration(supply.inHandDays))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InventoryScreenPreview() {
    AitouiTheme {
        InventoryScreen(
            state = InventoryState(
                items = listOf(
                    InventoryItem(
                        DispensableUnitDetails(1, 1, "Panadol", "Paracetamol", "500", "24", null),
                        supply = SupplyBreakdown(
                            undispensedFills = 3, tabletsPerUnit = 24, undispensedTablets = 72,
                            undispensedDays = 36, inHandTablets = 48, inHandDays = 24,
                        ),
                    ),
                    InventoryItem(
                        DispensableUnitDetails(2, 2, "Nurofen", "Ibuprofen", "200", "16", null),
                        supply = null,
                    ),
                ),
            ),
            onAction = {},
            onBack = {},
            onRunOutGraph = {},
        )
    }
}
