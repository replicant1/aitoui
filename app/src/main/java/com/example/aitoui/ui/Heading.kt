package com.example.aitoui.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * Marks this element as a heading so screen-reader users (e.g. TalkBack) can jump between screen and
 * section headings with heading navigation. Apply to top-app-bar titles and section headers — not to
 * dialog titles, which the dialog role already announces.
 */
fun Modifier.heading(): Modifier = semantics { heading() }
