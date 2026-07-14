package com.example.aitoui.inventory

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.image.FullImageDialog
import com.example.aitoui.image.ImageStore
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
        onBack = onBack,
        onRunOutGraph = onRunOutGraph,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    state: InventoryState,
    onBack: () -> Unit,
    onRunOutGraph: () -> Unit,
) {
    // The filename of a photo being viewed full-screen (via tapping its thumbnail), if any.
    var viewingFullImage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Inventory") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRunOutGraph) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = "Run-out graph",
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
                text = "For each medication, the figure shows how long before you run out, counting " +
                    "both the tablets you have in hand and the future dispensations still " +
                    "available on your scripts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.items, key = { it.unit.formatId }) { item ->
                    MedicationRow(
                        item = item,
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
    onViewImage: (String) -> Unit,
) {
    // A single gap so the whitespace beside the thumbnail equals the whitespace below it. Rows with no
    // thumbnail keep the supply lines tucked tight under the brand/dose block.
    val gap = 12.dp
    val hasThumbnail = item.unit.imagePath != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    contentDescription = "Tablet photo for ${item.unit.brandName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ThumbnailShape)
                        .clickable { onViewImage(imagePath) },
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
                    text = "${item.unit.dosePerTablet}mg × Qty ${item.unit.tabletsPerUnit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = item.supply?.let { humanizeDuration(it.totalDays) } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // Bottom block: the two supply lines, full width, left-justified below the thumbnail.
        item.supply?.let { supply ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (supply.undispensedFills == 0) {
                        "No scripts"
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
                        "None in hand"
                    } else {
                        "In hand: ${supply.inHandTablets} tabs = " +
                            humanizeDuration(supply.inHandDays)
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
            onBack = {},
            onRunOutGraph = {},
        )
    }
}
