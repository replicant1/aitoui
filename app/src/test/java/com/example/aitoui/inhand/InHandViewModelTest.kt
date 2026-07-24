package com.example.aitoui.inhand

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
import com.example.aitoui.data.InHandDao
import com.example.aitoui.data.InHandDateEntity
import com.example.aitoui.data.InHandDetails
import com.example.aitoui.data.InHandEntity
import com.example.aitoui.data.InHandRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class InHandViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    @Test
    fun `init loads saved in-hand list from repository`() = runTest {
        val item = InHandDetails(id = 1L, dispensableUnitId = 10L, medicationId = 1L,
            brandName = "Panadol", quantity = 24.0)
        val vm = createViewModel(savedInHand = listOf(item))

        vm.state.test {
            val state = awaitItem()
            assertThat(state.tabletsInHand.size).isEqualTo(1)
            assertThat(state.tabletsInHand.first().brand).isEqualTo("Panadol")
            assertThat(state.tabletsInHand.first().number).isEqualTo("24")
            // Loaded list matches its own signature → no unsaved changes
            assertThat(state.hasUnsavedChanges).isEqualTo(false)
        }
    }

    @Test
    fun `gatheredDate from repository is reflected in state`() = runTest {
        val inHandDao = FakeInHandDao()
        val vm = createViewModel(inHandDao = inHandDao)

        vm.state.test {
            awaitItem()

            inHandDao.setGatheredDate(1_700_000_000_000L)

            assertThat(awaitItem().gatheredDate).isEqualTo(1_700_000_000_000L)
        }
    }

    @Test
    fun `dispensable units from repository populate state`() = runTest {
        val u = unit(id = 1L, brandName = "Panadol")
        val vm = createViewModel(units = listOf(u))

        vm.state.test {
            assertThat(awaitItem().units).containsExactly(u)
        }
    }

    // ── Field actions ────────────────────────────────────────────────────────

    @Test
    fun `UnitSelected sets selectedUnitId`() = runTest {
        val u = unit(id = 1L)
        val vm = createViewModel(units = listOf(u))

        vm.state.test {
            awaitItem()
            vm.onAction(InHandAction.UnitSelected(1L))
            assertThat(awaitItem().selectedUnitId).isEqualTo(1L)
        }
    }

    @Test
    fun `NumberOfTabletsChanged filters to decimal only`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()
            vm.onAction(InHandAction.NumberOfTabletsChanged("1.5"))
            assertThat(awaitItem().numberOfTablets).isEqualTo("1.5")
        }
    }

    @Test
    fun `TabletsCounted sets numberOfTablets from count`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()
            vm.onAction(InHandAction.TabletsCounted(42))
            assertThat(awaitItem().numberOfTablets).isEqualTo("42")
        }
    }

    // ── Add ──────────────────────────────────────────────────────────────────

    @Test
    fun `Add appends entry and clears inputs`() = runTest {
        val u = unit(id = 1L, brandName = "Panadol")
        val vm = createViewModel(units = listOf(u))

        vm.state.test {
            awaitItem()
            vm.onAction(InHandAction.UnitSelected(1L))
            awaitItem()
            vm.onAction(InHandAction.NumberOfTabletsChanged("10"))
            awaitItem()

            vm.onAction(InHandAction.Add)

            val updated = awaitItem()
            assertThat(updated.tabletsInHand.size).isEqualTo(1)
            assertThat(updated.tabletsInHand.first().brand).isEqualTo("Panadol")
            assertThat(updated.tabletsInHand.first().number).isEqualTo("10")
            assertThat(updated.selectedUnitId).isNull()
            assertThat(updated.numberOfTablets).isEqualTo("")
        }
    }

    @Test
    fun `Add is a no-op when canAdd is false`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()
            vm.onAction(InHandAction.Add)
            expectNoEvents()
        }
    }

    // ── RowSelected / Delete ─────────────────────────────────────────────────

    @Test
    fun `RowSelected sets selectedId`() = runTest {
        val u = unit(id = 1L)
        val vm = createViewModel(units = listOf(u))

        vm.state.test {
            awaitItem()
            vm.onAction(InHandAction.UnitSelected(1L))
            awaitItem()
            vm.onAction(InHandAction.NumberOfTabletsChanged("5"))
            awaitItem()
            vm.onAction(InHandAction.Add)
            val afterAdd = awaitItem()
            val entryId = afterAdd.tabletsInHand.first().id

            vm.onAction(InHandAction.RowSelected(entryId))
            assertThat(awaitItem().selectedId).isEqualTo(entryId)
        }
    }

    @Test
    fun `Delete removes selected row`() = runTest {
        val u = unit(id = 1L)
        val vm = createViewModel(units = listOf(u))

        vm.state.test {
            awaitItem()
            vm.onAction(InHandAction.UnitSelected(1L))
            awaitItem()
            vm.onAction(InHandAction.NumberOfTabletsChanged("5"))
            awaitItem()
            vm.onAction(InHandAction.Add)
            val afterAdd = awaitItem()
            val entryId = afterAdd.tabletsInHand.first().id

            vm.onAction(InHandAction.RowSelected(entryId))
            awaitItem()

            vm.onAction(InHandAction.Delete)

            val cleared = awaitItem()
            assertThat(cleared.tabletsInHand).isEmpty()
            assertThat(cleared.selectedId).isNull()
        }
    }

    @Test
    fun `Delete is a no-op when nothing is selected`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()
            vm.onAction(InHandAction.Delete)
            expectNoEvents()
        }
    }

    // ── Merge ────────────────────────────────────────────────────────────────

    @Test
    fun `Merge collapses duplicate unit rows and clears selectedId`() = runTest {
        val u = unit(id = 1L, brandName = "Panadol")
        val vm = createViewModel(units = listOf(u))

        vm.state.test {
            awaitItem()
            // Add same unit twice
            vm.onAction(InHandAction.UnitSelected(1L)); awaitItem()
            vm.onAction(InHandAction.NumberOfTabletsChanged("10")); awaitItem()
            vm.onAction(InHandAction.Add); awaitItem()
            vm.onAction(InHandAction.UnitSelected(1L)); awaitItem()
            vm.onAction(InHandAction.NumberOfTabletsChanged("6")); awaitItem()
            vm.onAction(InHandAction.Add)
            val afterTwoAdds = awaitItem()
            assertThat(afterTwoAdds.tabletsInHand.size).isEqualTo(2)

            // Select first row, then merge
            vm.onAction(InHandAction.RowSelected(afterTwoAdds.tabletsInHand.first().id))
            awaitItem()

            vm.onAction(InHandAction.Merge)

            val merged = awaitItem()
            assertThat(merged.tabletsInHand.size).isEqualTo(1)
            assertThat(merged.tabletsInHand.first().number).isEqualTo("16")
            assertThat(merged.selectedId).isNull()
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    @Test
    fun `Save persists items and sets saved flag`() = runTest {
        val u = unit(id = 1L)
        val inHandDao = FakeInHandDao()
        val vm = createViewModel(units = listOf(u), inHandDao = inHandDao)

        vm.saved.test {
            assertThat(awaitItem()).isEqualTo(false)

            vm.state.test {
                awaitItem()
                vm.onAction(InHandAction.UnitSelected(1L)); awaitItem()
                vm.onAction(InHandAction.NumberOfTabletsChanged("10")); awaitItem()
                vm.onAction(InHandAction.Add); awaitItem()
                vm.onAction(InHandAction.Save)
                // replaceAll() → setDate() → gatheredDate flow emits → state.gatheredDate update
                awaitItem()
            }

            assertThat(awaitItem()).isEqualTo(true)
        }

        assertThat(inHandDao.savedEntities.size).isEqualTo(1)
        assertThat(inHandDao.savedEntities.first().dispensableUnitId).isEqualTo(1L)
        assertThat(inHandDao.savedEntities.first().quantity).isEqualTo(10.0)
    }

    @Test
    fun `unit removed from repository clears selectedUnitId`() = runTest {
        val u = unit(id = 1L)
        val duDao = FakeDispensableUnitDao(initialUnits = listOf(u))
        val vm = createViewModel(duDao = duDao)

        vm.state.test {
            awaitItem()
            vm.onAction(InHandAction.UnitSelected(1L))
            awaitItem() // selectedUnitId = 1

            duDao.setUnits(emptyList())

            assertThat(awaitItem().selectedUnitId).isNull()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun unit(id: Long = 1L, brandName: String = "Panadol") = DispensableUnitDetails(
        formatId = id, medicationId = 1L, brandName = brandName,
        activeIngredient = "Paracetamol", dosePerTablet = "500", tabletsPerUnit = "24", imagePath = null,
    )

    private fun createViewModel(
        units: List<DispensableUnitDetails> = emptyList(),
        savedInHand: List<InHandDetails> = emptyList(),
        duDao: FakeDispensableUnitDao = FakeDispensableUnitDao(initialUnits = units),
        inHandDao: FakeInHandDao = FakeInHandDao(initialInHand = savedInHand),
    ) = InHandViewModel(
        dispensableUnitRepository = DispensableUnitRepository(duDao),
        inHandRepository = InHandRepository(inHandDao),
    )

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class FakeDispensableUnitDao(
        initialUnits: List<DispensableUnitDetails> = emptyList(),
    ) : DispensableUnitDao {
        private val state = MutableStateFlow(initialUnits)

        fun setUnits(units: List<DispensableUnitDetails>) { state.value = units }

        override fun getAllWithMedication(): Flow<List<DispensableUnitDetails>> = state
        override suspend fun insert(entity: DispensableUnitEntity): Long = throw UnsupportedOperationException()
        override suspend fun count(): Int = state.value.size
        override suspend fun setImagePath(id: Long, imagePath: String?) {}
        override suspend fun deleteById(id: Long) {}
    }

    private class FakeInHandDao(
        initialInHand: List<InHandDetails> = emptyList(),
    ) : InHandDao {
        private val initialList = initialInHand
        private val dateFlow = MutableStateFlow<Long?>(null)
        val savedEntities = mutableListOf<InHandEntity>()

        fun setGatheredDate(millis: Long) { dateFlow.value = millis }

        override suspend fun getAllWithMedication(): List<InHandDetails> = initialList
        override fun getAllWithMedicationFlow(): Flow<List<InHandDetails>> = flowOf(initialList)

        override suspend fun insert(entity: InHandEntity): Long {
            savedEntities.add(entity)
            return savedEntities.size.toLong()
        }

        override suspend fun insertAll(entities: List<InHandEntity>) {
            savedEntities.addAll(entities)
        }

        override suspend fun clear() { savedEntities.clear() }

        override suspend fun incrementQuantity(dispensableUnitId: Long, delta: Double): Int {
            val idx = savedEntities.indexOfFirst { it.dispensableUnitId == dispensableUnitId }
            return if (idx >= 0) {
                savedEntities[idx] = savedEntities[idx].copy(quantity = savedEntities[idx].quantity + delta)
                1
            } else 0
        }

        override suspend fun setDate(entity: InHandDateEntity) { dateFlow.value = entity.gatheredAtMillis }

        override fun getDateFlow(): Flow<Long?> = dateFlow
    }
}

