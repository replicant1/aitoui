package com.example.aitoui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aitoui.R
import com.example.aitoui.ui.theme.AitouiTheme

class SettingsRobot(
    private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun setContent(
        state: SettingsState = SettingsState(),
        onAction: (SettingsAction) -> Unit = {},
        onBack: () -> Unit = {},
    ) = apply {
        composeTestRule.setContent {
            AitouiTheme {
                SettingsScreen(
                    state = state,
                    onAction = onAction,
                    onBack = onBack,
                )
            }
        }
    }

    fun assertTitleDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.settings_appbar_title))
            .assertIsDisplayed()
    }

    fun assertWarningWindowDescriptionDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.settings_warning_window_description))
            .assertIsDisplayed()
    }

    fun assertWarningWindowValue(value: String) = apply {
        composeTestRule.onNodeWithTag(SETTINGS_WARNING_WINDOW_FIELD_TAG)
            .assertIsDisplayed()
        // The field value is rendered as Text inside the OutlinedTextField
        composeTestRule.onNodeWithText(value).assertIsDisplayed()
    }

    fun enterWarningWindowDays(value: String) = apply {
        composeTestRule.onNodeWithTag(SETTINGS_WARNING_WINDOW_FIELD_TAG)
            .performTextReplacement(value)
    }

    fun clickBack() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.settings_back_button_cd))
            .performClick()
    }
}

