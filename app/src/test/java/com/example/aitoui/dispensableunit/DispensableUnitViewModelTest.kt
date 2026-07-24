package com.example.aitoui.dispensableunit

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.aitoui.data.DispensableUnit
import com.example.aitoui.data.DispensableUnitDao
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.DispensableUnitEntity
import com.example.aitoui.data.DispensableUnitRepository
import com.example.aitoui.data.DoseUnit
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
class DispensableUnitViewModelTest {

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
    fun `medications from repository populate state`() = runTest {
        val med = Medication(id = 1L, brandName = "Panadol", activeIngredient = "Paracetamol")
        val vm = createViewModel(medications = listOf(med))

        vm.state.test {
            val state = awaitItem()
            assertThat(state.medications).containsExactly(med)
        }
    }

    @Test
    fun `selecting a medication sets selectedMedicationId`() = runTest {
        val med = Medication(id = 1L, brandName = "Panadol", activeIngredient = "Paracetamol")
        val vm = createViewModel(medications = listOf(med))

        vm.state.test {
            awaitItem() // initial

            vm.onAction(DispensableUnitAction.MedicationSelected(1L))

            val updated = awaitItem()
            assertThat(updated.selectedMedicationId).isEqualTo(1L)
            assertThat(updated.selectedMedicationName).isEqualTo("Panadol")
        }
    }

    @Test
    fun `dose per tablet changed filters to decimal input`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()

            vm.onAction(DispensableUnitAction.DosePerTabletChanged("500"))

