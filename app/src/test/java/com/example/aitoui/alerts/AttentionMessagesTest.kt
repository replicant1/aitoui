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
        assertEquals(listOf(AttentionKind.NO_SCRIPTS), messages.map { it.kind })
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
    fun `a non-prescription medication still raises supply messages`() {
        // The prescription guard only affects "no scripts" — low-supply warnings still apply.
        val ks = kinds(supply(brand = "Cartia", inHandDays = 3, fills = 0, totalDays = 3, requiresPrescription = false))
        assertTrue(AttentionKind.NO_SCRIPTS !in ks)
        assertTrue(AttentionKind.LOW_TOTAL_SUPPLY in ks)
    }

    @Test
    fun `low in hand while scripts remain reports the in-hand time remaining`() {
        // Plenty of total supply (lots of scripts), but only 5 days in hand.
        val messages = attentionMessages(listOf(supply(brand = "Cartia", inHandDays = 5, fills = 5, totalDays = 300)))
        assertEquals(listOf(AttentionKind.LOW_IN_HAND_HAS_SCRIPTS), messages.map { it.kind })
        // The time remaining uses the Inventory screen's humaniser (5 days → "5 days").
        assertEquals("You have only 5 days of Cartia in hand.", messages.single().text)
    }

    @Test
    fun `the in-hand time is humanised into weeks like the Inventory screen`() {
        // 10 days in hand at the default rate → the humaniser expresses it as weeks.
        val text = attentionMessages(listOf(supply(brand = "Tensig", inHandDays = 10, fills = 3, totalDays = 300)))
            .single { it.kind == AttentionKind.LOW_IN_HAND_HAS_SCRIPTS }.text
        assertEquals("You have only 1.4 weeks of Tensig in hand.", text)
    }

    @Test
    fun `low in hand does not fire without scripts to dispense`() {
        // No scripts and 10 days in hand: no-scripts + low-total, but never the "get a script dispensed" nudge.
        val ks = kinds(supply(inHandDays = 10, fills = 0, totalDays = 10))
        assertTrue(AttentionKind.NO_SCRIPTS in ks)
        assertTrue(AttentionKind.LOW_TOTAL_SUPPLY in ks)
        assertTrue(AttentionKind.LOW_IN_HAND_HAS_SCRIPTS !in ks)
    }

    @Test
    fun `less than two weeks of total supply raises the low-total message`() {
        assertTrue(AttentionKind.LOW_TOTAL_SUPPLY in kinds(supply(inHandDays = 3, fills = 1, totalDays = 10)))
    }

    @Test
    fun `the two-week boundary is exclusive for total supply and inclusive for in hand`() {
        // Exactly 14 days total → not "less than two weeks", so no low-total message.
        assertTrue(AttentionKind.LOW_TOTAL_SUPPLY !in kinds(supply(inHandDays = 14, fills = 2, totalDays = 14)))
        assertTrue(AttentionKind.LOW_TOTAL_SUPPLY in kinds(supply(inHandDays = 13, fills = 2, totalDays = 13)))
        // Exactly 14 days in hand (with scripts) → "two weeks or less", so the in-hand nudge fires.
        assertTrue(AttentionKind.LOW_IN_HAND_HAS_SCRIPTS in kinds(supply(inHandDays = 14, fills = 2, totalDays = 300)))
        assertTrue(AttentionKind.LOW_IN_HAND_HAS_SCRIPTS !in kinds(supply(inHandDays = 15, fills = 2, totalDays = 300)))
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
        // With a one-week window, 10 days total is fine; with the default two weeks it is not.
        assertTrue(attentionMessages(listOf(supply(inHandDays = 10, fills = 3, totalDays = 10)), warningDays = 7).none {
            it.kind == AttentionKind.LOW_TOTAL_SUPPLY
        })
        assertTrue(attentionMessages(listOf(supply(inHandDays = 10, fills = 3, totalDays = 10))).any {
            it.kind == AttentionKind.LOW_TOTAL_SUPPLY
        })
    }
}
