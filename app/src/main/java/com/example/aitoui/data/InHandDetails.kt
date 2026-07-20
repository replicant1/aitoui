package com.example.aitoui.data

/**
 * An [InHandEntity] joined through its dispensable unit to the [Medication], for display on the In Hand
 * screen and for aggregating supply per medication. [medicationId] is the unit's medication.
 */
data class InHandDetails(
    val id: Long,
    val dispensableUnitId: Long,
    val medicationId: Long,
    val brandName: String,
    val quantity: Double,
)
