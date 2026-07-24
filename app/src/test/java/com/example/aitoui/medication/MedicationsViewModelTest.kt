package com.example.aitoui.medication

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.example.aitoui.data.Medication
import com.example.aitoui.data.MedicationDao
import com.example.aitoui.data.MedicationEntity
import com.example.aitoui.data.MedicationRepository
import com.example.aitoui.data.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationsViewModelTest {

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
    fun `medications from repository populate state sorted alphabetically by brand name`() = runTest {
        val zebra = medication(id = 1L, brandName = "Zebra")
        val aardvark = medication(id = 2L, brandName = "Aardvark")
        val vm = createViewModel(initialMedications = listOf(zebra, aardvark))

        vm.state.test {
            val state = awaitItem()
            assertThat(state.medications.map { it.brandName })
                .containsExactly("Aardvark", "Zebra")
        }
    }

    // ── Delete actions ───────────────────────────────────────────────────────

    @Test
    fun `delete tapped sets pendingDeleteMedicationId`() = runTest {
        val med = medication(id = 1L)
        val vm = createViewModel(initialMedications = listOf(med))

        vm.state.test {
            awaitItem()

            vm.onAction(MedicationsAction.DeleteTapped(1L))

            val updated = awaitItem()
            assertThat(updated.pendingDeleteMedicationId).isEqualTo(1L)
            assertThat(updated.pendingDeleteMedication).isEqualTo(med)
        }
    }

    @Test
    fun `cancel delete clears pendingDeleteMedicationId`() = runTest {
        val med = medication(id = 1L)
        val vm = createViewModel(initialMedications = listOf(med))

        vm.state.test {
            awaitItem()
            vm.onAction(MedicationsAction.DeleteTapped(1L))
            awaitItem()

            vm.onAction(MedicationsAction.CancelDelete)

            assertThat(awaitItem().pendingDeleteMedicationId).isNull()
        }
    }

    @Test
    fun `confirm delete removes medication from repository`() = runTest {
        val med = medication(id = 1L)
        val dao = FakeMedicationDao(initialMedications = listOf(med))
        val vm = createViewModel(dao = dao)

        vm.state.test {
            awaitItem()
            vm.onAction(MedicationsAction.DeleteTapped(1L))
            awaitItem()

            vm.onAction(MedicationsAction.ConfirmDelete)

            awaitItem() // list emptied
        }

        assertThat(dao.currentMedications).isEmpty()
    }

    @Test
    fun `confirm delete clears pendingDeleteMedicationId`() = runTest {
        val med = medication(id = 1L)
        val vm = createViewModel(initialMedications = listOf(med))

        vm.state.test {
            awaitItem()
            vm.onAction(MedicationsAction.DeleteTapped(1L))
            awaitItem()

            vm.onAction(MedicationsAction.ConfirmDelete)

            assertThat(awaitItem().pendingDeleteMedicationId).isNull()
        }
    }

    @Test
    fun `pending delete is cleared when medication disappears from repository`() = runTest {
        val med = medication(id = 1L)
        val dao = FakeMedicationDao(initialMedications = listOf(med))
        val vm = createViewModel(dao = dao)

        vm.state.test {
            awaitItem()
            vm.onAction(MedicationsAction.DeleteTapped(1L))
            awaitItem() // pendingDeleteMedicationId set

            // Simulate medication vanishing from repository (e.g. deleted from another screen)
            dao.setMedications(emptyList())

            assertThat(awaitItem().pendingDeleteMedicationId).isNull()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun medication(
        id: Long = 1L,
        brandName: String = "Panadol",
        activeIngredient: String = "Paracetamol",
    ) = Medication(id = id, brandName = brandName, activeIngredient = activeIngredient)

    private fun createViewModel(
        initialMedications: List<Medication> = emptyList(),
        dao: FakeMedicationDao = FakeMedicationDao(initialMedications),
    ) = MedicationsViewModel(repository = MedicationRepository(dao))

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class FakeMedicationDao(
        initialMedications: List<Medication> = emptyList(),
    ) : MedicationDao {
        private val state = MutableStateFlow(initialMedications.map { it.toEntity() })

        val currentMedications: List<MedicationEntity> get() = state.value

        fun setMedications(meds: List<Medication>) {
            state.value = meds.map { it.toEntity() }
        }

        override fun getAll(): Flow<List<MedicationEntity>> = state
        override suspend fun insert(entity: MedicationEntity): Long = throw UnsupportedOperationException()
        override suspend fun count(): Int = state.value.size
        override suspend fun getAllNow(): List<MedicationEntity> = state.value
        override suspend fun updateNames(id: Long, brandName: String, activeIngredient: String) = throw UnsupportedOperationException()
        override suspend fun deleteById(id: Long) {
            state.value = state.value.filterNot { it.id == id }
        }
    }
}

