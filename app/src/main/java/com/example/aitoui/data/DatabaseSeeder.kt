package com.example.aitoui.data

/**
 * Seeds the database with a moderate amount of realistic dummy data so the app can be exercised
 * without manual entry. Intended for debug builds only (see [com.example.aitoui.AitouiApp]).
 *
 * Idempotent: it only seeds when the [medications] table is empty, so repeated app startups never
 * grow the data without bound.
 *
 * Data is split across the normalized schema: a [Medication] (brand + active ingredient), a
 * [MedicationFormat] referencing it (dose/tablets), and [Script]s referencing the format.
 *
 * The medication data is a curated, embedded list of real-world brand/active-ingredient pairs
 * (no network call: the app has no INTERNET permission and a startup fetch would be fragile).
 */
object DatabaseSeeder {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** brand, activeIngredient, dosePerTablet, tabletsPerBox */
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
     * One script per medication format. Columns: brand (must match a MEDICATIONS entry), directions,
     * quantity (total dispensations allowed), repeats, valid-for (days), dispensed (times dispensed
     * so far, ≤ quantity). The inventory shows "dispensed/quantity" per format.
     */
    private val SCRIPTS = listOf(
        ScriptSeed("Panadol", "Take two tablets every 4–6 hours as needed", 6, 5, 180, 2),
        ScriptSeed("Amoxil", "Take one capsule three times a day for 7 days", 1, 0, 30, 1),
        ScriptSeed("Augmentin", "Take one tablet twice a day for 5 days", 1, 0, 30, 0),
        ScriptSeed("Ventolin", "Two puffs as needed for shortness of breath", 4, 3, 365, 1),
        ScriptSeed("Lipitor", "Take one tablet at night", 6, 5, 365, 3),
        ScriptSeed("Zoloft", "Take one tablet in the morning", 6, 5, 180, 6),
        ScriptSeed("Nexium", "Take one tablet before breakfast", 3, 2, 90, 1),
        ScriptSeed("Diabex", "Take one tablet twice a day with meals", 6, 5, 365, 4),
    )

    suspend fun seedIfEmpty(
        medicationRepository: MedicationRepository,
        formatRepository: MedicationFormatRepository,
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
                MedicationFormat(
                    medicationId = medicationId,
                    dosePerTablet = med.dosePerTablet,
                    tabletsPerBox = med.tabletsPerBox,
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
                    directions = seed.directions,
                    quantity = seed.quantity,
                    repeats = seed.repeats,
                    validToMillis = nowMillis + seed.validForDays * DAY_MILLIS,
                )
            )
            // The script's dispensed count is now derived from the dispensations table, so record
            // the seeded prior dispensations there rather than as a column on the script.
            if (seed.dispensed > 0) {
                dispensationRepository.add(
                    Dispensation(
                        scriptId = scriptId,
                        dispensableUnitId = unitId,
                        number = seed.dispensed,
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
        val tabletsPerBox: String,
    )

    private data class ScriptSeed(
        val brand: String,
        val directions: String,
        val quantity: Int,
        val repeats: Int,
        val validForDays: Long,
        val dispensed: Int,
    )
}
