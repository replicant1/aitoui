package com.example.aitoui.data

/**
 * A prescription "Script" — what a doctor writes and you take to the pharmacy.
 * [dispensableUnitId] is a foreign key to the dispensable unit (`dispensable_units` table) it is for.
 */
data class Script(
    val id: Long = 0,
    val dispensableUnitId: Long,
    val directions: String,
    val quantity: Int,
    val repeats: Int,
    val validToMillis: Long,
)
