# Research: Phase 2a Android UI

**Date:** 2026-11-04 · **Source PRD:** [paperclip-agent-factory-android-PRD.md](./paperclip-agent-factory-android-PRD.md)

## Research questions

1. What exactly do the `SecretStore`, `Biometrics`, `Connectivity` and `PairingBrowser` ports require of the Android implementations (signatures, result values)?
2. What does the convention plugin enable today, and what is needed for `androidDeviceTest`?
3. What does the Android live platform look like today, and what must the new entry point replace?
4. What does `openAndroidPersistence` already do, and what is untested?
5. Does AGP 9.5 need an extra Kotlin plugin for an application module?
6. Can Roborazzi and Compose UI tests run "Robolectric-free" on the JVM with JUnit 5?
7. How will the cold-start budget be measured (Macrobenchmark module)?

## Answers (verified against the code on this branch, 2026-11-04)

1. **Ports** (`shared/domain/.../domain/port/Ports.kt`):
   - `SecretStore`: `put(key: SecretKey, value: Secret, policy: SecretPolicy): SecretWriteResult`; `get(key: SecretKey, reason: String): SecretReadResult`; `delete(key)`; `deleteNamespace(instanceId)`; **`namespaces(): Set<InstanceId>`** (lists names only, no value read, no prompt; used to sweep secrets whose factory record is gone). The PRD does not mention `namespaces()`; the Keystore store must implement it and the contract suite exercises it. `reason` is the text for the authentication prompt.
   - `SecretTypes.kt`: `SecretWriteResult` is exactly `Stored`, `Failed`. `SecretReadResult` is `Found(secret)`, `Missing`, `Invalidated` (new fingerprint; re-pair, FR-015), `Denied` (person declined the guarding authentication). `SecretPolicy(requireUserPresence: Boolean)`. `SecretKey.path` is `instance/{id}/{name}` with names `boardToken`, `clientCertificate`, `clientCertificatePassword`. The PRD's mapping (KeyPermanentlyInvalidated -> `Invalidated`; cancelled, `UserNotAuthenticatedException`, lockout -> `Denied`; absent -> `Missing`) uses only values that exist; any storage or crypto failure on write -> `Failed`. Decision for the spec: a failed/unreadable (corrupt) record on read maps to `Missing`, never plaintext fallback (confirm in T1.2).
   - `Biometrics.authenticate(reason: AuthReason): BiometricResult`; `AuthReason` is `UnlockApp`, `ConfirmDestructiveAction`; `BiometricResult` is `Approved`, `Cancelled`, `Failed`, `LockedOut`, `Unavailable` (matches the PRD's five).
   - `Connectivity.isOnline: StateFlow<Boolean>`; `PairingBrowser.open(approvalUrl: Secret)`; `AppLifecycle.isForeground: StateFlow<Boolean>`.
2. **Convention plugin** (`build-logic/src/main/kotlin/pafm.kmp-library.gradle.kts`): plugins `kotlin.multiplatform`, `com.android.kotlin.multiplatform.library`, `kover`; targets `jvm` (JVM 21), `iosArm64`, `iosSimulatorArm64`, `android` with `compileSdk = 37`, `minSdk = 28`, `withHostTest { }` only; `allWarningsAsErrors`; commonTest has kotlin-test, kotest-assertions, turbine, coroutines-test. No `withDeviceTest`, so no `androidDeviceTest` source set exists anywhere (confirmed: no matches in any build file or source). T0.7 adds it. The exact DSL (`withDeviceTest {}` with instrumentation runner and dependency configuration) is not verified here; confirm against AGP 9.5 docs when T0.7 starts. Versions: Kotlin 2.4.21, AGP 9.5.0 (`gradle/libs.versions.toml`); `gradle.properties` has `android.useAndroidX=true` and no AGP opt-outs.
3. **Android live platform** (`shared/umbrella/src/androidMain/.../LiveAndroidPlatform.kt`): only `internal actual fun defaultLivePlatform(): LivePlatform = error("The live graph is not available on Android until ...")`. `LivePlatform` (commonMain, internal) requires `clock, idGenerator, timeZone, secretStore, biometrics, connectivity, persistence`, a default `httpClient(allowInsecureLocal)` and `release()`. An existing `LiveAndroidDefaultsTest` (androidHostTest) asserts the current behaviour and must change with T2.2. The iOS pattern to mirror is `LiveIosPlatform.kt` (`openLiveGraph` at line 75) with `PlatformServices.kt`. The umbrella exports `platform`, `presentation`, `application`, `domain` through `api`; `securestore` is not an `api` export, which suits keeping `KeystoreSecretStore` an implementation detail.
4. **AndroidPersistence** (`shared/infrastructure/persistence/src/androidMain/.../AndroidPersistence.kt`): `openAndroidPersistence(context, clock): PaperclipPersistence`; database `paperclip.db` in `noBackupFilesDir` via `noBackupDirectory(true)`; sidecars `-wal -shm -journal` and a `paperclip.db.sweep-pending` marker; recreated empty if unusable (`openRecovering`). Its KDoc states no emulator test covers it: T1.5.
5. **AGP 9.5 and the Kotlin plugin: unverified.** AGP 9 introduced built-in Kotlin support for application and library modules (no `org.jetbrains.kotlin.android` plugin needed), but this was not confirmed against AGP 9.5 documentation or a build spike in this step. The Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`, existing `kotlin` version) is still required. Record the result of the first `:androidApp:assembleDebug` in T0.2 and in implementation-notes.md. If an extra plugin is required, add it via the catalog.
6. **Roborazzi, Robolectric and JUnit 5.** Roborazzi's JVM screenshot mode runs on Robolectric (JUnit 4 runner), and Compose UI test rules are JUnit 4 based; the PRD says "JUnit 5" (AGENTS.md table) and "Robolectric-free where possible". Likely resolution: JUnit 4 + Robolectric for Compose and screenshot tests in `androidApp/src/test` (with the JUnit Vintage engine if the build uses the JUnit 5 platform), plain JUnit 5 for the pure `*Presentation`/`*Labels`/route tests. This is an open item to settle in T0.1/T3.9 and record as an ADR.
7. **Macrobenchmark and baseline profile** need a separate `com.android.test` module (for example `:androidApp:benchmark`) that is not in the PRD's module table; add it in T8.6 and to `settings.gradle.kts`.

## Industry standards

OWASP MASVS L2, Android Keystore and BiometricPrompt guidance, Material 3, WCAG 2.2 AA, Compose accessibility testing.

## Existing implementations

`iosApp/Sources/App` is the template (DesignSystem, Environment, Localization, Models, Screens, Shell, Pairing); the iOS Keychain store and `LocalAuthenticationBiometrics`/`NwConnectivity` are the platform precedent; `LiveIosGraph.openLiveGraph` (ADR-120) is the entry-point precedent.

## API documentation

Pinned Paperclip server and the contract-tested gateways are unchanged by this phase.

## Best practices

Test first; shared first, UI second; fakes behind seams (`KeystoreBackend`, `AuthHost`, fake ConnectivityManager wrapper) so host tests carry most of the proof; keep emulator tests few.

## Open questions

1. AGP 9.5 built-in Kotlin for an application module (Q5): verify at T0.1.
2. Exact `withDeviceTest {}` DSL and instrumentation dependencies in the KMP library plugin (Q2): verify at T0.7.
3. Roborazzi plus Robolectric plus JUnit 5/Vintage (Q6): decide at T0.1.
4. Where the Macrobenchmark module lives and whether the CI emulator can run it per PR (Q7).
5. Behaviour on a corrupt or undecryptable Keystore record that is not an invalidation: `Missing` (proposed) vs `Failed` semantic for reads (reads have no failure value).
6. The PRD's counts (16 state holders, 381 JVM tests, 98% coverage, 67 Konsist rules, 28 Swift files) were not re-verified in this step.

## References

[paperclip-agent-factory-android-PRD.md](./paperclip-agent-factory-android-PRD.md) · [../261102-phase-2i-iphone-ui/research.md](../261102-phase-2i-iphone-ui/research.md)
