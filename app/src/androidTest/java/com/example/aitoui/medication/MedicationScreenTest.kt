package com.example.aitoui.medication

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicationScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { MedicationRobot(composeTestRule) }

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
            .setContent(state = MedicationState())
            .assertTitleDisplayed()
    }

    @Test
    fun description_isDisplayed() {
        robot
            .setContent(state = MedicationState())
            .assertDescriptionDisplayed()
    }

    @Test
    fun saveButton_isDisabled_whenFormIsEmpty() {
        robot
            .setContent(state = MedicationState())
            .assertSaveButtonDisabled()
    }

    @Test
    fun saveButton_isEnabled_whenBothFieldsFilled() {
        robot
            .setContent(
                state = MedicationState(
                    brandName = "Panadol",
                    activeIngredient = "Paracetamol",
                ),
            )
            .assertSaveButtonEnabled()
    }

    @Test
    fun clickingBack_invokesOnBack() {
        var backCalled = false
        robot
            .setContent(state = MedicationState(), onBack = { backCalled = true })
            .clickBack()
        assertTrue(backCalled)
    }

    @Test
    fun clickingSave_dispatchesSaveAction_whenCanSave() {
        var capturedAction: MedicationAction? = null
        robot
            .setContent(
                state = MedicationState(
                    brandName = "Panadol",
                    activeIngredient = "Paracetamol",
                ),
                onAction = { capturedAction = it },
            )
            .clickSave()
        assertEquals(MedicationAction.Save, capturedAction)
    }

    @Test
    fun enteringBrandName_dispatchesBrandNameChanged() {
        val actions = mutableListOf<MedicationAction>()
        robot
            .setContent(state = MedicationState(), onAction = actions::add)
            .enterBrandName("Panadol")
        assertTrue(actions.any { it is MedicationAction.BrandNameChanged })
    }

    @Test
    fun enteringActiveIngredient_dispatchesActiveIngredientChanged() {
        val actions = mutableListOf<MedicationAction>()
        robot
            .setContent(state = MedicationState(), onAction = actions::add)
            .enterActiveIngredient("Paracetamol")
        assertTrue(actions.any { it is MedicationAction.ActiveIngredientChanged })
    }

    @Test
    fun clickingPrescriptionSwitch_dispatchesRequiresPrescriptionChanged() {
        var capturedAction: MedicationAction? = null
        robot
            .setContent(
                state = MedicationState(requiresPrescription = true),
                onAction = { capturedAction = it },
            )
            .clickPrescriptionSwitch()
        assertEquals(MedicationAction.RequiresPrescriptionChanged(false), capturedAction)
    }
}

