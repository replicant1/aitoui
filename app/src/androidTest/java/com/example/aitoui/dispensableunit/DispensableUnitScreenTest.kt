package com.example.aitoui.dispensableunit

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aitoui.data.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DispensableUnitScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { DispensableUnitRobot(composeTestRule) }

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
            .setContent(state = DispensableUnitState())
            .assertTitleDisplayed()
    }

    @Test
    fun description_isDisplayed() {
        robot
            .setContent(state = DispensableUnitState())
            .assertDescriptionDisplayed()
    }

    @Test
    fun saveButton_isDisabled_whenStateCannotSave() {
        robot
            .setContent(state = DispensableUnitState()) // no medication, no dose, no tablets
            .assertSaveButtonDisabled()
    }

    @Test
    fun saveButton_isEnabled_whenStateCanSave() {
        robot
            .setContent(
                state = DispensableUnitState(
                    medications = listOf(Medication(1L, "Panadol", "Paracetamol")),
                    selectedMedicationId = 1L,
                    dosePerTablet = "500",
                    tabletsPerUnit = "24",
                ),
            )
            .assertSaveButtonEnabled()
    }

    @Test
    fun clickingBack_invokesOnBack() {
        var backCalled = false
        robot
            .setContent(state = DispensableUnitState(), onBack = { backCalled = true })
            .clickBack()
        assertTrue(backCalled)
    }

    @Test
    fun clickingSave_dispatchesSaveAction_whenCanSave() {
        var capturedAction: DispensableUnitAction? = null
        robot
            .setContent(
                state = DispensableUnitState(
                    medications = listOf(Medication(1L, "Panadol", "Paracetamol")),
                    selectedMedicationId = 1L,
                    dosePerTablet = "500",
                    tabletsPerUnit = "24",
                ),
                onAction = { capturedAction = it },
            )
            .clickSave()
        assertEquals(DispensableUnitAction.Save, capturedAction)
    }

    @Test
    fun enteringDosePerTablet_dispatchesDosePerTabletChanged() {
        val actions = mutableListOf<DispensableUnitAction>()
        robot
            .setContent(state = DispensableUnitState(), onAction = actions::add)
            .enterDosePerTablet("500")
        assertTrue(actions.any { it is DispensableUnitAction.DosePerTabletChanged })
    }

    @Test
    fun enteringTabletsPerUnit_dispatchesTabletsPerUnitChanged() {
        val actions = mutableListOf<DispensableUnitAction>()
        robot
            .setContent(state = DispensableUnitState(), onAction = actions::add)
            .enterTabletsPerUnit("30")
        assertTrue(actions.any { it is DispensableUnitAction.TabletsPerUnitChanged })
    }
}

