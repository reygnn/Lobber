# TODO

Punkte aus dem Code-Review vom 2026-04-29. Reihenfolge ist grob nach
„Sicherheit / korrektheit / nice-to-have" sortiert; nichts davon blockiert
v0.1, aber alles ist potenziell relevant vor einer breiteren Distribution.

---

## Sicherheit

### Host-Key-Verifikation: TOFU-Fingerprint statt `PromiscuousVerifier`

`ssh/SshjClient.kt:26-27` und `ssh/SshjBootstrap.kt:21-22` akzeptieren aktuell
jeden Host-Key. Bekannter TODO, durchgängig markiert. Slot in
`SshConfig.knownHostFingerprint` und Pref-Key `KEY_HOST_FP` in `SettingsStore`
sind schon vorbereitet.

Nötig:
- Beim ersten Connect Fingerprint anzeigen + bestätigen lassen.
- Persistieren via `SettingsStore` (neue `saveHostFingerprint(...)`).
- `PromiscuousVerifier` durch `FingerprintVerifier(fingerprint)` ersetzen,
  beim Onboarding-Wizard *bevor* das Passwort gesendet wird.
- README-Sektion „Sicherheit" entsprechend updaten.

### Atomares Schreiben des Private-Key-Files

`data/SettingsStore.kt:56` — `keyFile.writeText(privateKeyPem)` ist nicht
atomar. CLAUDE.md spricht von „atomically", die Implementierung schreibt aber
direkt. In der Praxis durch die Android-Sandbox unkritisch, aber bei
gleichzeitigem Lesen aus `config: Flow<SshConfig?>` während eines erneuten
`save()` wäre ein Half-Written-File theoretisch sichtbar.

Lösung: in `filesDir` ein Temp-File schreiben + `renameTo(keyFile)`. Selbe
Behandlung für die neue `id_ed25519.pub`.

### Permissions werden erst *nach* `writeText` gesetzt

`data/SettingsStore.kt:56-62` — der Key landet kurzzeitig mit Default-Permissions
auf der Disk, bevor `setReadable`/`setWritable` ihn auf `0600` ziehen. Window
ist mikroskopisch und durch die App-Sandbox abgedeckt, aber bei
defence-in-depth gehört das andersrum: erst erzeugen, Permissions setzen,
*dann* schreiben (oder direkt `Files.newOutputStream` mit POSIX-Attributen,
falls auf Android verfügbar).

---

## Korrektheit / Robustheit

### `LogLine.ExitCode(cmd.exitStatus ?: -1)`

