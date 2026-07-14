package com.example.aitoui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PbsScriptParserTest {

    private fun date(ddMMyyyy: String): Long =
        SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(ddMMyyyy)!!.time

    /** OCR lines mirroring the real PB038 sample (Tensig 50mg, Qty 60, 5 repeats, 0 dispensed). */
    private val sample = listOf(
        OcrLine("PW2048021", 620, 30, 1060, 90),
        OcrLine("Repeat authorisation", 120, 100, 560, 140),
        OcrLine("M/C 2473-38751-8 1 to", 230, 190, 640, 225),
        OcrLine("06/2029", 250, 225, 470, 260),           // Medicare expiry (mm/yyyy) — must be ignored
        OcrLine("Prescriber no. 2939926-MD", 660, 190, 1050, 225),
        OcrLine("eRx> 208861", 330, 300, 1050, 360),       // reference number — not the token
        OcrLine("MR RODNEY BAILEY", 280, 400, 760, 440),
        OcrLine("TENSIG TABLETS 50MG BLISTER (ATENOLOL) **Qty 60**", 150, 720, 1120, 770),
        OcrLine("Take ONE tablet TWICE a day as directed", 150, 780, 780, 820),
        OcrLine("Dr Samantha Ting", 590, 920, 860, 960),
        OcrLine("Date", 210, 1000, 300, 1035),
        OcrLine("PBS approval no.", 430, 1000, 640, 1035),
        OcrLine("17/06/2026", 200, 1080, 400, 1120),       // date of issue
        OcrLine("17325W", 470, 1080, 600, 1120),
        OcrLine("No. of repeats", 430, 1090, 640, 1120),
        OcrLine("authorised", 430, 1120, 600, 1150),
        OcrLine("No. of times already dispensed (including original supply)", 700, 980, 1120, 1090),
        OcrLine("5", 520, 1120, 560, 1160),                // repeats value
        OcrLine("0", 800, 1120, 840, 1160),                // times-already-dispensed value
        OcrLine("eRx: 1TV4J832WYJHBHY3D8", 240, 1230, 620, 1270),
        OcrLine("Valid to 17/06/2027", 230, 1400, 590, 1440),
        OcrLine("ROSEVILLE PHARMACY", 700, 1330, 1150, 1370),
        OcrLine("20/06/26 SK 17325W", 800, 1560, 1180, 1600), // 2-digit year — must be ignored as a date
    )

    @Test
    fun `parses the PB038 sample`() {
        val r = PbsScriptParser.parse(sample)

        assertEquals("1TV4J832WYJHBHY3D8", r.serialNo)
        assertEquals(date("17/06/2026"), r.dateOfIssueMillis)
        assertEquals(date("17/06/2027"), r.validToMillis)
        assertEquals(5, r.repeats)
        assertEquals(0, r.priorDispensed)
        assertEquals("TENSIG", r.brand)
        assertEquals("ATENOLOL", r.activeIngredient)
        assertEquals("50", r.dosePerTablet)
        assertEquals("60", r.tabletsPerUnit)
        assertEquals("Take ONE tablet TWICE a day as directed", r.instructions)
    }

    @Test
    fun `derives valid-to as issue plus twelve months when not printed`() {
        val withoutValidTo = sample.filterNot { it.text.startsWith("Valid to") }
        val r = PbsScriptParser.parse(withoutValidTo)
        assertEquals(date("17/06/2026"), r.dateOfIssueMillis)
        assertEquals(date("17/06/2027"), r.validToMillis)   // 17/06/2026 + 12 months
    }

    @Test
    fun `ignores the reference number and keeps the mixed alphanumeric eRx token`() {
        assertEquals("1TV4J832WYJHBHY3D8", PbsScriptParser.parse(sample).serialNo)
    }

    @Test
    fun `returns nulls for a blank scan`() {
        val r = PbsScriptParser.parse(emptyList())
        assertNull(r.serialNo)
        assertNull(r.dateOfIssueMillis)
        assertNull(r.repeats)
        assertNull(r.brand)
    }
}
