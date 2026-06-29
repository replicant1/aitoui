package com.example.aitoui.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.ui.theme.AitouiTheme

@Composable
fun MedicationRoot(
    onBack: () -> Unit,
    viewModel: MedicationViewModel = viewModel(factory = MedicationViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MedicationScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationScreen(
    state: MedicationState,
    onAction: (MedicationAction) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Medication") },
                actions = {
                    TextButton(
                        onClick = { onAction(MedicationAction.Save) },
                        enabled = state.canSave,
                    ) {
                        Text("Save")
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
            OutlinedTextField(
                value = state.brandName,
                onValueChange = { onAction(MedicationAction.BrandNameChanged(it)) },
                label = { Text("Brand Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.activeIngredient,
                onValueChange = { onAction(MedicationAction.ActiveIngredientChanged(it)) },
                label = { Text("Active Ingredient") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
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
        )
    }
}
