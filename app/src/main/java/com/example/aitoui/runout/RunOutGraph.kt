package com.example.aitoui.runout

import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.ScriptDetails
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** A 30-day month — used only to round the time axis out to a whole number of months. */
const val MONTH_DAYS = 30.0

private const val DAY_MILLIS = 86_400_000.0

/** A tick on the time axis at a calendar-month boundary. */
data class MonthTick(
    /** Days from now at which this calendar month begins. */
    val dayOffset: Double,
    /** Abbreviated month name to label the tick, e.g. "Aug". */
    val label: String,
)

/**
 * One dispensable unit's projected supply over time — a single line on the Run-out Graph. Supply is
 * modelled as a straight decline from [totalTablets] now (in-hand plus every remaining, undispensed
 * script fill) down to zero at the medication's daily consumption rate.
 */
data class RunOutSeries(
    val unitId: Long,
    val label: String,
    /** Tablets available now: in-hand plus all remaining (undispensed) script fills. */
    val totalTablets: Int,
    /** Consumption rate in tablets per day (always > 0). */
    val dailyRate: Double,
    /** Index into the colour palette, assigned in display order. */
    val colorIndex: Int,
) {
    /** The day (counting from now) at which this unit's supply reaches zero. */
    val runOutDay: Double get() = totalTablets / dailyRate

    /** Tablets remaining [day] days from now, never below zero. */
    fun tabletsAt(day: Double): Double = (totalTablets - dailyRate * day).coerceAtLeast(0.0)

    /** Whole days of supply left as at [day] days from now, never below zero. */
    fun daysRemainingAt(day: Double): Int = floor((runOutDay - day).coerceAtLeast(0.0)).toInt()
}

/** The full data set for the Run-out Graph: the series plus the axis domains. */
data class RunOutGraphData(
    val series: List<RunOutSeries> = emptyList(),
    /** X-axis extent in days — a whole number of [MONTH_DAYS] months, at least one month. */
    val domainDays: Double = MONTH_DAYS,
    /** Y-axis extent in tablets — a whole number of [tabletTickStep], at least one step. */
    val domainTablets: Int = 10,
    /** Spacing of Y-axis ticks in tablets (10 for small supplies, 50 for large). */
    val tabletTickStep: Int = 10,
    /** Time-axis ticks at each calendar-month boundary within the domain. */
    val monthTicks: List<MonthTick> = emptyList(),
) {
    val isEmpty: Boolean get() = series.isEmpty()
}

/**
 * Builds the Run-out Graph data from the same inputs as the Inventory screen. One series is produced
 * per dispensable unit that has a positive daily rate (a unit with no schedule cannot be projected and
 * is omitted). Series are ordered by label and assigned palette indices in that order. The axis domains
 * are rounded out to a whole number of months and a round tablet-tick step so the axes read cleanly.
 */
fun computeRunOutGraph(
    units: List<DispensableUnitDetails>,
    scripts: List<ScriptDetails>,
    dailyByMedication: Map<Long, Double>,
    inHandByMedication: Map<Long, Double>,
    nowMillis: Long = 0L,
    daysSinceGathered: Double = 0.0,
): RunOutGraphData {
    val scriptsByUnit = scripts.groupBy { it.dispensableUnitId }
    val series = units.mapNotNull { unit ->
        val rate = dailyByMedication[unit.medicationId] ?: 0.0
        if (rate <= 0.0) return@mapNotNull null            // no schedule → cannot project a run-out
        val tabletsPerUnit = unit.tabletsPerUnit.toIntOrNull() ?: 0
        // The in-hand figure was gathered [daysSinceGathered] days ago; decay it at the daily rate.
        val inHand = ((inHandByMedication[unit.medicationId] ?: 0.0) - rate * daysSinceGathered)
            .coerceAtLeast(0.0).roundToInt()
        val fills = scriptsByUnit[unit.formatId].orEmpty()
            .sumOf { (it.repeats + 1 - it.dispensed).coerceAtLeast(0) }
        RunOutSeries(
            unitId = unit.formatId,
            label = unit.label,
            totalTablets = inHand + fills * tabletsPerUnit,
            dailyRate = rate,
            colorIndex = 0,
        )
    }
        .sortedBy { it.label.lowercase() }
        .mapIndexed { index, s -> s.copy(colorIndex = index) }

    val maxRunOut = series.maxOfOrNull { it.runOutDay } ?: 0.0
    val maxTablets = series.maxOfOrNull { it.totalTablets } ?: 0
    val step = if (maxTablets <= 100) 10 else 50
    val domainDays = ceil(maxRunOut / MONTH_DAYS).coerceAtLeast(1.0) * MONTH_DAYS
    val domainTablets = (ceil(maxTablets.toDouble() / step).toInt().coerceAtLeast(1)) * step

    return RunOutGraphData(series, domainDays, domainTablets, step, monthBoundaryTicks(nowMillis, domainDays))
}

/**
 * The calendar-month boundaries (the 1st of each month) that fall within (now, now + [domainDays]],
 * expressed as day offsets from now and labelled with the abbreviated name of the month they begin.
 */
private fun monthBoundaryTicks(nowMillis: Long, domainDays: Double): List<MonthTick> {
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMillis
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    // Step to the first month boundary strictly after now.
    if (cal.timeInMillis <= nowMillis) cal.add(Calendar.MONTH, 1)

    val endMillis = nowMillis + (domainDays * DAY_MILLIS).toLong()
    val format = SimpleDateFormat("MMM", Locale.getDefault())
    val ticks = mutableListOf<MonthTick>()
    while (cal.timeInMillis <= endMillis) {
        ticks += MonthTick((cal.timeInMillis - nowMillis) / DAY_MILLIS, format.format(cal.time))
        cal.add(Calendar.MONTH, 1)
    }
    return ticks
}
