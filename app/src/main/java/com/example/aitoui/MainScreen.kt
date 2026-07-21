package com.example.aitoui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.aitoui.alerts.AttentionKind
import com.example.aitoui.alerts.AttentionMessage
import com.example.aitoui.backup.BackupFileName
import com.example.aitoui.backup.DownloadsBackupStore
import com.example.aitoui.data.DATABASE_SCHEMA_VERSION
import com.example.aitoui.ui.heading
import com.example.aitoui.ui.theme.AitouiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    state: MainState = MainState(),
    onAction: (MainAction) -> Unit = {},
    onMedications: () -> Unit = {},
    onDispensableUnits: () -> Unit = {},
    onDailySchedule: () -> Unit = {},
    onInHand: () -> Unit = {},
    onInventory: () -> Unit = {},
    onScripts: () -> Unit = {},
    onLog: () -> Unit = {},
) {
    val context = LocalContext.current

    // On API 24-28, Downloads access needs a runtime storage permission; request it, then dispatch.
    var pendingAction by remember { mutableStateOf<MainAction?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted && action != null) onAction(action)
    }
    fun dispatchWithStorage(action: MainAction) {
        val needsPermission = DownloadsBackupStore.needsLegacyPermission() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingAction = action
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            onAction(action)
        }
    }

    // Load uses the Storage Access Framework: the user picks a pxtx.zip (from anywhere, including one copied
    // from another device), so no broad storage permission is needed.
    val loadPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onAction(MainAction.LoadFilePicked(uri.toString()))
    }

    // A completed restore has swapped the database files; restart the process so Room opens the new DB.
    LaunchedEffect(state.restoreComplete) {
        if (state.restoreComplete) {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
            }
            Runtime.getRuntime().exit(0)
        }
    }

    // The scripts / dispensable units / medications group, plus Save down the left column.
    val prescribingGroup = listOf(
        MainMenuItem("Scripts", Icons.Filled.Description, onScripts),
        MainMenuItem("Dispensable Units", Icons.Filled.Widgets, onDispensableUnits),
        MainMenuItem("Medications", Icons.Filled.Medication, onMedications),
        MainMenuItem("Save", Icons.Filled.Save) { dispatchWithStorage(MainAction.SaveTapped) },
    )
    // Everything else, plus Load down the right column (paired with Save on the bottom row).
    val otherGroup = listOf(
        MainMenuItem("Daily Schedule", Icons.Filled.CalendarMonth, onDailySchedule),
        MainMenuItem("In Hand", Icons.Filled.BackHand, onInHand),
        MainMenuItem("Inventory", Icons.Filled.Inventory2, onInventory),
        MainMenuItem("Load", Icons.Filled.FolderOpen) {
            loadPickerLauncher.launch(arrayOf("*/*"))
        },
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
                        modifier = Modifier.heading(),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Extra top padding vertically separates the grid from the title bar.
                .padding(start = 16.dp, top = 32.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The 2x4 grid fills all space above the version line: four equal-height rows, each a pair
            // (prescribing item on the left, everything-else item on the right).
            prescribingGroup.zip(otherGroup).forEach { (left, right) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MainMenuButton(left.label, left.icon, left.onClick, Modifier.weight(1f).fillMaxHeight())
                    MainMenuButton(right.label, right.icon, right.onClick, Modifier.weight(1f).fillMaxHeight())
                }
            }

            // Attention messages sit between the grid and the version line, and vanish entirely when empty.
            if (state.messages.isNotEmpty()) {
                AttentionMessages(state.messages)
            }

            Text(
                text = "App version:${BuildConfig.VERSION_NAME} — DB version:$DATABASE_SCHEMA_VERSION",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // --- Backup Save/Load dialogs ---

    state.pendingSaveFileName?.let { fileName ->
        AlertDialog(
            onDismissRequest = { onAction(MainAction.CancelSave) },
            title = { Text("Save backup") },
            text = {
                Column {
                    Text("Choose a file name for the backup (saved to your Downloads folder).")
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { onAction(MainAction.SaveFileNameChanged(it)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(MainAction.ConfirmSave) },
                    enabled = BackupFileName.isValid(fileName),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(MainAction.CancelSave) }) { Text("Cancel") }
            },
        )
    }

    if (state.busy) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Working…") },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )
    }

    if (state.pendingLoadUri != null) {
        AlertDialog(
            onDismissRequest = { onAction(MainAction.CancelLoad) },
            title = { Text("Load backup?") },
            text = {
                Text(
                    "This replaces all current data and images with the selected backup. It cannot be " +
                        "undone, and the app will restart.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(MainAction.ConfirmLoad) }) { Text("Load") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(MainAction.CancelLoad) }) { Text("Cancel") }
            },
        )
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = { onAction(MainAction.DismissMessage) },
            title = { Text("Backup saved") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { onAction(MainAction.DismissMessage) }) { Text("OK") }
            },
        )
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { onAction(MainAction.DismissMessage) },
            title = { Text("Couldn't load backup") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { onAction(MainAction.DismissMessage) }) { Text("OK") }
            },
        )
    }
}

/** Warning amber, legible on the menu's surface-variant message panel in both light and dark themes. */
private val WarningColor = Color(0xFFF9A825)

/**
 * The attention-message panel shown above the version line: a rounded surface holding one row per message,
 * each a warning icon on the left and the message text on the right. Rendered only when there are messages.
 */
@Composable
private fun AttentionMessages(messages: List<AttentionMessage>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        messages.forEach { message ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Warning",
                    tint = WarningColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
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

@Preview(showBackground = true, name = "With attention messages")
@Composable
private fun MainScreenWithMessagesPreview() {
    AitouiTheme {
        MainScreen(
            state = MainState(
                messages = listOf(
                    AttentionMessage(
                        AttentionKind.NO_SCRIPTS_FOR_PRESCRIPTION_MEDICATION,
                        "You have no scripts for Lipitor left.\nGo to doctor for new scripts.",
                    ),
                    AttentionMessage(
                        AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITH_SCRIPTS,
                        "You have only 1.4 weeks of Cartia in hand.\nGet a script filled.",
                    ),
                ),
            ),
        )
    }
}
