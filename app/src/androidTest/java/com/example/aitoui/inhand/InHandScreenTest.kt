package com.example.aitoui.inhand

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
class InHandScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { InHandRobot(composeTestRule) }

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
        robot.setContent(state = InHandState()).assertTitleDisplayed()
    }

    @Test
    fun description_isDisplayed() {
        robot.setContent(state = InHandState()).assertDescriptionDisplayed()
    }

    @Test
    fun clickingBack_invokesOnBack() {
        var backCalled = false
        robot.setContent(state = InHandState(), onBack = { backCalled = true }).clickBack()
        assertTrue(backCalled)
    }

    @Test
    fun clickingSave_dispatchesSaveAction() {
        var capturedAction: InHandAction? = null
        robot.setContent(state = InHandState(), onAction = { capturedAction = it }).clickSave()
        assertEquals(InHandAction.Save, capturedAction)
    }

    @Test
    fun addButton_isDisabled_whenCanAddIsFalse() {
        robot.setContent(state = InHandState()).assertAddButtonDisabled()
    }

    @Test
    fun addButton_isEnabled_whenCanAddIsTrue() {
        robot
            .setContent(
                state = InHandState(
                    units = listOf(sampleUnit()),
                    selectedUnitId = 1L,
                    numberOfTablets = "3",
                ),
            )
            .assertAddButtonEnabled()
    }

    @Test
    fun clickingAdd_dispatchesAddAction() {
        var capturedAction: InHandAction? = null
        robot
            .setContent(
                state = InHandState(
                    units = listOf(sampleUnit()),
                    selectedUnitId = 1L,
                    numberOfTablets = "3",
                ),
                onAction = { capturedAction = it },
            )
            .clickAdd()
        assertEquals(InHandAction.Add, capturedAction)
    }

    @Test
    fun deleteButton_isDisabled_whenNoRowSelected() {
        robot
            .setContent(
                state = InHandState(
                    tabletsInHand = listOf(InHandEntry(0L, 1L, "Panadol", "10")),
                    selectedId = null,
                ),
            )
            .assertDeleteButtonDisabled()
    }

    @Test
    fun deleteButton_isEnabled_whenRowSelected() {
        robot
            .setContent(
                state = InHandState(
                    tabletsInHand = listOf(InHandEntry(0L, 1L, "Panadol", "10")),
                    selectedId = 0L,
                ),
            )
            .assertDeleteButtonEnabled()
    }

    @Test
    fun clickingDelete_dispatchesDeleteAction() {
        var capturedAction: InHandAction? = null
        robot
            .setContent(
                state = InHandState(
                    tabletsInHand = listOf(InHandEntry(0L, 1L, "Panadol", "10")),
                    selectedId = 0L,
                ),
                onAction = { capturedAction = it },
            )
            .clickDelete()
        assertEquals(InHandAction.Delete, capturedAction)
    }

    @Test
    fun mergeButton_isDisabled_whenCanMergeIsFalse() {
        // Two different units — no duplicates → canMerge = false
        robot
            .setContent(
                state = InHandState(
                    tabletsInHand = listOf(
                        InHandEntry(0L, 1L, "Panadol", "10"),
                        InHandEntry(1L, 2L, "Nurofen", "5"),
                    ),
                ),
            )
            .assertMergeButtonDisabled()
    }

    @Test
    fun mergeButton_isEnabled_whenCanMergeIsTrue() {
        // Same unit twice → canMerge = true
        robot
            .setContent(
                state = InHandState(
                    tabletsInHand = listOf(
                        InHandEntry(0L, 1L, "Panadol", "10"),
                        InHandEntry(1L, 1L, "Panadol", "6"),
                    ),
                ),
            )
            .assertMergeButtonEnabled()
    }

    @Test
    fun clickingMerge_dispatchesMergeAction() {
        var capturedAction: InHandAction? = null
        robot
            .setContent(
                state = InHandState(
                    tabletsInHand = listOf(
                        InHandEntry(0L, 1L, "Panadol", "10"),
                        InHandEntry(1L, 1L, "Panadol", "6"),
                    ),
                ),
                onAction = { capturedAction = it },
            )
            .clickMerge()
        assertEquals(InHandAction.Merge, capturedAction)
    }

    @Test
    fun enteringNumberOfTablets_dispatchesNumberOfTabletsChanged() {
        val actions = mutableListOf<InHandAction>()
        robot.setContent(state = InHandState(), onAction = actions::add).enterNumberOfTablets("5")
        assertTrue(actions.any { it is InHandAction.NumberOfTabletsChanged })
    }

    @Test
    fun clickingRow_dispatchesRowSelected() {
        var capturedAction: InHandAction? = null
        robot
            .setContent(
                state = InHandState(
                    tabletsInHand = listOf(InHandEntry(0L, 1L, "Panadol", "10")),
                ),
                onAction = { capturedAction = it },
            )
            .clickRow("Panadol")
        assertEquals(InHandAction.RowSelected(0L), capturedAction)
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

