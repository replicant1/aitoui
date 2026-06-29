package com.example.aitoui.data

/**
 * A [MedicationFormat] joined with its [Medication], for display in lists/dropdowns where the
 * brand name and active ingredient are needed alongside the format's dosage details.
 */
data class MedicationFormatDetails(
    val formatId: Long,
    val medicationId: Long,
    val brandName: String,
    val activeIngredient: String,
    val dosePerTablet: String,
    val tabletsPerBox: String,
    /** Times dispensed, from this format's script (0 if none). */
    val dispensed: Int,
    /** Total dispensations allowed, from the same script (0 if none). */
    val quantity: Int,
) {
    /** Short label for dropdowns, e.g. "Panadol (500mg)". */
    val label: String get() = "$brandName (${dosePerTablet}mg)"
}
