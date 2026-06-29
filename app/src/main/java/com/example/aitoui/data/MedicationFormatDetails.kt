package com.example.aitoui.data

/**
 * A [MedicationFormat] (dispensable unit) joined with its [Medication], for display in
 * lists/dropdowns where the brand name and active ingredient are needed alongside the dosage.
 */
data class MedicationFormatDetails(
    val formatId: Long,
    val medicationId: Long,
    val brandName: String,
    val activeIngredient: String,
    val dosePerTablet: String,
    val tabletsPerBox: String,
) {
    /** Short label for dropdowns, e.g. "Panadol (500mg)". */
    val label: String get() = "$brandName (${dosePerTablet}mg)"
}
