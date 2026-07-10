package com.example.aitoui.data

/** A [DailyScheduleEntity] joined with its [Medication], for display on the Daily Schedule screen. */
data class DailyScheduleDetails(
    val id: Long,
    val medicationId: Long,
    val brandName: String,
    val quantity: Double,
)
