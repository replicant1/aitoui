package com.example.aitoui.script

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aitoui.R
import com.example.aitoui.ui.theme.AitouiTheme

class ScriptsRobot(
    private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun setContent(
        state: ScriptsState,
        onAction: (ScriptsAction) -> Unit = {},
        onBack: () -> Unit = {},
        onAddScript: () -> Unit = {},
    ) = apply {
        composeTestRule.setContent {
            AitouiTheme {
                ScriptsScreen(
                    state = state,
                    onAction = onAction,
                    onBack = onBack,
                    onAddScript = onAddScript,
                )
            }
        }
    }

    fun assertTitleDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.scripts_appbar_title))
            .assertIsDisplayed()
    }

    fun assertDescriptionDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.scripts_description_text))
            .assertIsDisplayed()
    }

    fun assertScriptCardVisible(brandName: String) = apply {
        composeTestRule.onNodeWithText(brandName, substring = true).assertIsDisplayed()
    }

    fun clickDispenseIcon() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.scripts_dispense_icon_cd))
            .performClick()
    }

    fun clickDeleteIcon(brandName: String) = apply {
        composeTestRule
            .onNodeWithContentDescription(
                context.getString(R.string.scripts_delete_icon_cd, brandName),
            )
            .performClick()
    }

    fun clickAddScriptFab() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.scripts_add_button_cd))
            .performClick()
    }

    fun clickBack() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.scripts_back_button_cd))
            .performClick()
    }

    fun clickSortButton() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.scripts_sort_order_button_cd))
            .performClick()
    }

    fun clickSortOption(label: String) = apply {
        composeTestRule.onNodeWithText(label).performClick()
    }

    // ── Dispense dialog ──────────────────────────────────────────────────────

    fun assertDispenseDialogVisible() = apply {
        composeTestRule.onNodeWithTag(SCRIPTS_DISPENSE_DIALOG_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.scripts_dispense_dialog_title))
            .assertIsDisplayed()
    }

    fun clickConfirmDispense() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.scripts_dispense_button_label))
            .performClick()
    }

    fun clickCancelDispense() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.main_cancel_button_label))
            .performClick()
    }

    // ── Cannot-dispense (maxed out) dialog ───────────────────────────────────

    fun assertMaxedOutDialogVisible() = apply {
        composeTestRule.onNodeWithTag(SCRIPTS_MAXED_OUT_DIALOG_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.scripts_cannot_dispense_title))
            .assertIsDisplayed()
    }

    fun clickDismissMaxedOut() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.main_ok_label)).performClick()
    }

    // ── Delete dialog ────────────────────────────────────────────────────────

    fun assertDeleteDialogVisible() = apply {
        composeTestRule.onNodeWithTag(SCRIPTS_DELETE_DIALOG_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.scripts_delete_dialog_title))
            .assertIsDisplayed()
    }

    fun clickConfirmDelete() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.scripts_delete_button_label))
            .performClick()
    }

    fun clickCancelDelete() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.main_cancel_button_label))
            .performClick()
    }
}

