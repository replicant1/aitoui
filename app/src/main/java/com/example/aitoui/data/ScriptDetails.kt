package com.example.aitoui.data

/**
 * A [Script] joined with its dispensable unit / medication, for the Dispense screen's script dropdown.
 */
data class ScriptDetails(
    val scriptId: Long,
    val dispensableUnitId: Long,
    val brandName: String,
    val dosePerTablet: String,
    val dispensed: Int,
) {
    /** Dropdown label, e.g. "Panadol (500mg) — 2 dispensed". */
    val label: String get() = "$brandName (${dosePerTablet}mg) — $dispensed dispensed"
}
