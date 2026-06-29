package com.example.aitoui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aitoui.MainScreen
import com.example.aitoui.inventory.InventoryRoot
import com.example.aitoui.medication.MedicationRoot
import com.example.aitoui.medicationformat.MedicationFormatRoot
import com.example.aitoui.script.AddScriptRoot
import com.example.aitoui.taketablets.TakeTabletsRoot

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = MainRoute) {
        composable<MainRoute> {
            MainScreen(
                onAddMedication = { navController.navigate(MedicationRoute) },
                onAddMedicationFormat = { navController.navigate(MedicationFormatRoute) },
                onTakeTablets = { navController.navigate(TakeTabletsRoute) },
                onInventory = { navController.navigate(InventoryRoute) },
                onAddScript = { navController.navigate(ScriptRoute) },
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
    }
}
