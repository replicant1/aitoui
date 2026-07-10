package com.example.aitoui.data

/**
 * A [Script] joined with its dispensable unit / medication, for the Dispense screen's script dropdown
 * and the Scripts list screen. [dispensed] is the derived count of times dispensed; [repeats] is the
 * maximum number of times the script may be dispensed.
 */
data class ScriptDetails(
    val scriptId: Long,
    val dispensableUnitId: Long,
    val medicationId: Long,
    val brandName: String,
    val activeIngredient: String,
    val dosePerTablet: String,
    val tabletsPerUnit: String,
    val dispensed: Int,
    val repeats: Int,
) {
    /** Medication name with dosage, e.g. "Panadol (500mg)". */
    val medicationLabel: String get() = "$brandName (${dosePerTablet}mg)"

    /** Dropdown label, e.g. "Panadol (500mg) — 2 dispensed". */
    val label: String get() = "$medicationLabel — $dispensed dispensed"
}
