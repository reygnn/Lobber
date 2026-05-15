# Lobber

> *"lob a build at the server"* — a small Android app that shows which AABs
> are sitting on your build host and installs them with a single tap.

---

## What is Lobber?

Lobber solves exactly one problem: **you're sitting somewhere in the office
with your test phone, and the fresh AAB build is on the build server.**
Instead of fiddling with USB cables, `adb`, `bundletool`, or file-transfer
gymnastics, you open Lobber, tap the AAB you want, and the phone remotely
runs an install script on the server that deploys the app via `adb install`
back to the same phone.

The actual installation is done by a script called **`install-aab.sh`** that
lives on the build host — Lobber only invokes it and streams its live output
back to the UI. That means the script can do whatever makes sense for you
(extract a universal APK from the AAB, sign it, feed it to `adb install`,
log the version, send a Slack notification, …) without the app needing to
know any of it.

**Lobber is explicitly a LAN tool for a known build-host setup.** It is not
a generic SSH client, not an app-store replacement, and not meant for use
over the open internet (see [Security](#security)).

---

## How do I use it? (First-time setup)

### 1. Prepare the server

On the build host you need:

- a running **SSH daemon** reachable from the phone (the same Wi-Fi is
  enough),
- temporarily **`PasswordAuthentication yes`** in `sshd_config` if you want
  to use the auto-setup wizard (see step 2) — you can switch it back off
  once setup succeeds,
- a **working dir** where the `.aab` files land *and* where `install-aab.sh`
  lives — e.g. `/srv/builds`,
- the **`install-aab.sh`** script marked executable (`chmod +x`); it
  receives the AAB filename as its first argument (`$1`).

Minimal example `install-aab.sh` (adapt to your needs):

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

Important: the script will only see the **attached phone** from the build
server if `adb` on the server knows about it — typically via a USB cable
from server to phone, an `adb` Wi-Fi pairing setup, or an `adb`-over-TCP
workflow. Lobber is the *control* bridge; the *install* bridge you build
on the server side.

### 2. Launch the app — auto-setup wizard (recommended)

The very first launch drops you into the **first-time setup screen**. The
wizard handles keypair generation and public-key deployment for you:

| Field         | Example                                                |
|---------------|--------------------------------------------------------|
| Host          | `buildserver.local` or `192.168.1.42`                  |
| Port          | `22`                                                   |
| User          | the SSH user on the build host (e.g. `ci`)             |
| Password      | the user's SSH password (not stored)                   |
| Working dir   | the directory with the `.aab` files and `install-aab.sh` |

**"Start auto-setup"** then runs through:

1. **Generate Ed25519 key** — directly on the phone, in `filesDir`.
2. **Push public key to build host** — password login, appends the
   `ssh-ed25519 …` line to `~/.ssh/authorized_keys`, sets `chmod 700` on
   `~/.ssh` and `chmod 600` on `authorized_keys`.
3. **Verify public-key login** — separate connect with the new key. If
   that fails, the configuration is not persisted.
4. **Save configuration** — private key as `id_ed25519` (mode `0600`),
   public key as `id_ed25519.pub` in `filesDir`. The password disappears
   from the form state.

After that you can put `PasswordAuthentication no` back — from now on
Lobber talks to the server only via public-key auth.

### 2b. Manual path — bring your own key

If you already have a key, or the server doesn't allow password login,
tap **"I already have a key"** in the wizard. You land on the classic
**Settings screen** with a paste field for the PEM block.

Generate the key on a regular machine (not on the phone):

```bash
ssh-keygen -t ed25519 -f lobber_key -N ""
```

Push the public key to the server:

```bash
ssh-copy-id -i lobber_key.pub ci@buildserver.local
```

Then paste the **entire contents** of `lobber_key` (including the
`-----BEGIN OPENSSH PRIVATE KEY-----` and `-----END …-----` lines) into
the `id_ed25519` field in Settings, fill in the remaining fields, and tap
"Save".

### 3. Install

The installer screen lists every `.aab` file in your working dir.

- **Tap an `.aab`** to start the install. The screen switches to the live
  log view.
- **stdout** appears normally, **stderr** in the theme's error color, and
  at the end a line `─── exit 0 ───` in the primary color or
  `─── exit N ───` in the error color depending on the script's return
  code.
- **The colored dot left of the title** shows ADB status (primary =
  active, error = inactive) and is tappable — a tap jumps straight to the
  device's developer options so you can flip USB or wireless debugging on
  the fly.
- **`⟳`** in the top bar reloads the AAB list (e.g. after a fresh build).
- **`⚙`** opens Settings if you want to change host details.

That's the regular workflow: new build lands on the server → open Lobber
→ `⟳` → tap an AAB → done.

---

## Target

`minSdk = targetSdk = compileSdk = 36` — runs exclusively on **Android
16**. No compatibility shims, no AppCompat themes, no Material 2 leftovers.

---

## Stack

- Kotlin 2.2.21, Jetpack Compose, Material 3 + Dynamic Color (Material You),
  Navigation-Compose 2.9
- AGP 8.13, JDK 17
- DataStore Preferences 1.2 for configuration
- **sshj 0.40** + **BouncyCastle 1.79** for SSH; `slf4j-nop` as runtimeOnly
- MVVM with manual DI (no Hilt, no Koin)
- Tests: JUnit 4, **MockK**, kotlinx-coroutines-test, **Turbine**

---

## Build & Test

Gradle is checked in via the wrapper — no local Gradle installation
required.

```bash
./gradlew assembleDebug    # build debug APK
./gradlew test             # unit tests (no instrumentation needed)
```

---

## Architecture

```
com.github.reygnn.lobber
├── LobberApplication.kt        singleton holder for SettingsStore
├── MainActivity.kt             Compose entry, NavHost (loading|onboarding|settings|installer)
├── ssh/
│   ├── SshClient.kt            interface + LogLine + SshConfig + shellQuote
│   ├── SshjClient.kt           sshj impl, one connection per operation
│   ├── SshKeygen.kt            Ed25519 generator + OpenSSH PEM serialization
│   ├── SshBootstrap.kt         interface: pushPublicKey, verifyPubkeyAuth
│   └── SshjBootstrap.kt        sshj impl: one-shot password connect for setup
├── data/
│   └── SettingsStore.kt        DataStore<Preferences> + id_ed25519/.pub in filesDir
└── ui/
    ├── InstallViewModel.kt     StateFlow<InstallUiState>, listAabs/install
    ├── SettingsViewModel.kt    StateFlow<SettingsUiState>, form + validation
    ├── OnboardingViewModel.kt  StateFlow<OnboardingUiState>, step machine for first-time setup
    └── Screens.kt              Settings/Installer/Onboarding + AabList/InstallProgress
```

**Key design decisions** are spelled out in detail in
[`CLAUDE.md`](CLAUDE.md) — in short:

- `SshClient` is always an interface, `InstallViewModel` receives a
  `(SshConfig) -> SshClient` factory → MockK-friendly.
- **One SSH connection per operation.** No pool, no long-lived session.
- Manual DI via `LobberApplication` + `LobberViewModelFactory`.
- The private key lives as a file in `filesDir/id_ed25519` with mode
  `0600`.

---

## Security

**Intentionally minimal, because this is a tool for a known LAN setup:**

- The private key lives in `context.filesDir/id_ed25519`, isolated by the
  Android app sandbox. Mode `0600` as defense in depth.
- Android 16 ships device encryption by default. If you need more, add
  `androidx.biometric` before app start, or use `EncryptedFile`.
- **Host-key verification: currently `PromiscuousVerifier()`** with a
  `TODO` marker in `ssh/SshjClient.kt`. Acceptable for a trusted build
  LAN, **not safe over the open internet**. Replace with a TOFU
  fingerprint flow before wider distribution (the slot in
  `SshConfig.knownHostFingerprint` is already there).

---

## Tests

`app/src/test/java/.../InstallViewModelTest.kt` follows
[`TESTING_CONVENTIONS.kt`](app/src/test/java/com/github/reygnn/lobber/TESTING_CONVENTIONS.kt):

- Single dispatcher via `MainDispatcherRule` (no separate `TestScope` /
  `StandardTestDispatcher`).
- MockK for mocks, Turbine for flow assertions,
  `expectMostRecentItem()` against StateFlow conflation.
- Stub every flow the VM reads at construction — otherwise all VM tests
  fail simultaneously with a MockK error.

---

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — architecture rules, test conventions, Git
  workflow (worth reading for human contributors too, not just Claude
  Code).
- [`WHY_CLAUDE.md`](WHY_CLAUDE.md) — background on the conventions.

---

## Intentionally left out (potential extensions)

- **Remote build trigger** — would be a second function, currently out
  of scope.
- **Server profiles** (dev/staging) — one profile for now; if needed, a
  list in DataStore.
- **Biometric lock** — wire up `androidx.biometric` if needed.
- **File picker for key import** — paste only right now; addable via
  `ActivityResultContracts.OpenDocument`.
- **More locales** — Lobber currently speaks English (default) and
  German (`res/values/strings.xml` + `res/values-de/strings.xml`).
  Additional languages can be added via
  `res/values-<locale>/strings.xml`; missing keys fall back to English.

---

## Versioning

`versionName` in `app/build.gradle.kts` matches the GitHub release tag
exactly.

---

## Renaming

If you don't like "Lobber": rename everything under
`com.github.reygnn.lobber` with find/replace, plus `rootProject.name` in
`settings.gradle.kts` and `<string name="app_name">` in
`res/values/strings.xml`. The TopAppBar title comes from
`installer_title` in the same file (formatted with the version from
`BuildConfig.VERSION_NAME`).
