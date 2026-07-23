package com.example.aitoui.data

/** A medication, identified only by its brand name and active ingredient. */
data class Medication(
    val id: Long = 0,
    val brandName: String,
    val activeIngredient: String,
    /** Whether the medication needs a prescription to obtain. */
    val requiresPrescription: Boolean = true,
)

private val medicationWhitespaceRegex = Regex("\\s+")
private val medicationWordRegex = Regex("\\b[\\p{L}\\p{N}]+\\b")
private val medicationUppercaseTokens = setOf("xr", "sr", "cr", "mr", "ir", "er", "xl", "la", "ec", "dr", "cd", "od", "pr")
private val medicationLowercaseTokens = setOf("mg", "mcg", "g", "kg", "ml", "l")
private val medicationMixedCaseTokens = mapOf("hcl" to "HCl", "hbr" to "HBr")
private val medicationRomanNumerals = setOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x")

/** Normalises medication-related display text to trimmed, single-spaced title case. */
fun String.cleanMedicationName(): String {
    val collapsed = trim().replace(medicationWhitespaceRegex, " ")
    if (collapsed.isEmpty()) return ""

    val lower = collapsed.lowercase()
    val result = StringBuilder(lower.length)
    var capitalizeNext = true

    for (char in lower) {
        when {
            capitalizeNext && char.isLetter() -> {
                result.append(char.titlecaseChar())
                capitalizeNext = false
            }

            else -> {
                result.append(char)
                capitalizeNext = !char.isLetterOrDigit()
            }
        }
    }
    return medicationWordRegex.replace(result.toString()) { match ->
        val token = match.value
        val lower = token.lowercase()
        when {
            lower in medicationUppercaseTokens -> lower.uppercase()
            lower in medicationLowercaseTokens -> lower
            lower in medicationMixedCaseTokens -> medicationMixedCaseTokens.getValue(lower)
            lower in medicationRomanNumerals -> lower.uppercase()
            else -> token
        }
    }
}

fun Medication.cleaned(): Medication = copy(
    brandName = brandName.cleanMedicationName(),
    activeIngredient = activeIngredient.cleanMedicationName(),
)

