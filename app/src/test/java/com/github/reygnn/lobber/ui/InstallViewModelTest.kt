package com.github.reygnn.lobber.ui

import app.cash.turbine.test
import com.github.reygnn.lobber.MainDispatcherRule
import com.github.reygnn.lobber.data.SettingsStore
import com.github.reygnn.lobber.ssh.AabEntry
import com.github.reygnn.lobber.ssh.LogLine
import com.github.reygnn.lobber.ssh.SshClient
import com.github.reygnn.lobber.ssh.SshConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Follows TESTING_CONVENTIONS: single dispatcher via the project's
 * MainDispatcherRule (in test root). No standalone TestScope or
 * StandardTestDispatcher created here.
 */
class InstallViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = mockk<SettingsStore>()
    private val client = mockk<SshClient>()
    private val config = SshConfig(
        host = "buildserver",
        username = "ci",
        workingDir = "/srv/builds",
        privateKeyPem = "PEM",
    )

    private lateinit var vm: InstallViewModel

    @Before
    fun setUp() {
        every { settings.config } returns flowOf(config)
        vm = InstallViewModel(settings = settings, createClient = { client })
    }

    @Test
    fun `loadAabs populates state with files`() = runTest(mainDispatcherRule.dispatcher) {
        val entries = listOf(
            AabEntry("app-release.aab", 1714680000),
            AabEntry("app-debug.aab", 1714600000),
        )
        coEvery { client.listAabs() } returns entries

        vm.state.test {
            awaitItem() // Initialer State
            vm.loadAabs()

            // Wir nutzen expectMostRecentItem, falls loading=true zu schnell geht
            val final = expectMostRecentItem()
            assertEquals(false, final.loading)
            assertEquals(entries, final.aabs)
        }
    }

    @Test
    fun `loadAabs reports error when listing fails`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { client.listAabs() } throws IOException("connection refused")

        vm.state.test {
            awaitItem() // Initialer State
            vm.loadAabs()

            val final = expectMostRecentItem()
            assertEquals(false, final.loading)
            assertEquals("connection refused", final.error)
        }
    }

    @Test
    fun `install streams log lines and clears installing on exit`() = runTest(mainDispatcherRule.dispatcher) {
        every { client.executeStreaming(any()) } returns flowOf(
            LogLine.Stdout("starting"),
            LogLine.Stdout("done"),
            LogLine.ExitCode(0),
        )

        vm.state.test {
            awaitItem() // Initialer State
            vm.install("app-release.aab")

            // Hier warten wir auf das Ergebnis nach dem Stream
            val final = expectMostRecentItem()
            assertNull(final.installing)
            assertEquals(3, final.log.size)
            assertEquals(LogLine.ExitCode(0), final.log.last())
            assertEquals(0, final.lastExitCode)
        }
    }

    @Test
    fun `install with unknown exit propagates null code`() = runTest(mainDispatcherRule.dispatcher) {
        every { client.executeStreaming(any()) } returns flowOf(
            LogLine.Stdout("running"),
            LogLine.ExitCode(null),
        )

        vm.state.test {
            awaitItem()
            vm.install("app-release.aab")

            val final = expectMostRecentItem()
            assertNull(final.installing)
            assertEquals(LogLine.ExitCode(null), final.log.last())
            assertNull(final.lastExitCode)
        }
    }

    @Test
    fun `loadAabs is a no-op while an install is running`() = runTest(mainDispatcherRule.dispatcher) {
        // Hold the install flow open so we stay in `installing != null`.
        val gate = MutableSharedFlow<LogLine>(replay = 1)
        every { client.executeStreaming(any()) } returns gate
        coEvery { client.listAabs() } returns listOf(
            AabEntry("a.aab", 1714680000),
            AabEntry("b.aab", 1714600000),
        )

        vm.install("app-release.aab")
        vm.state.test {
            // Wait until we're actually in the installing state.
            while (awaitItem().installing == null) { /* drain */ }

            vm.loadAabs() // Should be ignored.

            // Let the install finish.
            gate.emit(LogLine.ExitCode(0))
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { client.listAabs() }
    }

    @Test
    fun `install propagates flow errors into state`() = runTest(mainDispatcherRule.dispatcher) {
        every { client.executeStreaming(any()) } returns flow { throw IOException("ssh closed") }

        vm.state.test {
            awaitItem() // Initialer State
            vm.install("app-release.aab")

            val final = expectMostRecentItem()
            assertNull(final.installing)
            assertEquals("ssh closed", final.error)
        }
    }
}
