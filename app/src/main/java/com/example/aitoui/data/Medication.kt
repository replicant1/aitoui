package com.example.aitoui.data

/** Domain model for a saved medication. Fields are raw text, matching the entry form. */
data class Medication(
    val id: Long = 0,
    val brandName: String,
    val activeIngredient: String,
    val dosePerTablet: String,
    val tabletsPerBox: String,
    val boxes: String,
)
