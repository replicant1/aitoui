package com.example.aitoui.script

import androidx.annotation.StringRes
import com.example.aitoui.R

/**
 * Which prompt the medication- and dispensable-unit-resolution dialogs show, given how many records the
 * entered details matched and whether the user is [blocked] from creating a new one.
 *
 * These live outside the composables so the wording rules can be unit-tested: the copy is
 * count-sensitive ("select *the* known medication" vs "select *a* known medication"), and reading it
 * off a screenshot is the only other way to tell the variants apart.
 */

/** Header above the list of already-known medications. */
@StringRes
fun medResolutionKnownPrompt(knownCount: Int): Int =
    if (knownCount == 1) {
        R.string.add_script_med_resolution_known_prompt_single
    } else {
        R.string.add_script_med_resolution_known_prompt_multiple
    }

/** The "what to do now" line under the medication list. */
@StringRes
fun medResolutionActionPrompt(knownCount: Int, blocked: Boolean): Int = when {
    knownCount == 0 -> R.string.add_script_med_resolution_action_no_known
    blocked && knownCount == 1 -> R.string.add_script_med_resolution_action_blocked_single
    blocked -> R.string.add_script_med_resolution_action_blocked_multiple
    knownCount == 1 -> R.string.add_script_med_resolution_action_choose_known_or_new_single
    else -> R.string.add_script_med_resolution_action_choose_known_or_new_multiple
}

/** Header above the list of dispensable units the medication already has. */
@StringRes
fun duResolutionExistingPrompt(existingCount: Int): Int =
    if (existingCount == 1) {
        R.string.add_script_du_resolution_existing_prompt_single
    } else {
        R.string.add_script_du_resolution_existing_prompt_multiple
    }

/** The "what to do now" line under the dispensable-unit list. */
@StringRes
fun duResolutionActionPrompt(existingCount: Int, blocked: Boolean): Int = when {
    existingCount == 0 -> R.string.add_script_du_resolution_action_no_existing
    blocked && existingCount == 1 -> R.string.add_script_du_resolution_action_blocked_single
    blocked -> R.string.add_script_du_resolution_action_blocked_multiple
    existingCount == 1 -> R.string.add_script_du_resolution_action_choose_existing_or_new_single
    else -> R.string.add_script_du_resolution_action_choose_existing_or_new_multiple
}
