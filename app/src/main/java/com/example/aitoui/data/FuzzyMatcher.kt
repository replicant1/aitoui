package com.example.aitoui.data

/**
 * Classifies entered medication / dispensable-unit values against existing database records, for the Add
 * Script "resolve on Save" flow. Pure and JVM-testable.
 *
 * A medication is offered as a **candidate** when its brand or active ingredient is [SIMILAR] enough, and a
 * new one is **blocked** (refused) when an existing medication is near-identical on both. A dispensable unit
 * is **blocked** when the medication already has one with the same dose and tablets-per-unit.
 */
object FuzzyMatcher {

    /** Brand OR active this similar (0..1) makes an existing medication a candidate worth offering. */
    const val SIMILAR = 0.6

    /** Brand AND active this similar makes it a near-duplicate: creating a new medication is refused. */
    const val BLOCK = 0.9

    data class MedicationMatches(
        val exact: List<Medication>,
        val similar: List<Medication>,
        /** An existing medication is too similar to allow creating a new one. */
        val blocked: Boolean,
    ) {
        val hasCandidates: Boolean get() = exact.isNotEmpty() || similar.isNotEmpty()
    }

    fun classifyMedications(brand: String, active: String, existing: List<Medication>): MedicationMatches {
        val normBrand = TextSimilarity.normalize(brand)
        val normActive = TextSimilarity.normalize(active)

        val exact = existing.filter {
            TextSimilarity.normalize(it.brandName) == normBrand &&
                TextSimilarity.normalize(it.activeIngredient) == normActive
        }
        val exactIds = exact.mapTo(HashSet()) { it.id }

        val similar = existing
            .filter { it.id !in exactIds }
            .map { it to (TextSimilarity.ratio(brand, it.brandName) + TextSimilarity.ratio(active, it.activeIngredient)) }
            .filter {
                TextSimilarity.ratio(brand, it.first.brandName) >= SIMILAR ||
                    TextSimilarity.ratio(active, it.first.activeIngredient) >= SIMILAR
            }
            .sortedByDescending { it.second }
            .map { it.first }

        val blocked = existing.any {
            TextSimilarity.ratio(brand, it.brandName) >= BLOCK &&
                TextSimilarity.ratio(active, it.activeIngredient) >= BLOCK
        }
        return MedicationMatches(exact, similar, blocked)
    }

    data class DispensableUnitMatches(
        /** The resolved medication's existing dispensable units, offered to pick from. */
        val candidates: List<DispensableUnitDetails>,
        /** A dispensable unit with the same dose and tablets-per-unit already exists: creating a new one is refused. */
        val blocked: Boolean,
    ) {
        val hasCandidates: Boolean get() = candidates.isNotEmpty()
    }

    fun classifyDispensableUnits(
        medicationId: Long,
        dosePerTablet: String,
        tabletsPerUnit: String,
        existing: List<DispensableUnitDetails>,
    ): DispensableUnitMatches {
        val forMedication = existing.filter { it.medicationId == medicationId }
        val blocked = forMedication.any {
            TextSimilarity.normalize(it.dosePerTablet) == TextSimilarity.normalize(dosePerTablet) &&
                TextSimilarity.normalize(it.tabletsPerUnit) == TextSimilarity.normalize(tabletsPerUnit)
        }
        return DispensableUnitMatches(forMedication, blocked)
    }
}
