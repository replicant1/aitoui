package com.example.aitoui.data

/**
 * A [Script] joined with its dispensable unit / medication, for the Dispense screen's script dropdown
 * and the Scripts list screen. [dispensed] is the derived count of times dispensed; [repeats] is the
 * number of repeat dispensations, so the script allows `repeats + 1` dispensations in total.
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
    /** Date the script was issued, as UTC epoch millis. */
    val dateOfIssue: Long,
    /** Directions for use, e.g. "Take ONE tablet TWICE a day as directed". */
    val instructions: String = "",
    val doseUnit: String = DoseUnit.MILLIGRAMS.storedAbbreviation,
) {
    /** Medication name with dosage, e.g. "Panadol (500mg)". */
    val medicationLabel: String get() = "$brandName (${dosePerTablet}$doseUnit)"

    /** Dropdown label, e.g. "Panadol (500mg) — 2 dispensed". */
    val label: String get() = "$medicationLabel — $dispensed dispensed"
}
