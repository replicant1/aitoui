package com.example.aitoui.inventory

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
class InventoryScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { InventoryRobot(composeTestRule) }

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
            .setContent(state = InventoryState())
            .assertTitleDisplayed()
    }

    @Test
    fun description_isDisplayed() {
        robot
            .setContent(state = InventoryState())
            .assertDescriptionDisplayed()
    }

    @Test
    fun inventoryItem_isRendered_withBrandName() {
        robot
            .setContent(state = InventoryState(items = listOf(sampleItem())))
            .assertItemVisible("Panadol")
    }

    @Test
    fun clickingBack_invokesOnBack() {
        var backCalled = false
        robot
            .setContent(state = InventoryState(), onBack = { backCalled = true })
            .clickBack()
        assertTrue(backCalled)
    }

    @Test
    fun clickingRunOutGraphButton_invokesOnRunOutGraph() {
        var graphCalled = false
        robot
            .setContent(state = InventoryState(), onRunOutGraph = { graphCalled = true })
            .clickRunOutGraphButton()
        assertTrue(graphCalled)
    }

    @Test
    fun clickingSortOrderButton_opensMenu() {
        robot
            .setContent(state = InventoryState())
            .clickSortOrderButton()
            .assertSortMenuVisible()
    }

    @Test
    fun clickingSortOption_dispatchesSortOrderChanged() {
        var capturedAction: InventoryAction? = null
        robot
            .setContent(
                state = InventoryState(),
                onAction = { capturedAction = it },
            )
            .clickSortOrderButton()
            .clickSortOption(SortOption.BrandName)
        assertEquals(InventoryAction.SortOrderChanged(SortOption.BrandName), capturedAction)
    }

    private fun sampleItem() = InventoryItem(
        unit = DispensableUnitDetails(
            formatId = 1L,
            medicationId = 1L,
            brandName = "Panadol",
            activeIngredient = "Paracetamol",
            dosePerTablet = "500",
            tabletsPerUnit = "24",
            imagePath = null,
        ),
        supply = SupplyBreakdown(
            undispensedFills = 2,
            tabletsPerUnit = 24,
            undispensedTablets = 48,
            undispensedDays = 24,
            inHandTablets = 12,
            inHandDays = 6,
        ),
    )
}

