package com.example.aitoui.dispensableunit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.image.ImageStore
import com.example.aitoui.ui.theme.AitouiTheme
import java.io.File

@Composable
fun DispensableUnitsRoot(
    onBack: () -> Unit,
    onAddDispensableUnit: () -> Unit,
    viewModel: DispensableUnitsViewModel = viewModel(factory = DispensableUnitsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DispensableUnitsScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onAddDispensableUnit = onAddDispensableUnit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispensableUnitsScreen(
    state: DispensableUnitsState,
    onAction: (DispensableUnitsAction) -> Unit,
    onBack: () -> Unit,
    onAddDispensableUnit: () -> Unit,
) {
    val context = LocalContext.current

    // The unit id and temp file for an in-flight camera capture, if any.
    var pendingCapture by remember { mutableStateOf<Pair<Long, File>?>(null) }
    // The unit whose existing photo is being managed (retake/remove), if any.
    var managingPhotoUnitId by remember { mutableStateOf<Long?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val capture = pendingCapture
        pendingCapture = null
        when {
            success && capture != null -> onAction(DispensableUnitsAction.PhotoCaptured(capture.first, capture.second))
            else -> capture?.second?.delete()   // cancelled/failed — drop the empty temp file
        }
    }

    fun startCapture(unitId: Long) {
        val (uri, file) = ImageStore.newCaptureTarget(context)
        pendingCapture = unitId to file
        cameraLauncher.launch(uri)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Dispensable Units") },
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
            FloatingActionButton(onClick = onAddDispensableUnit) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add dispensable unit")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = "These are all the dispensable units — the specific formats (dose and pack " +
                    "size) in which your medications come.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.units, key = { it.formatId }) { unit ->
                    DispensableUnitRow(
                        unit = unit,
                        onDeleteClick = { onAction(DispensableUnitsAction.DeleteTapped(unit.formatId)) },
                        onCaptureClick = { startCapture(unit.formatId) },
                        onManagePhotoClick = { managingPhotoUnitId = unit.formatId },
                    )
                }
            }
        }
    }

    // Confirm before deleting a dispensable unit.
    state.pendingDeleteUnit?.let { unit ->
        AlertDialog(
            onDismissRequest = { onAction(DispensableUnitsAction.CancelDelete) },
            title = { Text("Delete dispensable unit?") },
            text = {
                Text(
                    "Delete ${unit.brandName} (${unit.dosePerTablet}mg)? This also removes its " +
                        "scripts and dispensation history, and cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(DispensableUnitsAction.ConfirmDelete) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(DispensableUnitsAction.CancelDelete) }) { Text("Cancel") }
            },
        )
    }

    // Manage an existing photo: retake (replaces it) or remove it.
    managingPhotoUnitId?.let { unitId ->
        val unit = state.units.firstOrNull { it.formatId == unitId }
        if (unit == null) {
            managingPhotoUnitId = null
        } else {
            AlertDialog(
                onDismissRequest = { managingPhotoUnitId = null },
                title = { Text("${unit.brandName} photo") },
                text = { Text("Retake replaces the current photo. Remove clears it.") },
                confirmButton = {
                    TextButton(onClick = {
                        managingPhotoUnitId = null
                        startCapture(unitId)
                    }) { Text("Retake") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        managingPhotoUnitId = null
                        onAction(DispensableUnitsAction.PhotoRemoved(unitId))
                    }) { Text("Remove") }
                },
            )
        }
    }
}

/**
 * A single dispensable unit. The medication component (brand name over active ingredient) mirrors the
 * Medications screen; a third line adds the unit's dose and pack size. A leading tablet photo (or a
 * camera button to add one) sits at the left; the delete icon is at top-right.
 */
@Composable
private fun DispensableUnitRow(
    unit: DispensableUnitDetails,
    onDeleteClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onManagePhotoClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            TabletPhoto(
                imagePath = unit.imagePath,
                contentDescription = "Tablet photo for ${unit.brandName}",
                onCaptureClick = onCaptureClick,
                onManagePhotoClick = onManagePhotoClick,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            ) {
                // Medication component — as presented on the Medications screen.
                Text(
                    text = unit.brandName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = unit.activeIngredient,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Dispensable-unit specifics.
                Text(
                    text = "${unit.dosePerTablet}mg × Qty ${unit.tabletsPerUnit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Delete dispensable unit")
            }
        }
    }
}

/**
 * A ~56dp square showing the unit's tablet photo, or a camera button placeholder when there is none.
 * Tapping the photo opens the retake/remove dialog; tapping the placeholder starts a capture.
 */
@Composable
private fun TabletPhoto(
    imagePath: String?,
    contentDescription: String,
    onCaptureClick: () -> Unit,
    onManagePhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val context = LocalContext.current
    if (imagePath != null) {
        AsyncImage(
            model = ImageStore.fileFor(context, imagePath),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(56.dp)
                .clip(shape)
                .clickable(onClick = onManagePhotoClick),
        )
    } else {
        Box(
            modifier = modifier
                .size(56.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onCaptureClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = "Add tablet photo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DispensableUnitsScreenPreview() {
    AitouiTheme {
        DispensableUnitsScreen(
            state = DispensableUnitsState(
                units = listOf(
                    DispensableUnitDetails(1, 1, "Panadol", "Paracetamol", "500", "24", null),
                    DispensableUnitDetails(2, 2, "Nurofen", "Ibuprofen", "200", "16", null),
                    DispensableUnitDetails(3, 3, "Ventolin", "Salbutamol", "100", "200", null),
                ),
            ),
            onAction = {},
            onBack = {},
            onAddDispensableUnit = {},
        )
    }
}
