package com.github.reygnn.lobber.ssh

import kotlinx.coroutines.flow.Flow

data class SshConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
    val workingDir: String,
    val privateKeyPem: String,
    val knownHostFingerprint: String? = null,
)

sealed interface LogLine {
    data class Stdout(val text: String) : LogLine
    data class Stderr(val text: String) : LogLine
    /** [code] ist `null`, wenn sshj keinen Exit-Status liefert ("unbekannt"). */
    data class ExitCode(val code: Int?) : LogLine
}

interface SshClient {
    suspend fun listAabs(): List<String>
    fun executeStreaming(command: String): Flow<LogLine>
}
