package com.example.aitoui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aitoui.ui.theme.AitouiTheme

class MainRobot(
    private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun setContent(
        state: MainState = MainState(),
        onAction: (MainAction) -> Unit = {},
        onMedications: () -> Unit = {},
        onDispensableUnits: () -> Unit = {},
        onDailySchedule: () -> Unit = {},
        onInHand: () -> Unit = {},
        onInventory: () -> Unit = {},
        onScripts: () -> Unit = {},
        onSettings: () -> Unit = {},
        onLog: () -> Unit = {},
    ) = apply {
        composeTestRule.setContent {
            AitouiTheme {
                MainScreen(
                    state = state,
                    onAction = onAction,
                    onMedications = onMedications,
                    onDispensableUnits = onDispensableUnits,
                    onDailySchedule = onDailySchedule,
                    onInHand = onInHand,
                    onInventory = onInventory,
                    onScripts = onScripts,
                    onSettings = onSettings,
                    onLog = onLog,
                )
            }
        }
    }

    fun assertTitleDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.main_appbar_title))
            .assertIsDisplayed()
    }

    fun assertMenuButtonDisplayed(label: String) = apply {
        composeTestRule.onNodeWithText(label).assertIsDisplayed()
    }

    fun clickSettings() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.main_settings_button_cd))
            .performClick()
    }

    fun clickMenuButton(label: String) = apply {
        composeTestRule.onNodeWithText(label).performClick()
    }

    fun assertSaveDialogShown() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.main_save_backup_title))
            .assertIsDisplayed()
    }

    fun assertLoadDialogShown() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.main_load_backup_title))
            .assertIsDisplayed()
    }

    fun assertBusyDialogShown() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.main_working_title))
            .assertIsDisplayed()
    }

    fun assertAttentionMessageDisplayed(text: String) = apply {
        composeTestRule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    fun enterSaveFileName(name: String) = apply {
        composeTestRule.onNodeWithTag(MAIN_SAVE_FILENAME_FIELD_TAG)
            .performTextReplacement(name)
    }

    fun assertSaveConfirmEnabled() = apply {
        composeTestRule.onNodeWithTag(MAIN_SAVE_CONFIRM_BUTTON_TAG)
            .assertIsEnabled()
    }

    fun assertSaveConfirmDisabled() = apply {
        composeTestRule.onNodeWithTag(MAIN_SAVE_CONFIRM_BUTTON_TAG)
            .assertIsNotEnabled()
    }
}

