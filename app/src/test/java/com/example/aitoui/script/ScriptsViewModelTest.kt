package com.example.aitoui.script

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.example.aitoui.data.DispensationDao
import com.example.aitoui.data.DispensationEntity
import com.example.aitoui.data.DispensationRepository
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
class ScriptsViewModelTest {

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
    fun `scripts from repository populate state sorted by issue date`() = runTest {
        val older = script(id = 1, brandName = "Panadol", dateOfIssue = 100L)
        val newer = script(id = 2, brandName = "Amoxil", dateOfIssue = 200L)
        val vm = createViewModel(initialDetails = listOf(newer, older))

        vm.state.test {
            val state = awaitItem()
            assertThat(state.scripts).containsExactly(older, newer)
        }
    }

    @Test
    fun `dispensed tapped on available script sets pendingDispenseScriptId`() = runTest {
        val s = script(id = 1, dispensed = 0, repeats = 5)
        val vm = createViewModel(initialDetails = listOf(s))

        vm.state.test {
            awaitItem() // initial

            vm.onAction(ScriptsAction.DispensedTapped(1L))

            val updated = awaitItem()
            assertThat(updated.pendingDispenseScriptId).isEqualTo(1L)
            assertThat(updated.pendingDispenseScript).isEqualTo(s)
            assertThat(updated.maxedOutScriptId).isNull()
        }
    }

    @Test
    fun `dispensed tapped on maxed out script sets maxedOutScriptId`() = runTest {
        // dispensed > repeats → maxed out
        val s = script(id = 1, dispensed = 6, repeats = 5)
        val vm = createViewModel(initialDetails = listOf(s))

        vm.state.test {
            awaitItem()

            vm.onAction(ScriptsAction.DispensedTapped(1L))

            val updated = awaitItem()
            assertThat(updated.maxedOutScriptId).isEqualTo(1L)
            assertThat(updated.pendingDispenseScriptId).isNull()
        }
    }

    @Test
    fun `cancel dispense clears pendingDispenseScriptId`() = runTest {
        val s = script(id = 1, dispensed = 0, repeats = 5)
        val vm = createViewModel(initialDetails = listOf(s))

        vm.state.test {
            awaitItem()
            vm.onAction(ScriptsAction.DispensedTapped(1L))
            awaitItem() // pendingDispenseScriptId set

            vm.onAction(ScriptsAction.CancelDispense)

            val cleared = awaitItem()
            assertThat(cleared.pendingDispenseScriptId).isNull()
        }
    }

    @Test
    fun `confirm dispense records dispensation and adds tablets to in hand`() = runTest {
        val s = script(id = 1, dispensableUnitId = 10L, tabletsPerUnit = "30", dispensed = 0, repeats = 5)
        val dispensationDao = FakeDispensationDao()
        val inHandDao = FakeInHandDao()
        val vm = createViewModel(
            initialDetails = listOf(s),
            dispensationDao = dispensationDao,
            inHandDao = inHandDao,
        )

        vm.state.test {
            awaitItem()
            vm.onAction(ScriptsAction.DispensedTapped(1L))
            awaitItem()

            vm.onAction(ScriptsAction.ConfirmDispense)

            val cleared = awaitItem()
            assertThat(cleared.pendingDispenseScriptId).isNull()
        }

        assertThat(dispensationDao.entities).containsExactly(
            DispensationEntity(id = 1, scriptId = 1L, dispensableUnitId = 10L, number = 1, dispensedAtMillis = dispensationDao.entities.first().dispensedAtMillis),
        )
        assertThat(inHandDao.addedTablets).containsExactly(10L to 30.0)
    }

