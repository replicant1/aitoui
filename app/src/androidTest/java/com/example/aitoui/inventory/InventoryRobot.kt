package com.example.aitoui.inventory

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aitoui.R
import com.example.aitoui.ui.theme.AitouiTheme

class InventoryRobot(
    private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun setContent(
        state: InventoryState,
        onAction: (InventoryAction) -> Unit = {},
        onBack: () -> Unit = {},
        onRunOutGraph: () -> Unit = {},
    ) = apply {
        composeTestRule.setContent {
            AitouiTheme {
                InventoryScreen(
                    state = state,
                    onAction = onAction,
                    onBack = onBack,
                    onRunOutGraph = onRunOutGraph,
                )
            }
        }
    }

    fun assertTitleDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.inventory_appbar_title))
            .assertIsDisplayed()
    }

    fun assertDescriptionDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.inventory_description_text))
            .assertIsDisplayed()
    }

    fun assertItemVisible(brandName: String) = apply {
        composeTestRule.onNodeWithText(brandName, substring = true).assertIsDisplayed()
    }

    fun clickBack() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.inventory_back_button_cd))
            .performClick()
    }

    fun clickRunOutGraphButton() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.inventory_run_out_graph_button_cd))
            .performClick()
    }

    fun clickSortOrderButton() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.inventory_sort_order_button_cd))
            .performClick()
    }

    fun assertSortMenuVisible() = apply {
        // Each SortOption has a label rendered as a DropdownMenuItem text
        composeTestRule.onNodeWithText(SortOption.BrandName.label).assertIsDisplayed()
        composeTestRule.onNodeWithText(SortOption.TimeRemaining.label).assertIsDisplayed()
    }

    fun clickSortOption(option: SortOption) = apply {
        composeTestRule.onNodeWithText(option.label).performClick()
    }
}

