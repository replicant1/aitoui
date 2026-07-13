package com.example.aitoui.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Seeds the database with a moderate amount of realistic dummy data so the app can be exercised
 * without manual entry. Intended for debug builds only (see [com.example.aitoui.AitouiApp]).
 *
 * Idempotent: it only seeds when the [medications] table is empty, so repeated app startups never
 * grow the data without bound.
 *
 * Data is split across the normalized schema: a [Medication] (brand + active ingredient), a
 * [DispensableUnit] referencing it (dose/tablets), and [Script]s referencing the format.
 *
 * The medication data is a curated, embedded list of real-world brand/active-ingredient pairs
 * (no network call: the app has no INTERNET permission and a startup fetch would be fragile).
 */
object DatabaseSeeder {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** brand, activeIngredient, dosePerTablet, tabletsPerUnit */
    private val MEDICATIONS = listOf(
        MedicationSeed("Lithicarb", "Lithium Carbide", "250", "200"),
        MedicationSeed("Tensig", "Atenolol", "50", "60"),
        MedicationSeed("Dytrex", "Duloxetine", "60", "56"),
        MedicationSeed("Mirtanza", "Mirtazapine", "45", "60"),
        MedicationSeed("Prexum", "Perindopril Arginine", "5", "60"),
        MedicationSeed("Lipitor", "Atorvastatin", "40", "60"),
        MedicationSeed("Dithiazide", "Hydrochlorothiazide", "25", "30"),
        MedicationSeed("Tamsulosin", "Tamsulosin Hydrochloride", "0.4", "30"),
    )

    /**
     * One script per medication format. Columns: brand (must match a MEDICATIONS entry), valid-for
     * (days), dispensed (times dispensed so far), date-of-issue ("dd-MM-yyyy" text, parsed to millis),
     * serial number and repeats. The inventory shows the dispensed count per format.
     */
    private val SCRIPTS = listOf(
        ScriptSeed("Prexum", 365, 2, "20-03-2026", "PH0201893", 5),
        ScriptSeed("Mirtanza", 365, 0, "17-06-2026", "PW2048023", 2),
        ScriptSeed("Tensig", 365, 0, "17-06-2026", "PW2048021", 5),
        ScriptSeed("Dytrex", 365, 1, "17-06-2026", "PW2048022", 2),
        ScriptSeed("Mirtanza", 365, 2, "20-03-2026", "PH0201894", 2),
        ScriptSeed("Prexum", 365, 4, "26-04-2025", "PH0208390", 5),
        ScriptSeed("Lipitor", 365, 3, "26-04-2025", "NT1017819", 5),
        ScriptSeed("Tensig", 365, 5, "30-10-2025", "PH0201890", 5),
        ScriptSeed("Lipitor", 365, 4, "30-10-2025", "PH0201891", 5),
        ScriptSeed("Dithiazide", 365, 4, "04-11-2025", "PH0201889", 5),
        ScriptSeed("Lithicarb", 365, 1, "01-07-2026", "PH0203782", 2),
    )

    /**
     * How many tablets of each medication are taken per day (brand must match a MEDICATIONS entry).
     * Fractional quantities are allowed, e.g. half a tablet a day.
     */
    private val DAILY_SCHEDULE = listOf(
        // AM
        DailyScheduleSeed("Dytrex", 2.0),
        DailyScheduleSeed("Tamsulosin", 1.0),
        DailyScheduleSeed("Prexum", 1.0),
        DailyScheduleSeed("Lithicarb", 3.0),
        DailyScheduleSeed("Dithiazide", 0.5),
        DailyScheduleSeed("Tensig", 1.0),
        // PM
        DailyScheduleSeed("Tensig", 1.0),
        DailyScheduleSeed("Mirtanza", 1.0),
        DailyScheduleSeed("Lipitor", 1.0),
        DailyScheduleSeed("Lithicarb", 3.0),
    )

