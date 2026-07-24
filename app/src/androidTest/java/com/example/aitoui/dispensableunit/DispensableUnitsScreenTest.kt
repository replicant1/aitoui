package com.example.aitoui.dispensableunit

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aitoui.data.DispensableUnitDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DispensableUnitsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { DispensableUnitsRobot(composeTestRule) }

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
            .setContent(state = DispensableUnitsState())
            .assertTitleDisplayed()
    }

    @Test
    fun description_isDisplayed() {
        robot
            .setContent(state = DispensableUnitsState())
            .assertDescriptionDisplayed()
    }

    @Test
    fun unitCard_isRendered_withBrandName() {
        robot
            .setContent(state = DispensableUnitsState(units = listOf(sampleUnit())))
            .assertUnitCardVisible("Panadol")
    }

    @Test
    fun emptyList_rendersNoUnitCards() {
        robot
            .setContent(state = DispensableUnitsState())
            .assertDescriptionDisplayed()
        // Just verifying description still shows and no crash — no unit text expected.
    }

    @Test
    fun clickingDeleteIcon_dispatchesDeleteTapped() {
        var capturedAction: DispensableUnitsAction? = null
        robot
            .setContent(
                state = DispensableUnitsState(units = listOf(sampleUnit())),
                onAction = { capturedAction = it },
            )
            .clickDeleteIcon()
        assertEquals(DispensableUnitsAction.DeleteTapped(1L), capturedAction)
    }

    @Test
    fun deleteDialog_isShown_andConfirmDispatchesConfirmDelete() {
        var capturedAction: DispensableUnitsAction? = null
        val unit = sampleUnit()
        robot
            .setContent(
                state = DispensableUnitsState(
                    units = listOf(unit),
                    pendingDeleteUnitId = unit.formatId,
                ),
                onAction = { capturedAction = it },
            )
            .assertDeleteDialogVisible()
            .clickConfirmDelete()
        assertEquals(DispensableUnitsAction.ConfirmDelete, capturedAction)
    }

    @Test
    fun deleteDialog_cancelDispatchesCancelDelete() {
        var capturedAction: DispensableUnitsAction? = null
        val unit = sampleUnit()
        robot
            .setContent(
                state = DispensableUnitsState(
                    units = listOf(unit),
                    pendingDeleteUnitId = unit.formatId,
                ),
                onAction = { capturedAction = it },
            )
            .assertDeleteDialogVisible()
            .clickCancelDelete()
        assertEquals(DispensableUnitsAction.CancelDelete, capturedAction)
    }

    @Test
    fun clickingAddFab_invokesOnAddDispensableUnit() {
        var addCalled = false
        robot
            .setContent(
                state = DispensableUnitsState(),
                onAddDispensableUnit = { addCalled = true },
            )
            .clickAddFab()
        assertTrue(addCalled)
    }

    @Test
    fun clickingBack_invokesOnBack() {
        var backCalled = false
        robot
            .setContent(
                state = DispensableUnitsState(),
                onBack = { backCalled = true },
            )
            .clickBack()
        assertTrue(backCalled)
    }

    private fun sampleUnit() = DispensableUnitDetails(
        formatId = 1L,
        medicationId = 1L,
        brandName = "Panadol",
        activeIngredient = "Paracetamol",
        dosePerTablet = "500",
        tabletsPerUnit = "24",
        imagePath = null,
    )
}

