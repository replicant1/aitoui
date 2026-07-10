package com.example.aitoui.data

/**
 * A recorded dispensation — the [dispensableUnitId] was dispensed [number] times against
 * [scriptId] at the pharmacy. [scriptId] is a foreign key to [Script.id] and [dispensableUnitId]
 * a foreign key to [DispensableUnit.id] (the `dispensable_units` table).
 */
data class Dispensation(
    val id: Long = 0,
    val scriptId: Long,
    val dispensableUnitId: Long,
    val number: Int,
    val dispensedAtMillis: Long,
)
