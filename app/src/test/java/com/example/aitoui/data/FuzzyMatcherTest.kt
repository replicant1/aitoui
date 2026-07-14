package com.example.aitoui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    private val meds = listOf(
        Medication(1, "Lipitor", "Atorvastatin"),
        Medication(2, "Tensig", "Atenolol"),
        Medication(3, "Nurofen", "Ibuprofen"),
    )

    @Test
    fun `exact medication match (case and whitespace insensitive) is found and blocks a new record`() {
        val r = FuzzyMatcher.classifyMedications("  lipitor ", "ATORVASTATIN", meds)
        assertEquals(listOf(1L), r.exact.map { it.id })
        assertTrue(r.blocked)
        assertTrue(r.hasCandidates)
    }

    @Test
    fun `a near-duplicate (typo in active) is not exact but is blocked and offered as similar`() {
        val r = FuzzyMatcher.classifyMedications("Lipitor", "Atorvastatn", meds) // missing an 'i'
        assertTrue(r.exact.isEmpty())
        assertTrue(r.blocked)                       // brand exact + active ~0.92 -> both >= BLOCK
        assertEquals(listOf(1L), r.similar.map { it.id })
    }

    @Test
    fun `a loosely similar medication is offered but not blocked`() {
        val r = FuzzyMatcher.classifyMedications("Lipito", "Zbc", meds) // brand ~0.86, active unrelated
        assertTrue(r.exact.isEmpty())
        assertFalse(r.blocked)                      // brand < BLOCK, so a new record is allowed
        assertEquals(listOf(1L), r.similar.map { it.id })
    }

    @Test
    fun `a genuinely new medication has no candidates and is not blocked`() {
        val r = FuzzyMatcher.classifyMedications("Ventolin", "Salbutamol", meds)
        assertTrue(r.exact.isEmpty())
        assertTrue(r.similar.isEmpty())
        assertFalse(r.blocked)
    }

    private fun unit(formatId: Long, medicationId: Long, dose: String, qty: String) =
        DispensableUnitDetails(formatId, medicationId, "Brand", "Active", dose, qty, null)

    private val units = listOf(
        unit(10, 2, "50", "60"),
        unit(11, 2, "25", "30"),
        unit(20, 1, "40", "60"),
    )

    @Test
    fun `dispensable-unit duplicate (same dose and tablets per unit) is blocked`() {
        val r = FuzzyMatcher.classifyDispensableUnits(2, "50", "60", units)
        assertEquals(listOf(10L, 11L), r.candidates.map { it.formatId })  // the medication's dispensable units
        assertTrue(r.blocked)
    }

    @Test
    fun `a new dispensable unit for the same medication is offered candidates but not blocked`() {
        val r = FuzzyMatcher.classifyDispensableUnits(2, "100", "30", units)
        assertEquals(listOf(10L, 11L), r.candidates.map { it.formatId })
        assertFalse(r.blocked)
    }

    @Test
    fun `a brand-new medication (no existing dispensable units) has no candidates`() {
        val r = FuzzyMatcher.classifyDispensableUnits(99, "50", "60", units)
        assertTrue(r.candidates.isEmpty())
        assertFalse(r.blocked)
    }

    @Test
    fun `text similarity normalises and rates`() {
        assertEquals("lipitor 500mg", TextSimilarity.normalize("  Lipitor (500mg)!! "))
        assertEquals(1.0, TextSimilarity.ratio("Lipitor", "lipitor"), 1e-9)
        assertEquals(0.0, TextSimilarity.ratio("abc", ""), 1e-9)
    }
}
