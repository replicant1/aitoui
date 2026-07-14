package com.example.aitoui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aitoui.MainScreen
import com.example.aitoui.MainViewModel
import com.example.aitoui.inventory.InventoryRoot
import com.example.aitoui.runout.RunOutGraphRoot
import com.example.aitoui.medication.MedicationRoot
import com.example.aitoui.medication.MedicationsRoot
import com.example.aitoui.dispensableunit.DispensableUnitsRoot
import com.example.aitoui.dispensableunit.DispensableUnitRoot
import com.example.aitoui.script.AddScriptRoot
import com.example.aitoui.script.ScriptsRoot
import com.example.aitoui.dailyschedule.DailyScheduleRoot
import com.example.aitoui.inhand.InHandRoot
import com.example.aitoui.scan.ScanScriptRoot

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = MainRoute) {
        composable<MainRoute> {
            val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
            val mainState by mainViewModel.state.collectAsStateWithLifecycle()
            MainScreen(
                state = mainState,
                onAction = mainViewModel::onAction,
                onMedications = { navController.navigate(MedicationsRoute) },
                onDispensableUnits = { navController.navigate(DispensableUnitsRoute) },
                onDailySchedule = { navController.navigate(DailyScheduleRoute) },
                onInHand = { navController.navigate(InHandRoute) },
                onInventory = { navController.navigate(InventoryRoute) },
                onScripts = { navController.navigate(ScriptsRoute) },
                onLog = { mainViewModel.logDatabase() },
            )
        }
        composable<MedicationRoute> {
            MedicationRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<MedicationsRoute> {
            MedicationsRoot(
                onBack = { navController.popBackStack() },
                onAddMedication = { navController.navigate(MedicationRoute) },
            )
        }
        composable<DispensableUnitRoute> {
            DispensableUnitRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<DispensableUnitsRoute> {
            DispensableUnitsRoot(
                onBack = { navController.popBackStack() },
                onAddDispensableUnit = { navController.navigate(DispensableUnitRoute) },
            )
        }
        composable<DailyScheduleRoute> {
            DailyScheduleRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<InHandRoute> {
            InHandRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<InventoryRoute> {
            InventoryRoot(
                onBack = { navController.popBackStack() },
                onRunOutGraph = { navController.navigate(RunOutGraphRoute) },
            )
        }
        composable<RunOutGraphRoute> {
            RunOutGraphRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<ScanScriptRoute> {
            ScanScriptRoot(
                onScanned = { parsed ->
                    navController.navigate(
                        ScriptRoute(
                            brandName = parsed.brand,
                            activeIngredient = parsed.activeIngredient,
                            dosePerTablet = parsed.dosePerTablet,
                            tabletsPerUnit = parsed.tabletsPerUnit,
                            serialNo = parsed.serialNo,
                            serialNo2 = parsed.serialNo2,
                            dateOfIssueMillis = parsed.dateOfIssueMillis,
                            validToMillis = parsed.validToMillis,
                            repeats = parsed.repeats,
                            instructions = parsed.instructions,
                            priorDispensed = parsed.priorDispensed ?: 0,
                        ),
                    ) { popUpTo(ScanScriptRoute) { inclusive = true } }
                },
                onEnterManually = {
                    navController.navigate(ScriptRoute()) { popUpTo(ScanScriptRoute) { inclusive = true } }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ScriptRoute> {
            AddScriptRoot(
                onBack = { navController.popBackStack() }
            )
        }
        composable<ScriptsRoute> {
            ScriptsRoot(
                onBack = { navController.popBackStack() },
                onAddScript = { navController.navigate(ScanScriptRoute) },
            )
        }
    }
}
