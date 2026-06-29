package com.example.aitoui.data

/**
 * A prescription "Script" — what a doctor writes and you take to the pharmacy.
 * [medicationFormatId] is a foreign key to [MedicationFormat.id].
 */
data class Script(
    val id: Long = 0,
    val medicationFormatId: Long,
    val directions: String,
    val quantity: Int,
    val repeats: Int,
    val validToMillis: Long,
    /** Quantity already dispensed against this script. New scripts start at 0. */
    val dispensed: Int = 0,
)
