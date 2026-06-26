package com.example.aitoui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aitoui.MainScreen
import com.example.aitoui.medication.MedicationRoot
import com.example.aitoui.taketablets.TakeTabletsRoot

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = MainRoute) {
        composable<MainRoute> {
            MainScreen(
                onAddMedication = { navController.navigate(MedicationRoute) },
                onTakeTablets = { navController.navigate(TakeTabletsRoute) },
            )
        }
        composable<MedicationRoute> {
            MedicationRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<TakeTabletsRoute> {
            TakeTabletsRoot(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
