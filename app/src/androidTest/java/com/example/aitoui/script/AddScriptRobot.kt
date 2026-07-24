package com.example.aitoui.script

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aitoui.R
import com.example.aitoui.data.Medication
import com.example.aitoui.ui.theme.AitouiTheme

class AddScriptRobot(
    private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun setContent(
        state: AddScriptState,
        onAction: (AddScriptAction) -> Unit = {},
        onBack: () -> Unit = {},
    ) = apply {
        composeTestRule.setContent {
            AitouiTheme {
                AddScriptScreen(
                    state = state,
                    onAction = onAction,
                    onBack = onBack,
                )
            }
        }
    }

    fun enterBrandName(value: String) = apply {
        composeTestRule.onNodeWithTag(ADD_SCRIPT_BRAND_NAME_TAG).performTextInput(value)
    }

    fun enterActiveIngredient(value: String) = apply {
        composeTestRule.onNodeWithTag(ADD_SCRIPT_ACTIVE_INGREDIENT_TAG).performTextInput(value)
    }

    fun clickSave() = apply {
        composeTestRule.onNodeWithTag(ADD_SCRIPT_SAVE_TAG).performClick()
    }

    fun assertSaveEnabled() = apply {
        composeTestRule.onNodeWithTag(ADD_SCRIPT_SAVE_TAG).assertIsEnabled()
    }

    fun assertSaveDisabled() = apply {
        composeTestRule.onNodeWithTag(ADD_SCRIPT_SAVE_TAG).assertIsNotEnabled()
    }

    fun assertDuplicateSerialDialogVisible() = apply {
        composeTestRule.onNodeWithTag(ADD_SCRIPT_DUPLICATE_SERIAL_DIALOG_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.add_script_duplicate_serial_message))
            .assertIsDisplayed()
    }

    fun dismissDuplicateSerialDialog() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.add_script_ok_button_label)).performClick()
    }

    fun assertMedicationResolutionVisible() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.add_script_med_resolution_title))
            .assertIsDisplayed()
    }

    fun selectMedication(medication: Medication) = apply {
        composeTestRule.onNodeWithText(medication.brandName).performClick()
    }

    fun clickContinue() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.add_script_continue_button_label))
            .performClick()
    }
}
