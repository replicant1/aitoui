package com.example.aitoui.scan

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Extracts [ParsedScript] fields from the OCR of a PB038 (yellow PBS/RPBS repeat authorisation) form.
 *
 * The form is standardised, so extraction is **label-anchored**: find a printed label, read the value on
 * or near it, and validate by format. It's best-effort and layout-specific — the Add Script review step is
 * the safety net for anything it misses or misreads. Pure and JVM-testable (operates on [OcrLine]s).
 */
object PbsScriptParser {

    private val FULL_DATE = Regex("""\b(\d{2}/\d{2}/\d{4})\b""")
    private val VALID_TO = Regex("""(?i)valid\s*to\s*(\d{2}/\d{2}/\d{4})""")
    // The eRx token: printed as "eRx: <TOKEN>", an 8+ char run of uppercase letters and digits.
    private val ERX_TOKEN = Regex("""(?i)eRx\s*[:>]?\s*([0-9A-Z]{8,})""")
    // Item transcription, e.g. "TENSIG TABLETS 50MG BLISTER (ATENOLOL) **Qty 60**".
    private val ITEM = Regex("""(?i)^(.+?)\s+(\d+(?:\.\d+)?)\s*MG\b.*?\(([^)]+)\).*?Qty\s*\**\s*(\d+)""")
    private val DIRECTIONS = Regex("""(?i)^(take|apply|use|instil|inhale|inject|insert|dissolve|chew|spray|swallow)\b.*""")

    fun parse(lines: List<OcrLine>): ParsedScript {
        val validTo = firstMatch(lines, VALID_TO)?.let { parseDate(it) }
        val issue = dateOfIssue(lines, excluding = validTo?.let { millisToDate(it) })

        val (repeatsLabel, dispensedLabel) = repeatAndDispenseLabels(lines)
        val ints = lines.filter { it.text.trim().matches(Regex("""\d{1,3}""")) }

        val item = lines.firstNotNullOfOrNull { line -> ITEM.find(line.text)?.let { line to it } }
        val instructions = lines.map { it.text.trim() }.firstOrNull { DIRECTIONS.matches(it) }

        return ParsedScript(
            serialNo = erxToken(lines),
            dateOfIssueMillis = issue,
            validToMillis = validTo ?: issue?.let { plusTwelveMonths(it) },
            repeats = intNear(repeatsLabel, ints),
            priorDispensed = intNear(dispensedLabel, ints),
            brand = item?.second?.groupValues?.get(1)?.let { brandOf(it) },
            activeIngredient = item?.second?.groupValues?.get(3)?.trim(),
            dosePerTablet = item?.second?.groupValues?.get(2),
            tabletsPerUnit = item?.second?.groupValues?.get(4),
            instructions = instructions,
        )
    }

    private fun erxToken(lines: List<OcrLine>): String? =
        lines.mapNotNull { ERX_TOKEN.find(it.text)?.groupValues?.get(1) }
            // Keep tokens that mix letters and digits (excludes plain numbers like a reference "208861").
            .filter { it.any(Char::isDigit) && it.any(Char::isLetter) }
            .maxByOrNull { it.length }

    /** The one full dd/MM/yyyy date that is not the "valid to" date — the date of issue. */
    private fun dateOfIssue(lines: List<OcrLine>, excluding: String?): Long? {
        val dates = lines.flatMap { FULL_DATE.findAll(it.text).map { m -> m.groupValues[1] }.toList() }
            .distinct()
            .filter { it != excluding }
        return dates.mapNotNull { parseDate(it) }.minOrNull()
    }

    /** Finds the "repeats authorised" and "times already dispensed" label lines. */
    private fun repeatAndDispenseLabels(lines: List<OcrLine>): Pair<OcrLine?, OcrLine?> {
        val repeats = lines.firstOrNull { it.text.contains("repeats", ignoreCase = true) }
        val dispensed = lines.firstOrNull {
            val t = it.text.lowercase()
            "already dispensed" in t || "times already" in t
        }
        return repeats to dispensed
    }

    /** The integer value printed nearest below/beside [label] (its column), or null. */
    private fun intNear(label: OcrLine?, ints: List<OcrLine>): Int? {
        if (label == null) return null
        return ints
            .filter { it !== label && it.bottom >= label.top }   // at or below the label
            .minByOrNull { (it.top - label.top) + abs(it.centerX - label.centerX) }
            ?.text?.trim()?.toIntOrNull()
    }

    /** Brand is the leading word of the item name (drug names are single-word: TENSIG, LIPITOR…). */
    private fun brandOf(itemName: String): String? =
        itemName.trim().split(Regex("""\s+""")).firstOrNull()?.takeIf { it.isNotBlank() }

    private fun firstMatch(lines: List<OcrLine>, regex: Regex): String? =
        lines.firstNotNullOfOrNull { regex.find(it.text)?.groupValues?.get(1) }

    // --- date helpers: dd/MM/yyyy <-> UTC start-of-day millis (the app's stored-date convention) ---

    private fun formatter() = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }

    private fun parseDate(text: String): Long? = runCatching { formatter().parse(text)?.time }.getOrNull()

    private fun millisToDate(millis: Long): String = formatter().format(java.util.Date(millis))

    private fun plusTwelveMonths(millis: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = millis
        cal.add(Calendar.MONTH, 12)
        return cal.timeInMillis
    }
}
