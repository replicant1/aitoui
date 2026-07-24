package com.example.aitoui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aitoui.alerts.AttentionKind
import com.example.aitoui.alerts.AttentionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { MainRobot(composeTestRule) }

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
            .setContent()
            .assertTitleDisplayed()
    }

    @Test
    fun scriptsButton_isDisplayed() {
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.main_menu_scripts_label)
        robot.setContent().assertMenuButtonDisplayed(label)
    }

    @Test
    fun medicationsButton_isDisplayed() {
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.main_menu_medications_label)
        robot.setContent().assertMenuButtonDisplayed(label)
    }

    @Test
    fun dispensableUnitsButton_isDisplayed() {
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.main_menu_dispensable_units_label)
        robot.setContent().assertMenuButtonDisplayed(label)
    }

    @Test
    fun dailyScheduleButton_isDisplayed() {
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.main_menu_daily_schedule_label)
        robot.setContent().assertMenuButtonDisplayed(label)
    }

    @Test
    fun inHandButton_isDisplayed() {
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.main_menu_in_hand_label)
        robot.setContent().assertMenuButtonDisplayed(label)
    }

    @Test
    fun inventoryButton_isDisplayed() {
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.main_menu_inventory_label)
        robot.setContent().assertMenuButtonDisplayed(label)
    }

    @Test
    fun clickingSettings_invokesOnSettings() {
        var called = false
        robot
            .setContent(onSettings = { called = true })
            .clickSettings()
        assertTrue(called)
    }

    @Test
    fun clickingScripts_invokesOnScripts() {
        var called = false
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.main_menu_scripts_label)
        robot
            .setContent(onScripts = { called = true })
            .clickMenuButton(label)
        assertTrue(called)
    }

    @Test
    fun clickingMedications_invokesOnMedications() {
        var called = false
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.main_menu_medications_label)
        robot
            .setContent(onMedications = { called = true })
            .clickMenuButton(label)
        assertTrue(called)
    }

    @Test
    fun saveDialog_isShown_whenPendingSaveFileName() {
        robot
            .setContent(state = MainState(pendingSaveFileName = "pxtx-24072026-db27.zip"))
            .assertSaveDialogShown()
    }

    @Test
    fun saveDialog_confirmButton_isDisabled_whenFileNameIsBlank() {
        robot
            .setContent(state = MainState(pendingSaveFileName = ""))
            .assertSaveConfirmDisabled()
    }

    @Test
    fun saveDialog_confirmButton_isEnabled_whenFileNameIsValid() {
        robot
            .setContent(state = MainState(pendingSaveFileName = "pxtx-24072026-db27.zip"))
            .assertSaveConfirmEnabled()
    }

    @Test
    fun loadDialog_isShown_whenPendingLoadUri() {
        robot
            .setContent(state = MainState(pendingLoadUri = "content://some/path"))
            .assertLoadDialogShown()
    }

    @Test
    fun busyDialog_isShown_whenBusyState() {
        robot
            .setContent(state = MainState(busy = true))
            .assertBusyDialogShown()
    }

    @Test
    fun attentionMessage_isDisplayed_whenPresent() {
        val message = "You have no scripts for Panadol left.\nGo to doctor for new scripts."
        robot
            .setContent(
                state = MainState(
                    messages = listOf(
                        AttentionMessage(
                            kind = AttentionKind.NO_SCRIPTS_FOR_PRESCRIPTION_MEDICATION,
                            text = message,
                        ),
                    ),
                ),
            )
            .assertAttentionMessageDisplayed("You have no scripts for Panadol left.")
    }
}

