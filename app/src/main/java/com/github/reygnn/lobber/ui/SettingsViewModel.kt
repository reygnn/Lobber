package com.github.reygnn.lobber.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.reygnn.lobber.data.ConfigState
import com.github.reygnn.lobber.data.SettingsStore
import com.github.reygnn.lobber.ssh.SshConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val workingDir: String = "",
    val privateKeyPem: String = "",
    val saving: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val settings: SettingsStore,
) : ViewModel() {

    val configState: StateFlow<ConfigState> = settings.isConfigured
        .map { if (it) ConfigState.Configured else ConfigState.Unconfigured }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = ConfigState.Loading)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _savedEvents = Channel<Unit>(capacity = Channel.BUFFERED)
    /** Einmalige Side-Effect-Events für Navigation; Compose collected per LaunchedEffect. */
    val savedEvents: Flow<Unit> = _savedEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            settings.config.first()?.let { cfg -> applyExisting(cfg) }
        }
    }

    private fun applyExisting(cfg: SshConfig) {
        _state.update {
            it.copy(
                host = cfg.host,
                port = cfg.port.toString(),
                username = cfg.username,
                workingDir = cfg.workingDir,
                privateKeyPem = cfg.privateKeyPem,
            )
        }
    }

    fun onHost(v: String) = _state.update { it.copy(host = v) }
    fun onPort(v: String) = _state.update { it.copy(port = v.filter { c -> c.isDigit() }) }
    fun onUsername(v: String) = _state.update { it.copy(username = v) }
    fun onWorkingDir(v: String) = _state.update { it.copy(workingDir = v) }
    fun onPrivateKey(v: String) = _state.update { it.copy(privateKeyPem = v) }

    fun save() {
        val s = _state.value
        val port = s.port.toIntOrNull() ?: 22
        if (s.host.isBlank() || s.username.isBlank() || s.workingDir.isBlank() || s.privateKeyPem.isBlank()) {
            _state.update { it.copy(error = "Alle Felder ausfüllen") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                settings.save(
                    host = s.host.trim(),
                    port = port,
                    username = s.username.trim(),
                    workingDir = s.workingDir.trim(),
                    privateKeyPem = s.privateKeyPem,
                )
            }.onSuccess {
                _state.update { it.copy(saving = false) }
                _savedEvents.trySend(Unit)
            }.onFailure { e ->
                _state.update { it.copy(saving = false, error = e.message ?: "Fehler") }
            }
        }
    }
}
