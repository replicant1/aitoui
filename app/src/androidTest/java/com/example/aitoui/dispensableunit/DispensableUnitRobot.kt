package com.example.aitoui.dispensableunit

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aitoui.R
import com.example.aitoui.ui.theme.AitouiTheme

class DispensableUnitRobot(
    private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun setContent(
        state: DispensableUnitState,
        onAction: (DispensableUnitAction) -> Unit = {},
        onBack: () -> Unit = {},
    ) = apply {
        composeTestRule.setContent {
            AitouiTheme {
                DispensableUnitScreen(
                    state = state,
                    onAction = onAction,
                    onBack = onBack,
                )
            }
        }
    }

    fun assertTitleDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.dispensable_unit_appbar_title))
            .assertIsDisplayed()
    }

    fun assertDescriptionDisplayed() = apply {
        // Use substring because the description appends the required-fields note at runtime.
        composeTestRule.onNodeWithText("A Dispensable Unit is", substring = true)
            .assertIsDisplayed()
    }

    fun assertSaveButtonDisabled() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.dispensable_unit_save_button_label))
            .assertIsNotEnabled()
    }

    fun assertSaveButtonEnabled() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.dispensable_unit_save_button_label))
            .assertIsEnabled()
    }

    fun clickBack() = apply {
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.dispensable_unit_back_button_cd),
        ).performClick()
    }

    fun clickSave() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.dispensable_unit_save_button_label))
            .performClick()
    }

    fun enterDosePerTablet(value: String) = apply {
        composeTestRule.onNodeWithTag(DU_DOSE_FIELD_TAG).performTextInput(value)
    }

    fun enterTabletsPerUnit(value: String) = apply {
        composeTestRule.onNodeWithTag(DU_TABLETS_FIELD_TAG).performTextInput(value)
    }
}

