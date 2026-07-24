package com.example.aitoui.settings

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.example.aitoui.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state reflects repository currentWarningWindowDays`() = runTest {
        val vm = createViewModel(currentDays = 21)

        vm.state.test {
            assertThat(awaitItem().warningWindowDays).isEqualTo("21")
        }
    }

    // ── WarningWindowChanged ──────────────────────────────────────────────────

    @Test
    fun `warningWindowChanged updates state with provided digits`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem() // initial

            vm.onAction(SettingsAction.WarningWindowChanged("28"))

            assertThat(awaitItem().warningWindowDays).isEqualTo("28")
        }
    }

    @Test
    fun `warningWindowChanged strips non-digit characters`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()

            vm.onAction(SettingsAction.WarningWindowChanged("1a2b3"))

            assertThat(awaitItem().warningWindowDays).isEqualTo("123")
        }
    }

    @Test
    fun `warningWindowChanged limits input to four digits`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()

            vm.onAction(SettingsAction.WarningWindowChanged("12345"))

            assertThat(awaitItem().warningWindowDays).isEqualTo("1234")
        }
    }

    @Test
    fun `warningWindowChanged allows blank input for mid-edit state`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            awaitItem()

            vm.onAction(SettingsAction.WarningWindowChanged(""))

            assertThat(awaitItem().warningWindowDays).isEqualTo("")
        }
    }

    @Test
    fun `warningWindowChanged persists valid positive integer value`() = runTest {
        val repo = buildFakeRepo()
        val vm = SettingsViewModel(repo)

        vm.state.test {
            awaitItem()
            vm.onAction(SettingsAction.WarningWindowChanged("30"))
            awaitItem()
        }

        verify(repo).setWarningWindowDays(30)
    }

    @Test
    fun `warningWindowChanged does not persist zero value`() = runTest {
        val repo = buildFakeRepo()
        val vm = SettingsViewModel(repo)

        vm.state.test {
            awaitItem()
            vm.onAction(SettingsAction.WarningWindowChanged("0"))
            awaitItem()
        }

        // setWarningWindowDays should never be called with 0
        verify(repo, org.mockito.Mockito.never()).setWarningWindowDays(0)
    }

    @Test
    fun `warningWindowChanged does not persist blank value`() = runTest {
        val repo = buildFakeRepo()
        val vm = SettingsViewModel(repo)

        vm.state.test {
            awaitItem()
            vm.onAction(SettingsAction.WarningWindowChanged(""))
            awaitItem()
        }

        verify(repo, org.mockito.Mockito.never()).setWarningWindowDays(org.mockito.ArgumentMatchers.anyInt())
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildFakeRepo(currentDays: Int = 14): SettingsRepository {
        val repo = mock(SettingsRepository::class.java)
        whenever(repo.currentWarningWindowDays()).thenReturn(currentDays)
        return repo
    }

    private fun createViewModel(currentDays: Int = 14) =
        SettingsViewModel(buildFakeRepo(currentDays))
}

