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

/**
 * @property name Dateiname (ohne Pfad), wie er an das Install-Skript übergeben wird.
 * @property mtimeEpochSeconds mtime des Datei-**Ziels** (folgt Symlinks). Bei einem
 *   Symlink auf `app/build/outputs/bundle/release/app-release.aab` ist das der
 *   Zeitpunkt, an dem bundletool das AAB geschrieben hat.
 */
data class AabEntry(
    val name: String,
    val mtimeEpochSeconds: Long,
)

interface SshClient {
    /** Liste, sortiert nach mtime absteigend (frischestes AAB zuerst). */
    suspend fun listAabs(): List<AabEntry>
    fun executeStreaming(command: String): Flow<LogLine>
    /**
     * Prüft, ob der AAB (im Working-Dir) das gegebene Package enthält. Nutzt
     * `unzip -p … | grep -aFq` auf dem Build-Host — Package-ID steckt als UTF-8-
     * String im binären AndroidManifest.xml, fixed-string-Match reicht.
     * Returns false, wenn `unzip` fehlt oder der AAB nicht lesbar ist.
     */
    suspend fun aabContainsPackage(aabName: String, pkg: String): Boolean
}
