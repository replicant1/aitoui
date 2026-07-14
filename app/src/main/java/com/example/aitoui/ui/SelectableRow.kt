package com.example.aitoui.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Makes a list row a single-choice selectable target: a >= 48dp-tall touch area that TalkBack
 * announces together with its selected state (via [selectable] + a [role]), replacing a bare
 * `clickable` whose selection was conveyed by background colour alone. Apply it where `.clickable`
 * was, i.e. before `.background()`, so the fill and the 48dp minimum cover the same area.
 */
fun Modifier.selectableRow(
    selected: Boolean,
    role: Role = Role.RadioButton,
    onClick: () -> Unit,
): Modifier = heightIn(min = 48.dp).selectable(selected = selected, role = role, onClick = onClick)
