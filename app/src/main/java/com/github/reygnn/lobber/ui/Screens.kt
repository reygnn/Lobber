package com.github.reygnn.lobber.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.reygnn.lobber.ssh.LogLine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onSaved: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val s by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(s.saved) {
        if (s.saved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                if (onBack != null) {
                    TextButton(onClick = onBack) { Text("Zurück") }
                }
            },
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = s.host, onValueChange = viewModel::onHost,
                label = { Text("Host") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.port, onValueChange = viewModel::onPort,
                label = { Text("Port") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.username, onValueChange = viewModel::onUsername,
                label = { Text("User") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.workingDir, onValueChange = viewModel::onWorkingDir,
                label = { Text("Working dir (mit install-aab.sh)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.privateKeyPem, onValueChange = viewModel::onPrivateKey,
                label = { Text("id_ed25519 (PEM, kompletter Block)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )

            s.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = viewModel::save,
                enabled = !s.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (s.saving) "Speichere…" else "Speichern")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerScreen(
    viewModel: InstallViewModel,
    onOpenSettings: () -> Unit,
) {
    val s by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (s.aabs.isEmpty() && !s.loading) viewModel.loadAabs()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Lobber") },
            actions = {
                IconButton(onClick = viewModel::loadAabs, enabled = !s.loading && s.installing == null) {
                    Text("⟳")
                }
                IconButton(onClick = onOpenSettings) { Text("⚙") }
            },
        )
    }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                s.installing != null -> InstallProgress(aab = s.installing!!, log = s.log)
                s.loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> AabList(
                    aabs = s.aabs,
                    error = s.error,
                    onInstall = viewModel::install,
                )
            }
        }
    }
}

@Composable
private fun AabList(
    aabs: List<String>,
    error: String?,
    onInstall: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        if (aabs.isEmpty() && error == null) {
            Text("Keine .aab im Working-Dir gefunden.")
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(aabs) { name ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, modifier = Modifier.weight(1f))
                        Button(onClick = { onInstall(name) }) { Text("Install") }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallProgress(aab: String, log: List<LogLine>) {
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Installing $aab", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(log) { line ->
                val (text, color) = when (line) {
                    is LogLine.Stdout    -> line.text to Color.Unspecified
                    is LogLine.Stderr    -> line.text to MaterialTheme.colorScheme.error
                    is LogLine.ExitCode  -> when (line.code) {
                        null -> "─── exit unbekannt ───" to MaterialTheme.colorScheme.onSurfaceVariant
                        0    -> "─── exit 0 ───" to MaterialTheme.colorScheme.primary
                        else -> "─── exit ${line.code} ───" to MaterialTheme.colorScheme.error
                    }
                }
                Text(
                    text = text,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onDone: () -> Unit,
    onManual: () -> Unit,
) {
    val s by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(s.step) {
        if (s.step == OnboardingStep.Done) {
            viewModel.consumeDone()
            onDone()
        }
    }

    val running = s.step != OnboardingStep.Idle && s.step != OnboardingStep.Done

    Scaffold(topBar = {
        TopAppBar(title = { Text("Erst-Setup") })
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Lobber erzeugt einen Schlüssel auf dem Phone und legt den Pubkey " +
                    "auf dem Build-Host ab. Dafür brauchst du einmalig User & Passwort.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = s.host, onValueChange = viewModel::onHost,
                label = { Text("Host") }, singleLine = true, enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.port, onValueChange = viewModel::onPort,
                label = { Text("Port") }, singleLine = true, enabled = !running,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.username, onValueChange = viewModel::onUsername,
                label = { Text("User") }, singleLine = true, enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.password, onValueChange = viewModel::onPassword,
                label = { Text("Passwort (nur einmalig)") }, singleLine = true, enabled = !running,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.workingDir, onValueChange = viewModel::onWorkingDir,
                label = { Text("Working dir (mit install-aab.sh)") }, singleLine = true, enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            )

            s.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = "  " + stepLabel(s.step),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick = viewModel::start,
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) "Läuft …" else "Auto-Setup starten")
            }

            TextButton(
                onClick = onManual,
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ich habe schon einen Schlüssel")
            }
        }
    }
}

private fun stepLabel(step: OnboardingStep): String = when (step) {
    OnboardingStep.Idle           -> ""
    OnboardingStep.GeneratingKey  -> "Erzeuge Ed25519-Schlüssel …"
    OnboardingStep.PushingKey     -> "Lege Pubkey auf dem Build-Host ab …"
    OnboardingStep.Verifying      -> "Verifiziere Pubkey-Login …"
    OnboardingStep.Saving         -> "Speichere Konfiguration …"
    OnboardingStep.Done           -> "Fertig"
}
