package com.example.aitoui.data

/**
 * Seeds the database with a moderate amount of realistic dummy data so the app can be exercised
 * without manual entry. Intended for debug builds only (see [com.example.aitoui.AitouiApp]).
 *
 * Idempotent: it only seeds when the [medication_formats] table is empty, so repeated app startups
 * never grow the data without bound.
 *
 * The medication data is a curated, embedded list of real-world brand/active-ingredient pairs
 * (no network call: the app has no INTERNET permission and a startup fetch would be fragile).
 */
object DatabaseSeeder {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** brand, activeIngredient, dosePerTablet, tabletsPerBox */
    private val FORMATS = listOf(
        MedicationFormat(brandName = "Panadol", activeIngredient = "Paracetamol", dosePerTablet = "500", tabletsPerBox = "24"),
        MedicationFormat(brandName = "Nurofen", activeIngredient = "Ibuprofen", dosePerTablet = "200", tabletsPerBox = "24"),
        MedicationFormat(brandName = "Amoxil", activeIngredient = "Amoxicillin", dosePerTablet = "500", tabletsPerBox = "20"),
        MedicationFormat(brandName = "Augmentin", activeIngredient = "Amoxicillin/Clavulanate", dosePerTablet = "875", tabletsPerBox = "14"),
        MedicationFormat(brandName = "Ventolin", activeIngredient = "Salbutamol", dosePerTablet = "100", tabletsPerBox = "200"),
        MedicationFormat(brandName = "Lipitor", activeIngredient = "Atorvastatin", dosePerTablet = "20", tabletsPerBox = "30"),
        MedicationFormat(brandName = "Zoloft", activeIngredient = "Sertraline", dosePerTablet = "50", tabletsPerBox = "30"),
        MedicationFormat(brandName = "Nexium", activeIngredient = "Esomeprazole", dosePerTablet = "40", tabletsPerBox = "30"),
        MedicationFormat(brandName = "Lyrica", activeIngredient = "Pregabalin", dosePerTablet = "75", tabletsPerBox = "56"),
        MedicationFormat(brandName = "Diabex", activeIngredient = "Metformin", dosePerTablet = "500", tabletsPerBox = "100"),
        MedicationFormat(brandName = "Coumadin", activeIngredient = "Warfarin", dosePerTablet = "5", tabletsPerBox = "50"),
        MedicationFormat(brandName = "Cipramil", activeIngredient = "Citalopram", dosePerTablet = "20", tabletsPerBox = "28"),
    )

    /** brand (must match a FORMATS entry), directions, quantity, repeats, valid-for (days from now) */
    private val SCRIPTS = listOf(
        ScriptSeed("Panadol", "Take two tablets every 4–6 hours as needed", 48, 2, 180),
        ScriptSeed("Amoxil", "Take one capsule three times a day for 7 days", 21, 0, 30),
        ScriptSeed("Augmentin", "Take one tablet twice a day for 5 days", 10, 0, 30),
        ScriptSeed("Ventolin", "Two puffs as needed for shortness of breath", 1, 3, 365),
        ScriptSeed("Lipitor", "Take one tablet at night", 30, 5, 365),
        ScriptSeed("Zoloft", "Take one tablet in the morning", 30, 5, 180),
        ScriptSeed("Nexium", "Take one tablet before breakfast", 30, 2, 90),
        ScriptSeed("Diabex", "Take one tablet twice a day with meals", 60, 5, 365),
    )

    suspend fun seedIfEmpty(
        formatRepository: MedicationFormatRepository,
        scriptRepository: ScriptRepository,
        nowMillis: Long,
    ) {
        if (formatRepository.count() > 0) return // already seeded — never grow without bound

        val idByBrand = HashMap<String, Long>()
        for (format in FORMATS) {
            idByBrand[format.brandName] = formatRepository.add(format)
        }

        for (seed in SCRIPTS) {
            val formatId = idByBrand[seed.brand] ?: continue
            scriptRepository.add(
                Script(
                    medicationFormatId = formatId,
                    directions = seed.directions,
                    quantity = seed.quantity,
                    repeats = seed.repeats,
                    validToMillis = nowMillis + seed.validForDays * DAY_MILLIS,
                )
            )
        }
    }

    private data class ScriptSeed(
        val brand: String,
        val directions: String,
        val quantity: Int,
        val repeats: Int,
        val validForDays: Long,
    )
}
