package com.example.aitoui.medication

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
class MedicationsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { MedicationsRobot(composeTestRule) }

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
            .setContent(state = MedicationsState())
            .assertTitleDisplayed()
    }

    @Test
    fun description_isDisplayed() {
        robot
            .setContent(state = MedicationsState())
            .assertDescriptionDisplayed()
    }

    @Test
    fun medicationCard_isRendered_withBrandName() {
        robot
            .setContent(state = MedicationsState(medications = listOf(sampleMedication())))
            .assertMedicationCardVisible("Panadol")
    }

    @Test
    fun emptyList_rendersNoCards() {
        robot
            .setContent(state = MedicationsState())
            .assertDescriptionDisplayed()
    }

    @Test
    fun clickingDeleteIcon_dispatchesDeleteTapped() {
        var capturedAction: MedicationsAction? = null
        robot
            .setContent(
                state = MedicationsState(medications = listOf(sampleMedication())),
                onAction = { capturedAction = it },
            )
            .clickDeleteIcon("Panadol")
        assertEquals(MedicationsAction.DeleteTapped(1L), capturedAction)
    }

    @Test
    fun deleteDialog_isShown_andConfirmDispatchesConfirmDelete() {
        var capturedAction: MedicationsAction? = null
        val med = sampleMedication()
        robot
            .setContent(
                state = MedicationsState(
                    medications = listOf(med),
                    pendingDeleteMedicationId = med.id,
                ),
                onAction = { capturedAction = it },
            )
            .assertDeleteDialogVisible()
            .clickConfirmDelete()
        assertEquals(MedicationsAction.ConfirmDelete, capturedAction)
    }

    @Test
    fun deleteDialog_cancelDispatchesCancelDelete() {
        var capturedAction: MedicationsAction? = null
        val med = sampleMedication()
        robot
            .setContent(
                state = MedicationsState(
                    medications = listOf(med),
                    pendingDeleteMedicationId = med.id,
                ),
                onAction = { capturedAction = it },
            )
            .assertDeleteDialogVisible()
            .clickCancelDelete()
        assertEquals(MedicationsAction.CancelDelete, capturedAction)
    }

    @Test
    fun clickingAddFab_invokesOnAddMedication() {
        var addCalled = false
        robot
            .setContent(
                state = MedicationsState(),
                onAddMedication = { addCalled = true },
            )
            .clickAddFab()
        assertTrue(addCalled)
    }

    @Test
    fun clickingBack_invokesOnBack() {
        var backCalled = false
        robot
            .setContent(
                state = MedicationsState(),
                onBack = { backCalled = true },
            )
            .clickBack()
        assertTrue(backCalled)
    }

    private fun sampleMedication() = Medication(
        id = 1L,
        brandName = "Panadol",
        activeIngredient = "Paracetamol",
    )
}

