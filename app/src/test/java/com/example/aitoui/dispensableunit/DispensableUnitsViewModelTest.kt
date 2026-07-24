package com.example.aitoui.dispensableunit

import android.content.Context
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.example.aitoui.data.DispensableUnitDao
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.DispensableUnitEntity
import com.example.aitoui.data.DispensableUnitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class DispensableUnitsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    /**
     * A mock Context is sufficient here because [DispensableUnitsViewModel] only passes it to
     * [com.example.aitoui.image.ImageStore.delete], which short-circuits immediately when
     * [DispensableUnitDetails.imagePath] is null — which it always is in these tests.
     */
    private val context: Context = mock(Context::class.java)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    @Test
    fun `units from repository populate state sorted alphabetically by brand name`() = runTest {
        val zebra = unit(id = 1L, brandName = "Zebra")
        val aardvark = unit(id = 2L, brandName = "Aardvark")
        val vm = createViewModel(initialUnits = listOf(zebra, aardvark))

        vm.state.test {
            val state = awaitItem()
            assertThat(state.units.map { it.brandName }).containsExactly("Aardvark", "Zebra")
        }
    }

    // ── DeleteTapped ─────────────────────────────────────────────────────────

    @Test
    fun `delete tapped sets pendingDeleteUnitId`() = runTest {
        val u = unit(id = 1L)
        val vm = createViewModel(initialUnits = listOf(u))

        vm.state.test {
            awaitItem()

            vm.onAction(DispensableUnitsAction.DeleteTapped(1L))

            val updated = awaitItem()
            assertThat(updated.pendingDeleteUnitId).isEqualTo(1L)
            assertThat(updated.pendingDeleteUnit).isEqualTo(u)
        }
    }

    @Test
    fun `cancel delete clears pendingDeleteUnitId`() = runTest {
        val u = unit(id = 1L)
        val vm = createViewModel(initialUnits = listOf(u))

        vm.state.test {
            awaitItem()
            vm.onAction(DispensableUnitsAction.DeleteTapped(1L))
            awaitItem()

            vm.onAction(DispensableUnitsAction.CancelDelete)

            val cleared = awaitItem()
            assertThat(cleared.pendingDeleteUnitId).isNull()
        }
    }

    @Test
    fun `confirm delete removes unit from repository`() = runTest {
        val u = unit(id = 1L)
        val dao = FakeDispensableUnitDao(initialUnits = listOf(u))
        val vm = createViewModel(dao = dao)

        vm.state.test {
            awaitItem()
            vm.onAction(DispensableUnitsAction.DeleteTapped(1L))
            awaitItem()

            vm.onAction(DispensableUnitsAction.ConfirmDelete)

            awaitItem() // units emptied
        }

        assertThat(dao.currentUnits).isEmpty()
    }

    @Test
    fun `confirm delete clears pendingDeleteUnitId`() = runTest {
        val u = unit(id = 1L)
        val vm = createViewModel(initialUnits = listOf(u))

        vm.state.test {
            awaitItem()
            vm.onAction(DispensableUnitsAction.DeleteTapped(1L))
            awaitItem()

            vm.onAction(DispensableUnitsAction.ConfirmDelete)

            val cleared = awaitItem()
            assertThat(cleared.pendingDeleteUnitId).isNull()
        }
    }

    @Test
    fun `pending delete is cleared when unit disappears from repository`() = runTest {
        val u = unit(id = 1L)
        val dao = FakeDispensableUnitDao(initialUnits = listOf(u))
        val vm = createViewModel(dao = dao)

        vm.state.test {
            awaitItem()
            vm.onAction(DispensableUnitsAction.DeleteTapped(1L))
            awaitItem() // pendingDeleteUnitId set

            // Simulate unit vanishing from the repository (e.g. deleted from another screen).
            dao.setUnits(emptyList())

            val updated = awaitItem()
            assertThat(updated.pendingDeleteUnitId).isNull()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun unit(
        id: Long = 1L,
        brandName: String = "Panadol",
    ) = DispensableUnitDetails(
        formatId = id,
        medicationId = 1L,
        brandName = brandName,
        activeIngredient = "Paracetamol",
        dosePerTablet = "500",
        tabletsPerUnit = "24",
        imagePath = null,
    )

    private fun createViewModel(
        initialUnits: List<DispensableUnitDetails> = emptyList(),
        dao: FakeDispensableUnitDao = FakeDispensableUnitDao(initialUnits),
    ) = DispensableUnitsViewModel(
        repository = DispensableUnitRepository(dao),
        appContext = context,
    )

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class FakeDispensableUnitDao(
        initialUnits: List<DispensableUnitDetails> = emptyList(),
    ) : DispensableUnitDao {
        private val state = MutableStateFlow(initialUnits)

        val currentUnits: List<DispensableUnitDetails> get() = state.value

        fun setUnits(units: List<DispensableUnitDetails>) {
            state.value = units
        }

        override fun getAllWithMedication(): Flow<List<DispensableUnitDetails>> = state

        override suspend fun deleteById(id: Long) {
            state.value = state.value.filterNot { it.formatId == id }
        }

        override suspend fun insert(entity: DispensableUnitEntity): Long = throw UnsupportedOperationException()
        override suspend fun count(): Int = state.value.size
        override suspend fun setImagePath(id: Long, imagePath: String?) {}
    }
}

