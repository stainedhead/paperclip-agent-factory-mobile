# PRD: Paperclip Agent Factory Mobile, Android app (Phase 2a)

**Created:** 2026-11-04
**Status:** Draft
**Owner:** J
**Related:** [Main PRD](../../paperclip-agent-factory-mobile-PRD.md) · [INTENT.md](../../INTENT.md) · [AGENTS.md](../../AGENTS.md) · [Design mocks](../../Paperclip-Remote-mobile-UI-design.html) · [Phase 2i iPhone PRD](../261102-phase-2i-iphone-ui/phase-2i-iphone-ui-PRD.md) · [Technical details](../../docs/technical-details.md) · [Product details](../../docs/product-details.md)

---

## Problem Statement

The shared Kotlin core is built and the iPhone app (Phase 2i) renders it on the real adapters. Android users, and the owner's own Android devices, still have nothing: there is no `androidApp/` module, and the shared code's Android half is deliberately incomplete. `PaperclipCore.createLiveGraph` throws on Android ("until the Android app phase supplies a `Context` and the Keystore"), `securestore` and `platform` ship only fail-closed stand-ins for Android (`UnavailableSecretStore`, `UnavailableBiometrics`, `OptimisticConnectivity`), and no CI workflow builds an APK.

Phase 2a delivers the Android app: a native Jetpack Compose UI with the same scope and flows as the iPhone app, on the same shared core. The core is not reimplemented. What Android adds is (1) the Android device services behind the existing ports, (2) the live-graph entry point for Android, and (3) Compose screens that render shared state and forward intents, exactly as the SwiftUI screens do.

## Goals

- **G1 Same scope and flows as the iPhone app.** Screens 1 to 13, 15 and 16 of the main PRD inventory (everything except screen 14, Notification settings, which is Phase 3), with the same states, role gating, offline behaviour and device-authentication gates.
- **G2 Share, do not reimplement.** Every business rule, use case, state holder, error mapping, refresh policy and API/persistence adapter is the existing shared Kotlin. The Android app adds no rule and no copy of one. New shared work is limited to the Android `actual`s of existing ports and one Android graph entry point.
- **G3 Native Android.** Material 3, a bottom navigation bar, top app bars, modal bottom sheets, predictive back, edge-to-edge, dynamic color with a fallback palette, `BiometricPrompt`, Custom Tabs for pairing. Shared behaviour, platform-shaped presentation (INTENT principle 4).
- **G4 Security parity or better.** Board keys only in the Android Keystore path of `SecretStore`; no secret in logs, backups, screenshots or the database; destructive actions gated by `BiometricPrompt` through the `Biometrics` port; no cleartext in release; recents-screen and screenshot protection.
- **G5 Demo mode and live mode behind one seam.** The app runs on `PaperclipCore.createDemoGraph()` in debug builds (and from the first-run "Try the demo factory"), and on the live graph otherwise, with identical Composables.
- **G6 Proof.** JVM/Robolectric-free state-holder tests stay in shared; Android adds Compose UI tests and Roborazzi screenshots on the demo graph, instrumented tests for the Keystore and biometric bridge, and an `android.yml` workflow. Screenshots replace the design mocks in the docs.
- **G7 Quality gates stay green.** Coverage on `domain`/`application` and `presentation` is not reduced; Konsist rules extend to cover `androidApp`; `./gradlew check lint` passes; Android Lint has zero new warnings.

## Non-Goals

- Anything the iPhone app does not do. If a feature is deferred on iOS it is deferred on Android (see "Parity ledger").
- Push notifications, FCM, the relay, and screen 14 (Phase 3).
- Tablet, foldable-specific, Wear OS, widgets, ongoing notifications (v1.1).
- Changing shared business rules. A gap found while building the UI becomes a shared Kotlin pull request with a failing test first, then the UI consumes it (AGENTS.md: shared first, UI second).
- Compose Multiplatform for iOS. The two UIs stay native (main PRD, "Key technology choices").
- Publishing to Google Play. Distribution stays the open question from the main PRD.

---

## What exists today (review findings)

Findings from reading the repository on `main` (commit `fe1e0b4`). They set the scope of the shared changes below.

| Area | State | Consequence for Android |
|---|---|---|
| Shared modules | Every module is KMP with targets `jvm`, `android` (AGP KMP library plugin, minSdk 28, compileSdk 37, host tests), `iosArm64`, `iosSimulatorArm64` (convention plugin `build-logic/.../pafm.kmp-library.gradle.kts`). | They already compile for Android; `androidApp` can depend on them directly. |
| Presentation | 16 state holders exposing `StateFlow<State>` + `onIntent`, 381 JVM tests, 98% coverage. UI text is keys (`ErrorKey`, `ActionsDisabledReason`, label enums), never prose. | Compose collects `StateFlow` directly (no SKIE needed). Android owns a `strings.xml` that resolves every key. |
| Umbrella | `PaperclipCore.createGraph / createDemoGraph / createLiveGraph`. `LivePlatform` is `internal`. `defaultLivePlatform()` on Android is `error(...)`. | Add an Android `LivePlatform` and a public Android entry point that takes a `Context`. |
| `api` | `PaperclipHttpClient` with an OkHttp engine on Android (`PlatformEngine.kt`, `PlatformFailures.kt`), `CallbackPairingBrowser`, 11 gateway adapters, contract-tested. | Reused unchanged. TLS/name failures already map to `CertificateInvalid` on Android. |
| `persistence` | `openAndroidPersistence(context, clock)` exists: SQLDelight Android driver, database in `noBackupFilesDir`, recreated empty if unusable. Its own KDoc says no emulator test covers it. | Wire it in; add the missing instrumented test. |
| `securestore` | iOS `KeychainSecretStore` only. Android has nothing but common `UnavailableSecretStore`. | Build `KeystoreSecretStore` (AES-256-GCM key in Keystore, ciphertext in a no-backup file). Largest new shared item. |
| `platform` | iOS `LocalAuthenticationBiometrics`, `NwConnectivity`. Common `SystemClock`, `UuidIdGenerator`, `SystemDeviceTimeZone`. Android has fail-closed stand-ins. | Build `BiometricPromptBiometrics` and `ConnectivityManagerConnectivity`. |
| Konsist | 67 rules in `shared/umbrella/src/konsistTest` guard shared modules and the iOS surface. `Secret.reveal()` is allowed only in infrastructure, `shared/testing` and `Secret`. | Extend rules to `androidApp` (no shared-layer bypass, no `reveal`, no direct gateway/adapter imports). |
| iOS app | `iosApp/Sources/App`: `DesignSystem`, `Environment` (`AppGraphProvider`: demo / first-run demo / live), `Localization`, `Models` (one `@Observable` model per holder), `Screens`, `Shell` (router, tab shell, lock, privacy shield, confirm sheets), `Pairing`. 376 Swift Testing cases, 131 XCUITests. | This is the template. Each Swift file has a Compose counterpart (map below). |
| CI | `shared.yml`, `ios.yml`, `contract.yml`, `security.yml`. `android.yml` listed in AGENTS.md but absent. | Add `android.yml`. |
| QR scan, deep links | Not implemented on iPhone (no match in `iosApp/Sources`). | Not in Android scope; tracked in the parity ledger. |

