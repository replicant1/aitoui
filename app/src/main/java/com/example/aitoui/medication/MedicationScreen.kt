package com.example.aitoui.medication

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.ui.AppTextField
import com.example.aitoui.ui.REQUIRED_FIELDS_NOTE
import com.example.aitoui.ui.UnsavedChangesDialog
import com.example.aitoui.ui.heading
import com.example.aitoui.ui.theme.AitouiTheme

@Composable
fun MedicationRoot(
    onBack: () -> Unit,
    viewModel: MedicationViewModel = viewModel(factory = MedicationViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // After a medication is saved, return to the Medications screen.
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }
    MedicationScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationScreen(
    state: MedicationState,
    onAction: (MedicationAction) -> Unit,
    onBack: () -> Unit,
) {
    var showLeavePrompt by remember { mutableStateOf(false) }
    // Guard both back affordances (arrow + system back): prompt to save when there are unsaved edits.
    val attemptBack = { if (state.hasUnsavedChanges) showLeavePrompt = true else onBack() }

    BackHandler(enabled = state.hasUnsavedChanges) { showLeavePrompt = true }
    if (showLeavePrompt) {
        UnsavedChangesDialog(
            canSave = state.canSave,
            onSave = { showLeavePrompt = false; onAction(MedicationAction.Save) },
            onDiscard = { showLeavePrompt = false; onBack() },
            onCancel = { showLeavePrompt = false },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Medication", modifier = Modifier.heading()) },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onAction(MedicationAction.Save) },
                        enabled = state.canSave,
                    ) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "A Medication is a proprietary formulation based around an active " +
                    "ingredient, that can be prescribed by your doctor. $REQUIRED_FIELDS_NOTE",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppTextField(
                value = state.brandName,
                onValueChange = { onAction(MedicationAction.BrandNameChanged(it)) },
                label = "Brand Name",
            )
            AppTextField(
                value = state.activeIngredient,
                onValueChange = { onAction(MedicationAction.ActiveIngredientChanged(it)) },
                label = "Active Ingredient",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Requires prescription", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Turn off for over-the-counter medications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.requiresPrescription,
                    onCheckedChange = { onAction(MedicationAction.RequiresPrescriptionChanged(it)) },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MedicationScreenPreview() {
    AitouiTheme {
        MedicationScreen(
            state = MedicationState(brandName = "Panadol", activeIngredient = "Paracetamol"),
            onAction = {},
            onBack = {},
        )
    }
}
