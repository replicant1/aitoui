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
    ) = MedicationSupply(
        medicationId = 1,
        brandName = brand,
        dailyRate = 1.0,
        inHandDays = inHandDays,
        undispensedFills = fills,
        totalDays = totalDays,
    )

    private fun kinds(vararg s: MedicationSupply) = attentionMessages(s.toList()).map { it.kind }

    @Test
    fun `a well-stocked medication raises no messages`() {
        assertTrue(attentionMessages(listOf(supply(inHandDays = 100, fills = 3, totalDays = 100))).isEmpty())
    }

    @Test
    fun `no undispensed scripts raises the no-scripts message`() {
        val messages = attentionMessages(listOf(supply(brand = "Lipitor", inHandDays = 100, fills = 0, totalDays = 100)))
        assertEquals(listOf(AttentionKind.NO_SCRIPTS), messages.map { it.kind })
        assertTrue(messages.single().text.contains("Lipitor"))
    }

    @Test
    fun `low in hand while scripts remain nudges to get one dispensed`() {
        // Plenty of total supply (lots of scripts), but only 10 days in hand.
        assertEquals(
            listOf(AttentionKind.LOW_IN_HAND_HAS_SCRIPTS),
            kinds(supply(inHandDays = 10, fills = 5, totalDays = 300)),
        )
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