    @Test
    fun `confirm dispense on final fill deletes the script`() = runTest {
        // dispensed == repeats → isFinalFill
        val s = script(id = 1, dispensed = 5, repeats = 5)
        val scriptDao = FakeScriptDao(initialDetails = listOf(s))
        val vm = createViewModel(initialDetails = listOf(s), scriptDao = scriptDao)

        vm.state.test {
            awaitItem()
            vm.onAction(ScriptsAction.DispensedTapped(1L))
            awaitItem()

            vm.onAction(ScriptsAction.ConfirmDispense)

            // The dao removes the script → the flow emits an empty list → state reflects it
            awaitItem() // pendingDispenseScriptId cleared (might be combined with delete emission)
        }

        assertThat(scriptDao.allDetails).isEmpty()
    }

    @Test
    fun `dismiss maxed out clears maxedOutScriptId`() = runTest {
        val s = script(id = 1, dispensed = 6, repeats = 5)
        val vm = createViewModel(initialDetails = listOf(s))

        vm.state.test {
            awaitItem()
            vm.onAction(ScriptsAction.DispensedTapped(1L))
            awaitItem() // maxedOutScriptId set

            vm.onAction(ScriptsAction.DismissMaxedOut)

            val cleared = awaitItem()
            assertThat(cleared.maxedOutScriptId).isNull()
        }
    }

    @Test
    fun `delete tapped sets pendingDeleteScriptId`() = runTest {
        val s = script(id = 1)
        val vm = createViewModel(initialDetails = listOf(s))

        vm.state.test {
            awaitItem()

            vm.onAction(ScriptsAction.DeleteTapped(1L))

            val updated = awaitItem()
            assertThat(updated.pendingDeleteScriptId).isEqualTo(1L)
            assertThat(updated.pendingDeleteScript).isEqualTo(s)
        }
    }

    @Test
    fun `cancel delete clears pendingDeleteScriptId`() = runTest {
        val s = script(id = 1)
        val vm = createViewModel(initialDetails = listOf(s))

        vm.state.test {
            awaitItem()
            vm.onAction(ScriptsAction.DeleteTapped(1L))
            awaitItem()

            vm.onAction(ScriptsAction.CancelDelete)

            val cleared = awaitItem()
            assertThat(cleared.pendingDeleteScriptId).isNull()
        }
    }

    @Test
    fun `confirm delete removes script and clears pendingDeleteScriptId`() = runTest {
        val s = script(id = 1)
        val scriptDao = FakeScriptDao(initialDetails = listOf(s))
        val vm = createViewModel(initialDetails = listOf(s), scriptDao = scriptDao)

        vm.state.test {
            awaitItem()
            vm.onAction(ScriptsAction.DeleteTapped(1L))
            awaitItem()

            vm.onAction(ScriptsAction.ConfirmDelete)

            awaitItem() // state after delete
        }

        assertThat(scriptDao.allDetails).isEmpty()
    }

    @Test
    fun `sort order changed re-sorts scripts by brand name`() = runTest {
        val aardvark = script(id = 1, brandName = "Aardvark", dateOfIssue = 200L)
        val zebra = script(id = 2, brandName = "Zebra", dateOfIssue = 100L)
        val vm = createViewModel(initialDetails = listOf(aardvark, zebra))

        vm.state.test {
            val initial = awaitItem()
            // Default sort: by issue date → zebra (100) first, then aardvark (200)
            assertThat(initial.scripts.map { it.brandName }).containsExactly("Zebra", "Aardvark")

            vm.onAction(ScriptsAction.SortOrderChanged(SortOrder.BrandName))

            val sorted = awaitItem()
            assertThat(sorted.scripts.map { it.brandName }).containsExactly("Aardvark", "Zebra")
            assertThat(sorted.sortOrder).isEqualTo(SortOrder.BrandName)
        }
    }

