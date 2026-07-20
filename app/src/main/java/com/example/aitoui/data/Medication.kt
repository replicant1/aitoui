package com.example.aitoui.data

/** A medication, identified only by its brand name and active ingredient. */
data class Medication(
    val id: Long = 0,
    val brandName: String,
    val activeIngredient: String,
    /** Whether the medication needs a prescription to obtain. */
    val requiresPrescription: Boolean = true,
)
