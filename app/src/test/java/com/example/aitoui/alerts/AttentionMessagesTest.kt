package com.example.aitoui.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionMessagesTest {

    private fun supply(
        brand: String = "Med",
        inHandDays: Int = 100,
        fills: Int = 2,
        totalDays: Int = 100,
        requiresPrescription: Boolean = true,
    ) = MedicationSupply(
        medicationId = 1,
        brandName = brand,
        dailyRate = 1.0,
        inHandDays = inHandDays,
        undispensedFills = fills,
        totalDays = totalDays,
        requiresPrescription = requiresPrescription,
    )

    private fun kinds(vararg s: MedicationSupply) = attentionMessages(s.toList()).map { it.kind }

    @Test
    fun `a well-stocked medication raises no messages`() {
        assertTrue(attentionMessages(listOf(supply(inHandDays = 100, fills = 3, totalDays = 100))).isEmpty())
    }

    @Test
    fun `no undispensed scripts raises the no-scripts message for a prescription medication`() {
        val messages = attentionMessages(listOf(supply(brand = "Lipitor", inHandDays = 100, fills = 0, totalDays = 100)))
        assertEquals(listOf(AttentionKind.NO_SCRIPTS_FOR_PRESCRIPTION_MEDICATION), messages.map { it.kind })
        assertTrue(messages.single().text.contains("Lipitor"))
    }

    @Test
    fun `a non-prescription medication never raises the no-scripts message`() {
        // Over-the-counter medication with no scripts and plenty of stock → no message at all.
        assertTrue(
            attentionMessages(
                listOf(supply(brand = "Cartia", inHandDays = 100, fills = 0, totalDays = 100, requiresPrescription = false)),
            ).isEmpty(),
        )
    }

    @Test
    fun `a non-prescription medication raises no messages`() {
        // Every remaining message applies only to prescription medications.
        val ks = kinds(supply(brand = "Cartia", inHandDays = 3, fills = 0, totalDays = 3, requiresPrescription = false))
        assertTrue(ks.isEmpty())
    }

    @Test
    fun `a non-prescription medication never raises the low-in-hand nudge`() {
        // Low in hand with scripts, but over-the-counter → the "get a script dispensed" nudge is suppressed.
        val ks = kinds(supply(inHandDays = 5, fills = 5, totalDays = 300, requiresPrescription = false))
        assertTrue(AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITH_SCRIPTS !in ks)
    }

    @Test
    fun `a low prescription medication with no scripts left raises the go-to-doctor message`() {
        val messages = attentionMessages(listOf(supply(brand = "Lipitor", inHandDays = 3, fills = 0, totalDays = 3)))
        assertTrue(AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITHOUT_SCRIPTS in messages.map { it.kind })
        assertEquals(
            "Less than 2 weeks of Lipitor left with no scripts remaining — go to doctor for new scripts.",
            messages.single { it.kind == AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITHOUT_SCRIPTS }.text,
        )
    }

    @Test
    fun `the go-to-doctor message needs a prescription, no scripts, and under two weeks total`() {
        val kind = AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITHOUT_SCRIPTS
        // Has scripts to fall back on → no.
        assertTrue(kind !in kinds(supply(inHandDays = 3, fills = 2, totalDays = 3)))
        // Over-the-counter → no.
        assertTrue(kind !in kinds(supply(inHandDays = 3, fills = 0, totalDays = 3, requiresPrescription = false)))
        // Two weeks or more of total supply → no.
        assertTrue(kind !in kinds(supply(inHandDays = 100, fills = 0, totalDays = 100)))
    }

    @Test
    fun `low in hand while scripts remain reports the in-hand time remaining`() {
        // Plenty of total supply (lots of scripts), but only 5 days in hand.
        val messages = attentionMessages(listOf(supply(brand = "Cartia", inHandDays = 5, fills = 5, totalDays = 300)))
        assertEquals(listOf(AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITH_SCRIPTS), messages.map { it.kind })
        // The time remaining uses the Inventory screen's humaniser (5 days → "5 days").
        assertEquals("You have only 5 days of Cartia in hand — get a script filled.", messages.single().text)
    }

    @Test
    fun `the in-hand time is humanised into weeks like the Inventory screen`() {
        // 10 days in hand at the default rate → the humaniser expresses it as weeks.
        val text = attentionMessages(listOf(supply(brand = "Tensig", inHandDays = 10, fills = 3, totalDays = 300)))
            .single { it.kind == AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITH_SCRIPTS }.text
        assertEquals("You have only 1.4 weeks of Tensig in hand — get a script filled.", text)
    }

    @Test
    fun `no scripts raises the no-scripts and without-scripts messages, never the with-scripts nudge`() {
        // No scripts and 10 days in hand (under two weeks total).
        val ks = kinds(supply(inHandDays = 10, fills = 0, totalDays = 10))
        assertTrue(AttentionKind.NO_SCRIPTS_FOR_PRESCRIPTION_MEDICATION in ks)
        assertTrue(AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITHOUT_SCRIPTS in ks)
        assertTrue(AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITH_SCRIPTS !in ks)
    }

    @Test
    fun `the two-week boundary is exclusive for total supply and inclusive for in hand`() {
        val without = AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITHOUT_SCRIPTS
        val with = AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITH_SCRIPTS
        // Total supply (without-scripts message): exactly 14 days is NOT "less than two weeks"; 13 is.
        assertTrue(without !in kinds(supply(inHandDays = 14, fills = 0, totalDays = 14)))
        assertTrue(without in kinds(supply(inHandDays = 13, fills = 0, totalDays = 13)))
        // In hand (with-scripts nudge): exactly 14 days IS "two weeks or less"; 15 is not.
        assertTrue(with in kinds(supply(inHandDays = 14, fills = 2, totalDays = 300)))
        assertTrue(with !in kinds(supply(inHandDays = 15, fills = 2, totalDays = 300)))
    }

    @Test
    fun `messages are ordered by medication brand name`() {
        val messages = attentionMessages(
            listOf(
                supply(brand = "Zoltar", fills = 0),
                supply(brand = "Amlo", fills = 0),
            ),
        )
        assertEquals(listOf("Amlo", "Zoltar"), messages.map { it.text.substringAfter("for ").substringBefore(" left") })
    }

    @Test
    fun `the warning window is configurable`() {
        // A prescription med, no scripts, 10 days total: fine under a one-week window, low under the default two.
        val kind = AttentionKind.LOW_IN_HAND_PRESCRIPTION_MEDICATION_WITHOUT_SCRIPTS
        assertTrue(
            attentionMessages(listOf(supply(inHandDays = 10, fills = 0, totalDays = 10)), warningDays = 7)
                .none { it.kind == kind },
        )
        assertTrue(
            attentionMessages(listOf(supply(inHandDays = 10, fills = 0, totalDays = 10)))
                .any { it.kind == kind },
        )
    }
}
