package com.example.aitoui.dailyschedule

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.aitoui.data.DailyScheduleDao
import com.example.aitoui.data.DailyScheduleDetails
import com.example.aitoui.data.DailyScheduleEntity
import com.example.aitoui.data.DailyScheduleRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class DailyScheduleViewModelTest {

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
    fun `init loads saved schedule from repository`() = runTest {
        val item = DailyScheduleDetails(
            id = 1L, dispensableUnitId = 10L, medicationId = 1L,
            brandName = "Panadol", quantity = 2.0,
        )
        val vm = createViewModel(savedSchedule = listOf(item))

        vm.state.test {
            val state = awaitItem()
            assertThat(state.tabletsTaken.size).isEqualTo(1)
            assertThat(state.tabletsTaken.first().brand).isEqualTo("Panadol")
            assertThat(state.tabletsTaken.first().number).isEqualTo("2")
            // Loaded schedule matches its own signature → no unsaved changes
            assertThat(state.hasUnsavedChanges).isEqualTo(false)
        }
    }

    @Test
    fun `dispensable units from repository populate state`() = runTest {
        val unit = unit(id = 1L, brandName = "Panadol")
        val vm = createViewModel(units = listOf(unit))

        vm.state.test {
            assertThat(awaitItem().units).containsExactly(unit)
        }
    }

    // ── Field actions ────────────────────────────────────────────────────────

    @Test
    fun `MedicationSelected sets selectedUnitId`() = runTest {
        val u = unit(id = 1L)
        val vm = createViewModel(units = listOf(u))

        vm.state.test {
            awaitItem()

            vm.onAction(DailyScheduleAction.MedicationSelected(1L))

            val updated = awaitItem()
            assertThat(updated.selectedUnitId).isEqualTo(1L)
            assertThat(updated.selectedUnit).isEqualTo(u)
        }
    }

    @Test
    fun `NumberOfTabletsChanged filters to decimal only`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()

            vm.onAction(DailyScheduleAction.NumberOfTabletsChanged("1.5"))

            assertThat(awaitItem().numberOfTablets).isEqualTo("1.5")
        }
    }

    // ── Add ──────────────────────────────────────────────────────────────────

    @Test
    fun `Add appends entry and clears inputs`() = runTest {
        val u = unit(id = 1L, brandName = "Panadol")
        val vm = createViewModel(units = listOf(u))

        vm.state.test {
            awaitItem()
            vm.onAction(DailyScheduleAction.MedicationSelected(1L))
            awaitItem()
            vm.onAction(DailyScheduleAction.NumberOfTabletsChanged("2"))
            awaitItem()

            vm.onAction(DailyScheduleAction.Add)

            val updated = awaitItem()
            assertThat(updated.tabletsTaken.size).isEqualTo(1)
            assertThat(updated.tabletsTaken.first().brand).isEqualTo("Panadol")
            assertThat(updated.tabletsTaken.first().number).isEqualTo("2")
            // Inputs cleared
            assertThat(updated.selectedUnitId).isNull()
            assertThat(updated.numberOfTablets).isEqualTo("")
        }
    }

    @Test
    fun `Add is a no-op when canAdd is false`() = runTest {
        val vm = createViewModel() // no unit selected, no tablets

        vm.state.test {
            awaitItem()

            vm.onAction(DailyScheduleAction.Add)

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
            vm.onAction(DailyScheduleAction.MedicationSelected(1L))
            awaitItem()
            vm.onAction(DailyScheduleAction.NumberOfTabletsChanged("1"))
            awaitItem()
            vm.onAction(DailyScheduleAction.Add)
            val afterAdd = awaitItem()
            val entryId = afterAdd.tabletsTaken.first().id

            vm.onAction(DailyScheduleAction.RowSelected(entryId))

            assertThat(awaitItem().selectedId).isEqualTo(entryId)
        }
    }

    @Test
    fun `Delete removes selected row`() = runTest {
        val u = unit(id = 1L)
        val vm = createViewModel(units = listOf(u))

        vm.state.test {
            awaitItem()
            vm.onAction(DailyScheduleAction.MedicationSelected(1L))
            awaitItem()
            vm.onAction(DailyScheduleAction.NumberOfTabletsChanged("1"))
            awaitItem()
            vm.onAction(DailyScheduleAction.Add)
            val afterAdd = awaitItem()
            val entryId = afterAdd.tabletsTaken.first().id

            vm.onAction(DailyScheduleAction.RowSelected(entryId))
            awaitItem()

            vm.onAction(DailyScheduleAction.Delete)

            val cleared = awaitItem()
            assertThat(cleared.tabletsTaken).isEmpty()
            assertThat(cleared.selectedId).isNull()
        }
    }

    @Test
    fun `Delete is a no-op when nothing is selected`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()

            vm.onAction(DailyScheduleAction.Delete) // selectedId is null

            expectNoEvents()
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    @Test
    fun `Save persists items and sets saved flag`() = runTest {
        val u = unit(id = 1L)
        val dao = FakeDailyScheduleDao()
        val vm = createViewModel(units = listOf(u), dailyScheduleDao = dao)

        vm.saved.test {
            assertThat(awaitItem()).isEqualTo(false)

            vm.state.test {
                awaitItem()
                vm.onAction(DailyScheduleAction.MedicationSelected(1L))
                awaitItem()
                vm.onAction(DailyScheduleAction.NumberOfTabletsChanged("2"))
                awaitItem()
                vm.onAction(DailyScheduleAction.Add)
                awaitItem()

                vm.onAction(DailyScheduleAction.Save)
            }

            assertThat(awaitItem()).isEqualTo(true)
        }

        assertThat(dao.savedEntities.size).isEqualTo(1)
        assertThat(dao.savedEntities.first().dispensableUnitId).isEqualTo(1L)
        assertThat(dao.savedEntities.first().quantity).isEqualTo(2.0)
    }

    @Test
    fun `unit removed from repository clears selectedUnitId`() = runTest {
        val u = unit(id = 1L)
        val duDao = FakeDispensableUnitDao(initialUnits = listOf(u))
        val vm = createViewModel(duDao = duDao)

        vm.state.test {
            awaitItem()
            vm.onAction(DailyScheduleAction.MedicationSelected(1L))
            awaitItem() // selectedUnitId = 1

            duDao.setUnits(emptyList())

            assertThat(awaitItem().selectedUnitId).isNull()
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
        units: List<DispensableUnitDetails> = emptyList(),
        savedSchedule: List<DailyScheduleDetails> = emptyList(),
        duDao: FakeDispensableUnitDao = FakeDispensableUnitDao(initialUnits = units),
        dailyScheduleDao: FakeDailyScheduleDao = FakeDailyScheduleDao(initialSchedule = savedSchedule),
    ) = DailyScheduleViewModel(
        dispensableUnitRepository = DispensableUnitRepository(duDao),
        dailyScheduleRepository = DailyScheduleRepository(dailyScheduleDao),
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

    private class FakeDailyScheduleDao(
        initialSchedule: List<DailyScheduleDetails> = emptyList(),
    ) : DailyScheduleDao {
        private val initialList = initialSchedule
        val savedEntities = mutableListOf<DailyScheduleEntity>()

        override suspend fun getAllWithMedication(): List<DailyScheduleDetails> = initialList
        override fun getAllWithMedicationFlow(): Flow<List<DailyScheduleDetails>> = flowOf(initialList)

        override suspend fun insertAll(entities: List<DailyScheduleEntity>) {
            savedEntities.addAll(entities)
        }

        override suspend fun clear() {
            savedEntities.clear()
        }
    }
}