| Test source sets | The convention plugin enables only `withHostTest { }`. AGENTS.md and this PRD call for `androidDeviceTest` (Keystore, biometrics, persistence on an emulator), which does not exist yet. | Task 0.7 enables `withDeviceTest { }` in the convention plugin, adds the instrumentation dependencies and a Gradle task for the emulator job. Without it AND-001 and acceptance criterion 5 cannot be proven. |

**Known constraint:** the AGP KMP library plugin has no build types, so shared code cannot read `BuildConfig.DEBUG`. The `allowInsecureLocal` flag (`http://localhost` and `http://10.0.2.2` only) is therefore decided in `androidApp` (debug source set) and passed into `createLiveGraph`, as the iPhone app does with `#if DEBUG`.

---

## Architecture

### Layering (unchanged)

```
androidApp (Compose) ──► shared/umbrella (graph) ──► presentation ─► application ─► domain ◄─ infrastructure/*
```

`androidApp` talks to the shared core only through `umbrella` (graph, state holders) and the presentation/domain types it re-exports. It never imports an adapter, a gateway or a DTO. Compose code contains no business rule, no HTTP, no persistence and no secret handling (AGENTS.md "Apps render state and forward intents").

### New and changed modules

| Module | Change | Source set |
|---|---|---|
| `androidApp/` (new, `com.android.application`) | Compose UI, Application class, Activity, navigation, resources, manifest, network security config, backup rules | `main`, `debug`, `test`, `androidTest` |
| `shared/infrastructure/securestore` | `KeystoreSecretStore(context)` + internal `KeystoreBackend` seam (mirrors iOS `KeychainBackend`) | `androidMain`, tests in `androidDeviceTest` + `androidHostTest` (fake backend) |
| `shared/infrastructure/platform` | `BiometricPromptBiometrics(host)`, `ConnectivityManagerConnectivity(context)` | `androidMain`, `androidDeviceTest` |
| `shared/umbrella` | `AndroidLivePlatform` (internal) in `androidMain`; public `AndroidLiveGraph.openLiveGraph(context, allowInsecureLocal, pairingBrowser, settings)` (`@Throws`-equivalent: returns a result type, see below); replaces the `error(...)` in `defaultLivePlatform()` | `androidMain`, `androidHostTest` |
| `androidApp/benchmark` (new, `com.android.test`; added in spec review) | Macrobenchmark `StartupBenchmark`, frame timing, baseline profile generator (task 8.6) | `main` |
| `gradle/libs.versions.toml` | Compose BOM, activity-compose, navigation-compose, lifecycle-runtime-compose, androidx.biometric, androidx.browser, datastore (see Dependencies), Roborazzi, Compose UI test | catalog only |
| `.github/workflows/android.yml` (new) | lint, unit, screenshot tests, `assembleRelease`, instrumented tests on an emulator | CI |

`settings.gradle.kts` adds `:androidApp`. The module dependency set is `implementation(project(":shared:umbrella"))`; presentation/application/domain arrive through the umbrella's `api` exports, matching how the iOS app sees the framework.

### Android device services (the real new shared code)

| Port | Android implementation | Notes |
|---|---|---|
| `SecretStore` | `KeystoreSecretStore` | Per-app AES-256-GCM key in the Android Keystore, StrongBox when `FEATURE_STRONGBOX_KEYSTORE` is present, falling back to the TEE. `setUserAuthenticationRequired(true)` with per-use auth when biometric protection is on (FR-012). Ciphertext (IV + tag + bytes) in a file in `noBackupFilesDir`, one record per `SecretKey` (`instance/{id}/{name}`). Not `androidx.security:security-crypto` (deprecated; main PRD). `put` honours `SecretPolicy`; `deleteNamespace(instanceId)` removes that factory's records and nothing else. `KeyPermanentlyInvalidatedException` maps to `SecretReadResult.Invalidated` (so re-pairing is offered, FR-015); a cancelled or failed authentication, `UserNotAuthenticatedException` and lockout map to `SecretReadResult.Denied`; an absent record is `Missing`. These are the same variants the Keychain store returns, so behaviour is identical on both platforms. It never falls back to unprotected storage. |
| `Biometrics` | `BiometricPromptBiometrics` | `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` (fingerprint, face, then PIN/pattern, the Android equivalent of Face ID with passcode). Maps `AuthReason` (`UnlockApp`, `ConfirmDestructiveAction`) to localized prompt text supplied by the app (`AndroidLiveSettings`, mirroring `IosLiveSettings`). Maps success to `Approved`, user cancel (including back and the negative button) to `Cancelled`, a failed attempt after the system's retries to `Failed`, `ERROR_LOCKOUT` and `ERROR_LOCKOUT_PERMANENT` to `LockedOut`, and no enrolled credential, no hardware, no resumed activity or a required security update to `Unavailable` (the five existing `BiometricResult` values); anything that is not an approval blocks the mutation (DeviceAuthPolicy is unchanged). |
| `Connectivity` | `ConnectivityManagerConnectivity` | `registerDefaultNetworkCallback` into a `StateFlow<Boolean>`; online means a default network with `NET_CAPABILITY_VALIDATED`. Unregistered in `close()` from `PaperclipGraph.close()` through `LivePlatform.release()`. Replaces `OptimisticConnectivity` on Android, which would otherwise break FR-095 (offline gating). |
| `PairingBrowser` | existing `CallbackPairingBrowser` + a Custom Tabs launcher | The Kotlin app calls `CallbackPairingBrowser` directly; `Secret.reveal()` stays inside it, so no new reveal site appears (Konsist rule intact). The callback opens the approval URL in a Custom Tab (`androidx.browser`) and calls `finished()` when the user returns (activity resumes after the tab closes) or cancels. `StateFlow`-free; the shared pairing poller does the rest (FR-020). |
| `Clock`, `IdGenerator`, `DeviceTimeZone` | existing `SystemClock`, `UuidIdGenerator`, `SystemDeviceTimeZone` | Reuse. |
| Persistence | existing `openAndroidPersistence(context, clock)` | Reuse; add the emulator test it lacks. |

