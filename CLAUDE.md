# CLAUDE.md

Project conventions for **Lobber** (Android remote installer that streams
`./install-aab.sh <file>` over SSH from a build host). This file is read
automatically by Claude Code at session start. Keep it focused and actionable —
not a marketing description (that's `README.md`'s job).

---

## Stack

- Kotlin 2.2.21, Jetpack Compose, Material 3
- Min SDK 36 / Target 36 / Compile 36 — **Android 16 only**, no compatibility shims
- JDK 17
- MVVM, manual DI via `LobberApplication.kt` (no Hilt, no Koin)
- Persistence via DataStore Preferences; private SSH key as a file in `filesDir`
- SSH via sshj 0.40 + BouncyCastle 1.79
- Testing: JUnit 4, MockK, kotlinx-coroutines-test, Turbine

## Build & test

```bash
./gradlew assembleDebug    # debug APK
./gradlew test             # unit tests (no instrumentation needed)
```

Gradle is bundled via wrapper. No local Gradle install required.

---

## Architecture

```
ssh/SshClient.kt              interface: listAabs(), executeStreaming(cmd) → Flow<LogLine>
ssh/SshjClient.kt             sshj-backed implementation; one connection per operation
data/SettingsStore.kt         DataStore Preferences + private-key file in filesDir
ui/InstallViewModel.kt        list AABs, run install, collect streaming logs
ui/SettingsViewModel.kt       settings form state + validation
ui/Screens.kt                 Compose: Settings / Installer / AabList / InstallProgress
LobberApplication.kt          Application singleton holding SettingsStore
MainActivity.kt               NavHost: loading → settings → installer
```

## Hard architectural rules

1. **`SshClient` is an interface, always.** `InstallViewModel` takes a
   `createClient: (SshConfig) -> SshClient` factory (defaulting to `::SshjClient`)
   so tests can inject a MockK-mocked client. Don't reach for `SshjClient`
   directly from a ViewModel.

2. **One SSH connection per operation.** `SshjClient` opens, authenticates,
   runs the command, tears down. Don't introduce a connection pool or a
   long-lived session — over a build LAN this is cheap, and the simplicity is
   intentional. A stale or half-broken pooled connection costs much more than a
   millisecond of TCP setup.

3. **Manual DI through `LobberApplication`.** The Application class holds
   `lateinit var settingsStore: SettingsStore`, initialized in `onCreate()`.
   ViewModels receive the store via `LobberViewModelFactory` in `MainActivity`.
   Don't add a DI framework; the indirection it would buy doesn't justify the
   cost for a two-VM app.

4. **Private SSH key lives in `filesDir/id_ed25519` with mode `0600`.** Written
   atomically in `SettingsStore.save()`. Don't move it to SharedPreferences,
   external storage, or anywhere else — the file is sandboxed by Android, and
   the explicit mode is a belt-and-suspenders defence.

---

## SSH host-key verification: known TODO

`SshjClient` currently uses `PromiscuousVerifier()` — it accepts any host key
without prompting. This is acceptable for a tool against a known build host on
a trusted LAN, but is **not safe over the open internet** and must be replaced
with a fingerprint trust-on-first-use flow before any wider distribution. The
TODO is marked in `ssh/SshjClient.kt`. Don't ship a release without addressing
it.

---

## Test conventions

### The runTest dispatcher rule (non-negotiable)

```kotlin
@get:Rule val mainDispatcherRule = MainDispatcherRule()

@Test fun whatever() = runTest(mainDispatcherRule.dispatcher) {
    // ...
}
```

**Always pass the dispatcher.** Plain `runTest { }` creates its own
`StandardTestDispatcher` with a separate `TestCoroutineScheduler`. Code under
test runs on `Dispatchers.Main` (the rule's `UnconfinedTestDispatcher`), so
`advanceTimeBy()` won't drive it. Tests fail or pass non-deterministically.

### Mock every flow the ViewModel reads at construction time

Every flow the ViewModel collects via `stateIn(...)` in its constructor or
`init` block must be stubbed before instantiating the VM. If you extend
`SettingsStore` with a new flow and forget to stub it in test setup, MockK
throws during VM construction and **all VM tests fail simultaneously** —
distinctive symptom: many reds at once, all with the same generic error.

```kotlin
private fun stubStore() {
    every { settings.config } returns flowOf(testConfig)
    every { settings.isConfigured } returns flowOf(true)
    coEvery { settings.save(any(), any(), any(), any(), any()) } just Runs
    // Add new SettingsStore flows here when extending the class.
}
```

### Other conventions

- New tests are **MockK only**. Do not introduce Mockito.
- Use Turbine (`vm.state.test { ... }`) to assert StateFlow emissions. StateFlow
  conflates, so prefer `expectMostRecentItem()` when several updates arrive
  faster than the test can read them.

---

## Localization

Lobber ships **English (default) and German**. UI strings live in
`res/values/strings.xml` (English) and `res/values-de/strings.xml` (German).
Composables resolve them via `stringResource(R.string.…)`.

ViewModel-emitted error strings are still hardcoded for now — see the
todo.md "ViewModel-Fehlermeldungen lokalisieren" entry. When adding a new
user-facing string, prefer `strings.xml` from the start; only fall back to
hardcoded strings inside ViewModels if you can't reach Context, and keep them
in English so the default locale matches.

To add a third locale: drop a `res/values-<locale>/strings.xml` with the
overrides; missing keys fall back to the English default.

---

## Versioning

`versionName` in `app/build.gradle.kts` matches the GitHub release tag exactly.
Keep them aligned.

---

## Git workflow: branch before non-trivial work

Larger changes — **bigger bugfixes, refactorings, new features, anything that
touches multiple files or could plausibly be reverted as a unit** — must happen
on a dedicated Git branch, never directly on `main`. Trivial edits (typo fix,
single-line tweak, doc nit) can stay on the current branch.

When in doubt, **stop and ask the user before starting**. It is always cheaper
to confirm than to realise mid-implementation that the work is on the wrong
branch.

**Workflow:**

1. Before writing code, propose a branch name that fits the topic and ask for
   confirmation. Suggested prefixes:
   - `fix/<slug>` — bugfix
   - `refactor/<slug>` — refactoring
   - `feature/<slug>` — new feature
   - `chore/<slug>` — tooling, build, dependencies
   - `test/<slug>` — test-only changes
2. Create the branch from an up-to-date `main` (or the appropriate base) and
   switch to it before the first edit.
3. If you notice mid-task that you are still on `main`, stop and remind the
   user — do not silently keep working.

The branch name proposal is a suggestion; the user gets the final say.

**After a fast-forward merge into `main`:** switch back to `main` and ask the
user whether the merged branch should be deleted both locally and on the
remote. Do not delete it silently — even though the commits live on in `main`,
the user may want to keep the branch around (open PR, ongoing review,
historical reference). Always confirm before `git branch -d` and especially
before `git push origin --delete`.

---

## What this file is NOT

- Not a description of the project (see `README.md`).
- Not a changelog (see `RELEASE_*.md`).
- Not the place for transient notes about ongoing refactors. Add those to
  short-lived feature branches' commit messages, not here.

Update this file when an architectural rule changes or a new hard-won lesson
deserves to be future-proof. Do not bloat it with details that are obvious from
reading the code.
