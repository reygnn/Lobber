package com.github.reygnn.lobber.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.reygnn.lobber.BuildConfig
import com.github.reygnn.lobber.R
import com.github.reygnn.lobber.data.SettingsStore
import com.github.reygnn.lobber.ssh.AabEntry
import com.github.reygnn.lobber.ssh.LogLine
import com.github.reygnn.lobber.ssh.SshClient
import com.github.reygnn.lobber.ssh.SshConfig
import com.github.reygnn.lobber.ssh.SshjClient
import com.github.reygnn.lobber.ssh.shellQuote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InstallUiState(
    val configured: Boolean = true,
    val aabs: List<AabEntry> = emptyList(),
    val loading: Boolean = false,
    /**
     * AAB-Name solange ein Install-Stream läuft *oder* der Log noch sichtbar
     * sein soll. Wird erst durch [InstallViewModel.dismissInstall] auf `null`
     * gesetzt — vorher bleibt der Log + Exit-Code stehen, damit man ihn lesen
     * kann.
     */
    val installing: String? = null,
    val log: List<LogLine> = emptyList(),
    val lastExitCode: Int? = null,
    val error: UiText? = null,
    /**
     * AAB-Name, der gerade auf User-Bestätigung wartet, weil er als
     * Self-Install erkannt wurde. Während dieser Zustand aktiv ist, zeigt
     * die UI einen Warning-Dialog statt sofort zu installieren.
     */
    val pendingSelfInstall: String? = null,
) {
    /** True sobald `LogLine.ExitCode` angekommen ist — Hinweis für die UI,
     *  einen Dismiss-Button anzuzeigen statt nur den laufenden Stream. */
    val installFinished: Boolean
        get() = log.any { it is LogLine.ExitCode }
}

class InstallViewModel(
    private val settings: SettingsStore,
    private val script: String = "./install-aab.sh",
    private val createClient: (SshConfig) -> SshClient = { SshjClient(it) },
) : ViewModel() {

    private val _state = MutableStateFlow(InstallUiState())
    val state: StateFlow<InstallUiState> = _state.asStateFlow()

    fun loadAabs() {
        if (_state.value.installing != null) return
        if (_state.value.loading) return
        viewModelScope.launch {
            val config = settings.config.first()
            if (config == null) {
                _state.update { it.copy(configured = false) }
                return@launch
            }
            _state.update { it.copy(configured = true, loading = true, error = null) }
            runCatching { createClient(config).listAabs() }
                .onSuccess { aabs ->
                    _state.update { it.copy(loading = false, aabs = aabs) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = e.message?.let(UiText::Literal)
                            ?: UiText.Resource(R.string.error_unknown))
                    }
                }
        }
    }

    fun install(aab: String) {
        viewModelScope.launch {
            val config = settings.config.first() ?: run {
                _state.update { it.copy(configured = false) }
                return@launch
            }
            // Self-Install-Check: AAB-Manifest serverseitig nach unserer Package-ID
            // grepen. Falsch (false) bei Fehler — dann fehlt halt die Warnung,
            // aber ein Install schlägt nicht aus diesem Grund fehl.
            val isSelf = runCatching {
                createClient(config).aabContainsPackage(aab, BuildConfig.APPLICATION_ID)
            }.getOrDefault(false)
            if (isSelf) {
                _state.update { it.copy(pendingSelfInstall = aab) }
                return@launch
            }
            startInstall(aab, config)
        }
    }

    fun confirmSelfInstall() {
        val aab = _state.value.pendingSelfInstall ?: return
        _state.update { it.copy(pendingSelfInstall = null) }
        viewModelScope.launch {
            val config = settings.config.first() ?: run {
                _state.update { it.copy(configured = false) }
                return@launch
            }
            startInstall(aab, config)
        }
    }

    fun cancelSelfInstall() {
        _state.update { it.copy(pendingSelfInstall = null) }
    }

    private suspend fun startInstall(aab: String, config: SshConfig) {
        _state.update {
            it.copy(installing = aab, log = emptyList(), lastExitCode = null, error = null)
        }
        createClient(config)
            .executeStreaming("$script ${shellQuote(aab)}")
            .catch { e ->
                _state.update {
                    it.copy(installing = null, error = e.message?.let(UiText::Literal)
                        ?: UiText.Resource(R.string.error_unknown))
                }
            }
            .collect { line ->
                _state.update { current ->
                    current.copy(
                        log = current.log + line,
                        lastExitCode = if (line is LogLine.ExitCode) line.code else current.lastExitCode,
                    )
                }
            }
    }

    /** Schließt die Install-Progress-View und kehrt zur AAB-Liste zurück. */
    fun dismissInstall() {
        _state.update {
            it.copy(installing = null, log = emptyList(), lastExitCode = null)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