    @Test
    fun `pending dispense is cleared when script is removed from repository`() = runTest {
        val s = script(id = 1, dispensed = 0, repeats = 5)
        val scriptDao = FakeScriptDao(initialDetails = listOf(s))
        val vm = createViewModel(initialDetails = listOf(s), scriptDao = scriptDao)

        vm.state.test {
            awaitItem()
            vm.onAction(ScriptsAction.DispensedTapped(1L))
            awaitItem() // pendingDispenseScriptId set

            // Simulate script disappearing from the repository (e.g. deleted elsewhere)
            scriptDao.setDetails(emptyList())

            val updated = awaitItem()
            assertThat(updated.pendingDispenseScriptId).isNull()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun script(
        id: Long = 1L,
        dispensableUnitId: Long = 1L,
        brandName: String = "Panadol",
        activeIngredient: String = "Paracetamol",
        dosePerTablet: String = "500",
        tabletsPerUnit: String = "24",
        dispensed: Int = 0,
        repeats: Int = 5,
        dateOfIssue: Long = 0L,
    ) = ScriptDetails(
        scriptId = id,
        dispensableUnitId = dispensableUnitId,
        medicationId = 1L,
        brandName = brandName,
        activeIngredient = activeIngredient,
        dosePerTablet = dosePerTablet,
        tabletsPerUnit = tabletsPerUnit,
        dispensed = dispensed,
        repeats = repeats,
        dateOfIssue = dateOfIssue,
    )

    private fun createViewModel(
        initialDetails: List<ScriptDetails> = emptyList(),
        scriptDao: FakeScriptDao = FakeScriptDao(initialDetails),
        dispensationDao: FakeDispensationDao = FakeDispensationDao(),
        inHandDao: FakeInHandDao = FakeInHandDao(),
    ) = ScriptsViewModel(
        scriptRepository = ScriptRepository(scriptDao),
        dispensationRepository = DispensationRepository(dispensationDao),
        inHandRepository = InHandRepository(inHandDao),
    )

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class FakeScriptDao(
        initialDetails: List<ScriptDetails> = emptyList(),
    ) : ScriptDao {
        private val state = MutableStateFlow(initialDetails)

        val allDetails: List<ScriptDetails> get() = state.value

        fun setDetails(details: List<ScriptDetails>) {
            state.value = details
        }

        override fun getAllWithDetails(): Flow<List<ScriptDetails>> = state

        override suspend fun deleteById(id: Long) {
            state.value = state.value.filterNot { it.scriptId == id }
        }

        override suspend fun insert(entity: ScriptEntity): Long = throw UnsupportedOperationException()
        override suspend fun update(entity: ScriptEntity) = throw UnsupportedOperationException()
        override suspend fun delete(entity: ScriptEntity) = throw UnsupportedOperationException()
        override fun getAll(): Flow<List<ScriptEntity>> = flowOf(emptyList())
        override suspend fun getById(id: Long): ScriptEntity? = throw UnsupportedOperationException()
        override suspend fun countMatchingSerials(serials: List<String>): Int = throw UnsupportedOperationException()
    }

    private class FakeDispensationDao : DispensationDao {
        private var nextId = 1L
        private val state = MutableStateFlow<List<DispensationEntity>>(emptyList())

        val entities: List<DispensationEntity> get() = state.value

        override suspend fun insert(entity: DispensationEntity): Long {
            val id = if (entity.id == 0L) nextId++ else entity.id
            state.value = state.value + entity.copy(id = id)
            return id
        }

        override fun getAll(): Flow<List<DispensationEntity>> = state
    }

    private class FakeInHandDao : InHandDao {        /** Records every (dispensableUnitId, quantity) pair passed to [addTablets]. */
        val addedTablets = mutableListOf<Pair<Long, Double>>()

        override suspend fun addTablets(dispensableUnitId: Long, quantity: Double) {
            addedTablets.add(dispensableUnitId to quantity)
        }

        override suspend fun getAllWithMedication(): List<InHandDetails> = emptyList()
        override fun getAllWithMedicationFlow(): Flow<List<InHandDetails>> = flowOf(emptyList())
        override suspend fun insert(entity: InHandEntity): Long = 0L
        override suspend fun insertAll(entities: List<InHandEntity>) {}
        override suspend fun clear() {}
        override suspend fun incrementQuantity(dispensableUnitId: Long, delta: Double): Int = 0
        override suspend fun setDate(entity: InHandDateEntity) {}
        override fun getDateFlow(): Flow<Long?> = flowOf(null)
    }
}

