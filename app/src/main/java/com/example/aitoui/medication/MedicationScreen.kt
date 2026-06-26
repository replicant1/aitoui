package com.example.aitoui.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
                    TextButton(onClick = { onAction(MedicationAction.Save) }) {
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
                .verticalScroll(rememberScrollState())
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
            OutlinedTextField(
                value = state.dosePerTablet,
                onValueChange = { onAction(MedicationAction.DosePerTabletChanged(it)) },
                label = { Text("Dose per tablet") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.tabletsPerBox,
                onValueChange = { onAction(MedicationAction.TabletsPerBoxChanged(it)) },
                label = { Text("Tablets per box") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.boxes,
                onValueChange = { onAction(MedicationAction.BoxesChanged(it)) },
                label = { Text("Boxes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
            state = MedicationState(
                brandName = "Panadol",
                activeIngredient = "Paracetamol",
                dosePerTablet = "500",
                tabletsPerBox = "24",
                boxes = "2",
            ),
            onAction = {},
        )
    }
}
