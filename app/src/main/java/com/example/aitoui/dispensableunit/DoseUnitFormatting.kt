package com.example.aitoui.dispensableunit

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.aitoui.data.DoseUnit

/** Get abbreviation string for a DoseUnit (requires Compose context) */
@Composable
fun DoseUnit.abbreviation(): String = stringResource(abbreviationResId)

/** Get display name for a DoseUnit (requires Compose context) */
@Composable
fun DoseUnit.displayName(): String = stringResource(displayNameResId)

/** Format a dose value + unit as display string (e.g. "500 mg", "0.025 mg", "1000 IU") */
@Composable
fun formatDose(value: String, unit: DoseUnit): String {
    val numValue = value.toDoubleOrNull() ?: 0.0
    val stripped = if (numValue == numValue.toLong().toDouble())
        numValue.toLong().toString()
    else value
    val abbr = unit.abbreviation()
    return "$stripped $abbr"
}

