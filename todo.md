# TODO

Punkte aus dem Code-Review vom 2026-04-29 plus laufenden Iterationen. Reihenfolge
ist grob nach „Sicherheit / korrektheit / nice-to-have" sortiert; nichts davon
blockiert v0.2, aber alles ist potenziell relevant vor einer breiteren
Distribution.

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

## UI / UX

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

### ViewModel-Fehlermeldungen lokalisieren

`OnboardingViewModel`, `SettingsViewModel` und `InstallViewModel` setzen
Fehlertexte hardcoded (z. B. `"Alle Felder ausfüllen"`, `"Fehler"`,
`formatCauseChain`). Mit der Aufteilung auf `values/strings.xml` (en) und
`values-de/strings.xml` (de) erscheinen UI-Composables jetzt in der richtigen
Sprache, VM-emittierte Fehler aber weiterhin nur in der hardcoded Sprache.

Lösung-Skizze: VM hält statt `String` einen sealed `UiText { Resource(id);
Literal(s) }` o. Ä. im State; das Composable resolved über `stringResource()`.
Alternativ Context-Injection via `AndroidViewModel.getApplication()`.

---

## Features

### Import / Export der Settings

Aktuell stecken Host/Port/User/Working-Dir in DataStore und der private
Ed25519-Key in `filesDir/id_ed25519` — auf einem neuen Phone müsste der
User komplett neu durchs Onboarding. Ein „Settings exportieren" + „Settings
importieren" über Storage-Access-Framework-Picker (Document) oder Share-
Sheet würde das auf zwei Taps reduzieren.

Verschlüsselung ist hier zwingend, nicht optional: ein exportierter
Klartext-Blob mit dem Privkey wäre so sensibel wie der Key selbst, und
landet typischerweise in Cloud-Backup, Drive oder Telegram. Mögliche
Wege:
- Passphrase-derived AES-GCM (Argon2/scrypt → Key → AES-GCM-Encrypt) —
  User gibt beim Export+Import jeweils eine Passphrase ein; offline,
  ohne Server.
- Android `EncryptedFile` — nur sinnvoll für phone-internen Backup, nicht
  für portablen Export, da der Key im Android-Keystore steckt.

Format-Vorschlag: JSON-Wrapper mit Versions-Tag und base64-AES-GCM-Inhalt
(kompakt, leicht zu sichten). Datei-Endung z. B. `.lobber-config`.

UI-seitig: zwei Buttons im Settings-Screen oben, jeweils mit Passphrase-
Dialog. Vor Export warnen, dass der Key drin ist; vor Import warnen, dass
bestehende Settings überschrieben werden.

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
