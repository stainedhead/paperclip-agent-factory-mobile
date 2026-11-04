# Specification: Phase 2a Android UI

**Created:** 2026-11-04 · **Status:** Draft · **Spec dir:** `specs/261104-phase-2a-android-ui/`
**Source PRD:** [paperclip-agent-factory-android-PRD.md](./paperclip-agent-factory-android-PRD.md) · **Pattern:** [../261102-phase-2i-iphone-ui/spec.md](../261102-phase-2i-iphone-ui/spec.md) · **Main PRD:** [../../paperclip-agent-factory-mobile-PRD.md](../../paperclip-agent-factory-mobile-PRD.md)

Owner decisions for this run (2026-11-04): the PRD defaults are accepted for its six open questions (application id `com.stainedhead.paperclipmobile`; dynamic color for neutrals only; `DEVICE_CREDENTIAL` fallback allowed; emulator job per PR for the small instrumented set; resizeable activity allowed, no tablet layouts; QR and deep links as one follow-up PRD for both platforms).

## 1. Executive summary

Phase 2a delivers the Android app: a native Jetpack Compose UI with the same scope and flows as the iPhone app (Phase 2i), on the same shared Kotlin core. Business rules are not reimplemented. New shared work is limited to the Android `actual`s of existing ports (Keystore secret store, BiometricPrompt, ConnectivityManager) and one Android live-graph entry point. Everything else is `androidApp/`: Compose screens that render shared state and forward intents.

## 2. Problem statement

There is no `androidApp/` module. `PaperclipCore.createLiveGraph` throws on Android (`defaultLivePlatform()` in `LiveAndroidPlatform.kt` calls `error(...)`), `securestore` and `platform` ship only fail-closed Android stand-ins (`UnavailableSecretStore`, `UnavailableBiometrics`, `OptimisticConnectivity`), and no workflow builds an APK. Android users cannot decide, oversee or intervene on their factories from a phone.

## 3. Goals and non-goals

- **G1** Screens 1 to 13, 15 and 16 of the main PRD inventory (not 14, Phase 3), same states, role gating, offline behaviour and device-authentication gates as the iPhone app.
- **G2** Share, do not reimplement: no rule, use case, state holder, error mapping, refresh policy or API/persistence adapter is copied.
- **G3** Native Android: Material 3, bottom navigation, bottom sheets, predictive back, edge-to-edge, dynamic color for neutrals, `BiometricPrompt`, Custom Tabs for pairing.
- **G4** Security parity or better: Keystore-only secrets, no secret in logs, backups, screenshots or the database, destructive actions gated by `BiometricPrompt` through the `Biometrics` port, no cleartext in release, recents and screenshot protection.
- **G5** Demo and live mode behind one seam, identical Composables.
- **G6** Proof: shared state-holder tests stay in shared; Android adds Compose UI tests, Roborazzi screenshots on the demo graph, instrumented Keystore/biometric/persistence tests, and `android.yml`.
- **G7** Gates stay green: coverage not reduced, Konsist extended to `androidApp`, `./gradlew check lint` passes, zero new Lint warnings.

**Non-goals:** anything the iPhone app does not do (see parity ledger in the PRD); push, FCM, relay, screen 14; tablet, foldable, Wear OS, widgets (v1.1); changing shared business rules (gaps become shared PRs with a failing test first); Compose Multiplatform for iOS; Google Play publishing; QR scan and deep links (follow-up PRD for both platforms).

## 4. User requirements: functional requirements

Behaviour is defined by the main PRD (FR-001 to FR-097), `docs/product-details.md` and the state holders. The Android-specific requirements (full text in the PRD):

