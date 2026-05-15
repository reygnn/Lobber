# Lobber

> *„lob a build at the server"* — eine kleine Android-App, die auf dem Phone
> zeigt, welche AABs auf deinem Build-Host liegen, und sie per Knopfdruck
> installiert.

---

## Was ist Lobber?

Lobber löst genau ein Problem: **du sitzt mit deinem Test-Phone irgendwo im
Büro, und der frische AAB-Build liegt auf dem Build-Server.** Statt USB-Kabel,
`adb`, `bundletool`, oder File-Transfer-Gefummel öffnest du Lobber, tippst die
gewünschte `.aab` an, und das Phone führt remote ein Install-Skript auf dem
Server aus, das die App via `adb install` zurück auf dasselbe Phone deployt.

Das eigentliche Installieren erledigt ein Skript namens **`install-aab.sh`**,
das auf dem Build-Host liegt — Lobber ruft es nur auf und streamt dessen
Live-Output zurück ins UI. Dadurch kann das Skript machen, was bei dir Sinn
ergibt (universelles APK aus dem AAB extrahieren, signieren, an `adb install`
verfüttern, Versions-Logging, Slack-Notify, …) ohne dass die App davon
irgendetwas wissen muss.

**Lobber ist explizit ein LAN-Tool für ein bekanntes Build-Host-Setup.** Es
ist kein generischer SSH-Client, kein App-Store-Ersatz und nicht für den
Einsatz über das offene Internet gedacht (siehe [Sicherheit](#sicherheit)).

---

## Wie benutze ich es? (First-Time-Setup)

### 1. Server-Seite vorbereiten

Auf dem Build-Host brauchst du:

- einen laufenden **SSH-Daemon**, erreichbar vom Phone aus (gleiches WLAN
  reicht),
- vorübergehend **`PasswordAuthentication yes`** in `sshd_config`, falls du
  den Auto-Setup-Wizard nutzen willst (siehe Schritt 3) — kann nach
  erfolgreichem Setup wieder ausgeschaltet werden,
- ein **Working-Dir**, in dem die `.aab`-Dateien landen *und* in dem das
  Skript `install-aab.sh` liegt — z. B. `/srv/builds`,
- das Skript **`install-aab.sh`** ausführbar (`chmod +x`); es bekommt den
  Dateinamen der AAB als ersten Parameter (`$1`).

Minimal-Beispiel `install-aab.sh` (anpassen, was du brauchst):

```bash
#!/usr/bin/env bash
set -euo pipefail
AAB="$1"
APKS="${AAB%.aab}.apks"

bundletool build-apks --bundle="$AAB" --output="$APKS" \
    --connected-device --ks=keystore.jks --ks-pass=pass:secret \
    --ks-key-alias=upload --key-pass=pass:secret
bundletool install-apks --apks="$APKS"
echo "OK: $AAB"
```

Wichtig: Das Skript sieht das **angeschlossene Phone** vom Build-Server aus
nur, wenn dort `adb` das Phone kennt — typischerweise über USB-Kabel zum
Server, einen `adb`-WLAN-Pairing-Setup oder einen `adb`-over-TCP-Workflow.
Lobber ist die *Bedien*-Brücke; die *Install*-Brücke baust du serverseitig.

### 2. App starten — Auto-Setup-Wizard (empfohlen)

Beim allerersten Start landest du im **Erst-Setup-Screen**. Der Wizard
übernimmt Keypair-Generierung und Pubkey-Deployment für dich:

| Feld          | Beispiel                                              |
|---------------|-------------------------------------------------------|
| Host          | `buildserver.local` oder `192.168.1.42`               |
| Port          | `22`                                                  |
| User          | der SSH-User auf dem Build-Host (z. B. `ci`)          |
| Passwort      | das SSH-Passwort des Users (wird nicht gespeichert)   |
| Working dir   | das Verzeichnis mit den `.aab` und `install-aab.sh`   |

**„Auto-Setup starten"** durchläuft dann:

1. **Erzeuge Ed25519-Schlüssel** — direkt auf dem Phone, in `filesDir`.
2. **Lege Pubkey auf dem Build-Host ab** — Passwort-Login, hängt die
   `ssh-ed25519 …`-Zeile an `~/.ssh/authorized_keys`, setzt `chmod 700` auf
   `~/.ssh` und `chmod 600` auf `authorized_keys`.
3. **Verifiziere Pubkey-Login** — separater Connect mit dem neuen Key. Schlägt
   das fehl, wird die Konfiguration nicht persistiert.
4. **Speichere Konfiguration** — Privatkey als `id_ed25519` (Mode `0600`),
   Pubkey als `id_ed25519.pub` in `filesDir`. Das Passwort verschwindet aus
   dem Form-State.

Danach kannst du `PasswordAuthentication no` wieder setzen — Lobber redet
ab jetzt nur noch per Pubkey mit dem Server.

### 2b. Manueller Pfad — eigener Key

Falls du schon einen Schlüssel hast oder den Server keinen Passwort-Login
zulässt, tippe im Wizard auf **„Ich habe schon einen Schlüssel"**. Du landest
auf dem klassischen **Settings-Screen** mit Paste-Feld für den PEM-Block.

Schlüssel auf einem normalen Rechner (nicht auf dem Phone) erzeugen:

```bash
ssh-keygen -t ed25519 -f lobber_key -N ""
```

Pubkey auf den Server bringen:

```bash
ssh-copy-id -i lobber_key.pub ci@buildserver.local
```

Dann den **kompletten Inhalt** von `lobber_key` (inkl. der
`-----BEGIN OPENSSH PRIVATE KEY-----` und `-----END …-----` Zeilen) per Paste
in das `id_ed25519`-Feld der Settings einfügen, restliche Felder ausfüllen,
„Speichern".

### 3. Installieren

Der Installer-Screen listet alle `.aab`-Dateien aus deinem Working-Dir.

- **Tap auf eine `.aab`** startet den Install. Der Bildschirm wechselt zur
  Live-Log-Ansicht.
- **stdout** erscheint normal, **stderr** in der Theme-Error-Farbe, am Ende
  eine Zeile `─── exit 0 ───` in Primary-Farbe oder `─── exit N ───` in
  Error-Farbe je nach Skript-Returncode.
- **Der farbige Punkt links neben dem Titel** zeigt den ADB-Status (Primary
  = aktiv, Error = inaktiv) und ist tippbar — Tap springt direkt in die
  Entwickleroptionen des Geräts, um USB-/WLAN-Debugging schnell
  umzuschalten.
- **`⟳`** in der Top-Bar lädt die AAB-Liste neu (z. B. nach einem frischen
  Build).
- **`⚙`** öffnet die Settings, falls du Host-Daten ändern willst.

Damit ist der reguläre Workflow: neuer Build landet auf dem Server → Lobber
öffnen → `⟳` → AAB tippen → fertig.

---

## Targeting

`minSdk = targetSdk = compileSdk = 36` — läuft ausschließlich auf **Android
16**. Keine Compat-Shims, keine AppCompat-Themen, keine Material-2-Reste.

---

## Stack

- Kotlin 2.2.21, Jetpack Compose, Material 3 + Dynamic Color (Material You),
  Navigation-Compose 2.9
- AGP 8.13, JDK 17
- DataStore Preferences 1.2 für Konfiguration
- **sshj 0.40** + **BouncyCastle 1.79** für SSH; `slf4j-nop` als runtimeOnly
- MVVM mit manueller DI (kein Hilt, kein Koin)
- Tests: JUnit 4, **MockK**, kotlinx-coroutines-test, **Turbine**

---

## Build & Test

Gradle ist über den Wrapper eingecheckt — keine lokale Gradle-Installation
nötig.

```bash
./gradlew assembleDebug    # Debug-APK bauen
./gradlew test             # Unit-Tests (keine Instrumentation nötig)
```

---

## Architektur

```
com.github.reygnn.lobber
├── LobberApplication.kt        Singleton-Holder für SettingsStore
├── MainActivity.kt             Compose-Entry, NavHost (loading|onboarding|settings|installer)
├── ssh/
│   ├── SshClient.kt            Interface + LogLine + SshConfig + shellQuote
│   ├── SshjClient.kt           sshj-Impl, eine Connection pro Operation
│   ├── SshKeygen.kt            Ed25519-Generator + OpenSSH-PEM-Serialisierung
│   ├── SshBootstrap.kt         Interface: pushPublicKey, verifyPubkeyAuth
│   └── SshjBootstrap.kt        sshj-Impl: einmaliger Passwort-Connect fürs Setup
├── data/
│   └── SettingsStore.kt        DataStore<Preferences> + id_ed25519/.pub in filesDir
└── ui/
    ├── InstallViewModel.kt     StateFlow<InstallUiState>, listAabs/install
    ├── SettingsViewModel.kt    StateFlow<SettingsUiState>, Form + Validierung
    ├── OnboardingViewModel.kt  StateFlow<OnboardingUiState>, Step-Maschine fürs Erst-Setup
    └── Screens.kt              Settings/Installer/Onboarding + AabList/InstallProgress
```

**Wichtige Design-Entscheidungen** stehen ausführlich in
[`CLAUDE.md`](CLAUDE.md) — kurz:

- `SshClient` ist immer ein Interface, `InstallViewModel` bekommt eine
  `(SshConfig) -> SshClient` Factory → MockK-freundlich.
- **Eine SSH-Connection pro Operation.** Kein Pool, keine Long-Lived-Session.
- Manuelle DI über `LobberApplication` + `LobberViewModelFactory`.
- Private Key liegt als Datei in `filesDir/id_ed25519` mit Mode `0600`.

---

## Sicherheit

**Bewusst minimal, weil Tool für ein bekanntes LAN-Setup:**

- Private Key liegt in `context.filesDir/id_ed25519`, durch die Android-App-
  Sandbox isoliert. Mode `0600` als defence-in-depth.
- Android 16 hat default Geräte-Verschlüsselung. Für höheren Bedarf zusätzlich
  `androidx.biometric` vor App-Start oder `EncryptedFile`.
- **Host-Key-Verifikation: aktuell `PromiscuousVerifier()`** mit `TODO`-Marker
  in `ssh/SshjClient.kt`. Akzeptabel für ein vertrauenswürdiges Build-LAN,
  **nicht safe für Internet-Hops**. Vor breiterer Distribution durch einen
  TOFU-Fingerprint-Flow ersetzen (Slot in `SshConfig.knownHostFingerprint` ist
  schon vorhanden).

---

## Tests

`app/src/test/java/.../InstallViewModelTest.kt` folgt
[`TESTING_CONVENTIONS.kt`](app/src/test/java/com/github/reygnn/lobber/TESTING_CONVENTIONS.kt):

- Single Dispatcher via `MainDispatcherRule` (kein eigenes `TestScope`/
  `StandardTestDispatcher`).
- MockK für Mocks, Turbine für Flow-Assertions, `expectMostRecentItem()` gegen
  StateFlow-Konflation.
- Jeden Flow stubben, den die VM bei Konstruktion liest — sonst fallen alle
  VM-Tests gleichzeitig auf MockK-Fehler.

---

## Dokumentation

- [`CLAUDE.md`](CLAUDE.md) — Architekturregeln, Test-Konventionen, Git-Workflow
  (auch für menschliche Mitwirkende lesenswert, nicht nur für Claude Code).
- [`WHY_CLAUDE.md`](WHY_CLAUDE.md) — Hintergrund zu den Konventionen.

---

## Bewusst weggelassen (potenzielle Erweiterungen)

- **Build remote triggern** — wäre eine zweite Funktion, aktuell out-of-scope.
- **Server-Profile** (dev/staging) — derzeit ein Profil, bei Bedarf Liste in
  DataStore.
- **Biometric Lock** — `androidx.biometric` einbauen wenn nötig.
- **File-Picker für Key-Import** — aktuell nur Paste; mit
  `ActivityResultContracts.OpenDocument` ergänzbar.
- **Weitere Locales** — Lobber spricht aktuell Englisch (Default) und
  Deutsch (`res/values/strings.xml` + `res/values-de/strings.xml`). Weitere
  Sprachen via `res/values-<locale>/strings.xml` ergänzbar; fehlende Keys
  fallen auf Englisch zurück.

---

## Versionierung

`versionName` in `app/build.gradle.kts` matcht das GitHub-Release-Tag exakt.

---

## Namensgebung umbenennen

Falls dir „Lobber" nicht gefällt: alles unter `com.github.reygnn.lobber` per
Find/Replace umbenennen, plus `rootProject.name` in `settings.gradle.kts`
und `<string name="app_name">` in `res/values/strings.xml`. Der TopAppBar-
Titel kommt aus `installer_title` in derselben Datei (formatiert mit der
Versionsnummer aus `BuildConfig.VERSION_NAME`).