**Authenticating from a non-UI layer.** `BiometricPrompt` needs a `FragmentActivity` at prompt time, but the `Biometrics` port is called from a use case. `BiometricPromptBiometrics` therefore takes an `AuthHost` interface (in `platform/androidMain`) with `suspend fun current(): FragmentActivity?`; `MainActivity` registers itself while resumed. No activity (app in background) means `BiometricResult.Unavailable`, which blocks. This is recorded as an ADR.

**Entry point.** `AndroidLiveGraph.openLiveGraph(context, allowInsecureLocal, pairingBrowser, settings): AndroidGraphOpen` returns a sealed `Opened(graph)` / `Unavailable(cause)` so the app can show the shared `StartupNotice.LiveGraphUnavailable` with Try again and never falls back to fakes (same contract as `LiveIosGraph.openLiveGraph`, ADR-084, ADR-120). `PaperclipCore.createLiveGraph(...)` on Android keeps throwing `IllegalStateException` (no `Context`), now with the message pointing at `AndroidLiveGraph`.

### App structure (`androidApp`) and the iOS counterpart map

Package `com.stainedhead.paperclipmobile.android`. One Activity (`MainActivity`, `FragmentActivity` subclass so `BiometricPrompt` works), Compose navigation inside it.

| iOS (`iosApp/Sources/App`) | Android (`androidApp/src/main/kotlin/.../android`) |
|---|---|
| `PaperclipApp.swift`, `Environment/AppEnvironment.swift` | `PaperclipApplication` (owns the one graph per process), `AppEnvironment` (graph + provider + startup notice) |
| `Environment/AppGraphProvider.swift` (`Demo`, `FirstRunDemo`, `Live`) | `AppGraphProvider` (same three; `Live` uses `AndroidLiveGraph.openLiveGraph`) |
| `Environment/LaunchConfiguration.swift` (`-demo`) | `LaunchConfiguration` from an Intent extra `--ez demo true` and `BuildConfig.DEBUG` |
| `Models/*Model.swift` (`@Observable` per holder) | `HolderViewModel<S, I>`: an `androidx.lifecycle.ViewModel` that owns one state holder, exposes `state: StateFlow<S>`, `onIntent`, and closes the holder in `onCleared()` |
| `Models/*Presentation.swift` (Swift formatting of UI models) | `*Presentation.kt` (pure functions from state to display values; unit-tested on the JVM) |
| `Localization/*Labels.swift`, String Catalog | `res/values/strings.xml` + `*Labels.kt` mapping every key enum to `@StringRes` (`R.string.error_unreachable`, ...) |
| `DesignSystem/*` | `designsystem/` (Theme, Tokens, `AttentionCard`, `StatusPill`, `SeverityLabel`, `UpdatedLabel`, `DisabledReason`, `ErrorBanner`, `EmptyState`, `Motion`) |
| `Shell/RootView`, `TabShellView`, `AppRouter`, `RouteDestination` | `shell/RootScreen`, `TabShell` (Material 3 `NavigationBar` + `NavHost`), `AppRoute` (type-safe `@Serializable` routes), `RouteDestination` |
| `Shell/LockView`, `PrivacyShieldController` | `shell/LockScreen`, `PrivacyShield` (see Security) |
| `Shell/ConfirmSheet`, `ConfirmDestructiveSheet` | `ModalBottomSheet` composables with the lock glyph on gated buttons |
| `Pairing/ASWebAuthSessionPairingBrowser.swift` | `pairing/CustomTabsPairingBrowser.kt` |
| `Screens/*.swift` (24 files) | `screens/*Screen.kt` (one per screen, plus the same sub-views) |

Navigation mirrors the iOS router: five top-level destinations (Inbox, Factory, Work, Agents, Factories), each with its own back stack kept across tab switches (`NavHost` per tab, `saveState`/`restoreState`); routes `editFactory`, `settings`, `pairedDevices`, `manageFactories`, `decision`, `approval`, `agent`, `run`, `issue`; sheets `budgetIncident` and `settings`. The Inbox tab badge shows `deskBadgeCount` from the shared state.

### State ownership and lifecycle

- **Graph:** one per process, created in `PaperclipApplication` (or lazily on first launch), never recreated by rotation. `graph.close()` is for tests.
- **Holders:** one per screen instance, created by `graph.create...StateHolder()` inside a `ViewModel` keyed by the route, closed in `onCleared()`. Surviving rotation falls out of `ViewModel` scope; leaving the back stack entry closes the holder. A screen left while its mutation is in flight is closed only after the outcome is recorded (ADR-117 applies unchanged).
- **Refresh:** each screen starts `graph.refresh.start(holder.refreshTarget)` when it is `STARTED` and stops the session when it leaves (`LifecycleResumeEffect`). Foreground/background reaches the core through `ProcessLifecycleOwner` → `graph.lifecycle.setForeground(Boolean)`, which also drives the app lock (`WentToBackground`/`CameToForeground`).
- **Collecting state:** `collectAsStateWithLifecycle()`. After an intent the Composable reads the current value from the flow; it never keeps its own copy of business state. Free-text input (search, comment, answers, reasons) stays in `rememberSaveable` view state and only change intents go to the holder, because holders render asynchronously (ADR-108 rationale, unchanged).