| ID | Requirement | Milestone |
|---|---|---|
| AND-001 | `KeystoreSecretStore` implements `SecretStore`; passes the shared contract suite; invalidation after enrolment change reports `Invalidated`; secrets absent from DB, plaintext files and `adb backup`; `deleteNamespace` removes only that factory | 2a-1 |
| AND-002 | `BiometricPromptBiometrics` implements `Biometrics`; passes the contract suite; every non-`Approved` outcome blocks; no activity means `Unavailable` | 2a-1 |
| AND-003 | `ConnectivityManagerConnectivity` reports online/offline to a `StateFlow<Boolean>` (offline gating, refresh on reconnect) | 2a-1 |
| AND-004 | `AndroidLiveGraph.openLiveGraph` builds the live graph from a `Context`; failure returns `Unavailable` and leaves nothing open | 2a-2 |
| AND-010 | App environment: demo or live graph at launch; `LiveGraphUnavailable` notice with Try again, never fakes | 2a-3 |
| AND-011 | `HolderViewModel` pattern with lifecycle tests per holder type | 2a-3 |
| AND-012 | Tab shell, navigation, predictive back, routes ready for deep links (none handled) | 2a-3 |
| AND-013 | Device authentication through the use case and port, never from Compose; app lock via `AppLockPolicy` | 2a-3 |
| AND-014 | Privacy: `FLAG_SECURE` on pairing and lock screens; recents thumbnail hidden | 2a-3 |
| AND-015 | Design system from the mock tokens, light/dark, font scale 200%, 48 dp targets | 2a-3 |
| AND-016 | One string per key-enum entry, enforced by a test | 2a-3 |
| AND-017 | All text in `strings.xml`; Lint `HardcodedText` error plus Konsist rule | 2a-3 |
| AND-018 | Diagnostics without egress: typed redacted report, copy to clipboard, no logging in release | 2a-4 |
| AND-019 | Process death and configuration change behaviour | 2a-3 |
| AND-020 | First run (15) | 2a-3 |
| AND-021 to AND-024 | Factories (8), Add/Edit factory (9), Manage factories (16), Settings (13) and Paired devices | 2a-4 |
| AND-030 | Inbox (1) | 2a-5 |
| AND-040 to AND-042 | Decision (2), Approval (3), Budget incident sheet (12) | 2a-5 |
| AND-050 to AND-052 | Factory home (5), Agents (6) and Agent detail (11), Run viewer (7) | 2a-6 |
| AND-060, AND-061 | Work list (10), Issue (4) | 2a-7 |
| AND-070 | Polish: font scale, TalkBack, dark theme, reduced motion, haptics, small and large phones, adaptive icon, splash | 2a-8 |

## 5. Non-functional requirements

- **Security:** OWASP MASVS L2 for storage, crypto, auth, network and platform. Release: `allowBackup=false`, `dataExtractionRules` excluding everything, `usesCleartextTraffic=false`, cleartext config only in the debug source set (localhost, 10.0.2.2), R8 on, `Log` stripped, no crash SDK, nothing exported except the launcher Activity, no secret in `Intent` extras, `SavedStateHandle`, `rememberSaveable` or semantics.
- **Performance:** cold start to cached Inbox under 1.5 s (median of 10 `COLD` Macrobenchmark runs, release with baseline profile, Pixel 6 and CI emulator `google_apis` API 34 x86_64); at most 5% janky frames scrolling 200 Inbox cards; Compose stability config for the shared packages.
- **Battery and data:** no background work, no foreground service, no WorkManager.
- **Accessibility:** WCAG 2.2 AA, font scale 200%, TalkBack names, 48 dp targets, no information by colour alone.
- **Observability:** AND-018 only; no analytics, no crash SDK, no egress.
- **Reliability:** survives configuration change and process death without restoring secrets or repeating a mutation; the live graph opens once per process and `Unavailable` is retryable.
- **Platforms:** Android 9 (API 28) and later, phones; compileSdk 37 as the shared modules; JDK 21.
- **Compatibility:** CI emulators API 28, 34, latest; manual Pixel and Samsung pass.
- **Code quality:** TDD; ktlint, detekt zero issues, Lint zero new warnings, Konsist green; Kover gates for core and presentation not reduced; `androidApp` and new `androidMain` code are outside the Kover gates.
- **Maintainability:** a new screen needs one `ViewModel`, one Composable and one label mapping.

## 6. System architecture

Layering is unchanged: `androidApp` -> `umbrella` -> presentation -> application -> domain <- infrastructure. `androidApp` imports only umbrella-exposed types. See [architecture.md](./architecture.md).

New and changed modules:

