package com.example.aitoui.data

/**
 * A specific format (dosage/packaging) of a [Medication].
 * [medicationId] is a foreign key to [Medication.id].
 */
data class DispensableUnit(
    val id: Long = 0,
    val medicationId: Long,
    val dosePerTablet: String,
    val tabletsPerUnit: String,
    /** Filename of the tablet photo in internal storage (see ImageStore), or null if none. */
    val imagePath: String? = null,
)