            val updated = awaitItem()
            assertThat(updated.dosePerTablet).isEqualTo("500")
        }
    }

    @Test
    fun `tablets per unit changed filters to digits only`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()

            vm.onAction(DispensableUnitAction.TabletsPerUnitChanged("24"))

            val updated = awaitItem()
            assertThat(updated.tabletsPerUnit).isEqualTo("24")
        }
    }

    @Test
    fun `dose unit selected changes selectedDoseUnit`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()

            vm.onAction(DispensableUnitAction.DoseUnitSelected(DoseUnit.GRAMS))

            val updated = awaitItem()
            assertThat(updated.selectedDoseUnit).isEqualTo(DoseUnit.GRAMS)
        }
    }

    @Test
    fun `toggle dose unit menu flips doseUnitMenuExpanded`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            val initial = awaitItem()
            assertThat(initial.doseUnitMenuExpanded).isEqualTo(false)

            vm.onAction(DispensableUnitAction.ToggleDoseUnitMenu)

            assertThat(awaitItem().doseUnitMenuExpanded).isEqualTo(true)

            vm.onAction(DispensableUnitAction.ToggleDoseUnitMenu)

            assertThat(awaitItem().doseUnitMenuExpanded).isEqualTo(false)
        }
    }

    @Test
    fun `dismiss dose unit menu sets doseUnitMenuExpanded to false`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()
            vm.onAction(DispensableUnitAction.ToggleDoseUnitMenu)
            awaitItem() // expanded = true

            vm.onAction(DispensableUnitAction.DismissDoseUnitMenu)

            assertThat(awaitItem().doseUnitMenuExpanded).isEqualTo(false)
        }
    }

    @Test
    fun `save inserts dispensable unit and sets saved flag`() = runTest {
        val med = Medication(id = 1L, brandName = "Panadol", activeIngredient = "Paracetamol")
        val dao = FakeDispensableUnitDao()
        val vm = createViewModel(medications = listOf(med), dispensableUnitDao = dao)

        vm.saved.test {
            assertThat(awaitItem()).isEqualTo(false)

            vm.state.test {
                awaitItem()
                vm.onAction(DispensableUnitAction.MedicationSelected(1L))
                awaitItem()
                vm.onAction(DispensableUnitAction.DosePerTabletChanged("500"))
                awaitItem()
                vm.onAction(DispensableUnitAction.TabletsPerUnitChanged("24"))
                awaitItem()

                vm.onAction(DispensableUnitAction.Save)
                awaitItem() // form cleared
            }

            assertThat(awaitItem()).isEqualTo(true) // saved flag flipped
        }

        assertThat(dao.inserted.size).isEqualTo(1)
        assertThat(dao.inserted.first().medicationId).isEqualTo(1L)
        assertThat(dao.inserted.first().dosePerTablet).isEqualTo("500")
        assertThat(dao.inserted.first().tabletsPerUnit).isEqualTo("24")
    }

    @Test
    fun `save is a no-op when canSave is false`() = runTest {
        val vm = createViewModel()

        vm.saved.test {
            assertThat(awaitItem()).isEqualTo(false)

            vm.onAction(DispensableUnitAction.Save) // no medication selected → canSave = false

            // No new emissions on saved — it stays false.
            expectNoEvents()
        }
    }

    @Test
    fun `save clears the form fields but keeps medications list`() = runTest {
        val med = Medication(id = 1L, brandName = "Panadol", activeIngredient = "Paracetamol")
        val vm = createViewModel(medications = listOf(med))

        vm.state.test {
            awaitItem()
            vm.onAction(DispensableUnitAction.MedicationSelected(1L))
            awaitItem()
            vm.onAction(DispensableUnitAction.DosePerTabletChanged("500"))
            awaitItem()
            vm.onAction(DispensableUnitAction.TabletsPerUnitChanged("24"))
            awaitItem()

            vm.onAction(DispensableUnitAction.Save)

            val cleared = awaitItem()
            assertThat(cleared.selectedMedicationId).isNull()
            assertThat(cleared.dosePerTablet).isEqualTo("")
            assertThat(cleared.tabletsPerUnit).isEqualTo("")
            assertThat(cleared.medications).containsExactly(med) // medications preserved
        }
    }

    @Test
    fun `medication removed from repository clears selectedMedicationId`() = runTest {
        val med = Medication(id = 1L, brandName = "Panadol", activeIngredient = "Paracetamol")
        val medDao = FakeMedicationDao(initialMedications = listOf(med))
        val vm = createViewModel(medicationDao = medDao)

        vm.state.test {
            awaitItem()
            vm.onAction(DispensableUnitAction.MedicationSelected(1L))
            awaitItem() // selectedMedicationId = 1

            // Simulate medication being deleted elsewhere
            medDao.setMedications(emptyList())

            val updated = awaitItem()
            assertThat(updated.selectedMedicationId).isNull()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun createViewModel(
        medications: List<Medication> = emptyList(),
        dispensableUnitDao: FakeDispensableUnitDao = FakeDispensableUnitDao(),
        medicationDao: FakeMedicationDao = FakeMedicationDao(initialMedications = medications),
    ) = DispensableUnitViewModel(
        formatRepository = DispensableUnitRepository(dispensableUnitDao),
        medicationRepository = MedicationRepository(medicationDao),
    )

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class FakeMedicationDao(
        initialMedications: List<Medication> = emptyList(),
    ) : MedicationDao {
        private val state = MutableStateFlow(initialMedications.map { it.toEntity() })

        fun setMedications(meds: List<Medication>) {
            state.value = meds.map { it.toEntity() }
        }

        override fun getAll(): Flow<List<MedicationEntity>> = state
        override suspend fun insert(entity: MedicationEntity): Long = throw UnsupportedOperationException()
        override suspend fun count(): Int = state.value.size
        override suspend fun getAllNow(): List<MedicationEntity> = state.value
        override suspend fun updateNames(id: Long, brandName: String, activeIngredient: String) = throw UnsupportedOperationException()
        override suspend fun deleteById(id: Long) = throw UnsupportedOperationException()
    }

    private class FakeDispensableUnitDao : DispensableUnitDao {
        private var nextId = 1L
        val inserted = mutableListOf<DispensableUnitEntity>()

        override fun getAllWithMedication(): Flow<List<DispensableUnitDetails>> = flowOf(emptyList())

        override suspend fun insert(entity: DispensableUnitEntity): Long {
            val id = nextId++
            inserted.add(entity.copy(id = id))
            return id
        }

        override suspend fun count(): Int = inserted.size
        override suspend fun setImagePath(id: Long, imagePath: String?) {}
        override suspend fun deleteById(id: Long) {
            inserted.removeAll { it.id == id }
        }
    }
}

