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
            awaitItem() // Initialer State
            vm.loadAabs()

            // Wir nutzen expectMostRecentItem, falls loading=true zu schnell geht
            val final = expectMostRecentItem()
            assertEquals(false, final.loading)
            assertEquals(listOf("app-release.aab", "app-debug.aab"), final.aabs)
        }
    }

    @Test
    fun `loadAabs reports error when listing fails`() = runTest {
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
    fun `install streams log lines and clears installing on exit`() = runTest {
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
        }
    }

    @Test
    fun `install propagates flow errors into state`() = runTest {
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
