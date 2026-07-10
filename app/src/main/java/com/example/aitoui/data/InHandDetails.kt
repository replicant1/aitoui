package com.example.aitoui.data

/** An [InHandEntity] joined with its [Medication], for display on the In Hand screen. */
data class InHandDetails(
    val id: Long,
    val medicationId: Long,
    val brandName: String,
    val quantity: Double,
)