    /** Tablets currently in hand per medication (brand must match a MEDICATIONS entry). */
    private val IN_HAND = listOf(
        InHandSeed("Lipitor", 120.0),
        InHandSeed("Dytrex", 56.0),
        InHandSeed("Tamsulosin", 45.0),
        InHandSeed("Mirtanza", 180.0),
        InHandSeed("Dithiazide", 19.0),
        InHandSeed("Tensig", 120.0),
        InHandSeed("Prexum", 44.0),
        InHandSeed("Lithicarb", 154.0),
    )

    suspend fun seedIfEmpty(
        medicationRepository: MedicationRepository,
        formatRepository: DispensableUnitRepository,
        scriptRepository: ScriptRepository,
        dispensationRepository: DispensationRepository,
        dailyScheduleRepository: DailyScheduleRepository,
        inHandRepository: InHandRepository,
        nowMillis: Long,
    ) {
        if (medicationRepository.count() > 0) return // already seeded — never grow without bound

        // One medication and one dispensable unit per seed entry; capture the ids per brand.
        val unitIdByBrand = HashMap<String, Long>()
        val medicationIdByBrand = HashMap<String, Long>()
        for (med in MEDICATIONS) {
            val medicationId = medicationRepository.add(
                Medication(brandName = med.brand, activeIngredient = med.activeIngredient)
            )
            val unitId = formatRepository.add(
                DispensableUnit(
                    medicationId = medicationId,
                    dosePerTablet = med.dosePerTablet,
                    tabletsPerUnit = med.tabletsPerUnit,
                )
            )
            unitIdByBrand[med.brand] = unitId
            medicationIdByBrand[med.brand] = medicationId
        }

        // Each seed script is for a single dispensable unit (many scripts → one unit).
        for (seed in SCRIPTS) {
            val unitId = unitIdByBrand[seed.brand] ?: continue
            val scriptId = scriptRepository.add(
                Script(
                    dispensableUnitId = unitId,
                    serialNo = seed.serialNo,
                    dateOfIssue = parseDate(seed.dateOfIssue),
                    repeats = seed.repeats,
                    validToMillis = nowMillis + seed.validForDays * DAY_MILLIS,
                )
            )
            // The script's dispensed count is derived from the dispensations table, so record the
            // seeded prior dispensations there. Clamp to 0..repeats+1 (a script allows one more
            // dispensation than its repeats count) so it is never seeded past its limit.
            val dispensed = seed.dispensed.coerceIn(0, seed.repeats + 1)
            if (dispensed > 0) {
                dispensationRepository.add(
                    Dispensation(
                        scriptId = scriptId,
                        dispensableUnitId = unitId,
                        number = dispensed,
                        dispensedAtMillis = nowMillis,
                    )
                )
            }
        }

        // Seed the daily medication schedule (tablets taken per day, per medication).
        dailyScheduleRepository.save(
            DAILY_SCHEDULE.mapNotNull { seed ->
                val medicationId = medicationIdByBrand[seed.brand] ?: return@mapNotNull null
                DailyScheduleItem(medicationId = medicationId, quantity = seed.quantity)
            }
        )

        // Seed the tablets currently in hand, per medication, gathered "today".
        inHandRepository.save(
            IN_HAND.mapNotNull { seed ->
                val medicationId = medicationIdByBrand[seed.brand] ?: return@mapNotNull null
                InHandItem(medicationId = medicationId, quantity = seed.quantity)
            },
            gatheredAtMillis = nowMillis,
        )
    }

    private data class MedicationSeed(
        val brand: String,
        val activeIngredient: String,
        val dosePerTablet: String,
        val tabletsPerUnit: String,
    )

    private data class ScriptSeed(
        val brand: String,
        val validForDays: Long,
        val dispensed: Int,
        /** Date of issue in "dd-MM-yyyy" text form (parsed to epoch millis via [parseDate]). */
        val dateOfIssue: String,
        val serialNo: String,
        val repeats: Int,
    )

    private data class DailyScheduleSeed(
        val brand: String,
        val quantity: Double,
    )

    private data class InHandSeed(
        val brand: String,
        val quantity: Double,
    )

    /** Parses a "dd-MM-yyyy" date string to UTC epoch millis at the start of that day. */
    private fun parseDate(text: String): Long {
        val formatter = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return requireNotNull(formatter.parse(text)) { "Invalid seed date: $text" }.time
    }
}