| Module | Change |
|---|---|
| `androidApp/` (new, `com.android.application`) | Compose UI, Application, Activity, navigation, resources, manifest, network security config, backup rules |
| `shared/infrastructure/securestore` | `KeystoreSecretStore(context)` + internal `KeystoreBackend` seam |
| `shared/infrastructure/platform` | `BiometricPromptBiometrics(host)`, `AuthHost`, `ConnectivityManagerConnectivity(context)` |
| `shared/umbrella` | `AndroidLivePlatform` (internal), public `AndroidLiveGraph.openLiveGraph(...)`, `AndroidLiveSettings` |
| `build-logic` (`pafm.kmp-library`) | `withDeviceTest {}` for the modules that need it |
| `gradle/libs.versions.toml` | Compose BOM, activity/navigation/lifecycle compose, biometric, browser, splashscreen, profileinstaller, Roborazzi, UI test |
| `.github/workflows/android.yml` | lint, unit, screenshot verify, assembleRelease, emulator job |

## 7. Scope of changes

Create: `androidApp/**`, Android `actual`s in securestore/platform/umbrella, Android Konsist rules, `android.yml`, device-test source sets, ADRs, `docs/images/android-*.png`, Android sections in docs and `user-docs/`.
Modify: `settings.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/.../pafm.kmp-library.gradle.kts`, `LiveAndroidPlatform.kt` and `LiveAndroidDefaultsTest`, Konsist tests in `shared/umbrella/src/konsistTest`, `README.md`, `docs/*`, `user-docs/*`.
Dependencies added: see PRD "Dependencies"; versions only in the catalog.

## 8. Breaking changes

None to API, config or schema. `PaperclipCore.createLiveGraph` on Android keeps throwing `IllegalStateException`; only the message changes. `UnavailableSecretStore`, `UnavailableBiometrics` and `OptimisticConnectivity` stay for the JVM target.

## 9. Success criteria and acceptance criteria

The ten acceptance criteria of the PRD apply verbatim (screens reachable with demo values; main flows on the demo graph; role and offline variants; device authentication; secrets; live graph; lifecycle; accessibility and localisation; parity with each main-PRD criterion 1 to 9 and 11 to 13 mapped to a named Android test in `status.md`; gates). Quality gates: `./gradlew spotlessCheck detekt lint allTests konsistTest koverVerifyCore koverVerifyPresentation check`, `:androidApp:assembleRelease`, instrumented tests on an emulator, all five workflows green, docs and images updated in the same PRs.

## 10. Risks and mitigation

The eleven risks of the PRD apply (AuthHost and background prompts; Keystore vendor variance; no build types in the KMP library plugin; Compose stability; parallel streams touching `shared/`; flaky emulator CI; permission creep; Custom Tab return detection; signing and R8 ownership; CI emulator without StrongBox; UI drift). Added by this spec: Roborazzi needs Robolectric on the JVM, which conflicts with "Robolectric-free" and with JUnit 5 (see research Q5); a Macrobenchmark module is required but is not in the PRD module table (research Q8).

## 11. Timeline and milestones

2a-0 Foundations, 2a-1 device services (shared), 2a-2 live graph (shared), 2a-3 shell/design system/first run, 2a-4 Factories family, 2a-5 Inbox/Decision/Approval/Budget, 2a-6 Factory/Agents/Run, 2a-7 Work/Issue, 2a-8 polish and hardening, 2a-9 docs and release gate. 2a-1 and 2a-2 run alongside 2a-0; 2a-4 to 2a-7 are independent after 2a-3. Task breakdown: [tasks.md](./tasks.md). One pull request per milestone, shared first. Calendar dates: [TBD].

## 12. References

- Source PRD: `specs/261104-phase-2a-android-ui/paperclip-agent-factory-android-PRD.md`
- [../../AGENTS.md](../../AGENTS.md), [../../INTENT.md](../../INTENT.md)
- [../261102-phase-2i-iphone-ui/](../261102-phase-2i-iphone-ui/) (iPhone template), [../../docs/technical-details.md](../../docs/technical-details.md), [../../docs/product-details.md](../../docs/product-details.md)
