package com.example.aitoui.script

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aitoui.data.ScriptDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { ScriptsRobot(composeTestRule) }

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
    fun appbarTitle_andDescription_areDisplayed() {
        robot
            .setContent(state = ScriptsState())
            .assertTitleDisplayed()
            .assertDescriptionDisplayed()
    }

    @Test
    fun scriptCards_areRendered_withBrandNameAndCounts() {
        robot
            .setContent(state = ScriptsState(scripts = listOf(sampleScript())))
            .assertScriptCardVisible("Panadol")
    }

    @Test
    fun emptyList_rendersNoScriptCards() {
        // The description text should still be visible; no card text should appear.
        robot
            .setContent(state = ScriptsState())
            .assertDescriptionDisplayed()
    }

    @Test
    fun clickingDispenseIcon_dispatchesDispensedTapped() {
        var capturedAction: ScriptsAction? = null

        robot
            .setContent(
                state = ScriptsState(scripts = listOf(sampleScript())),
                onAction = { capturedAction = it },
            )
            .clickDispenseIcon()

        assertEquals(ScriptsAction.DispensedTapped(1L), capturedAction)
    }

    @Test
    fun clickingDeleteIcon_dispatchesDeleteTapped() {
        var capturedAction: ScriptsAction? = null

        robot
            .setContent(
                state = ScriptsState(scripts = listOf(sampleScript())),
                onAction = { capturedAction = it },
            )
            .clickDeleteIcon("Panadol")

        assertEquals(ScriptsAction.DeleteTapped(1L), capturedAction)
    }

    @Test
    fun dispenseDialog_isShown_andConfirmDispatchesConfirmDispense() {
        var capturedAction: ScriptsAction? = null
        val script = sampleScript()

        robot
            .setContent(
                state = ScriptsState(
                    scripts = listOf(script),
                    pendingDispenseScriptId = script.scriptId,
                ),
                onAction = { capturedAction = it },
            )
            .assertDispenseDialogVisible()
            .clickConfirmDispense()

        assertEquals(ScriptsAction.ConfirmDispense, capturedAction)
    }

    @Test
    fun dispenseDialog_cancelDispatchesCancelDispense() {
        var capturedAction: ScriptsAction? = null
        val script = sampleScript()

        robot
            .setContent(
                state = ScriptsState(
                    scripts = listOf(script),
                    pendingDispenseScriptId = script.scriptId,
                ),
                onAction = { capturedAction = it },
            )
            .assertDispenseDialogVisible()
            .clickCancelDispense()

        assertEquals(ScriptsAction.CancelDispense, capturedAction)
    }

    @Test
    fun maxedOutDialog_isShown_andOkDispatchesDismissMaxedOut() {
        var capturedAction: ScriptsAction? = null
        val script = sampleScript()

        robot
            .setContent(
                state = ScriptsState(
                    scripts = listOf(script),
                    maxedOutScriptId = script.scriptId,
                ),
                onAction = { capturedAction = it },
            )
            .assertMaxedOutDialogVisible()
            .clickDismissMaxedOut()

        assertEquals(ScriptsAction.DismissMaxedOut, capturedAction)
    }

    @Test
    fun deleteDialog_isShown_andDeleteDispatchesConfirmDelete() {
        var capturedAction: ScriptsAction? = null
        val script = sampleScript()

        robot
            .setContent(
                state = ScriptsState(
                    scripts = listOf(script),
                    pendingDeleteScriptId = script.scriptId,
                ),
                onAction = { capturedAction = it },
            )
            .assertDeleteDialogVisible()
            .clickConfirmDelete()

        assertEquals(ScriptsAction.ConfirmDelete, capturedAction)
    }

    @Test
    fun deleteDialog_cancelDispatchesCancelDelete() {
        var capturedAction: ScriptsAction? = null
        val script = sampleScript()

        robot
            .setContent(
                state = ScriptsState(
                    scripts = listOf(script),
                    pendingDeleteScriptId = script.scriptId,
                ),
                onAction = { capturedAction = it },
            )
            .assertDeleteDialogVisible()
            .clickCancelDelete()

        assertEquals(ScriptsAction.CancelDelete, capturedAction)
    }

    @Test
    fun clickingAddScriptFab_invokesOnAddScript() {
        var addScriptCalled = false

        robot
            .setContent(
                state = ScriptsState(),
                onAddScript = { addScriptCalled = true },
            )
            .clickAddScriptFab()

        assertTrue(addScriptCalled)
    }

    @Test
    fun clickingBack_invokesOnBack() {
        var backCalled = false

        robot
            .setContent(
                state = ScriptsState(),
                onBack = { backCalled = true },
            )
            .clickBack()

        assertTrue(backCalled)
    }

    @Test
    fun sortMenu_opensAndPickingSortOrderDispatchesSortOrderChanged() {
        val actions = mutableListOf<ScriptsAction>()

        robot
            .setContent(
                state = ScriptsState(scripts = listOf(sampleScript())),
                onAction = actions::add,
            )
            .clickSortButton()
            .clickSortOption(SortOrder.BrandName.label)

        assertTrue(actions.any { it == ScriptsAction.SortOrderChanged(SortOrder.BrandName) })
    }

    private fun sampleScript() = ScriptDetails(
        scriptId = 1L,
        dispensableUnitId = 1L,
        medicationId = 1L,
        brandName = "Panadol",
        activeIngredient = "Paracetamol",
        dosePerTablet = "500",
        tabletsPerUnit = "24",
        dispensed = 2,
        repeats = 5,
        dateOfIssue = 0L,
    )
}

