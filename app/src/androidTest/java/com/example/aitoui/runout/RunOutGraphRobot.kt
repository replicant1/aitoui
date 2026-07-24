package com.example.aitoui.runout

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

class RunOutGraphRobot(
    private val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    fun setContent(
        data: RunOutGraphData,
        onBack: () -> Unit = {},
    ) = apply {
        composeTestRule.setContent {
            AitouiTheme {
                RunOutGraphScreen(data = data, onBack = onBack)
            }
        }
    }

    fun assertTitleDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.run_out_graph_appbar_title))
            .assertIsDisplayed()
    }

    fun assertDescriptionDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.run_out_graph_description_text))
            .assertIsDisplayed()
    }

    fun assertEmptyStateDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.run_out_graph_empty_state_text))
            .assertIsDisplayed()
    }

    fun assertLegendHeaderDisplayed() = apply {
        composeTestRule.onNodeWithText(context.getString(R.string.run_out_graph_legend_header))
            .assertIsDisplayed()
    }

    fun clickBack() = apply {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.run_out_graph_back_button_cd))
            .performClick()
    }
}

