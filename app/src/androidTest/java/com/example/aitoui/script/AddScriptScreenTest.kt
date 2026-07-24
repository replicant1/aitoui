package com.example.aitoui.script

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
class AddScriptScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { AddScriptRobot(composeTestRule) }

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
    fun saveButton_isDisabled_whenFormCannotBeSaved() {
        robot
            .setContent(state = AddScriptState())
            .assertSaveDisabled()
    }

    @Test
    fun saveButton_isEnabled_andClickingItDispatchesSave() {
        var capturedAction: AddScriptAction? = null

        robot
            .setContent(
                state = savableState(),
                onAction = { capturedAction = it },
            )
            .assertSaveEnabled()
            .clickSave()

        assertEquals(AddScriptAction.Save, capturedAction)
    }

    @Test
    fun typingIntoFields_dispatchesChangeActions() {
        val actions = mutableListOf<AddScriptAction>()

        robot
            .setContent(
                state = AddScriptState(),
                onAction = actions::add,
            )
            .enterBrandName("Tensig")
            .enterActiveIngredient("Atenolol")

        assertTrue(actions.any { it == AddScriptAction.BrandNameChanged("Tensig") })
        assertTrue(actions.any { it == AddScriptAction.ActiveIngredientChanged("Atenolol") })
    }

    @Test
    fun duplicateSerialDialog_isShown_andOkDispatchesDismiss() {
        var capturedAction: AddScriptAction? = null

        robot
            .setContent(
                state = AddScriptState(duplicateSerial = true),
                onAction = { capturedAction = it },
            )
            .assertDuplicateSerialDialogVisible()
            .dismissDuplicateSerialDialog()

        assertEquals(AddScriptAction.DismissDuplicateSerial, capturedAction)
    }

    @Test
    fun medicationResolution_continueDispatchesPickedMedication() {
        val knownMedication = Medication(id = 7, brandName = "Tensig", activeIngredient = "Atenolol")
        var capturedAction: AddScriptAction? = null

        // Use a form brand name that differs from the known medication so onNodeWithText("Tensig")
        // finds exactly one node — the radio card — rather than also matching the text field.
        robot
            .setContent(
                state = savableState().copy(
                    brandName = "Ventolin",
                    activeIngredient = "Salbutamol",
                    medicationStep = MedicationResolution(
                        exact = listOf(knownMedication),
                        similar = emptyList(),
                        blocked = false,
                    ),
                ),
                onAction = { capturedAction = it },
            )
            .assertMedicationResolutionVisible()
            .selectMedication(knownMedication)
            .clickContinue()

        assertEquals(AddScriptAction.PickMedication(7), capturedAction)
    }

    private fun savableState() = AddScriptState(
        brandName = "Tensig",
        activeIngredient = "Atenolol",
        dosePerTablet = "50",
        tabletsPerUnit = "60",
        dateOfIssue = 1_000L,
        priorDispensed = "0",
        repeats = "5",
        validToMillis = 2_000L,
    )
}
