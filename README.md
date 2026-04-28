# Lobber

Android-16-only App: SSH zum Build-Server, AAB aus dem Working-Dir wählen,
`./install-aab.sh <name>` ausführen, Live-Log streamen.

> Name kommt von „lob a build at the server" — wenn dir was anderes besser
> gefällt: alles unter `com.github.reygnn.lobber` per Find/Replace umbenennen,
> plus `rootProject.name` in `settings.gradle.kts` und `app_name` in
> `strings.xml`.

## Targeting

`minSdk = targetSdk = compileSdk = 36` — läuft ausschließlich auf Android 16.

## Setup

1. `gradle/libs.versions.toml`, `settings.gradle.kts`, top-level
   `build.gradle.kts`, `app/build.gradle.kts` so übernehmen
2. App starten → erst Settings ausfüllen:
   - Host / Port / User / Working-Dir (das Verzeichnis mit den `.aab`-Files
     **und** der `install-aab.sh`)
   - `id_ed25519` als kompletten PEM-Block einfügen (paste)
3. Speichern → wechselt automatisch zum Installer
4. Tap auf eine `.aab` → Live-Log läuft

## Architektur

```
com.github.reygnn.lobber
├── LobberApplication.kt   Singleton SettingsStore
├── MainActivity.kt        Compose-Entry, NavHost (loading|settings|installer)
├── ssh/
│   ├── SshClient.kt       Interface + LogLine + SshConfig + shellQuote
│   └── SshjClient.kt      sshj-Impl, eine Connection pro Operation
├── data/
│   └── SettingsStore.kt   DataStore<Preferences> + Key-File in filesDir
└── ui/
    ├── InstallViewModel.kt    StateFlow<InstallUiState>
    ├── SettingsViewModel.kt   StateFlow<SettingsUiState>
    └── Screens.kt             SettingsScreen, InstallerScreen, AabList, InstallProgress
```

## Sicherheit (Stand v0.1)

- Key liegt in `context.filesDir/id_ed25519`, durch App-Sandbox isoliert
- Android 16 hat default Geräte-Verschlüsselung — für höheren Bedarf zusätzlich
  `androidx.biometric` vor App-Start oder `EncryptedFile`
- Host-Key: aktuell `PromiscuousVerifier()` mit `TODO`-Marker in `SshjClient`.
  Für internes Build-LAN ok; vor Produktiv-Gebrauch durch `FingerprintVerifier`
  ersetzen, Fingerprint via `SettingsStore.saveHostFingerprint()` persistieren

## Tests

`InstallViewModelTest.kt` folgt `TESTING_CONVENTIONS.kt`:
- single dispatcher via projektweiter `MainDispatcherRule` (im test-root)
- kein eigener `TestScope` / `StandardTestDispatcher`
- MockK + Turbine für Flow-Assertions

Falls die `MainDispatcherRule` noch nicht existiert: das ist nicht Teil
dieses Scaffolds (laut Konvention liegt sie zentral).

## Was bewusst weg gelassen wurde

- Build triggern remote — als zweite Funktion vorstellbar
- Server-Profile (dev/staging) — aktuell ein Profil; bei Bedarf Liste in
  DataStore
- Biometric Lock — `androidx.biometric` einbauen wenn nötig
- File-Picker für Key-Import — aktuell Paste; `ActivityResultContracts.OpenDocument`
  ergänzbar
