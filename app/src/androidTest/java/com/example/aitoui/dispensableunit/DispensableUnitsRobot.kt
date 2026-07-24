package com.example.aitoui.dispensableunit

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

class DispensableUnitsRobot(
    private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun setContent(
        state: DispensableUnitsState,
        onAction: (DispensableUnitsAction) -> Unit = {},
        onBack: () -> Unit = {},
        onAddDispensableUnit: () -> Unit = {},
    ) = apply {
        composeTestRule.setContent {
            AitouiTheme {
                DispensableUnitsScreen(
                    state = state,
                    onAction = onAction,
                    onBack = onBack,
                    onAddDispensableUnit = onAddDispensableUnit,
                )
            }
        }
    }

    fun assertTitleDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.dispensable_units_appbar_title))
            .assertIsDisplayed()
    }

    fun assertDescriptionDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.dispensable_units_description_text))
            .assertIsDisplayed()
    }

    fun assertUnitCardVisible(brandName: String) = apply {
        composeTestRule.onNodeWithText(brandName, substring = true).assertIsDisplayed()
    }

    fun clickDeleteIcon() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.dispensable_units_delete_icon_cd))
            .performClick()
    }

    fun clickAddFab() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.dispensable_units_add_button_cd))
            .performClick()
    }

    fun clickBack() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.dispensable_units_back_button_cd))
            .performClick()
    }

    // ── Delete dialog ────────────────────────────────────────────────────────

    fun assertDeleteDialogVisible() = apply {
        composeTestRule.onNodeWithTag(DISPENSABLE_UNITS_DELETE_DIALOG_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.dispensable_units_delete_dialog_title))
            .assertIsDisplayed()
    }

    fun clickConfirmDelete() = apply {
        composeTestRule
            .onNodeWithText(context.getString(R.string.dispensable_units_delete_button_label))
            .performClick()
    }

    fun clickCancelDelete() = apply {
        composeTestRule
            .onNodeWithText(context.getString(R.string.dispensable_units_cancel_button_label))
            .performClick()
    }
}

