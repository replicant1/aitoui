package com.example.aitoui.data

/** Domain model for a medication template (as defined in a script). Fields are raw text. */
data class MedicationTemplate(
    val id: Long = 0,
    val brandName: String,
    val activeIngredient: String,
    val dosePerTablet: String,
    val tabletsPerBox: String,
)
