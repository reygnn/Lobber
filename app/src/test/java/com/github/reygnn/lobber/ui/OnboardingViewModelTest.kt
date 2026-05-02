package com.github.reygnn.lobber.ui

import app.cash.turbine.test
import com.github.reygnn.lobber.MainDispatcherRule
import com.github.reygnn.lobber.data.SettingsStore
import com.github.reygnn.lobber.ssh.SshBootstrap
import com.github.reygnn.lobber.ssh.SshConfig
import com.github.reygnn.lobber.ssh.SshKeyPair
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = mockk<SettingsStore>()
    private val bootstrap = mockk<SshBootstrap>()
    private val testKeyPair = SshKeyPair(
        privateKeyPem = "PEM-BLOCK",
        publicKeyOpenSsh = "ssh-ed25519 AAAA test@host",
    )

    private lateinit var vm: OnboardingViewModel

    @Before
    fun setUp() {
        coEvery { settings.save(any(), any(), any(), any(), any()) } just Runs
        coEvery { settings.savePubKey(any()) } just Runs
        coEvery { bootstrap.pushPublicKey(any(), any(), any(), any(), any()) } just Runs
        coEvery { bootstrap.verifyPubkeyAuth(any()) } just Runs
        vm = OnboardingViewModel(settings = settings, bootstrap = bootstrap, keygen = { testKeyPair })
    }

    private fun fillForm() {
        vm.onHost("buildserver")
        vm.onPort("2222")
        vm.onUsername("ci")
        vm.onPassword("secret")
        vm.onWorkingDir("/srv/builds")
    }

    @Test
    fun `happy path completes with Done step and clears password`() = runTest(mainDispatcherRule.dispatcher) {
        fillForm()
        vm.start()
        vm.state.test {
            val final = expectMostRecentItem()
            assertEquals(OnboardingStep.Done, final.step)
            assertEquals("", final.password)
            assertNull(final.error)
        }
    }

    @Test
    fun `pushPublicKey failure surfaces error and resets to Idle`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { bootstrap.pushPublicKey(any(), any(), any(), any(), any()) } throws IOException("auth failed")
        fillForm()
        vm.start()
        vm.state.test {
            val final = expectMostRecentItem()
            assertEquals(OnboardingStep.Idle, final.step)
            assertEquals("IOException: auth failed", final.error)
        }
    }

    @Test
    fun `verifyPubkeyAuth failure surfaces error`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { bootstrap.verifyPubkeyAuth(any()) } throws IOException("verification failed")
        fillForm()
        vm.start()
        vm.state.test {
            val final = expectMostRecentItem()
            assertEquals(OnboardingStep.Idle, final.step)
            assertEquals("IOException: verification failed", final.error)
        }
    }

    @Test
    fun `error message includes cause chain`() = runTest(mainDispatcherRule.dispatcher) {
        val root = IllegalStateException("Ed25519 not found")
        val mid = java.security.GeneralSecurityException("KeyFactory failed", root)
        coEvery { bootstrap.verifyPubkeyAuth(any()) } throws IOException("Read OpenSSH Version 1 Key failed", mid)
        fillForm()
        vm.start()
        vm.state.test {
            val final = expectMostRecentItem()
            assertEquals(
                "IOException: Read OpenSSH Version 1 Key failed\n" +
                    "→ GeneralSecurityException: KeyFactory failed\n" +
                    "→ IllegalStateException: Ed25519 not found",
                final.error,
            )
        }
    }

    @Test
    fun `start without filled form yields validation error and does not call bootstrap`() = runTest(mainDispatcherRule.dispatcher) {
        vm.start()
        vm.state.test {
            val final = expectMostRecentItem()
            assertEquals("Alle Felder ausfüllen", final.error)
            assertEquals(OnboardingStep.Idle, final.step)
        }
        coVerify(exactly = 0) { bootstrap.pushPublicKey(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `pubkey is pushed and persisted on happy path`() = runTest(mainDispatcherRule.dispatcher) {
        val pushedLine = slot<String>()
        coEvery {
            bootstrap.pushPublicKey(any(), any(), any(), any(), capture(pushedLine))
        } just Runs

        fillForm()
        vm.start()
        vm.state.test { expectMostRecentItem() }

        assertEquals("ssh-ed25519 AAAA test@host", pushedLine.captured)
        coVerify { settings.savePubKey("ssh-ed25519 AAAA test@host") }
    }

    @Test
    fun `parsed port is forwarded to bootstrap and settings`() = runTest(mainDispatcherRule.dispatcher) {
        val cfg = slot<SshConfig>()
        coEvery { bootstrap.verifyPubkeyAuth(capture(cfg)) } just Runs

        fillForm()
        vm.start()
        vm.state.test { expectMostRecentItem() }

        assertEquals(2222, cfg.captured.port)
        coVerify { settings.save("buildserver", 2222, "ci", "/srv/builds", "PEM-BLOCK") }
    }
}
