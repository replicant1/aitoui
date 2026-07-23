package com.example.aitoui.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NumericInputSanitizerTest {

    @Test
    fun `decimal input keeps a single decimal point`() {
        assertEquals("12.5", "12.5".decimalInput())
        assertEquals("12.5", "12..5".decimalInput())
    }

    @Test
    fun `decimal input normalises commas and leading separators`() {
        assertEquals("0.5", ",5".decimalInput())
        assertEquals("0.5", ".5".decimalInput())
        assertEquals("25.75", "25,75".decimalInput())
    }

    @Test
    fun `decimal input strips non numeric characters`() {
        assertEquals("500.25", "5a0b0.2-5mg".decimalInput())
    }

    @Test
    fun `digits only strips all non digits`() {
        assertEquals("1234", "12a.3,4".digitsOnly())
    }
}
