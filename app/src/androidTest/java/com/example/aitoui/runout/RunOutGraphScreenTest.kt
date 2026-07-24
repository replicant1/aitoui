package com.example.aitoui.runout

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunOutGraphScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { RunOutGraphRobot(composeTestRule) }

    /**
     * Wake the emulator/device before each test. When the screen is in Doze mode, the
     * Choreographer stops dispatching frame callbacks; ComposeView.requestLayout() queues
     * a frame that never fires, so the TestOwner never registers and every assertion
     * throws "No compose hierarchies found in the app."
     */
    @Before
    fun wakeScreen() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("wm dismiss-keyguard").close()
    }

    @Test
    fun appbarTitle_isDisplayed() {
        robot
            .setContent(data = RunOutGraphData())
            .assertTitleDisplayed()
    }

    @Test
    fun description_isDisplayed() {
        robot
            .setContent(data = RunOutGraphData())
            .assertDescriptionDisplayed()
    }

    @Test
    fun emptyState_isDisplayed_whenDataIsEmpty() {
        robot
            .setContent(data = RunOutGraphData()) // isEmpty = true (no series)
            .assertEmptyStateDisplayed()
    }

    @Test
    fun legendHeader_isDisplayed_whenDataIsPresent() {
        robot
            .setContent(data = sampleData())
            .assertLegendHeaderDisplayed()
    }

    @Test
    fun clickingBack_invokesOnBack() {
        var backCalled = false
        robot
            .setContent(data = RunOutGraphData(), onBack = { backCalled = true })
            .clickBack()
        assertTrue(backCalled)
    }

    private fun sampleData() = RunOutGraphData(
        series = listOf(
            RunOutSeries(
                unitId = 1L,
                label = "Panadol (500mg)",
                totalTablets = 10,
                dailyRate = 2.0,
                colorIndex = 0,
            ),
        ),
        domainDays = 30.0,
        domainTablets = 10,
        tabletTickStep = 10,
        monthTicks = emptyList(),
    )
}

