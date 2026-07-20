package com.example.aitoui.data

/**
 * A [DailyScheduleEntity] joined through its dispensable unit to the [Medication], for display on the Daily
 * Schedule screen and for aggregating the daily rate per medication. [medicationId] is the unit's medication.
 */
data class DailyScheduleDetails(
    val id: Long,
    val dispensableUnitId: Long,
    val medicationId: Long,
    val brandName: String,
    val quantity: Double,
)
