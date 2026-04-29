package com.github.reygnn.lobber.ssh

/**
 * Einmal-Operationen für das Erst-Setup eines Build-Hosts: Pubkey via Passwort
 * pushen, anschließende Pubkey-Auth verifizieren.
 *
 * Bewusst getrennt von [SshClient]: dort haben wir immer einen vollständigen
 * [SshConfig] mit Privatschlüssel; hier ist Passwort-Auth ein einmaliger
 * Bootstrap, das Passwort wird nicht persistiert.
 */
interface SshBootstrap {

    /**
     * Verbindet sich per Passwort, hängt [publicKeyLine] an
     * `~/.ssh/authorized_keys` an und setzt die nötigen Permissions.
     *
     * @throws Exception bei Connection-, Auth- oder Schreibfehlern
     */
    suspend fun pushPublicKey(
        host: String,
        port: Int,
        username: String,
        password: String,
        publicKeyLine: String,
    )

    /**
     * Verbindet sich mit der frisch erzeugten [config] per Pubkey, um zu
     * prüfen dass [pushPublicKey] tatsächlich gewirkt hat.
     *
     * @throws Exception wenn die Pubkey-Auth scheitert
     */
    suspend fun verifyPubkeyAuth(config: SshConfig)
}
