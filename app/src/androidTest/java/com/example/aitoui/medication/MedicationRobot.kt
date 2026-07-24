package com.example.aitoui.medication

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

class MedicationRobot(
    private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun setContent(
        state: MedicationState,
        onAction: (MedicationAction) -> Unit = {},
        onBack: () -> Unit = {},
    ) = apply {
        composeTestRule.setContent {
            AitouiTheme {
                MedicationScreen(
                    state = state,
                    onAction = onAction,
                    onBack = onBack,
                )
            }
        }
    }

    fun assertTitleDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.medication_appbar_title))
            .assertIsDisplayed()
    }

    fun assertDescriptionDisplayed() = apply {
        // Use substring because the description appends the required-fields note at runtime.
        composeTestRule.onNodeWithText("A Medication is a proprietary", substring = true)
            .assertIsDisplayed()
    }

    fun assertSaveButtonDisabled() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.medication_save_button_label))
            .assertIsNotEnabled()
    }

    fun assertSaveButtonEnabled() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.medication_save_button_label))
            .assertIsEnabled()
    }

    fun clickBack() = apply {
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.medication_back_button_cd),
        ).performClick()
    }

    fun clickSave() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.medication_save_button_label))
            .performClick()
    }

    fun enterBrandName(value: String) = apply {
        composeTestRule.onNodeWithTag(MED_BRAND_NAME_FIELD_TAG).performTextInput(value)
    }

    fun enterActiveIngredient(value: String) = apply {
        composeTestRule.onNodeWithTag(MED_ACTIVE_INGREDIENT_FIELD_TAG).performTextInput(value)
    }

    fun clickPrescriptionSwitch() = apply {
        composeTestRule.onNodeWithTag(MED_PRESCRIPTION_SWITCH_TAG).performClick()
    }
}

