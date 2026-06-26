package com.example.aitoui.taketablets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.ui.theme.AitouiTheme

@Composable
fun TakeTabletsRoot(
    onBack: () -> Unit,
    viewModel: TakeTabletsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TakeTabletsScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeTabletsScreen(
    state: TakeTabletsState,
    onAction: (TakeTabletsAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Take Tablets") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    TextButton(onClick = { onAction(TakeTabletsAction.Save) }) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.brandName,
                onValueChange = { onAction(TakeTabletsAction.BrandNameChanged(it)) },
                label = { Text("Brand Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.numberOfTablets,
                onValueChange = { onAction(TakeTabletsAction.NumberOfTabletsChanged(it)) },
                label = { Text("Number of tablets") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onAction(TakeTabletsAction.Add) },
                enabled = state.canAdd,
            ) {
                Text("ADD")
            }

            Text(
                text = "Tablets Taken:",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.tabletsTaken, key = { it.id }) { entry ->
                        val selected = entry.id == state.selectedId
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAction(TakeTabletsAction.RowSelected(entry.id)) }
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            Button(
                onClick = { onAction(TakeTabletsAction.Delete) },
                enabled = state.canDelete,
            ) {
                Text("DELETE")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TakeTabletsScreenPreview() {
    AitouiTheme {
        TakeTabletsScreen(
            state = TakeTabletsState(
                brandName = "",
                numberOfTablets = "",
                tabletsTaken = listOf(
                    TabletEntry(0, "Panadol", "2"),
                    TabletEntry(1, "Nurofen", "1"),
                ),
                selectedId = 1,
            ),
            onAction = {},
            onBack = {},
        )
    }
}
