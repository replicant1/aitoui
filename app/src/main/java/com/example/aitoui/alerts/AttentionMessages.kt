package com.example.aitoui.alerts

import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.ScriptDetails
import com.example.aitoui.inventory.humanizeDuration
import kotlin.math.floor

/** Default "running low" window: two weeks. A single knob, so a future Settings screen can surface it. */
const val DEFAULT_WARNING_DAYS = 14

/** The kinds of attention message, so the UI can pick an icon and tests can assert precisely. */
enum class AttentionKind {
    /** Taking a prescription medication, but no undispensed script repeats remain. */
    NO_SCRIPTS_FOR_PRESCRIPTION_MEDICATION,

    /** Less than the warning window of total supply (in hand + undispensed scripts) remains. */
    LOW_TOTAL_SUPPLY,

    /**
     * A prescription medication with the warning window or less in hand, but undispensed scripts still
     * available to dispense.
     */
    LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITH_SCRIPTS,
}

/** One attention message shown on the main screen: a [kind] (drives the icon) and ready-to-show [text]. */
data class AttentionMessage(val kind: AttentionKind, val text: String)

/** Per-medication supply facts the message rules read; one entry per medication the user is taking. */
data class MedicationSupply(
    val medicationId: Long,
    val brandName: String,
    /** Tablets consumed per day; always > 0 here (only medications being taken are included). */
    val dailyRate: Double,
    /** Whole days of supply currently in hand. */
    val inHandDays: Int,
    /** Remaining script repeats across all this medication's scripts. */
    val undispensedFills: Int,
    /** Whole days before running out counting both in hand and undispensed scripts. */
    val totalDays: Int,
    /** Whether the medication needs a prescription; script-availability messages only apply when true. */
    val requiresPrescription: Boolean = true,
)

/**
 * Reduces the raw inventory data to one [MedicationSupply] per medication the user is *taking* (a positive
 * daily rate). Mirrors the Inventory screen's supply maths, but aggregated per medication rather than per
 * dispensable unit: in-hand is the medication's decayed on-hand quantity, undispensed is summed across all
 * of the medication's scripts. A scheduled medication with no dispensable unit (so nothing to measure or
 * name) is skipped.
 */
fun medicationSupplies(
    units: List<DispensableUnitDetails>,
    scripts: List<ScriptDetails>,
    dailyByMedication: Map<Long, Double>,
    inHandByMedication: Map<Long, Double>,
    daysSinceGathered: Double,
): List<MedicationSupply> {
    val brandByMedication = units.associate { it.medicationId to it.brandName }
    val rxByMedication = units.associate { it.medicationId to it.requiresPrescription }
    val scriptsByMedication = scripts.groupBy { it.medicationId }

    val result = mutableListOf<MedicationSupply>()
    for ((medicationId, rate) in dailyByMedication) {
        if (rate <= 0.0) continue
        val brandName = brandByMedication[medicationId] ?: continue

        val inHandQuantity = ((inHandByMedication[medicationId] ?: 0.0) - rate * daysSinceGathered)
            .coerceAtLeast(0.0)
        val inHandDays = floor(inHandQuantity / rate).toInt()

        val medScripts = scriptsByMedication[medicationId].orEmpty()
        fun fillsOf(s: ScriptDetails) = (s.repeats + 1 - s.dispensed).coerceAtLeast(0)
        val undispensedFills = medScripts.sumOf { fillsOf(it) }
        val undispensedTablets = medScripts.sumOf { fillsOf(it) * (it.tabletsPerUnit.toIntOrNull() ?: 0) }
        val undispensedDays = floor(undispensedTablets.toDouble() / rate).toInt()

        result += MedicationSupply(
            medicationId = medicationId,
            brandName = brandName,
            dailyRate = rate,
            inHandDays = inHandDays,
            undispensedFills = undispensedFills,
            totalDays = inHandDays + undispensedDays,
            requiresPrescription = rxByMedication[medicationId] ?: true,
        )
    }
    return result
}

/**
 * The attention messages for the current supply picture, ordered by medication brand then by kind. Only
 * medications being taken are considered (they're the only ones in [supplies]). [warningDays] is the
 * "running low" window (default [DEFAULT_WARNING_DAYS]).
 *
 * The three rules are independent, so a single medication can raise more than one message (e.g. no scripts
 * *and* less than two weeks left). Add new rules here as more message types are defined.
 */
fun attentionMessages(
    supplies: List<MedicationSupply>,
    warningDays: Int = DEFAULT_WARNING_DAYS,
): List<AttentionMessage> {
    val window = humanizeDuration(warningDays)
    val messages = mutableListOf<AttentionMessage>()
    for (s in supplies.sortedBy { it.brandName.lowercase() }) {
        // Only prescription medications can have scripts, so the "no scripts left" nudge is meaningless
        // for over-the-counter ones.
        if (s.undispensedFills == 0 && s.requiresPrescription) {
            messages += AttentionMessage(
                AttentionKind.NO_SCRIPTS_FOR_PRESCRIPTION_MEDICATION,
                "You have no scripts for ${s.brandName} left — go to doctor for new scripts.",
            )
        }
        if (s.totalDays < warningDays) {
            messages += AttentionMessage(
                AttentionKind.LOW_TOTAL_SUPPLY,
                "Less than $window of ${s.brandName} left — in hand and scripts combined.",
            )
        }
        if (s.inHandDays <= warningDays && s.undispensedFills > 0 && s.requiresPrescription) {
            messages += AttentionMessage(
                AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITH_SCRIPTS,
                "You have only ${humanizeDuration(s.inHandDays)} of ${s.brandName} in hand.",
            )
        }
    }
    return messages
}