---

## Functional Requirements

Behaviour is defined by the main PRD (FR-001 to FR-097), `docs/product-details.md` and the state holders. The requirements below are the Android-specific ones. "Same as iPhone" means: render the same state, send the same intents, show the same text keys, apply the same gating, with Material 3 shapes.

**Platform services (shared Kotlin, new `actual`s)**
- **AND-001** `KeystoreSecretStore` implements `SecretStore` and passes the existing `SecretStore` contract suite plus Android-specific tests: key invalidation after biometric enrolment change reports re-pair-needed; secrets are absent from the SQLDelight file, `noBackupFilesDir` plaintext and `adb backup`; `deleteNamespace` removes only that factory.
- **AND-002** `BiometricPromptBiometrics` implements `Biometrics` and passes the `Biometrics` contract suite; every non-approved outcome blocks the mutation; no activity means `Unavailable`.
- **AND-003** `ConnectivityManagerConnectivity` reports online/offline transitions to a `StateFlow<Boolean>`, so offline gating (`ActionsDisabledReason.Offline`) and refresh-on-reconnect work (FR-095, refresh policy).
- **AND-004** `AndroidLiveGraph.openLiveGraph` builds the live graph from a `Context`, `AndroidLiveSettings` (prompt texts, data directory, key alias) and a pairing browser; a failure to open the database or Keystore returns `Unavailable`, leaving nothing open and nothing changed. `LiveWiringRulesTest` and `LiveGraphTest` extend to the Android wiring (no fake in a `Live*` file; every resolved port is real).

**Shell and infrastructure (app)**
- **AND-010** App environment: demo or live graph chosen at launch (`demo` Intent extra in debug builds, and first-run "Try the demo factory"); on `StartupNotice.LiveGraphUnavailable` show the notice with Try again and a closed stand-in graph, never fakes.
- **AND-011** `HolderViewModel` pattern with a unit test per holder type that creating then clearing the ViewModel closes the holder, and that rotation does not.
- **AND-012** Tab shell, navigation, deep-link-ready routes (the `paperclipmobile://` scheme is reserved in the manifest but handles no links until the feature exists on iPhone), predictive back on every pushed screen and sheet.
- **AND-013** Device authentication: destructive confirmation sheets carry the lock glyph and call the use case; the use case calls the `Biometrics` port (so Compose never calls `BiometricPrompt` itself). App lock after the configured idle period via the shared `AppLockPolicy`; lock screen offers "Unlock" (biometric/credential).
- **AND-014** Privacy: `FLAG_SECURE` on the pairing screen and while the app lock is engaged; recents thumbnail hidden always (`setRecentsScreenshotEnabled(false)` on API 33+, `FLAG_SECURE` toggled around `onPause` below 33). Screenshots allowed elsewhere (main PRD NFR).
- **AND-015** Design system: Material 3 theme from the mock tokens (brand colors for severity and destructive styling, with dynamic color only for neutral surfaces so severity stays recognisable), light and dark, font scale to 200%, 48 dp touch targets, reusable composables with `@Preview`s.
- **AND-016** Errors, offline and empty states from keys: one string per `ErrorKey`, `ActionsDisabledReason`, `AddressIssue`, `StartupNotice`, `DeviceAuthIssue`; a unit test fails when any enum entry has no string (the Android twin of the String Catalog test).
- **AND-017** Localisation: all user-facing text in `strings.xml`; no string literals in composables (Android Lint `HardcodedText` is an error, plus a Konsist rule for `Text("...")` literals).

- **AND-018** Diagnostics without egress (observability). A Settings entry "Diagnostics" shows the app version, the pinned-server compatibility result, the last live-graph open outcome (`Opened`, or the cause class of `Unavailable`), the last Keystore and biometric error class and the last pairing outcome, as typed values only. "Copy report" puts a redacted text report (no URL, host, token, key id, company or user data) on the clipboard for the user to paste where they choose. Nothing is sent anywhere. Release logging is off; debug logging goes through one wrapper whose unit test rejects `Secret`, URLs and ids.
- **AND-019** Process death and configuration change. Rotation, font scale, locale and dark-mode changes keep holders and typed input (`ViewModel` + `rememberSaveable`). When the system kills the process: the route stack and the selected factory and company are restored from `SavedStateHandle`; typed free text is restored except on the pairing screens; secrets, pending device-auth prompts and in-flight mutations are never restored, and a screen that had a mutation in flight re-reads the entity and shows its true state (FR-096, `MutationResult.Unknown` wording). An interrupted pairing starts again from the address step.

**Screens (same as iPhone).** One requirement per screen. "Main FR" is the main PRD requirement the screen satisfies, "Goal" is G1 to G7 above. Common to all: render the state holder's state, send its intents, show the same text keys, apply the same role and offline gating, use Material 3 shapes.

