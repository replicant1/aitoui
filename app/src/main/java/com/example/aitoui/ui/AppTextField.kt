package com.example.aitoui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aitoui.ui.theme.AitouiTheme

/** Whether a field must be filled in. Fields are [Required] by default; only the few exceptions are [Optional]. */
enum class FieldRequirement { Required, Optional }

/**
 * The one sentence every form states once (in its top text block) so the "required unless marked optional"
 * convention is discoverable. Append it to the screen's existing intro text rather than adding a new line.
 */
const val REQUIRED_FIELDS_NOTE = "Fields are required unless marked “optional”."

/**
 * The app-wide text input. A thin wrapper over [OutlinedTextField] that gives every form one consistent,
 * accessible way to show whether a field is mandatory.
 *
 * Because most fields in this app are required, we mark the *minority*: an [Optional] field gets
 * "(optional)" appended to its label, and everything else is required by default (announced once per form
 * via [REQUIRED_FIELDS_NOTE]). The marker lives in the visible label **text**, not an asterisk or colour, so
 * a screen reader reads it for free — Compose has no "required" semantic to expose, and colour/glyph-only
 * markers fail WCAG 1.4.1 / 3.3.2.
 *
 * Requirement is *communicated* here; it is *enforced* by the caller passing [errorText] (e.g. "Required")
 * once validation fails. A non-null [errorText] flips the field to its error style, shows the message as
 * supporting text, and sets the error semantics so TalkBack announces the invalid state.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    requirement: FieldRequirement = FieldRequirement.Required,
    errorText: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val shownLabel = when (requirement) {
        FieldRequirement.Optional -> "$label (optional)"
        FieldRequirement.Required -> label
    }
    // Error message wins the supporting-text slot; otherwise show the caller's hint (if any).
    val supporting: (@Composable () -> Unit)? = when {
        errorText != null -> ({ Text(errorText) })
        supportingText != null -> ({ Text(supportingText) })
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(shownLabel) },
        isError = errorText != null,
        supportingText = supporting,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        modifier = modifier
            .fillMaxWidth()
            // Give TalkBack the error message as a proper error state, not just red styling.
            .semantics { errorText?.let { error(it) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun AppTextFieldPreview() {
    AitouiTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                value = "Panadol",
                onValueChange = {},
                label = "Brand name",
            )
            AppTextField(
                value = "",
                onValueChange = {},
                label = "Instructions",
                requirement = FieldRequirement.Optional,
                singleLine = false,
            )
            AppTextField(
                value = "",
                onValueChange = {},
                label = "Active ingredient",
                errorText = "Required",
            )
        }
    }
}
