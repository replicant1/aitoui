package com.example.aitoui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { SettingsRobot(composeTestRule) }

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
        robot.setContent().assertTitleDisplayed()
    }

    @Test
    fun warningWindowDescription_isDisplayed() {
        robot.setContent().assertWarningWindowDescriptionDisplayed()
    }

    @Test
    fun warningWindowField_showsStateValue() {
        robot
            .setContent(state = SettingsState(warningWindowDays = "14"))
            .assertWarningWindowValue("14")
    }

    @Test
    fun clickingBack_invokesOnBack() {
        var called = false
        robot
            .setContent(onBack = { called = true })
            .clickBack()
        assertTrue(called)
    }

    @Test
    fun enteringValue_dispatchesWarningWindowChanged() {
        var capturedAction: SettingsAction? = null
        robot
            .setContent(
                state = SettingsState(warningWindowDays = "14"),
                onAction = { capturedAction = it },
            )
            .enterWarningWindowDays("21")
        assertEquals(SettingsAction.WarningWindowChanged("21"), capturedAction)
    }
}

