package com.example.aitoui.data

/**
 * A [DispensableUnit] (dispensable unit) joined with its [Medication], for display in
 * lists/dropdowns where the brand name and active ingredient are needed alongside the dosage.
 */
data class DispensableUnitDetails(
    val formatId: Long,
    val medicationId: Long,
    val brandName: String,
    val activeIngredient: String,
    val dosePerTablet: String,
    val tabletsPerUnit: String,
    /** Filename of the tablet photo in internal storage (see ImageStore), or null if none. */
    val imagePath: String?,
    /** Whether the medication needs a prescription (from the joined medication). */
    val requiresPrescription: Boolean = true,
    val doseUnit: String = DoseUnit.MILLIGRAMS.storedAbbreviation,
) {
    /** Short label for dropdowns, e.g. "Panadol (500mg)". */
    val label: String get() = "$brandName (${dosePerTablet}$doseUnit)"
}
