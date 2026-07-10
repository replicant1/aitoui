package com.example.aitoui.data

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
        MedicationSeed("Panadol", "Paracetamol", "500", "24"),
        MedicationSeed("Nurofen", "Ibuprofen", "200", "24"),
        MedicationSeed("Amoxil", "Amoxicillin", "500", "20"),
        MedicationSeed("Augmentin", "Amoxicillin/Clavulanate", "875", "14"),
        MedicationSeed("Ventolin", "Salbutamol", "100", "200"),
        MedicationSeed("Lipitor", "Atorvastatin", "20", "30"),
        MedicationSeed("Zoloft", "Sertraline", "50", "30"),
        MedicationSeed("Nexium", "Esomeprazole", "40", "30"),
        MedicationSeed("Lyrica", "Pregabalin", "75", "56"),
        MedicationSeed("Diabex", "Metformin", "500", "100"),
        MedicationSeed("Coumadin", "Warfarin", "5", "50"),
        MedicationSeed("Cipramil", "Citalopram", "20", "28"),
    )

    /**
     * One script per medication format. Columns: brand (must match a MEDICATIONS entry), valid-for
     * (days), dispensed (times dispensed so far). Every seeded script defaults to 5 repeats. The
     * inventory shows the dispensed count per format.
     */
    private val SCRIPTS = listOf(
        ScriptSeed("Panadol", 180, 2),
        ScriptSeed("Amoxil", 30, 1),
        ScriptSeed("Augmentin", 30, 0),
        ScriptSeed("Ventolin", 365, 1),
        ScriptSeed("Lipitor", 365, 3),
        ScriptSeed("Zoloft", 180, 6),
        ScriptSeed("Nexium", 90, 1),
        ScriptSeed("Diabex", 365, 4),
    )

    suspend fun seedIfEmpty(
        medicationRepository: MedicationRepository,
        formatRepository: DispensableUnitRepository,
        scriptRepository: ScriptRepository,
        dispensationRepository: DispensationRepository,
        nowMillis: Long,
    ) {
        if (medicationRepository.count() > 0) return // already seeded — never grow without bound

        // One medication and one dispensable unit per seed entry; capture the unit id per brand.
        val unitIdByBrand = HashMap<String, Long>()
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
        }

        // Each seed script is for a single dispensable unit (many scripts → one unit).
        for (seed in SCRIPTS) {
            val unitId = unitIdByBrand[seed.brand] ?: continue
            val scriptId = scriptRepository.add(
                Script(
                    dispensableUnitId = unitId,
                    repeats = seed.repeats,
                    validToMillis = nowMillis + seed.validForDays * DAY_MILLIS,
                )
            )
            // The script's dispensed count is derived from the dispensations table, so record the
            // seeded prior dispensations there. Clamp to 0..repeats so a script is never seeded as
            // dispensed more times than it may be.
            val dispensed = seed.dispensed.coerceIn(0, seed.repeats)
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
        val repeats: Int = 5,
    )
}
