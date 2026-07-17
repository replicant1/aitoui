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

        assertEquals("PW2048021", r.serialNo)                    // PBS number in slot 1
        assertEquals("1TV4J832WYJHBHY3D8", r.serialNo2)          // eRx token in slot 2
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
    fun `puts the PBS number in slot 1 and the eRx token in slot 2, ignoring the reference number`() {
        val r = PbsScriptParser.parse(sample)
        // "eRx> 208861" is a short digits-only reference number — not a serial, so it is dropped.
        assertEquals("PW2048021", r.serialNo)
        assertEquals("1TV4J832WYJHBHY3D8", r.serialNo2)
    }

    @Test
    fun `leaves slot 1 empty and keeps the eRx token in slot 2 when there is no PBS number`() {
        val noPbsNumber = sample.filterNot { it.text.startsWith("PW2048021") }
        val r = PbsScriptParser.parse(noPbsNumber)
        assertNull(r.serialNo)
        assertEquals("1TV4J832WYJHBHY3D8", r.serialNo2)
    }

    /**
     * The exact ML Kit OCR of a real Dytrex PB038 (captured on-device). The item strength OCR'd as "6OMG"
     * (letter O for 0) and the eRx token didn't OCR at all — the two failures that left brand/active/dose/
     * tablets/serial blank before the fix. Kept verbatim as a regression fixture.
     */
    private val dytrexSample = listOf(
        OcrLine("ROSEVILLE", 751, 26, 1556, 192),
        OcrLine("PHARMACY", 732, 155, 1602, 325),
        OcrLine("PW2048022", 1452, 320, 2148, 420),
        OcrLine("-PBS/RPBS", 1098, 360, 1332, 426),
        OcrLine("Operc 2473-38751 -8 1 to", 683, 575, 1386, 662),
        OcrLine("06/2029", 980, 652, 1184, 700),               // Medicare expiry (mm/yyyy) — ignored
        OcrLine("2939926-MD", 1754, 581, 2087, 631),
        OcrLine("eRx>", 936, 774, 1110, 839),                  // label only — token didn't OCR
        OcrLine("MR RODNEY BAILEY", 836, 911, 1423, 963),
        OcrLine("208861", 1936, 920, 2118, 967),               // reference number — not the serial
        OcrLine("Original prescription transcription", 581, 1262, 1124, 1304),
        OcrLine("(item, strength, quantity, directions and deferred supply if applicable)", 582, 1300, 1689, 1348),
        OcrLine("DYTREX EC CAPSULES 6OMG (DULOXETINE) *Qty 56**", 605, 1443, 2197, 1515),
        OcrLine("Swallow whole TWO capsules in the morning as directed", 623, 1521, 2016, 1601),
        OcrLine("Dr Samantha Ting", 1358, 1757, 1764, 1803),
        OcrLine("2 Repeats Left", 606, 1765, 1001, 1825),
        OcrLine("Rpt No H5189", 2106, 1751, 2427, 1810),
        OcrLine("17/06/2026", 671, 2060, 966, 2117),           // date of issue
        OcrLine("No.p92IW", 1087, 2030, 1362, 2088),
        OcrLine("1", 1715, 2159, 1739, 2200),
        OcrLine("2", 1384, 2166, 1411, 2204),                  // repeats value, in the repeats column
        OcrLine("Valid to 17/06/2027", 786, 2458, 1343, 2522),
        OcrLine("20/062", 1624, 2756, 1841, 2818),             // partial date — must be ignored
        OcrLine("PBO38.2008", 2646, 3949, 2862, 4013),         // form code — not a PBS prescription number
    )

    @Test
    fun `parses a real Dytrex scan despite the 6OMG misread and missing eRx token`() {
        val r = PbsScriptParser.parse(dytrexSample)

        assertEquals("PW2048022", r.serialNo)                    // PBS number
        assertNull(r.serialNo2)                                  // eRx token didn't OCR on this form
        assertEquals("DYTREX", r.brand)
        assertEquals("DULOXETINE", r.activeIngredient)
        assertEquals("60", r.dosePerTablet)                    // "6O" repaired to "60"
        assertEquals("56", r.tabletsPerUnit)
        assertEquals(date("17/06/2026"), r.dateOfIssueMillis)
        assertEquals(date("17/06/2027"), r.validToMillis)
        assertEquals(2, r.repeats)
        assertEquals("Swallow whole TWO capsules in the morning as directed", r.instructions)
    }

    @Test
    fun `returns nulls for a blank scan`() {
        val r = PbsScriptParser.parse(emptyList())
        assertNull(r.serialNo)
        assertNull(r.dateOfIssueMillis)
        assertNull(r.repeats)
        assertNull(r.brand)
    }

    // --- eRx barcode/QR fallback (used when OCR yields no eRx token) ---

    @Test
    fun `erxFromBarcodes picks the eRx-shaped token from the CODE-128 and QR codes`() {
        // The PB038 carries the token in CODE-128 barcode #2 and the QR code (identical), plus a
        // ";"-separated Rpt-No/PBS-approval barcode that must be ignored.
        val barcodes = listOf("1TV4J832WYJHBHY3D8", "G5189;17325W", "1TV4J832WYJHBHY3D8")
        assertEquals("1TV4J832WYJHBHY3D8", PbsScriptParser.erxFromBarcodes(barcodes))
    }

    @Test
    fun `erxFromBarcodes excludes the semicolon-separated Rpt and PBS-approval barcode`() {
        assertNull(PbsScriptParser.erxFromBarcodes(listOf("G5189;17325W")))
    }

    @Test
    fun `erxFromBarcodes returns null when there are no barcodes`() {
        assertNull(PbsScriptParser.erxFromBarcodes(emptyList()))
    }

    @Test
    fun `erxFromBarcodes excludes short and plain-number barcodes`() {
        assertNull(PbsScriptParser.erxFromBarcodes(listOf("17325W", "208861", "123456789012")))
    }

    @Test
    fun `erxFromBarcodes prefers the longest shaped token`() {
        assertEquals(
            "1TV4J832WYJHBHY3D8",
            PbsScriptParser.erxFromBarcodes(listOf("AB12CD34", "1TV4J832WYJHBHY3D8")),
        )
    }
}
