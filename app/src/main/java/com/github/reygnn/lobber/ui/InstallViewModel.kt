package com.github.reygnn.lobber.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val installing: String? = null,
    val log: List<LogLine> = emptyList(),
    val lastExitCode: Int? = null,
    val error: String? = null,
)

class InstallViewModel(
    private val settings: SettingsStore,
    private val script: String = "./install-aab.sh",
    private val createClient: (SshConfig) -> SshClient = { SshjClient(it) },
) : ViewModel() {

    private val _state = MutableStateFlow(InstallUiState())
    val state: StateFlow<InstallUiState> = _state.asStateFlow()

    fun loadAabs() {
        if (_state.value.installing != null) return
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
                    _state.update { it.copy(loading = false, error = e.message ?: "Fehler") }
                }
        }
    }

    fun install(aab: String) {
        viewModelScope.launch {
            val config = settings.config.first() ?: run {
                _state.update { it.copy(configured = false) }
                return@launch
            }
            _state.update {
                it.copy(installing = aab, log = emptyList(), lastExitCode = null, error = null)
            }
            createClient(config)
                .executeStreaming("$script ${shellQuote(aab)}")
                .catch { e ->
                    _state.update {
                        it.copy(installing = null, error = e.message ?: "Fehler")
                    }
                }
                .collect { line ->
                    _state.update { current ->
                        current.copy(
                            log = current.log + line,
                            installing = if (line is LogLine.ExitCode) null else current.installing,
                            lastExitCode = if (line is LogLine.ExitCode) line.code else current.lastExitCode,
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