`ssh/SshjClient.kt:71` — wenn sshj keinen Exit-Code liefert, kommt `-1`. Die
UI rendert das in `ui/Screens.kt:204-208` als „─── exit -1 ───" rot, also als
ob das Skript mit Fehler beendet hätte. Korrektere Variante: eigener
`LogLine.ExitCode(code: Int?)` mit `null` für „unbekannt", in der UI als
neutraler Text („─── exit unbekannt ───").

### `InstallViewModel.loadAabs()` prüft nicht `installing`

`ui/InstallViewModel.kt:37-53` — wenn der Refresh-Button schneller drückt als
das UI ihn deaktiviert (oder per Tests/Configuration-Change), könnte
`loadAabs()` während eines laufenden Installs feuern. Die UI sperrt das in
`ui/Screens.kt:135` (`enabled = !s.loading && s.installing == null`), die VM
sollte aber selbst defensiv sein — single-source-of-truth.

Lösung: `if (_state.value.installing != null) return` als erste Zeile.

### `isConfigured: StateFlow<Boolean?>` mit `null` als Loading-State

`ui/SettingsViewModel.kt:31-32` und `MainActivity.kt:55-62` — `null` heißt
„DataStore noch nicht emittiert", `true`/`false` das eigentliche Ergebnis.
Funktioniert, aber `null` als Sentinel ist subtil. Ein `sealed interface
ConfigState { data object Loading; data object Unconfigured; data object
Configured }` wäre lesbarer, kostet aber etwas Boilerplate.

Eher Nice-to-have als Bug.

---

## Code-Hygiene

### Duplikat `shellQuote` / `shellQuoteArg`

Identische Implementierung in `ssh/SshjClient.kt:78` (`internal fun
shellQuote`) und `ui/InstallViewModel.kt:88` (`private fun shellQuoteArg`).
Eine davon kann weg — entweder `shellQuoteArg` durch `shellQuote` ersetzen
oder umgekehrt.

### `LobberViewModelFactory` als `private class` in `MainActivity.kt`

`MainActivity.kt:85-94` — bei jedem neuen ViewModel muss der `when`-Zweig in
der Activity erweitert werden (gerade beim Onboarding gemacht). Bei drei VMs
noch ok; sobald es vier oder fünf werden, in eigene Datei ziehen.

### `SettingsViewModel.consumeSaved()` als One-Shot-Event-Pattern

`ui/SettingsViewModel.kt:86` und `ui/OnboardingViewModel.kt:101` —
beide nutzen das gleiche „Boolean-Flag im State + consume nach
`LaunchedEffect`"-Muster. Compose-empfohlene Alternative wäre ein
`Channel`/`SharedFlow` für Side-Effects. Aktuell unauffällig, aber wenn ein
drittes Event-Pattern dazukommt, gemeinsam refaktorieren.

---

## Build / Release

### Kein Release-Signing-Config in `app/build.gradle.kts`

`app/build.gradle.kts:20-25` definiert nur `isMinifyEnabled = true` für
`release`. Ohne `signingConfig` baut `assembleRelease` zwar, das resultierende
APK ist aber unsigniert — auf Android 16 nicht installierbar.

Vor dem ersten Release-Build:
- `signingConfig`-Block (entweder Debug-Key für lokales Test-Sideloading oder
  einen separaten Upload-Key in `keystore/`).
- `versionCode` hochzählen (ist in `build.gradle.kts:15` bei `1`).

---

## UI / UX

### Dark Mode

Aktuell folgt die Compose-Oberfläche dem Default-`MaterialTheme`. Sauber
wäre ein eigenes `LobberTheme` mit Light/Dark-`ColorScheme`s, das per
`isSystemInDarkTheme()` umgeschaltet wird. Material 3 hat das in der Kombi
mit `dynamicLightColorScheme` / `dynamicDarkColorScheme` (Android 12+) als
einen Drei-Zeiler.

### Splash Screen mit Dark-Mode-Variante

Android 12+ erzwingt einen Splash über die `SplashScreen`-API. Default ist
ein weißer Window-Background — sticht im Dark Mode unangenehm raus. Nötig:
- `Theme.SplashScreen` als Theme deklarieren, `windowSplashScreenBackground`
  in `values/themes.xml` und `values-night/themes.xml` definieren.
- Adaptive App-Icon (`mipmap-anydpi-v26`) als `windowSplashScreenAnimatedIcon`,
  damit der Splash-Look einheitlich ist.

### Tastatur verdeckt Working-Dir-Feld

Im Settings- und Onboarding-Screen verschwindet das `workingDir`-Eingabefeld
unter der Software-Tastatur. Beide Screens haben zwar
`Modifier.verticalScroll(rememberScrollState())` (`ui/Screens.kt:73,249`),
aber die App zieht das IME-Inset nicht in den Layout-Pass — auf Android 15+
ist Edge-to-Edge default und Tastatur überlagert dann statt zu resizen.

Lösung in der Größenordnung einer Handvoll Zeilen:
- `Modifier.imePadding()` auf den scrollbaren Column-Container setzen.
- Optional `android:windowSoftInputMode="adjustResize"` auf die Activity in
  `AndroidManifest.xml`, falls das alleine nicht reicht.
- Beim Fokussieren des Felds idealerweise dorthin scrollen
  (`bringIntoViewRequester` oder `Modifier.onFocusEvent`).

### Install-Log nach Ende des AAB-Installs sichtbar lassen

`ui/Screens.kt:145` switcht mit `s.installing != null` zwischen Liste und
`InstallProgress`. Sobald `LogLine.ExitCode` ankommt, setzt
`InstallViewModel` `installing = null` (`InstallViewModel.kt:75`) und das UI
springt zurück zur Liste — der Log und der Exit-Code sind weg, bevor man
sie lesen kann.

Lösung: nach dem Install nicht automatisch zurücknavigieren. Stattdessen
den Log + Exit-Code stehen lassen und einen expliziten „Fertig"/„Schließen"-
Button zeigen, der dann erst auf die Liste zurückwechselt. State-Modellierung
am einfachsten als zusätzliches Feld (z. B. `lastInstallShown: String?`),
das die View bis zum Dismiss anzeigt.

---

## Doku-Konsistenz

### `strings.xml` existiert doch

`app/src/main/res/values/strings.xml` enthält `<string name="app_name">Lobber</string>`,
referenziert via `android:label="@string/app_name"` im `AndroidManifest.xml:8`.

Sowohl `CLAUDE.md` (Sektion „Localization") als auch die aktuelle
`README.md` (Sektion „Namensgebung umbenennen") behaupten, es gäbe keine
`strings.xml`. Das ist falsch.

Optionen:
- Doku korrigieren: bestätigen, dass es eine minimale `strings.xml` mit nur
  `app_name` gibt, und Lokalisierungs-Pfad davon ausgehend beschreiben.
- Oder den `app_name`-Eintrag entfernen und ihn z. B. via `applicationId`
  oder `android:label` direkt im Manifest setzen, dann passt die bisherige
  Aussage „keine `strings.xml`" wieder.

---

## Nicht-blockierende Beobachtungen

- `executeStreaming` in `SshjClient` öffnet die SSH-Connection erst beim
  Collect des channelFlow — sauber, kein Risiko hängender Sockets.
- `LogLine` ist als `sealed interface` mit exhaustivem `when` in der UI
  ausgewertet — beim Hinzufügen neuer Subtypes warnt der Compiler.
- `executeStreaming` startet zwei Coroutines (stdout, stderr) auf
  `Dispatchers.IO`. Wenn `cmd.errorStream` blockiert während stdout fertig
  ist, könnte der Flow hängen, bis stderr-EOF kommt — unauffällig in der
  Praxis (Skripte schließen stderr beim Exit), aber gut zu wissen.
