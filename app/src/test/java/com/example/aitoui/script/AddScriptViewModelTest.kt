package com.example.aitoui.script

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.example.aitoui.data.DispensableUnit
import com.example.aitoui.data.DispensableUnitDao
import com.example.aitoui.data.DispensableUnitDetails
import com.example.aitoui.data.DispensableUnitEntity
import com.example.aitoui.data.DispensableUnitRepository
import com.example.aitoui.data.Dispensation
import com.example.aitoui.data.DispensationDao
import com.example.aitoui.data.DispensationEntity
import com.example.aitoui.data.DispensationRepository
import com.example.aitoui.data.DoseUnit
import com.example.aitoui.data.Medication
import com.example.aitoui.data.MedicationDao
import com.example.aitoui.data.MedicationEntity
import com.example.aitoui.data.MedicationRepository
import com.example.aitoui.data.Script
import com.example.aitoui.data.ScriptDao
import com.example.aitoui.data.ScriptDetails
import com.example.aitoui.data.ScriptEntity
import com.example.aitoui.data.ScriptRepository
import com.example.aitoui.navigation.ScriptRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddScriptViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save with duplicate serial shows blocking dialog and does not start medication resolution`() = runTest {
        val fixture = createFixture(
            scripts = listOf(
                Script(
                    id = 9,
                    dispensableUnitId = 4,
                    serialNo = "RX-123",
                    dateOfIssue = 100L,
                    repeats = 1,
                    validToMillis = 200L,
                ),
            ),
            prefill = validRoute(serialNo = "RX-123"),
        )

        fixture.viewModel.state.test {
            val initial = awaitItem()
            assertThat(initial.canSave).isTrue()

            fixture.viewModel.onAction(AddScriptAction.Save)

            val updated = awaitItem()
            assertThat(updated.duplicateSerial).isTrue()
            assertThat(updated.medicationStep).isNull()
        }
    }

    @Test
    fun `save emits medication resolution candidates for matching medications`() = runTest {
        val fixture = createFixture(
            medications = listOf(
                Medication(id = 1, brandName = "Tensig", activeIngredient = "Atenolol"),
            ),
            prefill = validRoute(serialNo = ""),
        )

        fixture.viewModel.state.test {
            val initial = awaitItem()
            assertThat(initial.canSave).isTrue()

            fixture.viewModel.onAction(AddScriptAction.Save)

            val resolved = awaitItem()
            assertThat(resolved.medicationStep?.candidates?.map { it.id }).isEqualTo(listOf(1L))
            assertThat(resolved.medicationStep?.blocked).isTrue()
        }
    }

    @Test
    fun `picking an existing medication opens dispensable unit resolution for that medication`() = runTest {
        val fixture = createFixture(
            medications = listOf(
                Medication(id = 1, brandName = "Tensig", activeIngredient = "Atenolol"),
            ),
            dispensableUnits = listOf(
                DispensableUnit(
                    id = 10,
                    medicationId = 1,
                    dosePerTablet = "50",
                    tabletsPerUnit = "60",
                    doseUnit = DoseUnit.MILLIGRAMS.storedAbbreviation,
                ),
            ),
            prefill = validRoute(serialNo = ""),
        )

        fixture.viewModel.state.test {
            awaitItem()

            fixture.viewModel.onAction(AddScriptAction.Save)
            val medStepState = awaitItem()
            assertThat(medStepState.medicationStep?.candidates?.map { it.id }).isEqualTo(listOf(1L))

            fixture.viewModel.onAction(AddScriptAction.PickMedication(1))

            val clearedMedStep = awaitItem()
            assertThat(clearedMedStep.medicationStep).isNull()

            val duStepState = awaitItem()
            assertThat(duStepState.dispensableUnitStep?.resolvedMedication)
                .isEqualTo(ResolvedMedication.Existing(1))
            assertThat(duStepState.dispensableUnitStep?.candidates?.map { it.formatId }).isEqualTo(listOf(10L))
            assertThat(duStepState.dispensableUnitStep?.blocked).isTrue()
        }
    }

    @Test
    fun `creating a new medication and dispensable unit persists script and prior dispensation then marks saved`() = runTest {
        val fixture = createFixture(
            prefill = validRoute(
                brandName = "Ventolin",
                activeIngredient = "Salbutamol",
                serialNo = "  RX-NEW  ",
                serialNo2 = "  ERX-NEW  ",
                priorDispensed = 2,
            ),
        )

        fixture.viewModel.saved.test {
            assertThat(awaitItem()).isFalse()

            fixture.viewModel.state.test {
                awaitItem()

                fixture.viewModel.onAction(AddScriptAction.Save)
                val medStepState = awaitItem()
                assertThat(medStepState.medicationStep?.candidates).isEqualTo(emptyList<Medication>())
                assertThat(medStepState.medicationStep?.blocked).isFalse()

                fixture.viewModel.onAction(AddScriptAction.CreateMedication)
                val clearedMedStep = awaitItem()
                assertThat(clearedMedStep.medicationStep).isNull()

                val duStepState = awaitItem()
                assertThat(duStepState.dispensableUnitStep?.candidates)
                    .isEqualTo(emptyList<DispensableUnitDetails>())
                assertThat(duStepState.dispensableUnitStep?.blocked).isFalse()
                assertThat(duStepState.dispensableUnitStep?.resolvedMedication)
                    .isEqualTo(ResolvedMedication.New("Ventolin", "Salbutamol"))

                fixture.viewModel.onAction(AddScriptAction.CreateDispensableUnit)
                val clearedDuStep = awaitItem()
                assertThat(clearedDuStep.dispensableUnitStep).isNull()

                val reset = awaitItem()
                assertThat(reset).isEqualTo(AddScriptState())
            }

            assertThat(awaitItem()).isTrue()
        }

        val storedMedication = fixture.medicationDao.entities.single()
        assertThat(storedMedication.brandName).isEqualTo("Ventolin")
        assertThat(storedMedication.activeIngredient).isEqualTo("Salbutamol")

        val storedUnit = fixture.dispensableUnitDao.entities.single()
        assertThat(storedUnit.medicationId).isEqualTo(storedMedication.id)
        assertThat(storedUnit.dosePerTablet).isEqualTo("50")
        assertThat(storedUnit.tabletsPerUnit).isEqualTo("60")
        assertThat(storedUnit.doseUnit).isEqualTo(DoseUnit.MILLIGRAMS.storedAbbreviation)

        val storedScript = fixture.scriptDao.entities.single()
        assertThat(storedScript.dispensableUnitId).isEqualTo(storedUnit.id)
        assertThat(storedScript.serialNo).isEqualTo("RX-NEW")
        assertThat(storedScript.serialNo2).isEqualTo("ERX-NEW")
        assertThat(storedScript.repeats).isEqualTo(5)
        assertThat(storedScript.instructions).isEqualTo("Take one tablet twice a day")

        val dispensation = fixture.dispensationDao.entities.single()
        assertThat(dispensation.scriptId).isEqualTo(storedScript.id)
        assertThat(dispensation.dispensableUnitId).isEqualTo(storedUnit.id)
        assertThat(dispensation.number).isEqualTo(2)
        assertThat(dispensation.dispensedAtMillis).isEqualTo(1_000L)
    }

    private fun createFixture(
        medications: List<Medication> = emptyList(),
        dispensableUnits: List<DispensableUnit> = emptyList(),
        scripts: List<Script> = emptyList(),
        prefill: ScriptRoute = validRoute(),
    ): Fixture {
        val medicationDao = FakeMedicationDao(medications.map { it.toEntity() })
        val dispensableUnitDao = FakeDispensableUnitDao(
            initialEntities = dispensableUnits.map { it.toEntity() },
            medications = medicationDao.entitiesFlow,
        )
        val scriptDao = FakeScriptDao(scripts.map { it.toEntity() })
        val dispensationDao = FakeDispensationDao()
        return Fixture(
            medicationDao = medicationDao,
            dispensableUnitDao = dispensableUnitDao,
            scriptDao = scriptDao,
            dispensationDao = dispensationDao,
            viewModel = AddScriptViewModel(
                scriptRepository = ScriptRepository(scriptDao),
                medicationRepository = MedicationRepository(medicationDao),
                dispensableUnitRepository = DispensableUnitRepository(dispensableUnitDao),
                dispensationRepository = DispensationRepository(dispensationDao),
                prefill = prefill,
            ),
        )
    }

    private fun validRoute(
        brandName: String = "Tensig",
        activeIngredient: String = "Atenolol",
        serialNo: String = "RX-001",
        serialNo2: String = "",
        priorDispensed: Int = 0,
    ) = ScriptRoute(
        brandName = brandName,
        activeIngredient = activeIngredient,
        dosePerTablet = "50",
        tabletsPerUnit = "60",
        serialNo = serialNo,
        serialNo2 = serialNo2,
        dateOfIssueMillis = 1_000L,
        validToMillis = 2_000L,
        repeats = 5,
        instructions = "Take one tablet twice a day",
        priorDispensed = priorDispensed,
    )

    private data class Fixture(
        val medicationDao: FakeMedicationDao,
        val dispensableUnitDao: FakeDispensableUnitDao,
        val scriptDao: FakeScriptDao,
        val dispensationDao: FakeDispensationDao,
        val viewModel: AddScriptViewModel,
    )

    private class FakeMedicationDao(
        initialEntities: List<MedicationEntity> = emptyList(),
    ) : MedicationDao {
        private var nextId = (initialEntities.maxOfOrNull { it.id } ?: 0L) + 1L
        private val state = MutableStateFlow(initialEntities)

        val entities: List<MedicationEntity> get() = state.value
        val entitiesFlow: MutableStateFlow<List<MedicationEntity>> get() = state

        override suspend fun insert(entity: MedicationEntity): Long {
            val id = if (entity.id == 0L) nextId++ else entity.id
            state.value = state.value + entity.copy(id = id)
            return id
        }

        override suspend fun count(): Int = state.value.size

        override fun getAll(): Flow<List<MedicationEntity>> = state

        override suspend fun getAllNow(): List<MedicationEntity> = state.value

        override suspend fun updateNames(id: Long, brandName: String, activeIngredient: String) {
            state.value = state.value.map { entity ->
                if (entity.id == id) {
                    entity.copy(brandName = brandName, activeIngredient = activeIngredient)
                } else {
                    entity
                }
            }
        }

        override suspend fun deleteById(id: Long) {
            state.value = state.value.filterNot { it.id == id }
        }
    }

    private class FakeDispensableUnitDao(
        initialEntities: List<DispensableUnitEntity> = emptyList(),
        medications: Flow<List<MedicationEntity>>,
    ) : DispensableUnitDao {
        private var nextId = (initialEntities.maxOfOrNull { it.id } ?: 0L) + 1L
        private val state = MutableStateFlow(initialEntities)

        val entities: List<DispensableUnitEntity> get() = state.value

        private val formatsWithMedication = combine(state, medications) { units, meds ->
            units.mapNotNull { unit ->
                meds.find { it.id == unit.medicationId }?.let { medication ->
                    DispensableUnitDetails(
                        formatId = unit.id,
                        medicationId = unit.medicationId,
                        brandName = medication.brandName,
                        activeIngredient = medication.activeIngredient,
                        dosePerTablet = unit.dosePerTablet,
                        tabletsPerUnit = unit.tabletsPerUnit,
                        imagePath = unit.imagePath,
                        requiresPrescription = medication.requiresPrescription,
                        doseUnit = unit.doseUnit,
                    )
                }
            }
        }

        override suspend fun insert(entity: DispensableUnitEntity): Long {
            val id = if (entity.id == 0L) nextId++ else entity.id
            state.value = state.value + entity.copy(id = id)
            return id
        }

        override suspend fun count(): Int = state.value.size

        override fun getAllWithMedication(): Flow<List<DispensableUnitDetails>> = formatsWithMedication

        override suspend fun setImagePath(id: Long, imagePath: String?) {
            state.value = state.value.map { entity ->
                if (entity.id == id) entity.copy(imagePath = imagePath) else entity
            }
        }

        override suspend fun deleteById(id: Long) {
            state.value = state.value.filterNot { it.id == id }
        }
    }

    private class FakeScriptDao(
        initialEntities: List<ScriptEntity> = emptyList(),
    ) : ScriptDao {
        private var nextId = (initialEntities.maxOfOrNull { it.id } ?: 0L) + 1L
        private val state = MutableStateFlow(initialEntities)

        val entities: List<ScriptEntity> get() = state.value

        override suspend fun insert(entity: ScriptEntity): Long {
            val id = if (entity.id == 0L) nextId++ else entity.id
            state.value = state.value + entity.copy(id = id)
            return id
        }

        override suspend fun update(entity: ScriptEntity) {
            state.value = state.value.map { current -> if (current.id == entity.id) entity else current }
        }

        override suspend fun delete(entity: ScriptEntity) {
            state.value = state.value.filterNot { it.id == entity.id }
        }

        override suspend fun deleteById(id: Long) {
            state.value = state.value.filterNot { it.id == id }
        }

        override fun getAll(): Flow<List<ScriptEntity>> = state

        override suspend fun getById(id: Long): ScriptEntity? = state.value.firstOrNull { it.id == id }

        override suspend fun countMatchingSerials(serials: List<String>): Int =
            state.value.count { entity -> entity.serialNo in serials || entity.serialNo2 in serials }

        override fun getAllWithDetails(): Flow<List<ScriptDetails>> = flowOf(emptyList())
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

    private fun Medication.toEntity(): MedicationEntity = MedicationEntity(
        id = id,
        brandName = brandName,
        activeIngredient = activeIngredient,
        requiresPrescription = requiresPrescription,
    )

    private fun DispensableUnit.toEntity(): DispensableUnitEntity = DispensableUnitEntity(
        id = id,
        medicationId = medicationId,
        dosePerTablet = dosePerTablet,
        tabletsPerUnit = tabletsPerUnit,
        doseUnit = doseUnit,
        imagePath = imagePath,
    )

    private fun Script.toEntity(): ScriptEntity = ScriptEntity(
        id = id,
        dispensableUnitId = dispensableUnitId,
        serialNo = serialNo,
        serialNo2 = serialNo2,
        dateOfIssue = dateOfIssue,
        repeats = repeats,
        validToMillis = validToMillis,
        instructions = instructions,
    )

    private fun Dispensation.toEntity(): DispensationEntity = DispensationEntity(
        id = id,
        scriptId = scriptId,
        dispensableUnitId = dispensableUnitId,
        number = number,
        dispensedAtMillis = dispensedAtMillis,
    )
}


