package com.example.aitoui.data

import com.example.aitoui.R

/**
 * Dose unit of measurement. All strings (abbreviations and display names)
 * are externalized to strings.xml.
 */
enum class DoseUnit {
    MILLIGRAMS, GRAMS, INTERNATIONAL_UNITS, MILLILITRES, MICROGRAMS
    ;

    /** Abbreviation stored in the database and used in non-Compose formatting/comparisons. */
    fun storedAbbreviation(): String = when (this) {
        MILLIGRAMS -> "mg"
        GRAMS -> "g"
        INTERNATIONAL_UNITS -> "IU"
        MILLILITRES -> "mL"
        MICROGRAMS -> "μg"
    }

    /** String resource ID for abbreviation (e.g. "mg", "g", "IU") */
    fun abbreviationResId(): Int = when (this) {
        MILLIGRAMS -> R.string.dose_unit_milligrams_abbreviation
        GRAMS -> R.string.dose_unit_grams_abbreviation
        INTERNATIONAL_UNITS -> R.string.dose_unit_international_units_abbreviation
        MILLILITRES -> R.string.dose_unit_millilitres_abbreviation
        MICROGRAMS -> R.string.dose_unit_micrograms_abbreviation
    }

    /** String resource ID for localized display name */
    fun displayNameResId(): Int = when (this) {
        MILLIGRAMS -> R.string.dose_unit_milligrams_display_name
        GRAMS -> R.string.dose_unit_grams_display_name
        INTERNATIONAL_UNITS -> R.string.dose_unit_international_units_display_name
        MILLILITRES -> R.string.dose_unit_millilitres_display_name
        MICROGRAMS -> R.string.dose_unit_micrograms_display_name
    }
}

/** Look up DoseUnit by abbreviation (stored in database). */
fun doseUnitFromAbbreviation(abbr: String): DoseUnit? = when (abbr) {
    DoseUnit.MILLIGRAMS.storedAbbreviation() -> DoseUnit.MILLIGRAMS
    DoseUnit.GRAMS.storedAbbreviation() -> DoseUnit.GRAMS
    DoseUnit.INTERNATIONAL_UNITS.storedAbbreviation() -> DoseUnit.INTERNATIONAL_UNITS
    DoseUnit.MILLILITRES.storedAbbreviation() -> DoseUnit.MILLILITRES
    DoseUnit.MICROGRAMS.storedAbbreviation() -> DoseUnit.MICROGRAMS
    else -> null
}

