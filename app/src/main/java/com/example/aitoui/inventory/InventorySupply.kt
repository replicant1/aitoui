package com.example.aitoui.inventory

import com.example.aitoui.data.Dispensation
import com.example.aitoui.data.DispensableUnitDetails
import kotlin.math.floor
import kotlin.math.max

/**
 * A dispensable unit paired with its medication's computed days-of-supply-remaining.
 *
 * [daysRemaining] is `null` when the figure is not applicable (the medication has no daily-schedule
 * entry, i.e. no consumption rate); `0` means the medication is out of dispensed supply.
 */
data class InventoryItem(
    val unit: DispensableUnitDetails,
    val daysRemaining: Int?,
)

private const val DAY_MILLIS = 86_400_000.0

/**
 * Computes, per medication, how many whole days of dispensed-but-unconsumed supply remain.
 *
 * For each medication with a positive daily rate, dispensations are replayed chronologically as a
 * running tablet balance that depletes at the rate between events and up to [nowMillis], never going
 * below zero (supply that lapsed before the next dispensation is gone). The result is
 * `floor(remainingTablets / rate)`.
 *
 * Tablets are pooled across all of a medication's dispensable units, consistent with the daily
 * schedule being per-medication. Returns a map keyed by `medicationId`; a value is `null` when the
 * medication has no schedule entry or a non-positive rate.
 */
fun computeDaysRemaining(
    units: List<DispensableUnitDetails>,
    dispensations: List<Dispensation>,
    dailyByMedication: Map<Long, Double>,
    nowMillis: Long,
): Map<Long, Int?> {
    val unitById = units.associateBy { it.formatId }

    // Build "tablets added" events per medication from dispensations.
    val eventsByMed = HashMap<Long, MutableList<Pair<Long, Double>>>()
    for (d in dispensations) {
        val unit = unitById[d.dispensableUnitId] ?: continue      // unresolvable unit — skip
        if (d.dispensedAtMillis > nowMillis) continue             // future-dated — ignore
        val tabletsPerUnit = unit.tabletsPerUnit.toDoubleOrNull() ?: 0.0
        eventsByMed.getOrPut(unit.medicationId) { mutableListOf() }
            .add(d.dispensedAtMillis to d.number * tabletsPerUnit)
    }

    val result = HashMap<Long, Int?>()
    for (medicationId in units.map { it.medicationId }.toSet()) {
        val rate = dailyByMedication[medicationId] ?: 0.0
        if (rate <= 0.0) {
            result[medicationId] = null                           // no consumption rate — "—"
            continue
        }
        var stock = 0.0
        var lastT: Long? = null
        for ((time, tablets) in eventsByMed[medicationId].orEmpty().sortedBy { it.first }) {
            lastT?.let { stock = max(0.0, stock - (time - it) / DAY_MILLIS * rate) }
            stock += tablets
            lastT = time
        }
        lastT?.let { stock = max(0.0, stock - (nowMillis - it) / DAY_MILLIS * rate) }
        result[medicationId] = floor(stock / rate).toInt()
    }
    return result
}
