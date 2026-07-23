package com.example.aitoui.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationNameCleaningTest {

    @Test
    fun `cleanMedicationName converts all-caps brand and active ingredient to title case`() {
        assertEquals("Panadol", "PANADOL".cleanMedicationName())
        assertEquals("Paracetamol", "PARACETAMOL".cleanMedicationName())
    }

    @Test
    fun `cleanMedicationName trims and collapses internal whitespace`() {
        assertEquals("Vitamin C", "  VITAMIN   C  ".cleanMedicationName())
    }

    @Test
    fun `cleanMedicationName capitalises after separators while keeping digits`() {
        assertEquals("B12-Forte / Extra", "b12-forte / extra".cleanMedicationName())
    }

    @Test
    fun `cleanMedicationName keeps release abbreviations uppercase`() {
        assertEquals("Panadol XR", "panadol xr".cleanMedicationName())
        assertEquals("Metformin SR", "METFORMIN sr".cleanMedicationName())
    }

    @Test
    fun `cleanMedicationName applies known mixed-case chemical abbreviations`() {
        assertEquals("Tamsulosin HCl", "tamsulosin hcl".cleanMedicationName())
        assertEquals("Example HBr", "EXAMPLE hbr".cleanMedicationName())
    }

    @Test
    fun `cleanMedicationName keeps unit tokens lowercase`() {
        assertEquals("Vitamin D 1000mcg", "VITAMIN d 1000MCG".cleanMedicationName())
        assertEquals("Calcium 500mg", "CALCIUM 500MG".cleanMedicationName())
    }

    @Test
    fun `cleanMedicationName keeps roman numerals uppercase`() {
        assertEquals("Formula II", "formula ii".cleanMedicationName())
    }

    @Test
    fun `cleaned normalises both medication name fields`() {
        val medication = Medication(
            brandName = "  PANADOL OSTEO  ",
            activeIngredient = "PARACETAMOL",
        )

        assertEquals(
            Medication(
                brandName = "Panadol Osteo",
                activeIngredient = "Paracetamol",
            ),
            medication.cleaned(),
        )
    }
}

