package com.example.aitoui.runout

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.example.aitoui.data.DailyScheduleDao
import com.example.aitoui.data.DailyScheduleDetails
import com.example.aitoui.data.DailyScheduleEntity
import com.example.aitoui.data.DailyScheduleRepository
import com.example.aitoui.data.DispensableUnitDao
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.DispensableUnitEntity
import com.example.aitoui.data.DispensableUnitRepository
import com.example.aitoui.data.InHandDao
import com.example.aitoui.data.InHandDateEntity
import com.example.aitoui.data.InHandDetails
import com.example.aitoui.data.InHandEntity
import com.example.aitoui.data.InHandRepository
import com.example.aitoui.data.ScriptDao
import com.example.aitoui.data.ScriptDetails
import com.example.aitoui.data.ScriptEntity
import com.example.aitoui.data.ScriptRepository
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
class RunOutGraphViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

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
    fun `empty repositories produce empty graph data`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem() // initial RunOutGraphData() before upstream starts
            val computed = awaitItem() // computed from combine
            assertThat(computed.isEmpty).isTrue()
            assertThat(computed.series).isEmpty()
        }
    }

    @Test
    fun `unit with daily schedule and in-hand tablets produces a series`() = runTest {
        // Panadol: 10 tabs in hand at 2/day → one series, totalTablets = 10
        val vm = createViewModel(
            units = listOf(unit(formatId = 1L, medicationId = 1L, brandName = "Panadol")),
            inHand = listOf(inHand(medicationId = 1L, quantity = 10.0)),
            schedule = listOf(schedule(medicationId = 1L, quantity = 2.0)),
        )

        vm.state.test {
            awaitItem() // initial
            val computed = awaitItem()
            assertThat(computed.isEmpty).isFalse()
            assertThat(computed.series.map { it.label })
                .containsExactly("Panadol (500mg)")
        }
    }

    @Test
    fun `series are sorted alphabetically by label`() = runTest {
        val vm = createViewModel(
            units = listOf(
                unit(formatId = 1L, medicationId = 1L, brandName = "Zebra"),
                unit(formatId = 2L, medicationId = 2L, brandName = "Aardvark"),
            ),
            inHand = listOf(
                inHand(medicationId = 1L, quantity = 10.0),
                inHand(medicationId = 2L, quantity = 10.0),
            ),
            schedule = listOf(
                schedule(medicationId = 1L, quantity = 1.0),
                schedule(medicationId = 2L, quantity = 1.0),
            ),
        )

        vm.state.test {
            awaitItem() // initial
            val computed = awaitItem()
            assertThat(computed.series.map { it.label })
                .containsExactly("Aardvark (500mg)", "Zebra (500mg)")
        }
    }

    @Test
    fun `unit with no daily schedule rate is omitted from the graph`() = runTest {
        // No schedule for medication → rate = 0 → excluded from series
        val vm = createViewModel(
            units = listOf(unit(formatId = 1L, medicationId = 1L, brandName = "Panadol")),
            inHand = listOf(inHand(medicationId = 1L, quantity = 10.0)),
            schedule = emptyList(),
        )

        vm.state.test {
            awaitItem() // initial
            val computed = awaitItem()
            assertThat(computed.isEmpty).isTrue()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun unit(
        formatId: Long = 1L,
        medicationId: Long = 1L,
        brandName: String = "Panadol",
    ) = DispensableUnitDetails(
        formatId = formatId,
        medicationId = medicationId,
        brandName = brandName,
        activeIngredient = "Paracetamol",
        dosePerTablet = "500",
        tabletsPerUnit = "24",
        imagePath = null,
    )

    private fun inHand(medicationId: Long, quantity: Double) = InHandDetails(
        id = medicationId,
        dispensableUnitId = medicationId,
        medicationId = medicationId,
        brandName = "",
        quantity = quantity,
    )

    private fun schedule(medicationId: Long, quantity: Double) = DailyScheduleDetails(
        id = medicationId,
        dispensableUnitId = medicationId,
        medicationId = medicationId,
        brandName = "",
        quantity = quantity,
    )

    private fun createViewModel(
        units: List<DispensableUnitDetails> = emptyList(),
        inHand: List<InHandDetails> = emptyList(),
        schedule: List<DailyScheduleDetails> = emptyList(),
        scripts: List<ScriptDetails> = emptyList(),
        gatheredDate: Long? = null,
    ) = RunOutGraphViewModel(
        dispensableUnitRepository = DispensableUnitRepository(FakeDispensableUnitDao(units)),
        inHandRepository = InHandRepository(FakeInHandDao(inHand, gatheredDate)),
        dailyScheduleRepository = DailyScheduleRepository(FakeDailyScheduleDao(schedule)),
        scriptRepository = ScriptRepository(FakeScriptDao(scripts)),
    )

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class FakeDispensableUnitDao(
        initialUnits: List<DispensableUnitDetails> = emptyList(),
    ) : DispensableUnitDao {
        private val state = MutableStateFlow(initialUnits)
        override fun getAllWithMedication(): Flow<List<DispensableUnitDetails>> = state
        override suspend fun insert(entity: DispensableUnitEntity): Long = throw UnsupportedOperationException()
        override suspend fun count(): Int = state.value.size
        override suspend fun setImagePath(id: Long, imagePath: String?) {}
        override suspend fun deleteById(id: Long) {}
    }

    private class FakeInHandDao(
        initialInHand: List<InHandDetails> = emptyList(),
        initialGatheredDate: Long? = null,
    ) : InHandDao {
        private val inHandState = MutableStateFlow(initialInHand)
        private val dateState = MutableStateFlow(initialGatheredDate)

        override suspend fun getAllWithMedication(): List<InHandDetails> = inHandState.value
        override fun getAllWithMedicationFlow(): Flow<List<InHandDetails>> = inHandState
        override suspend fun insert(entity: InHandEntity): Long = throw UnsupportedOperationException()
        override suspend fun insertAll(entities: List<InHandEntity>) {}
        override suspend fun clear() {}
        override suspend fun incrementQuantity(dispensableUnitId: Long, delta: Double): Int =
            throw UnsupportedOperationException()
        override suspend fun setDate(entity: InHandDateEntity) {}
        override fun getDateFlow(): Flow<Long?> = dateState
    }

    private class FakeDailyScheduleDao(
        initialSchedule: List<DailyScheduleDetails> = emptyList(),
    ) : DailyScheduleDao {
        private val state = MutableStateFlow(initialSchedule)
        override suspend fun getAllWithMedication(): List<DailyScheduleDetails> = state.value
        override fun getAllWithMedicationFlow(): Flow<List<DailyScheduleDetails>> = state
        override suspend fun insertAll(entities: List<DailyScheduleEntity>) {}
        override suspend fun clear() {}
    }

    private class FakeScriptDao(
        initialScripts: List<ScriptDetails> = emptyList(),
    ) : ScriptDao {
        private val state = MutableStateFlow(initialScripts)
        override suspend fun insert(entity: ScriptEntity): Long = throw UnsupportedOperationException()
        override suspend fun update(entity: ScriptEntity): Unit = throw UnsupportedOperationException()
        override suspend fun delete(entity: ScriptEntity): Unit = throw UnsupportedOperationException()
        override suspend fun deleteById(id: Long) {}
        override fun getAll(): Flow<List<ScriptEntity>> = flowOf(emptyList())
        override suspend fun getById(id: Long): ScriptEntity? = null
        override suspend fun countMatchingSerials(serials: List<String>): Int = 0
        override fun getAllWithDetails(): Flow<List<ScriptDetails>> = state
    }
}

