package com.github.reygnn.lobber.ui

import app.cash.turbine.test
import com.github.reygnn.lobber.MainDispatcherRule
import com.github.reygnn.lobber.data.SettingsStore
import com.github.reygnn.lobber.ssh.LogLine
import com.github.reygnn.lobber.ssh.SshClient
import com.github.reygnn.lobber.ssh.SshConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
    fun `loadAabs populates state with files`() = runTest {
        coEvery { client.listAabs() } returns listOf("app-release.aab", "app-debug.aab")

        vm.state.test {
            assertEquals(InstallUiState(), awaitItem())          // initial
            vm.loadAabs()
            assertEquals(true, awaitItem().loading)              // loading
            val final = awaitItem()
            assertEquals(false, final.loading)
            assertEquals(listOf("app-release.aab", "app-debug.aab"), final.aabs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadAabs reports error when listing fails`() = runTest {
        coEvery { client.listAabs() } throws IOException("connection refused")

        vm.state.test {
            skipItems(1) // initial
            vm.loadAabs()
            skipItems(1) // loading=true
            val final = awaitItem()
            assertEquals(false, final.loading)
            assertEquals("connection refused", final.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `install streams log lines and clears installing on exit`() = runTest {
        every { client.executeStreaming(any()) } returns flowOf(
            LogLine.Stdout("starting"),
            LogLine.Stdout("done"),
            LogLine.ExitCode(0),
        )

        vm.state.test {
            skipItems(1) // initial
            vm.install("app-release.aab")
            assertEquals("app-release.aab", awaitItem().installing) // installing set
            skipItems(2)                                            // 2 stdout appends
            val final = awaitItem()                                 // exit code clears installing
            assertNull(final.installing)
            assertEquals(3, final.log.size)
            assertEquals(LogLine.ExitCode(0), final.log.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `install propagates flow errors into state`() = runTest {
        every { client.executeStreaming(any()) } returns flow { throw IOException("ssh closed") }

        vm.state.test {
            skipItems(1) // initial
            vm.install("app-release.aab")
            skipItems(1) // installing set
            val final = awaitItem()
            assertNull(final.installing)
            assertEquals("ssh closed", final.error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
