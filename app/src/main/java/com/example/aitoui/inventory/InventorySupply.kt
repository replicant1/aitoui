package com.example.aitoui.inventory

import com.example.aitoui.data.Dispensation
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.ScriptDetails
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A dispensable unit's remaining supply, split into what has already been dispensed (on hand) and what
 * is still to come from undispensed script repeats. Each part is expressed as tablets and whole days.
 */
data class SupplyBreakdown(
    /** Remaining script fills for this unit: sum of (repeats - dispensed), floored at 0. */
    val undispensedFills: Int,
    val tabletsPerUnit: Int,
    /** [undispensedFills] × [tabletsPerUnit]. */
    val undispensedTablets: Int,
    /** floor([undispensedTablets] / dailyRate). */
    val undispensedDays: Int,
    /** Tablets on hand from past dispensations (depleted by consumption to now). */
    val dispensedTablets: Int,
    /** floor([dispensedTablets] / dailyRate). */
    val dispensedDays: Int,
) {
    /** Total whole days before the medication runs out (dispensed + undispensed). */
    val totalDays: Int get() = dispensedDays + undispensedDays
}

data class InventoryItem(
    val unit: DispensableUnitDetails,
    /** null when the medication has no daily-schedule rate (nothing to divide by). */
    val supply: SupplyBreakdown?,
)

private const val DAY_MILLIS = 86_400_000.0

/**
 * Computes each dispensable unit's [SupplyBreakdown], keyed by unit `formatId`.
 *
 * Dispensed (on-hand) tablets come from replaying the unit's dispensations as a running balance that
 * depletes at the medication's daily rate up to [nowMillis], never below zero (supply that lapsed
 * before the next dispensation is gone). Undispensed tablets come from the unit's scripts' remaining
 * repeats (`repeats - dispensed`) × tablets-per-unit. Days are `floor(tablets / rate)`; a unit whose
 * medication has no positive daily rate maps to `null`.
 */
fun computeSupply(
    units: List<DispensableUnitDetails>,
    dispensations: List<Dispensation>,
    scripts: List<ScriptDetails>,
    dailyByMedication: Map<Long, Double>,
    nowMillis: Long,
): Map<Long, SupplyBreakdown?> {
    val dispsByUnit = dispensations
        .filter { it.dispensedAtMillis <= nowMillis }         // ignore future-dated dispensations
        .groupBy { it.dispensableUnitId }
    val scriptsByUnit = scripts.groupBy { it.dispensableUnitId }

    val result = HashMap<Long, SupplyBreakdown?>()
    for (unit in units) {
        val rate = dailyByMedication[unit.medicationId] ?: 0.0
        if (rate <= 0.0) {
            result[unit.formatId] = null                      // no consumption rate — "—"
            continue
        }
        val tabletsPerUnit = unit.tabletsPerUnit.toIntOrNull() ?: 0

        // On-hand: running balance of this unit's dispensations, depleted to now.
        var stock = 0.0
        var lastT: Long? = null
        for (d in dispsByUnit[unit.formatId].orEmpty().sortedBy { it.dispensedAtMillis }) {
            lastT?.let { stock = max(0.0, stock - (d.dispensedAtMillis - it) / DAY_MILLIS * rate) }
            stock += d.number * tabletsPerUnit
            lastT = d.dispensedAtMillis
        }
        lastT?.let { stock = max(0.0, stock - (nowMillis - it) / DAY_MILLIS * rate) }
        val dispensedTablets = stock.roundToInt()
        val dispensedDays = floor(stock / rate).toInt()

        // Undispensed: remaining repeats across this unit's scripts.
        val undispensedFills = scriptsByUnit[unit.formatId].orEmpty()
            .sumOf { (it.repeats - it.dispensed).coerceAtLeast(0) }
        val undispensedTablets = undispensedFills * tabletsPerUnit
        val undispensedDays = floor(undispensedTablets.toDouble() / rate).toInt()

        result[unit.formatId] = SupplyBreakdown(
            undispensedFills = undispensedFills,
            tabletsPerUnit = tabletsPerUnit,
            undispensedTablets = undispensedTablets,
            undispensedDays = undispensedDays,
            dispensedTablets = dispensedTablets,
            dispensedDays = dispensedDays,
        )
    }
    return result
}

/**
 * Formats a whole-day supply figure in days or weeks — e.g. 5 → "5 days", 20 → "2 weeks", 90 →
 * "12 weeks". Weeks are used for 7 days or more; larger units (months/years) are not used.
 */
fun humanizeDuration(days: Int): String = when {
    days >= 7 -> plural(days / 7, "week")
    else -> plural(days, "day")
}

private fun plural(n: Int, unit: String): String = "$n $unit${if (n == 1) "" else "s"}"
