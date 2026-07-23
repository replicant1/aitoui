package com.example.aitoui.ui

/** Keeps only digits for whole-number inputs. */
internal fun String.digitsOnly(): String = filter { it.isDigit() }

/**
 * Keeps digits plus a single decimal separator for dose-style inputs.
 *
 * Commas are normalised to dots so OCR/manual entry stays consistent with parsing and display.
 */
internal fun String.decimalInput(): String {
    val sanitized = StringBuilder(length)
    var hasDecimalSeparator = false

    replace(',', '.').forEach { char ->
        when {
            char.isDigit() -> sanitized.append(char)
            char == '.' && !hasDecimalSeparator -> {
                if (sanitized.isEmpty()) sanitized.append('0')
                sanitized.append('.')
                hasDecimalSeparator = true
            }
        }
    }

    return sanitized.toString()
}
