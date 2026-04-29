package com.github.reygnn.lobber.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.reygnn.lobber.ssh.SshConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "lobber-settings")

/**
 * SSH-Konfiguration in DataStore<Preferences>, Private-Key als Datei in
 * `filesDir`. App-private Storage ist durch die Android-Sandbox isoliert;
 * für höhere Anforderungen Key gegen `EncryptedFile` tauschen oder die
 * App hinter `androidx.biometric` verriegeln.
 */
class SettingsStore(private val context: Context) {

    private val keyFile = File(context.filesDir, "id_ed25519")
    private val pubKeyFile = File(context.filesDir, "id_ed25519.pub")

    /** Emits true once a valid config (incl. key file) is on disk. */
    val isConfigured: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HOST] != null &&
                prefs[KEY_USER] != null &&
                prefs[KEY_DIR] != null &&
                keyFile.exists()
    }

    val config: Flow<SshConfig?> = context.dataStore.data.map { prefs ->
        val host = prefs[KEY_HOST] ?: return@map null
        val user = prefs[KEY_USER] ?: return@map null
        val dir = prefs[KEY_DIR] ?: return@map null
        if (!keyFile.exists()) return@map null
        SshConfig(
            host = host,
            port = prefs[KEY_PORT] ?: 22,
            username = user,
            workingDir = dir,
            privateKeyPem = keyFile.readText(),
            knownHostFingerprint = prefs[KEY_HOST_FP],
        )
    }

    suspend fun save(
        host: String,
        port: Int,
        username: String,
        workingDir: String,
        privateKeyPem: String,
    ) {
        keyFile.writeText(privateKeyPem)
        // Restrict to owner-only as defence-in-depth (Android sandbox already isolates).
        keyFile.setReadable(false, false)
        keyFile.setReadable(true, true)
        keyFile.setWritable(false, false)
        keyFile.setWritable(true, true)

        context.dataStore.edit { prefs ->
            prefs[KEY_HOST] = host
            prefs[KEY_PORT] = port
            prefs[KEY_USER] = username
            prefs[KEY_DIR] = workingDir
        }
    }

    /** Schreibt die `ssh-ed25519 …`-Zeile als `id_ed25519.pub` in `filesDir`. */
    suspend fun savePubKey(publicKeyOpenSsh: String) {
        pubKeyFile.writeText(publicKeyOpenSsh)
    }

    private companion object {
        val KEY_HOST: Preferences.Key<String> = stringPreferencesKey("host")
        val KEY_PORT: Preferences.Key<Int> = intPreferencesKey("port")
        val KEY_USER: Preferences.Key<String> = stringPreferencesKey("user")
        val KEY_DIR: Preferences.Key<String> = stringPreferencesKey("dir")
        val KEY_HOST_FP: Preferences.Key<String> = stringPreferencesKey("host_fp")
    }
}
