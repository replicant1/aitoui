package com.example.aitoui.medication

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

class MedicationsRobot(
    private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun setContent(
        state: MedicationsState,
        onAction: (MedicationsAction) -> Unit = {},
        onBack: () -> Unit = {},
        onAddMedication: () -> Unit = {},
    ) = apply {
        composeTestRule.setContent {
            AitouiTheme {
                MedicationsScreen(
                    state = state,
                    onAction = onAction,
                    onBack = onBack,
                    onAddMedication = onAddMedication,
                )
            }
        }
    }

    fun assertTitleDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.medications_appbar_title))
            .assertIsDisplayed()
    }

    fun assertDescriptionDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.medications_description_text))
            .assertIsDisplayed()
    }

    fun assertMedicationCardVisible(brandName: String) = apply {
        composeTestRule.onNodeWithText(brandName, substring = true).assertIsDisplayed()
    }

    fun clickDeleteIcon(brandName: String) = apply {
        composeTestRule
            .onNodeWithContentDescription(
                context.getString(R.string.medications_delete_icon_cd, brandName),
            )
            .performClick()
    }

    fun clickAddFab() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.medications_add_button_cd))
            .performClick()
    }

    fun clickBack() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.medications_back_button_cd))
            .performClick()
    }

    // ── Delete dialog ────────────────────────────────────────────────────────

    fun assertDeleteDialogVisible() = apply {
        composeTestRule.onNodeWithTag(MEDICATIONS_DELETE_DIALOG_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.medications_delete_dialog_title))
            .assertIsDisplayed()
    }

    fun clickConfirmDelete() = apply {
        composeTestRule
            .onNodeWithText(context.getString(R.string.medications_delete_button_label))
            .performClick()
    }

    fun clickCancelDelete() = apply {
        composeTestRule
            .onNodeWithText(context.getString(R.string.main_cancel_button_label))
            .performClick()
    }
}