| Id | Screen (main PRD #) | Main FR | Goal | Android specifics | Milestone |
|---|---|---|---|---|---|
| AND-020 | First run (15) | FR-009, FR-003 to FR-005, FR-020 | G1, G5 | Address probe, pairing in a Custom Tab, app-lock choice, "Try the demo factory" | 2a-3 |
| AND-021 | Factories (8) | FR-001, FR-007, FR-023 | G1 | Status, key days left, switch factory and company | 2a-4 |
| AND-022 | Add / Edit factory (9) | FR-003 to FR-006, FR-016, FR-017 | G1 | Address rules and refusals from `AddressIssue`, re-pair, pin field | 2a-4 |
| AND-023 | Manage factories (16) | FR-006, FR-007 | G1, G4 | Drag-to-reorder, remove with confirmation and device authentication | 2a-4 |
| AND-024 | Settings and profile (13), Paired devices | FR-014, FR-017, FR-024, FR-025 | G1, G4 | Device name, app lock and idle period, sign out, revoke keys, Diagnostics (AND-018) | 2a-4 |
| AND-030 | Inbox (1) | FR-030 to FR-035 | G1 | Decide now / This week / Whenever, filter chips from `countsBySourceKind`, inline approve/reject and budget actions where `inlineResolvable` and the role allow, swipe to snooze or dismiss with a 5 s Undo snackbar, `PullToRefreshBox`, "Updated <time>", "Open in web app" fallback | 2a-5 |
| AND-040 | Decision (2) | FR-040 to FR-044 | G1, G4 | Option cards with effect summaries before the tap, destructive styling plus lock glyph, inputs honouring `required` and `maxLength`, execution results, resolved-elsewhere states, Snooze only from an Inbox card (ADR-025) | 2a-5 |
| AND-041 | Approval (3) | FR-050, FR-051 | G1, G4 | Payload per type, Approve, Reject with note (device auth), Request revision, Comment | 2a-5 |
| AND-042 | Budget incident sheet (12) | FR-052 | G1, G4 | `ModalBottomSheet`, amount above observed spend, server reason on `422` | 2a-5 |
| AND-050 | Factory home (5) | FR-078 | G1 | Tiles, month spend against budget, window spend, runs in progress | 2a-6 |
| AND-051 | Agents (6), Agent detail (11) | FR-070 to FR-072 | G1, G4 | Grouping, Pause (device auth), Resume, Run now, owner-only monthly budget | 2a-6 |
| AND-052 | Run viewer (7) | FR-075, FR-090 | G1, G4 | Events, auto-scrolling log tail, Cancel (device auth), 5 s refresh while live | 2a-6 |
| AND-060 | Work list (10) | FR-060, FR-062 | G1 | Saved filters, search, create issue | 2a-7 |
| AND-061 | Issue (4) | FR-061 to FR-063 | G1 | Thread, Plan, Runs, Files; the five interaction kinds as native forms; comment, status and assignee; unsupported kinds open the instance URL; attachments through the system viewer with the shared ranged read | 2a-7 |
| AND-070 | Polish | NFR | G3, G6 | Font scale 200%, TalkBack, dark theme, reduced motion, haptics, 360 dp and large phones, adaptive icon, splash | 2a-8 |

## Parity ledger

Everything on iPhone, tracked so a gap is a decision, not an accident. Update during the phase.

| Item | iPhone | Android in this phase |
|---|---|---|
| Screens 1 to 13, 15, 16 | Built | Build all |
| Screen 14 Notification settings | Phase 3 | Phase 3 |
| QR scan to add a factory, `paperclipmobile://add` deep link (FR-004), export config as QR (FR-008) | Not built | Not built; one follow-up PR for both platforms |
| Live WebSocket events | None (board keys refused); polling | Same polling |
| Demo mode | `-demo` in Debug, first-run option | Same, via Intent extra in debug |
| Android-only | n/a | Predictive back, dynamic color for neutrals, Custom Tabs, `BiometricPrompt` credential fallback |

---

## Non-Functional Requirements

| Area | Requirement |
|---|---|
| Security | OWASP MASVS L2 for storage, crypto, auth, network and platform. Release builds: `android:allowBackup="false"`, `dataExtractionRules` excluding everything, `usesCleartextTraffic="false"`, no `networkSecurityConfig` exceptions except a `debug`-source-set config that permits cleartext to `localhost` and `10.0.2.2` only. R8 on in release; `Log` calls stripped; no crash SDK. `exported="false"` on everything except the launcher Activity. No secret in `Intent` extras, `SavedStateHandle`, `rememberSaveable` or the Compose semantics tree. |
| Performance | Cold start to the cached Inbox under 1.5 s: median of 10 `StartupBenchmark` runs (`StartupMode.COLD`) of the release build with the baseline profile, on a Pixel 6 (the target) and on the CI emulator image `google_apis` API 34 x86_64 (a regression guard), recorded in `status.md`. Scrolling 200 Inbox cards with at most 5% janky frames (`FrameTimingMetric` over a scripted fling). Compose stability: state classes from `presentation` are plain data classes; add a `stability_config.conf` listing the shared package as stable since it is compiled outside the Compose compiler. |
| Battery and data | No background work. Refresh sessions stop with `STOPPED`; no foreground service; no WorkManager in this phase. |
| Accessibility | WCAG 2.2 AA; font scale 200%; TalkBack names for every control; 48 dp targets; no information by colour alone (severity has icon + word, as iOS); Compose accessibility checks enabled in UI tests. |
| Observability | AND-018: typed, redacted, local-only diagnostics; no analytics, no crash SDK, no network egress. Errors map to the shared `ErrorKey`s, never raw exception text. |
| Reliability | AND-019: survives configuration change and process death without restoring secrets or repeating a mutation; the live graph opens once per process and `Unavailable` is retryable. |
| Platforms | Android 9 (API 28) and later, phones. `targetSdk` and `compileSdk` latest stable (the shared modules use 37). JDK 21 toolchain. |
| Compatibility | Tested on API 28, 34 and latest in CI emulators; Samsung and Pixel manual pass for `BiometricPrompt` and StrongBox behaviour. |
| Code quality | TDD; ktlint (Spotless), detekt zero issues, Android Lint zero new warnings, Konsist green; Kover gates for `core` and `presentation` not reduced. `androidApp` and the new `androidMain` code are outside the Kover gates (as infrastructure and apps are), but every rule they depend on is shared and tested. |
| Maintainability | A new screen needs one `ViewModel`, one Composable and one label mapping; nothing else. |

## Dependencies (to add to `gradle/libs.versions.toml`)

Versions are pinned in the catalog at implementation time (latest stable then; never hard-coded in a build script). Candidates: Compose BOM, `androidx.activity:activity-compose`, `androidx.navigation:navigation-compose`, `androidx.lifecycle:lifecycle-runtime-compose` and `lifecycle-viewmodel-compose`, `androidx.biometric:biometric`, `androidx.browser:browser`, `androidx.core:core-splashscreen`, `androidx.profileinstaller`, Roborazzi, `androidx.compose.ui:ui-test-junit4`, `androidx.test:runner`. The Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` uses the existing `kotlin` version. AGP 9.5 has built-in Kotlin support for application modules; the plan checks whether `androidApp` needs no extra Kotlin plugin and records the result. Still to confirm in the spec step (not checked yet): that claim about AGP 9.5, and the exact `SecretWriteResult` values the Keystore store must return.

---

## Implementation Plan

Follows the Feature Specification Workflow in AGENTS.md. Next step after this PRD is `/dev-flow:create-spec` to produce `specs/YYMMDD-phase-2a-android-ui/`. The milestones below are the plan it will detail. Every milestone is TDD (red, green, refactor), ends with the full gate set green, updates `status.md` after each task, and lands as its own pull request; **shared-module milestones merge before the UI milestones that consume them** (AGENTS.md "Pull Requests").

### Milestone map and ordering

```
2a-0  Foundations (androidApp skeleton, catalog, CI)  ──┐
2a-1  Android device services (shared, PR first)       ──┤
2a-2  Android live graph entry point (shared)          ──┴─► 2a-3 Shell + design system + first run
                                                            2a-4 Factories family (8, 9, 13, 16)
                                                            2a-5 Inbox + Decision + Approval + Budget
                                                            2a-6 Factory + Agents + Run
                                                            2a-7 Work + Issue
                                                            2a-8 Polish, accessibility, security hardening
                                                            2a-9 Docs, screenshots, release gate
```

2a-1 and 2a-2 touch `shared/` and can run alongside 2a-0 (which touches only `androidApp/`, the catalog and CI). 2a-3 onward are UI-only and could be split across parallel streams after 2a-3 lands (4, 5, 6, 7 are independent screen families, as on iPhone), though one Gradle build runs at a time on a developer machine.

### 2a-0 Foundations

| # | Task | Test / proof |
|---|---|---|
| 0.1 | Add `:androidApp` to `settings.gradle.kts`; `build.gradle.kts` with `com.android.application`, Compose plugin, `minSdk 28`, `targetSdk`/`compileSdk` latest, JDK 21, `applicationId com.stainedhead.paperclipmobile` (open question 7 of main PRD), debug and release build types | `./gradlew :androidApp:assembleDebug` builds an empty Activity |
| 0.2 | Version catalog entries (see Dependencies); no literal versions in build scripts | `DetektConfigRulesTest`/catalog check still green |
| 0.3 | Manifest: single launcher Activity, `allowBackup=false`, `dataExtractionRules`, `usesCleartextTraffic=false`, `debug/` network security config (localhost, 10.0.2.2), `FLAG_SECURE`-ready theme | Android Lint clean; instrumented test reads the merged manifest |
| 0.4 | Wire detekt, Spotless/ktlint and Android Lint to include `androidApp`; confirm zero issues on the skeleton | `./gradlew check lint` |
| 0.5 | `.github/workflows/android.yml`: lint → unit tests → Roborazzi verify → `assembleRelease`, then an emulator job for `androidDeviceTest`/`androidTest`; all actions SHA-pinned with version comments | `CiPinningRulesTest` green; workflow green on the PR |
| 0.6 | Konsist rules for `androidApp` (see Testing): no import of `infrastructure.*` (except the platform-service types the umbrella exposes), no `Secret.reveal`, no `Text("literal")`, composables never call `BiometricPrompt` | Each rule has a planted-violation test |
| 0.7 | Enable `withDeviceTest { }` (and the `androidx.test` runner, `ext-junit`, coroutines-test) in `pafm.kmp-library.gradle.kts` for the modules that need it (`securestore`, `platform`, `persistence`, `umbrella`); add a smoke device test per module; document the emulator command in `user-docs/getting-started.md` | `./gradlew :shared:infrastructure:securestore:connectedAndroidDeviceTest` runs on an emulator; `./gradlew check` stays device-free |

### 2a-1 Android device services (shared PR)

| # | Task (red first) | Notes |
|---|---|---|
| 1.1 | `KeystoreBackend` seam + `KeystoreSecretStore` against a `FakeKeystoreBackend` in `androidHostTest`; run the shared `SecretStore` contract suite | Mirrors `KeychainBackend`/`FakeKeychainBackend` |
| 1.2 | Real backend: AES-256-GCM key generation (StrongBox → TEE fallback), `setUserAuthenticationRequired` when policy says so, file store in `noBackupFilesDir`, atomic writes, invalidation mapping | `androidDeviceTest` on an emulator |
| 1.3 | `BiometricPromptBiometrics` + `AuthHost`; map prompt outcomes to `BiometricResult`; run the `Biometrics` contract suite against it with a scripted prompt shim | Host test with a fake prompt launcher; device test with `BiometricManager` stubs on the emulator |
| 1.4 | `ConnectivityManagerConnectivity`: callback → `StateFlow<Boolean>`, close() unregisters | Host test with a fake `ConnectivityManager` wrapper; device test toggles airplane mode via `cmd connectivity` |
| 1.5 | Persistence emulator test for `openAndroidPersistence`: file in `noBackupFilesDir`, recreate-on-corruption, marker sweep | Closes the gap in its KDoc |
| 1.6 | Konsist/`InfrastructureDeviceRulesTest` update: the secret store names no `SharedPreferences`, `getSharedPreferences`, `openFileOutput` to a backed-up dir, `Log` with a secret | Planted-violation tests |
| 1.7 | ADR: Keystore design, `AuthHost`, why not `security-crypto` | `docs/architectural-decision-record.md` |

Exit: contract suites green for all three; secrets provably absent from DB and backups; no Kover regression.

### 2a-2 Android live graph (shared PR)

| # | Task | Notes |
|---|---|---|
| 2.1 | `AndroidLivePlatform` (internal `LivePlatform`): Keystore store, BiometricPrompt, ConnectivityManager, `openAndroidPersistence`, system clock/id/timezone, OkHttp client | `LivePlatform.release()` closes connectivity |
| 2.2 | `AndroidLiveGraph.openLiveGraph(context, allowInsecureLocal, pairingBrowser, settings)` returning `Opened`/`Unavailable`; `defaultLivePlatform()` error message updated | Nothing left open on failure (as 84/120) |
| 2.3 | Tests: `LiveAndroidGraphTest` (host, JDBC-backed stand-in platform like `LiveGraphTest`), `LiveWiringRulesTest` extended, release-build cleartext refusal (`http` stored address fails with `CertificateInvalid`) | |
| 2.4 | Expose `AndroidLiveSettings` (prompt texts, key alias) | Same fields as `IosLiveSettings` where they apply |

### 2a-3 Shell, design system, first run

Deliver: `PaperclipApplication`, `AppEnvironment`, `AppGraphProvider`, `HolderViewModel`, theme/tokens, core components, `RootScreen` (startup notice, lock, first run vs tabs), `TabShell` with five empty tabs, `CustomTabsPairingBrowser`, `LockScreen`, `PrivacyShield`, confirm sheets, label mapping infrastructure and the "every key has a string" test, First-run screen on the demo graph and live graph. Proof: Compose UI test that a demo launch reaches the first-run flow and "Try the demo factory" reaches the Inbox shell; ViewModel lifecycle tests; screenshot baselines for light/dark.

### 2a-4 Factories family

Factories (8), Add/Edit factory (9), Manage factories with reorder (16), Settings (13), Paired devices. Proof per screen: Compose test asserting the demo values of the mock (Home factory 23 days left, Cloud signed out, Test signed out), the pairing sheet opens a Custom Tab intent (verified with Espresso-Intents style `intended` or a fake launcher), remove asks confirmation then device auth with call counters through the demo graph.

### 2a-5 Inbox, Decision, Approval, Budget incident

The decision-critical family: nine Acme Labs cards, badge 3, three Decide-now cards; Decision with three options and effect summaries; destructive option with biometric cancel and approve; Approval payload by type, Reject with note, Request revision; budget incident sheet with amount input and `422` reason; swipe actions and Undo; offline variant; viewer variant. Acceptance criteria 5 and 6 of the main PRD are verified here.

### 2a-6 Factory home, Agents, Agent detail, Run viewer

Tiles and spend windows; grouped agents; pause with device auth (cancel and approve paths); Run now; set monthly budget (owner only); Run viewer events, log tail, cancel with device auth; 5 s refresh interval while live. Acceptance criterion 8.

### 2a-7 Work list and Issue

Saved filters, search, create issue; Issue header, Thread/Plan/Runs/Files; the five interaction kinds with live validation; comment with failure returning text to the box; unsupported kinds show "Open in web app" with the instance URL. Acceptance criterion 7.

### 2a-8 Polish, accessibility, security hardening

Font scale 200% screenshots on Inbox, Decision, Approval (no truncated primary actions); TalkBack traversal order and labels; dark theme and dynamic-color neutrals; small-screen (360 × 640 dp) and large-phone layouts; reduced motion; haptics; adaptive icon, splash; recents/`FLAG_SECURE` behaviour verified on API 28, 33 and latest; R8 release build smoke-tested (keep rules for kotlinx.serialization/SQLDelight come from the shared libraries, confirmed by running the release APK in demo mode and in live mode against the pinned server); baseline profile; Macrobenchmark cold-start number recorded.

### 2a-9 Documentation, screenshots, release gate

README status, `docs/product-summary.md`, `docs/product-details.md` (Android column or section), `docs/technical-details.md` (Android modules, device services, entry point, CI row, test counts), ADRs, `user-docs/` (Android notes in first-factory, pairing with Custom Tabs, app lock with fingerprint/PIN, Tailscale on Android, troubleshooting, Android build and test in `getting-started.md`), real screenshots from Roborazzi demo-graph runs in `docs/images/android-*.png` and `user-docs/images/` (never the pairing screen, never real data). `status.md` closed, spec archived, review step (`/dev-flow:review-code`) findings fixed with one commit each.

---

## Testing Strategy

| Layer | Where | Tools | What |
|---|---|---|---|
| Rules and state | shared `commonTest` | existing | Unchanged. Any new rule found missing is added here first. |
| New Android services | `androidHostTest` + `androidDeviceTest` | kotlin.test, fake backends, emulator | Contract suites for `SecretStore`, `Biometrics`; Keystore invalidation; no secret in DB/backups |
| Presentation mapping | `androidApp/src/test` (JVM) | JUnit 5 (Vintage engine alongside JUnit 4, see spec section 13), Kotest assertions | `*Presentation.kt` formatting, key→string completeness, route table, `HolderViewModel` lifecycle |
| Compose screens | `androidApp/src/test` (JUnit 4 + Robolectric, decided in spec review; Compose UI test and Roborazzi require it) and `androidTest` | Compose UI test, semantic tags | Per-screen demo values, flows, role/offline variants; call counters through the demo graph prove gating |
| Screenshots | `androidApp/src/test` | Roborazzi | Light/dark, font scale 1.0 and 2.0, small and large phones; images feed the docs |
| Accessibility | `androidTest` | Compose accessibility checks, Espresso `AccessibilityChecks` | One pass per screen family; failures are recorded honestly in an ADR as on iPhone (ADR-111/119) |
| Architecture | `shared/umbrella/src/konsistTest` | Konsist | `androidApp` import rules, no literals in `Text`, no `reveal`, no direct `BiometricPrompt` in composables; planted-violation self-tests |
| Contract | `contractTest` (existing) | Docker Paperclip | Unchanged; Android adds a smoke run of the live graph against the pinned server in the emulator job if the runner allows |

## Security Review Points

1. Keystore key never exportable; `KeyPermanentlyInvalidatedException` forces re-pair and never degrades to plaintext.
2. No `reveal()` outside infrastructure; pairing text passes through `CallbackPairingBrowser`.
3. `FLAG_SECURE` on pairing and lock screens; recents thumbnail hidden.
4. Release manifest has no cleartext, no backup, no exported components beyond the launcher; verified by a test on the merged manifest.
5. Custom Tabs open only the server-provided `approvalUrl` after the shared core validated its host against the factory address (existing behaviour); the tab is never a WebView and never given credentials.
6. Intents: the only deep-link scheme handler is absent until the feature exists; extras never carry secrets.
7. Logs: release builds strip `Log`; no URL, token or key id in any message (the shared core already redacts `Secret`).

## Acceptance Criteria

1. Every screen in AND-020 to AND-060 is reachable in the demo app and renders the demo values of its mock, asserted by Compose tests using semantic tags.
2. Main flows work end to end on the demo graph: first run, switch factory and company, decide a decision (non-destructive and destructive, with the biometric prompt cancelled then approved), approve and reject a hire (reject with note), resolve the budget incident, answer the agent's two questions, pause an agent, cancel a run, create an issue, snooze and dismiss with Undo.
3. Role and offline variants: a viewer sees no actions; offline disables actions with their reasons and shows cached data with "Updated".
4. Device authentication: with the scripted `Biometrics` cancelled or failed, no mutation occurs (call counters stay at zero); on a real device and an emulator with enrolled credentials the system prompt appears and approval runs the use case.
5. Secrets: after pairing against the pinned server on the emulator, the board key exists only in the Keystore-wrapped record; the SQLDelight file, shared preferences, logcat and an `adb backup`/`bmgr` export contain no token; enrolling a new fingerprint makes the next read report re-pair-needed.
6. Live graph: a launch without the demo flag opens the real graph and starts at first run; failure to open shows the startup notice with Try again and never fakes; a release build refuses a stored `http` address.
7. Lifecycle: leaving a screen closes its holder, rotation does not, switching tabs keeps state, backgrounding stops refresh sessions and foregrounding resumes them and re-engages the app lock after the idle period.
8. Accessibility and localisation: the Compose accessibility checks report no errors on each screen family; at font scale 2.0 every primary action in a named list (Inbox Approve and Reject, Decision Confirm and Dismiss, Approval Approve, Reject and Request revision, Budget incident Resolve) is displayed, not clipped, and at least 48 dp tall (semantics assertions plus Roborazzi baselines); every key enum entry has a string (test); Lint `HardcodedText` is clean.
9. Parity: `docs/product-details.md` carries an Android column for screens 1 to 13, 15 and 16, each cell naming the test that proves it; the parity ledger has no row marked "gap" without a linked decision; the main PRD's acceptance criteria 1 to 9 and 11 to 13 are each mapped in `status.md` to a named Android test, and all of those tests pass.
10. Gates: `./gradlew check lint`, `:androidApp:assembleRelease`, instrumented tests on an emulator, and all five workflows (`shared`, `android`, `ios`, `contract`, `security`) green; Kover gates not reduced; docs and images updated in the same PRs.

## Dependencies and Risks

| # | Item | Impact | Mitigation |
|---|---|---|---|
| 1 | `BiometricPrompt` needs a `FragmentActivity` but the port is called from a use case | Prompt cannot show from the background; wrong activity on rotation | `AuthHost` with the resumed activity; no activity means blocked, never approved (ADR) |
| 2 | Keystore behaviour varies by vendor (StrongBox absence, key invalidation semantics, slow `setUserAuthenticationRequired`) | Re-pair surprises, crashes on exotic devices | Capability probe with TEE fallback, exhaustive exception mapping, manual pass on Pixel and Samsung, emulator tests at API 28/34/latest |
| 3 | Kotlin 2.4 / AGP 9.5 KMP library plugin has no variants; app build types differ | `allowInsecureLocal` and debug-only code cannot live in shared | Decide in `androidApp` and pass in, as iOS does; Lint/Konsist test that release has no cleartext |
| 4 | Compose treats classes from non-Compose modules as unstable | Needless recompositions on big lists (Inbox) | Compose stability config marking the shared packages stable; Macrobenchmark check |
| 5 | Parallel work with other streams touching `shared/` | Merge conflicts | Shared PRs (2a-1, 2a-2) small and first; UI stream owns `androidApp/**` and `docs/images/android-*` only |
| 6 | Emulator CI is slow and flaky | Red CI | Instrumented tests limited to Keystore, biometric, persistence and a handful of flows; JVM Compose/Roborazzi tests carry the bulk |
| 7 | Android 13+ photo/notification permission prompts creep in | Unwanted permission requests | None requested in this phase; Lint check that the manifest declares only `INTERNET` and `ACCESS_NETWORK_STATE` and `USE_BIOMETRIC` |
| 8 | Custom Tab return detection is indirect (no result callback) | Pairing stuck waiting | The shared poller already ends on approved/cancelled/expired; the browser callback only opens the tab; resume of the activity calls `finished()`; test both paths |
| 9 | No signing, versioning or R8 ownership defined | A release build cannot be installed or is minified wrongly | Debug-signed and a local upload-key-signed internal APK are enough for this phase's exit; `versionCode` derived from the commit count and `versionName` from the catalog; the release key is a CI secret and is never committed; R8 keep rules live in `androidApp/proguard-rules.pro`, and a test installs and launches the release APK in demo mode; Play signing is a distribution decision (open question 3 of the main PRD) |
| 10 | CI emulator has no StrongBox and a software TEE | Real-device Keystore behaviour is untested in CI | Emulator image fixed to `google_apis` x86_64 at API 28, 34 and the latest; the capability probe and fallback are unit-tested through the backend seam; a manual pass on a Pixel and a Samsung device is part of the 2a-8 exit |
| 11 | Drift between the two UIs | Inconsistent behaviour | Behaviour lives only in shared holders; parity ledger reviewed at every milestone exit |

## Open Questions

1. **Application id:** keep `com.stainedhead.paperclipmobile` (main PRD open question 7)? Default: yes until publishing is decided.
2. **Dynamic color:** use Material You for neutrals only (proposed) or full dynamic color? Default: neutrals only so severity colors keep their meaning.
3. **Device credential fallback:** allow PIN/pattern when no biometric is enrolled (proposed, `DEVICE_CREDENTIAL`), or require biometrics strictly? iOS uses the passcode fallback, so the default is to allow.
4. **Emulator CI:** acceptable to run an emulator job per PR (slow) or only on `main` and nightly? Default: per PR for the small instrumented set.
5. **Tablets:** phones only in this phase; should the manifest declare `android:resizeableActivity="false"` or allow resizing? Default: allow resizing, no tablet layouts.
6. **QR and deep links:** a single follow-up PRD for both platforms (FR-004, FR-008) after Android parity. Confirm.
