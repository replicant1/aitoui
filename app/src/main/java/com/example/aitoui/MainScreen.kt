package com.example.aitoui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aitoui.ui.theme.AitouiTheme

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onAddMedication: () -> Unit = {},
    onTakeTablets: () -> Unit = {},
    onInventory: () -> Unit = {},
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "MainScreen",
                style = MaterialTheme.typography.headlineMedium,
            )
            Button(
                onClick = onAddMedication,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Add Medication")
            }
            Button(
                onClick = onTakeTablets,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Take Tablets")
            }
            Button(
                onClick = onInventory,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Inventory")
            }
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
