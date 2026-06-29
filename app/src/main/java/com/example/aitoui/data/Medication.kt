package com.example.aitoui.data

/** A medication, identified only by its brand name and active ingredient. */
data class Medication(
    val id: Long = 0,
    val brandName: String,
    val activeIngredient: String,
)
