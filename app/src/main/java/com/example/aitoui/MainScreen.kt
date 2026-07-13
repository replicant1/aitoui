package com.example.aitoui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitoui.ui.theme.AitouiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onMedications: () -> Unit = {},
    onDispensableUnits: () -> Unit = {},
    onDailySchedule: () -> Unit = {},
    onInHand: () -> Unit = {},
    onInventory: () -> Unit = {},
    onScripts: () -> Unit = {},
    onLog: () -> Unit = {},
) {
    // The scripts / dispensable units / medications group.
    val prescribingGroup = listOf(
        MainMenuItem("Scripts", Icons.Filled.Description, onScripts),
        MainMenuItem("Dispensable Units", Icons.Filled.Widgets, onDispensableUnits),
        MainMenuItem("Medications", Icons.Filled.Medication, onMedications),
    )
    // Everything else.
    val otherGroup = listOf(
        MainMenuItem("Daily Schedule", Icons.Filled.CalendarMonth, onDailySchedule),
        MainMenuItem("In Hand", Icons.Filled.BackHand, onInHand),
        MainMenuItem("Inventory", Icons.Filled.Inventory2, onInventory),
        // Temporarily hidden — "Log" is a debugging tool. Restore this item to bring it back.
        // MainMenuItem("Log", Icons.Filled.Storage, onLog),
    )

    // Deep-blue brand bar in light mode; in dark mode the full-width primary reads too bright, so
    // use the subdued dark-blue primaryContainer instead (keeps the blue identity, easier on the eye).
    val darkTheme = isSystemInDarkTheme()
    val barContainer = if (darkTheme) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val barContent = if (darkTheme) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "PxTx",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = barContainer,
                    titleContentColor = barContent,
                ),
            )
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Extra top padding vertically separates the grid from the title bar.
            contentPadding = PaddingValues(start = 16.dp, top = 32.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(prescribingGroup) { item ->
                MainMenuButton(label = item.label, icon = item.icon, onClick = item.onClick)
            }
            // Full-width divider separating the two groups.
            item(span = { GridItemSpan(maxLineSpan) }) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            items(otherGroup) { item ->
                MainMenuButton(label = item.label, icon = item.icon, onClick = item.onClick)
            }
        }
    }
}

private data class MainMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** A uniform, outlined, rounded tile filling its grid cell: an icon on top, label centered underneath. */
@Composable
private fun MainMenuButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = label,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    AitouiTheme {
        MainScreen()
    }
}
