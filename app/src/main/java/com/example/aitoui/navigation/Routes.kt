package com.example.aitoui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation routes. */
@Serializable
object MainRoute

@Serializable
object MedicationRoute

@Serializable
object MedicationsRoute

@Serializable
object DispensableUnitRoute

@Serializable
object DispensableUnitsRoute

@Serializable
object DailyScheduleRoute

@Serializable
object InHandRoute

@Serializable
object InventoryRoute

@Serializable
object RunOutGraphRoute

/** The camera + OCR screen that scans a PB038 form to pre-fill [ScriptRoute]. */
@Serializable
object ScanScriptRoute

/**
 * The Add Script screen. Opened blank for manual entry, or with these optional args pre-filled from a
 * scanned PB038 (see [ScanScriptRoute]). [priorDispensed] becomes a directly-inserted dispensation on save.
 */
@Serializable
data class ScriptRoute(
    val brandName: String? = null,
    val activeIngredient: String? = null,
    val dosePerTablet: String? = null,
    val tabletsPerUnit: String? = null,
    val serialNo: String? = null,
    val serialNo2: String? = null,
    val dateOfIssueMillis: Long? = null,
    val validToMillis: Long? = null,
    val repeats: Int? = null,
    val instructions: String? = null,
    val priorDispensed: Int = 0,
)

@Serializable
object ScriptsRoute
