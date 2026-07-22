package com.example.aitoui.inventory

import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.ScriptDetails
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A dispensable unit's remaining supply, split into what is currently in hand and what is still to come
 * from undispensed script repeats. Each part is expressed as tablets and whole days.
 */
data class SupplyBreakdown(
    /** Remaining script fills for this unit: sum of (repeats + 1 - dispensed), floored at 0. */
    val undispensedFills: Int,
    val tabletsPerUnit: Int,
    /** [undispensedFills] × [tabletsPerUnit]. */
    val undispensedTablets: Int,
    /** floor([undispensedTablets] / dailyRate). */
    val undispensedDays: Int,
    /** Tablets currently in hand, taken directly from the `in_hand` table for this medication. */
    val inHandTablets: Int,
    /** floor(in-hand tablets / dailyRate). */
    val inHandDays: Int,
) {
    /** Total whole days before the medication runs out (in hand + undispensed). */
    val totalDays: Int get() = inHandDays + undispensedDays
}

data class InventoryItem(
    val unit: DispensableUnitDetails,
    /** null when the medication has no daily-schedule rate (nothing to divide by). */
    val supply: SupplyBreakdown?,
)

/**
 * Computes each dispensable unit's [SupplyBreakdown], keyed by unit `formatId`.
 *
 * In-hand tablets come straight from [inHandByMedication] (the `in_hand` table) for the unit's
 * medication. Undispensed tablets come from the unit's scripts' remaining repeats
 * (`repeats + 1 - dispensed`, since a script allows one more dispensation than its repeats count) ×
 * tablets-per-unit. Days are `floor(tablets / rate)`; a unit whose medication has no positive daily
 * rate maps to `null`.
 */
fun computeSupply(
    units: List<DispensableUnitDetails>,
    scripts: List<ScriptDetails>,
    dailyByMedication: Map<Long, Double>,
    inHandByMedication: Map<Long, Double>,
    daysSinceGathered: Double = 0.0,
): Map<Long, SupplyBreakdown?> {
    val scriptsByUnit = scripts.groupBy { it.dispensableUnitId }

    val result = HashMap<Long, SupplyBreakdown?>()
    for (unit in units) {
        val rate = dailyByMedication[unit.medicationId] ?: 0.0
        if (rate <= 0.0) {
            result[unit.formatId] = null                      // no consumption rate — "—"
            continue
        }
        val tabletsPerUnit = unit.tabletsPerUnit.toIntOrNull() ?: 0

        // In hand: the medication's in-hand quantity, decayed at the daily rate over the days since it was
        // gathered, then converted to whole days at the daily rate.
        val inHandQuantity = ((inHandByMedication[unit.medicationId] ?: 0.0) - rate * daysSinceGathered)
            .coerceAtLeast(0.0)
        val inHandTablets = inHandQuantity.roundToInt()
        val inHandDays = floor(inHandQuantity / rate).toInt()

        // Undispensed: remaining repeats across this unit's scripts.
        val undispensedFills = scriptsByUnit[unit.formatId].orEmpty()
            .sumOf { (it.repeats + 1 - it.dispensed).coerceAtLeast(0) }
        val undispensedTablets = undispensedFills * tabletsPerUnit
        val undispensedDays = floor(undispensedTablets.toDouble() / rate).toInt()

        result[unit.formatId] = SupplyBreakdown(
            undispensedFills = undispensedFills,
            tabletsPerUnit = tabletsPerUnit,
            undispensedTablets = undispensedTablets,
            undispensedDays = undispensedDays,
            inHandTablets = inHandTablets,
            inHandDays = inHandDays,
        )
    }
    return result
}

/**
 * Formats a whole-day supply figure in the largest sensible calendrical unit — days, weeks, months or
 * years — to one decimal place when the value is not whole. E.g. 5 → "5 days", 13 → "1.9 weeks", 45 →
 * "1.5 months", 800 → "2.2 years". Approximate: a month is 30 days and a year is 365 days.
 */
fun humanizeDuration(days: Int): String = when {
    days >= 365 -> calendrical(days / 365.0, "year")
    days >= 30 -> calendrical(days / 30.0, "month")
    days >= 7 -> calendrical(days / 7.0, "week")
    else -> plural(days, "day")
}

/** Formats [value] to one decimal place (dropping a trailing ".0") with a pluralised [unit]. */
private fun calendrical(value: Double, unit: String): String {
    val tenths = (value * 10).roundToInt()
    val number = if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    return "$number $unit${if (tenths == 10) "" else "s"}"
}

private fun plural(n: Int, unit: String): String = "$n $unit${if (n == 1) "" else "s"}"

/**
 * The calendar date the supply runs out: [nowMillis] plus [totalDays] whole days, in the device's local
 * time zone. Formatted as "d MMM" (e.g. "5 Aug") when the run-out date falls in the same calendar year as
 * [nowMillis], or "d MMM yyyy" (e.g. "5 Aug 2027") when it falls in a later year.
 */
fun runOutDateLabel(totalDays: Int, nowMillis: Long): String {
    val runOut = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, totalDays)
    }
    val nowYear = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.YEAR)
    val pattern = if (runOut.get(Calendar.YEAR) == nowYear) "d MMM" else "d MMM yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(runOut.time)
}
