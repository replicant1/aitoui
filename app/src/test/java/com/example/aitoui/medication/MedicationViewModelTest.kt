package com.example.aitoui.medication

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Field actions ────────────────────────────────────────────────────────

    @Test
    fun `brand name changed sets brandName`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem() // initial

            vm.onAction(MedicationAction.BrandNameChanged("Panadol"))

            val updated = awaitItem()
            assertThat(updated.brandName).isEqualTo("Panadol")
        }
    }

    @Test
    fun `active ingredient changed sets activeIngredient`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()

            vm.onAction(MedicationAction.ActiveIngredientChanged("Paracetamol"))

            val updated = awaitItem()
            assertThat(updated.activeIngredient).isEqualTo("Paracetamol")
        }
    }

    @Test
    fun `requires prescription changed sets requiresPrescription`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            val initial = awaitItem()
            assertThat(initial.requiresPrescription).isEqualTo(true) // default

            vm.onAction(MedicationAction.RequiresPrescriptionChanged(false))

            assertThat(awaitItem().requiresPrescription).isEqualTo(false)
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    @Test
    fun `save inserts medication and sets saved flag`() = runTest {
        val dao = FakeMedicationDao()
        val vm = createViewModel(dao = dao)

        vm.saved.test {
            assertThat(awaitItem()).isEqualTo(false)

            vm.state.test {
                awaitItem()
                vm.onAction(MedicationAction.BrandNameChanged("Panadol"))
                awaitItem()
                vm.onAction(MedicationAction.ActiveIngredientChanged("Paracetamol"))
                awaitItem()

                vm.onAction(MedicationAction.Save)
                awaitItem() // form cleared
            }

            assertThat(awaitItem()).isEqualTo(true) // saved flag flipped
        }

        assertThat(dao.inserted.size).isEqualTo(1)
        assertThat(dao.inserted.first().brandName).isEqualTo("Panadol")
        assertThat(dao.inserted.first().activeIngredient).isEqualTo("Paracetamol")
    }

    @Test
    fun `save is a no-op when canSave is false`() = runTest {
        val vm = createViewModel()

        vm.saved.test {
            assertThat(awaitItem()).isEqualTo(false)

            vm.onAction(MedicationAction.Save) // blank form → canSave = false

            expectNoEvents()
        }
    }

    @Test
    fun `save clears the form`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()
            vm.onAction(MedicationAction.BrandNameChanged("Panadol"))
            awaitItem()
            vm.onAction(MedicationAction.ActiveIngredientChanged("Paracetamol"))
            awaitItem()
            vm.onAction(MedicationAction.RequiresPrescriptionChanged(false))
            awaitItem()

            vm.onAction(MedicationAction.Save)

            val cleared = awaitItem()
            assertThat(cleared.brandName).isEqualTo("")
            assertThat(cleared.activeIngredient).isEqualTo("")
            assertThat(cleared.requiresPrescription).isEqualTo(true)
        }
    }

    @Test
    fun `consumeSaved resets saved to false`() = runTest {
        val vm = createViewModel()

        vm.saved.test {
            assertThat(awaitItem()).isEqualTo(false)

            vm.state.test {
                awaitItem()
                vm.onAction(MedicationAction.BrandNameChanged("Panadol"))
                awaitItem()
                vm.onAction(MedicationAction.ActiveIngredientChanged("Paracetamol"))
                awaitItem()
                vm.onAction(MedicationAction.Save)
                awaitItem()
            }

            assertThat(awaitItem()).isEqualTo(true)

            vm.consumeSaved()
            assertThat(awaitItem()).isEqualTo(false)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun createViewModel(dao: FakeMedicationDao = FakeMedicationDao()) =
        MedicationViewModel(repository = MedicationRepository(dao))

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class FakeMedicationDao : MedicationDao {
        private var nextId = 1L
        val inserted = mutableListOf<MedicationEntity>()

        override fun getAll(): Flow<List<MedicationEntity>> = flowOf(emptyList())
        override suspend fun insert(entity: MedicationEntity): Long {
            val id = nextId++
            inserted.add(entity.copy(id = id))
            return id
        }
        override suspend fun count(): Int = inserted.size
        override suspend fun getAllNow(): List<MedicationEntity> = inserted
        override suspend fun updateNames(id: Long, brandName: String, activeIngredient: String) {}
        override suspend fun deleteById(id: Long) {
            inserted.removeAll { it.id == id }
        }
    }
}

