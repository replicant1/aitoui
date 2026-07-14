package com.example.aitoui.scan

/**
 * One recognised line of text with its bounding box (device pixels). A deliberately plain type — no ML
 * Kit or Android types — so [PbsScriptParser] can be unit-tested on the JVM. The ViewModel maps ML Kit's
 * `Text.Line` (text + `boundingBox`) onto this.
 */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

/**
 * The fields pulled off a PB038 form. Everything is nullable — the parser fills what it can find, and the
 * user reviews/corrects the rest on the Add Script screen. [dosePerTablet]/[tabletsPerUnit] match the
 * dispensable-unit model; [priorDispensed] is "No. of times already dispensed".
 */
data class ParsedScript(
    val serialNo: String? = null,
    /** A second serial number (e.g. the eRx token when the PBS number is in [serialNo]). */
    val serialNo2: String? = null,
    val dateOfIssueMillis: Long? = null,
    val validToMillis: Long? = null,
    val repeats: Int? = null,
    val priorDispensed: Int? = null,
    val brand: String? = null,
    val activeIngredient: String? = null,
    val dosePerTablet: String? = null,
    val tabletsPerUnit: String? = null,
    val instructions: String? = null,
)
