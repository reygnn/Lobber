package com.github.reygnn.lobber.ssh

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Android ships a stripped-down "BC" provider that lacks the algorithms sshj
 * needs to load Ed25519 keys (Android calls into Conscrypt and pruned BC for
 * most algos). sshj's auto-registration honours an existing "BC" entry and
 * does not replace it, so `KeyFactory.getInstance("Ed25519", "BC")` then
 * resolves to the stripped provider and fails with `NoSuchAlgorithmException`,
 * surfacing as "Read OpenSSH Version 1 Key failed".
 *
 * Registering our own [BouncyCastleProvider] at slot 1 ensures sshj — and any
 * other JCE consumer in the app — sees the full BC 1.79 with Ed25519 support.
 */
object SshSecurity {

    fun installBouncyCastle() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
