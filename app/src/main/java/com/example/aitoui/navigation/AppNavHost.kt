package com.example.aitoui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aitoui.MainScreen
import com.example.aitoui.MainViewModel
import com.example.aitoui.dispense.DispenseRoot
import com.example.aitoui.inventory.InventoryRoot
import com.example.aitoui.medication.MedicationRoot
import com.example.aitoui.medicationformat.MedicationFormatRoot
import com.example.aitoui.script.AddScriptRoot
import com.example.aitoui.script.ScriptsRoot
import com.example.aitoui.taketablets.TakeTabletsRoot

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = MainRoute) {
        composable<MainRoute> {
            val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
            MainScreen(
                onAddMedication = { navController.navigate(MedicationRoute) },
                onAddMedicationFormat = { navController.navigate(MedicationFormatRoute) },
                onTakeTablets = { navController.navigate(TakeTabletsRoute) },
                onInventory = { navController.navigate(InventoryRoute) },
                onAddScript = { navController.navigate(ScriptRoute) },
                onScripts = { navController.navigate(ScriptsRoute) },
                onDispense = { navController.navigate(DispenseRoute) },
                onLog = { mainViewModel.logDatabase() },
            )
        }
        composable<MedicationRoute> {
            MedicationRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<MedicationFormatRoute> {
            MedicationFormatRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<TakeTabletsRoute> {
            TakeTabletsRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<InventoryRoute> {
            InventoryRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<ScriptRoute> {
            AddScriptRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<ScriptsRoute> {
            ScriptsRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<DispenseRoute> {
            DispenseRoot(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
