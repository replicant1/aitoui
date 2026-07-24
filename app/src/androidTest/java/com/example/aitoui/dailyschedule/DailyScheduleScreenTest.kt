package com.example.aitoui.dailyschedule

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
class DailyScheduleScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { DailyScheduleRobot(composeTestRule) }

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
            .setContent(state = DailyScheduleState())
            .assertTitleDisplayed()
    }

    @Test
    fun description_isDisplayed() {
        robot
            .setContent(state = DailyScheduleState())
            .assertDescriptionDisplayed()
    }

    @Test
    fun clickingBack_invokesOnBack() {
        var backCalled = false
        robot
            .setContent(state = DailyScheduleState(), onBack = { backCalled = true })
            .clickBack()
        assertTrue(backCalled)
    }

    @Test
    fun clickingSave_dispatchesSaveAction() {
        var capturedAction: DailyScheduleAction? = null
        robot
            .setContent(state = DailyScheduleState(), onAction = { capturedAction = it })
            .clickSave()
        assertEquals(DailyScheduleAction.Save, capturedAction)
    }

    @Test
    fun addButton_isDisabled_whenCanAddIsFalse() {
        // canAdd = false when no unit selected and no tablets entered
        robot
            .setContent(state = DailyScheduleState())
            .assertAddButtonDisabled()
    }

    @Test
    fun addButton_isEnabled_whenCanAddIsTrue() {
        robot
            .setContent(
                state = DailyScheduleState(
                    units = listOf(sampleUnit()),
                    selectedUnitId = 1L,
                    numberOfTablets = "2",
                ),
            )
            .assertAddButtonEnabled()
    }

    @Test
    fun clickingAdd_dispatchesAddAction() {
        var capturedAction: DailyScheduleAction? = null
        robot
            .setContent(
                state = DailyScheduleState(
                    units = listOf(sampleUnit()),
                    selectedUnitId = 1L,
                    numberOfTablets = "2",
                ),
                onAction = { capturedAction = it },
            )
            .clickAdd()
        assertEquals(DailyScheduleAction.Add, capturedAction)
    }

    @Test
    fun deleteButton_isDisabled_whenNoRowSelected() {
        robot
            .setContent(
                state = DailyScheduleState(
                    tabletsTaken = listOf(DailyScheduleEntry(0L, 1L, "Panadol", "2")),
                    selectedId = null,
                ),
            )
            .assertDeleteButtonDisabled()
    }

    @Test
    fun deleteButton_isEnabled_whenRowSelected() {
        robot
            .setContent(
                state = DailyScheduleState(
                    tabletsTaken = listOf(DailyScheduleEntry(0L, 1L, "Panadol", "2")),
                    selectedId = 0L,
                ),
            )
            .assertDeleteButtonEnabled()
    }

    @Test
    fun clickingDelete_dispatchesDeleteAction() {
        var capturedAction: DailyScheduleAction? = null
        robot
            .setContent(
                state = DailyScheduleState(
                    tabletsTaken = listOf(DailyScheduleEntry(0L, 1L, "Panadol", "2")),
                    selectedId = 0L,
                ),
                onAction = { capturedAction = it },
            )
            .clickDelete()
        assertEquals(DailyScheduleAction.Delete, capturedAction)
    }

    @Test
    fun enteringNumberOfTablets_dispatchesNumberOfTabletsChanged() {
        val actions = mutableListOf<DailyScheduleAction>()
        robot
            .setContent(state = DailyScheduleState(), onAction = actions::add)
            .enterNumberOfTablets("2")
        assertTrue(actions.any { it is DailyScheduleAction.NumberOfTabletsChanged })
    }

    @Test
    fun clickingRow_dispatchesRowSelected() {
        var capturedAction: DailyScheduleAction? = null
        robot
            .setContent(
                state = DailyScheduleState(
                    tabletsTaken = listOf(DailyScheduleEntry(0L, 1L, "Panadol", "2")),
                ),
                onAction = { capturedAction = it },
            )
            .clickRow("Panadol")
        assertEquals(DailyScheduleAction.RowSelected(0L), capturedAction)
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

