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
        ssh.authPublickey(config.username, BcOpenSshKeyProvider(config.privateKeyPem))
        return ssh
    }

    override suspend fun listAabs(): List<String> = withContext(Dispatchers.IO) {
        connect().use { ssh ->
            ssh.startSession().use { session ->
                // -L follows symlinks (user's discovery workflow uses symlinks
                // into per-project bundle dirs), -type f filters out dangling
                // symlinks. find returns exit 0 with empty output when the
                // glob has no matches, so the empty-list path is unambiguous.
                val cmd = session.exec(
                    "find -L ${pathQuote(config.workingDir)} -maxdepth 1 -name '*.aab' -type f"
                )
                val out = cmd.inputStream.bufferedReader().readText()
                val err = cmd.errorStream.bufferedReader().readText()
                cmd.join(15, TimeUnit.SECONDS)
                val exit = cmd.exitStatus ?: -1
                if (exit != 0) {
                    throw java.io.IOException(
                        "find fehlgeschlagen (exit=$exit) für ${config.workingDir}: ${err.trim().ifEmpty { "(keine stderr)" }}"
                    )
                }
                out.lineSequence()
                    .filter { it.isNotBlank() }
                    .map { it.substringAfterLast('/') }
                    .toList()
            }
        }
    }

    override fun executeStreaming(command: String): Flow<LogLine> = channelFlow {
        val full = "cd ${pathQuote(config.workingDir)} && $command"

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
                    send(LogLine.ExitCode(cmd.exitStatus))
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}

internal fun shellQuote(s: String): String =
    "'" + s.replace("'", "'\\''") + "'"

/**
 * Wie [shellQuote], aber lässt einen führenden `~/` unquoted, sodass Bash
 * die Tilde zur Home-Expansion benutzt. Single-Quoted-Pfade unterdrücken
 * Tilde-Expansion komplett — `'~/foo'` sucht buchstäblich ein Verzeichnis
 * namens `~`. Mit dieser Variante wird daraus `~/'foo'`, was sich zu
 * `$HOME/foo` auflöst.
 */
internal fun pathQuote(path: String): String = when {
    path == "~" -> "~"
    path.startsWith("~/") -> "~/" + shellQuote(path.removePrefix("~/"))
    else -> shellQuote(path)
}
