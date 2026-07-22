package com.example.aitoui.script

import com.example.aitoui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The resolution dialogs' copy is count-sensitive: with one match it points at *the* known medication,
 * with several at *a* known medication. A single string covering both reads wrong in one of them, so
 * these tests pin each combination to its own resource.
 */
class ResolutionPromptsTest {

    // --- Medication dialog ---

    @Test
    fun `no known medications asks the user to take the new one`() {
        assertEquals(
            R.string.add_script_med_resolution_action_no_known,
            medResolutionActionPrompt(knownCount = 0, blocked = false),
        )
    }

    @Test
    fun `one known medication and a free choice points at the one above`() {
        assertEquals(
            R.string.add_script_med_resolution_action_choose_known_or_new_single,
            medResolutionActionPrompt(knownCount = 1, blocked = false),
        )
    }

    @Test
    fun `several known medications and a free choice points at any one above`() {
        assertEquals(
            R.string.add_script_med_resolution_action_choose_known_or_new_multiple,
            medResolutionActionPrompt(knownCount = 2, blocked = false),
        )
    }

    /** The regression: both free-choice arms once collapsed onto the singular wording. */
    @Test
    fun `the one-match and many-match free choices use different wording`() {
        assertNotEquals(
            medResolutionActionPrompt(knownCount = 1, blocked = false),
            medResolutionActionPrompt(knownCount = 2, blocked = false),
        )
    }

    @Test
    fun `blocked distinguishes one known medication from several`() {
        assertEquals(
            R.string.add_script_med_resolution_action_blocked_single,
            medResolutionActionPrompt(knownCount = 1, blocked = true),
        )
        assertEquals(
            R.string.add_script_med_resolution_action_blocked_multiple,
            medResolutionActionPrompt(knownCount = 3, blocked = true),
        )
    }

    @Test
    fun `blocked with no known medications still offers the new one`() {
        assertEquals(
            R.string.add_script_med_resolution_action_no_known,
            medResolutionActionPrompt(knownCount = 0, blocked = true),
        )
    }

    @Test
    fun `the known-medication header follows the count`() {
        assertEquals(
            R.string.add_script_med_resolution_known_prompt_single,
            medResolutionKnownPrompt(knownCount = 1),
        )
        assertEquals(
            R.string.add_script_med_resolution_known_prompt_multiple,
            medResolutionKnownPrompt(knownCount = 2),
        )
    }

    // --- Dispensable unit dialog ---

    @Test
    fun `no existing dispensable units asks the user to take the new one`() {
        assertEquals(
            R.string.add_script_du_resolution_action_no_existing,
            duResolutionActionPrompt(existingCount = 0, blocked = false),
        )
    }

    @Test
    fun `one existing dispensable unit and a free choice points at the one above`() {
        assertEquals(
            R.string.add_script_du_resolution_action_choose_existing_or_new_single,
            duResolutionActionPrompt(existingCount = 1, blocked = false),
        )
    }

    @Test
    fun `several existing dispensable units and a free choice points at any one above`() {
        assertEquals(
            R.string.add_script_du_resolution_action_choose_existing_or_new_multiple,
            duResolutionActionPrompt(existingCount = 2, blocked = false),
        )
    }

    @Test
    fun `the one-match and many-match dispensable unit free choices use different wording`() {
        assertNotEquals(
            duResolutionActionPrompt(existingCount = 1, blocked = false),
            duResolutionActionPrompt(existingCount = 2, blocked = false),
        )
    }

    @Test
    fun `blocked distinguishes one existing dispensable unit from several`() {
        assertEquals(
            R.string.add_script_du_resolution_action_blocked_single,
            duResolutionActionPrompt(existingCount = 1, blocked = true),
        )
        assertEquals(
            R.string.add_script_du_resolution_action_blocked_multiple,
            duResolutionActionPrompt(existingCount = 3, blocked = true),
        )
    }

    @Test
    fun `the existing dispensable unit header follows the count`() {
        assertEquals(
            R.string.add_script_du_resolution_existing_prompt_single,
            duResolutionExistingPrompt(existingCount = 1),
        )
        assertEquals(
            R.string.add_script_du_resolution_existing_prompt_multiple,
            duResolutionExistingPrompt(existingCount = 2),
        )
    }
}
