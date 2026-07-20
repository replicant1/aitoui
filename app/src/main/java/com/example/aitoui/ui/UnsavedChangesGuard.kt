package com.example.aitoui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Prompt shown when the user tries to leave an editor screen (back arrow or Android back) with unsaved
 * edits, so changes aren't silently lost.
 *
 * When the form is valid enough to persist ([canSave] = true) it offers three choices:
 *  - **Save** ([onSave]) — persist and leave (the screen's own save flow navigates away).
 *  - **Discard** ([onDiscard]) — leave without saving.
 *  - **Cancel** ([onCancel]) — stay on the screen (also triggered by tapping outside).
 *
 * When the edits are incomplete ([canSave] = false) saving isn't possible, so only **Discard** and **Cancel**
 * are offered.
 *
 * Pair this with a `BackHandler(enabled = hasUnsavedChanges)` and route the top-bar back arrow through the
 * same check, so both back affordances are guarded consistently.
 */
@Composable
fun UnsavedChangesDialog(
    canSave: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Unsaved changes") },
        text = {
            Text(
                if (canSave) "You've made changes that haven't been saved. Save them before leaving?"
                else "You've made changes that aren't complete enough to save. Discard them and leave?",
            )
        },
        confirmButton = {
            if (canSave) {
                TextButton(onClick = onSave) { Text("Save") }
            } else {
                TextButton(onClick = onDiscard) { Text("Discard") }
            }
        },
        dismissButton = {
            if (canSave) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    TextButton(onClick = onDiscard) { Text("Discard") }
                }
            } else {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}
