package com.example.aitoui.dailyschedule

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

class DailyScheduleRobot(
    private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun setContent(
        state: DailyScheduleState,
        onAction: (DailyScheduleAction) -> Unit = {},
        onBack: () -> Unit = {},
    ) = apply {
        composeTestRule.setContent {
            AitouiTheme {
                DailyScheduleScreen(
                    state = state,
                    onAction = onAction,
                    onBack = onBack,
                )
            }
        }
    }

    fun assertTitleDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.daily_schedule_appbar_title))
            .assertIsDisplayed()
    }

    fun assertDescriptionDisplayed() = apply {
        // Use substring because the description appends the required-fields note at runtime.
        composeTestRule.onNodeWithText("This is the number and type", substring = true)
            .assertIsDisplayed()
    }

    fun clickBack() = apply {
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.daily_schedule_back_button_cd),
        ).performClick()
    }

    fun clickSave() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.daily_schedule_save_button_label))
            .performClick()
    }

    fun enterNumberOfTablets(value: String) = apply {
        composeTestRule.onNodeWithTag(DS_TABLETS_FIELD_TAG).performTextInput(value)
    }

    fun assertAddButtonDisabled() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.daily_schedule_add_button_label))
            .assertIsNotEnabled()
    }

    fun assertAddButtonEnabled() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.daily_schedule_add_button_label))
            .assertIsEnabled()
    }

    fun clickAdd() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.daily_schedule_add_button_label))
            .performClick()
    }

    fun assertDeleteButtonDisabled() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.daily_schedule_delete_button_label))
            .assertIsNotEnabled()
    }

    fun assertDeleteButtonEnabled() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.daily_schedule_delete_button_label))
            .assertIsEnabled()
    }

    fun clickDelete() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.daily_schedule_delete_button_label))
            .performClick()
    }

    fun clickRow(brandName: String) = apply {
        composeTestRule.onNodeWithText(brandName, substring = true).performClick()
    }
}

