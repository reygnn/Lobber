package com.github.reygnn.lobber.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.IOException
import java.util.concurrent.TimeUnit

class SshjBootstrap : SshBootstrap {

    override suspend fun pushPublicKey(
        host: String,
        port: Int,
        username: String,
        password: String,
        publicKeyLine: String,
    ) = withContext(Dispatchers.IO) {
        val ssh = SSHClient()
        // TODO: gleiche TOFU-Fingerprint-Verifikation wie in SshjClient verwenden,
        // sobald sie existiert. Aktuell akzeptieren wir jeden Host-Key.
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connect(host, port)
        try {
            ssh.authPassword(username, password)
            ssh.startSession().use { session ->
                val cmd = session.exec(
                    "mkdir -p ~/.ssh && chmod 700 ~/.ssh && " +
                        "printf '%s\\n' ${shellQuote(publicKeyLine)} >> ~/.ssh/authorized_keys && " +
                        "chmod 600 ~/.ssh/authorized_keys"
                )
                cmd.join(15, TimeUnit.SECONDS)
                val exit = cmd.exitStatus ?: -1
                if (exit != 0) {
                    val err = cmd.errorStream.bufferedReader().readText()
                    throw IOException("Pubkey-Push fehlgeschlagen (exit=$exit): $err")
                }
            }
        } finally {
            ssh.disconnect()
        }
    }

    override suspend fun verifyPubkeyAuth(config: SshConfig) = withContext(Dispatchers.IO) {
        val ssh = SSHClient()
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connect(config.host, config.port)
        try {
            ssh.authPublickey(config.username, BcOpenSshKeyProvider(config.privateKeyPem))
        } finally {
            ssh.disconnect()
        }
    }
}
