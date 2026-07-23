package com.example.aitoui.data

import kotlin.math.max

/**
 * Classifies entered medication / dispensable-unit values against existing database records, for the Add
 * Script "resolve on Save" flow. Pure and JVM-testable.
 *
 * A medication is offered as a **candidate** when its brand or active ingredient is [SIMILAR] enough, and a
 * new one is **blocked** (refused) when an existing medication is near-identical on both. A dispensable unit
 * is **blocked** when the medication already has one with the same dose and tablets-per-unit.
 */
object FuzzyMatcher {

    /**
     * Brand OR active this similar (0..1) makes an existing medication a candidate worth offering. Kept
     * deliberately low: the resolve dialog always confirms the pick, so an extra candidate costs a glance
     * while a missed one costs a duplicate record — so we err towards surfacing more.
     */
    const val SIMILAR = 0.45

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

        // A medication matches exactly when the fields line up as entered, OR when the user has transposed
        // them — entered brand equals the medication's active ingredient, and entered active its brand.
        val exact = existing.filter {
            val medBrand = TextSimilarity.normalize(it.brandName)
            val medActive = TextSimilarity.normalize(it.activeIngredient)
            (medBrand == normBrand && medActive == normActive) ||
                (medBrand == normActive && medActive == normBrand)
        }
        val exactIds = exact.mapTo(HashSet()) { it.id }

        val similar = existing
            .filter { it.id !in exactIds }
            .map { it to medicationScore(brand, active, it) }
            .filter { it.second.qualifies }
            .sortedByDescending { it.second.total }
            .map { it.first }

        val blocked = existing.any { medicationScore(brand, active, it).blocks }

        return MedicationMatches(exact, similar, blocked)
    }

    /**
     * Similarity of the entered brand/active to a medication, taking the better of the as-entered and the
     * swapped orientations so a transposed brand/active still surfaces (and blocks) its medication.
     */
    private fun medicationScore(brand: String, active: String, med: Medication): MedicationScore {
        val brandVsBrand = TextSimilarity.ratio(brand, med.brandName)
        val activeVsActive = TextSimilarity.ratio(active, med.activeIngredient)
        val brandVsActive = TextSimilarity.ratio(brand, med.activeIngredient)
        val activeVsBrand = TextSimilarity.ratio(active, med.brandName)
        return MedicationScore(
            total = max(brandVsBrand + activeVsActive, brandVsActive + activeVsBrand),
            qualifies = brandVsBrand >= SIMILAR || activeVsActive >= SIMILAR ||
                brandVsActive >= SIMILAR || activeVsBrand >= SIMILAR,
            blocks = (brandVsBrand >= BLOCK && activeVsActive >= BLOCK) ||
                (brandVsActive >= BLOCK && activeVsBrand >= BLOCK),
        )
    }

    /** The as-entered/swapped similarity of one medication: its ranking [total] and the two thresholds. */
    private data class MedicationScore(val total: Double, val qualifies: Boolean, val blocks: Boolean)

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
        doseUnit: String,
        existing: List<DispensableUnitDetails>,
    ): DispensableUnitMatches {
        val forMedication = existing.filter { it.medicationId == medicationId }
        val blocked = forMedication.any {
            TextSimilarity.normalize(it.dosePerTablet) == TextSimilarity.normalize(dosePerTablet) &&
                TextSimilarity.normalize(it.tabletsPerUnit) == TextSimilarity.normalize(tabletsPerUnit) &&
                TextSimilarity.normalize(it.doseUnit) == TextSimilarity.normalize(doseUnit)
        }
        return DispensableUnitMatches(forMedication, blocked)
    }
}
