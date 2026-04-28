package com.github.reygnn.lobber.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.util.concurrent.TimeUnit

/**
 * sshj-based SshClient.
 *
 * Eine frische Connection pro Operation — billig für unseren Use Case (ein
 * list, ein install pro Session) und keine Leak-Gefahr beim UI-Wegnavigieren.
 */
class SshjClient(
    private val config: SshConfig,
) : SshClient {

    private fun connect(): SSHClient {
        val ssh = SSHClient()
        // TODO: nach erstem Connect Fingerprint persistieren und hier auf
        // FingerprintVerifier(config.knownHostFingerprint) umstellen.
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connect(config.host, config.port)
        val keys = ssh.loadKeys(config.privateKeyPem, null, null)
        ssh.authPublickey(config.username, keys)
        return ssh
    }

    override suspend fun listAabs(): List<String> = withContext(Dispatchers.IO) {
        connect().use { ssh ->
            ssh.startSession().use { session ->
                val cmd = session.exec(
                    "ls -1 ${shellQuote(config.workingDir)}/*.aab 2>/dev/null || true"
                )
                val out = cmd.inputStream.bufferedReader().readText()
                cmd.join(15, TimeUnit.SECONDS)
                out.lineSequence()
                    .filter { it.isNotBlank() }
                    .map { it.substringAfterLast('/') }
                    .toList()
            }
        }
    }

    override fun executeStreaming(command: String): Flow<LogLine> = channelFlow {
        val full = "cd ${shellQuote(config.workingDir)} && $command"

        connect().use { ssh ->
            ssh.startSession().use { session ->
                val cmd = session.exec(full)
                coroutineScope {
                    val stdoutJob = launch(Dispatchers.IO) {
                        cmd.inputStream.bufferedReader().lineSequence().forEach {
                            send(LogLine.Stdout(it))
                        }
                    }
                    val stderrJob = launch(Dispatchers.IO) {
                        cmd.errorStream.bufferedReader().lineSequence().forEach {
                            send(LogLine.Stderr(it))
                        }
                    }
                    stdoutJob.join()
                    stderrJob.join()
                    cmd.join()
                    send(LogLine.ExitCode(cmd.exitStatus ?: -1))
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}

internal fun shellQuote(s: String): String =
    "'" + s.replace("'", "'\\''") + "'"
