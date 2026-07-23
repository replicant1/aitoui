package com.example.aitoui.data

import androidx.annotation.StringRes
import com.example.aitoui.R

/**
 * Dose unit of measurement. All strings (abbreviations and display names)
 * are externalized to strings.xml.
 */
enum class DoseUnit(
    /** Abbreviation stored in the database and used in non-Compose formatting/comparisons. */
    val storedAbbreviation: String,
    /** String resource ID for abbreviation (e.g. "mg", "g", "IU"). */
    @StringRes val abbreviationResId: Int,
    /** String resource ID for localized display name. */
    @StringRes val displayNameResId: Int,
) {
    MILLIGRAMS("mg", R.string.dose_unit_milligrams_abbreviation, R.string.dose_unit_milligrams_display_name),
    GRAMS("g", R.string.dose_unit_grams_abbreviation, R.string.dose_unit_grams_display_name),
    INTERNATIONAL_UNITS(
        "IU",
        R.string.dose_unit_international_units_abbreviation,
        R.string.dose_unit_international_units_display_name,
    ),
    MILLILITRES("mL", R.string.dose_unit_millilitres_abbreviation, R.string.dose_unit_millilitres_display_name),
    MICROGRAMS("μg", R.string.dose_unit_micrograms_abbreviation, R.string.dose_unit_micrograms_display_name),
}

/** Look up DoseUnit by abbreviation (stored in database). */
fun doseUnitFromAbbreviation(abbr: String): DoseUnit? =
    DoseUnit.entries.firstOrNull { it.storedAbbreviation == abbr }

