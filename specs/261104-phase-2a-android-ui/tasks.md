# Tasks: Phase 2a Android UI

**Date:** 2026-11-04 · **Status:** Planning

## Progress summary

0 / 54 tasks complete (0 / 10 milestones).

## Task format

`ID` · title · dependencies · size (S, M, L) · TDD notes. Every task is red, green, refactor: write the failing test, see it fail for the stated reason, write the minimum code, refactor with all tests green. Every milestone ends with the full gate set green, `status.md` updated after each task, and its own pull request; shared milestones (2a-1, 2a-2) merge before the UI milestones that consume them.

## 2a-0 Foundations (androidApp skeleton, catalog, CI, device-test source set)

Milestone dependencies: none (parallel with 2a-1, 2a-2)

- **T0.1** Add `:androidApp` (com.android.application, Compose plugin, minSdk 28, JDK 21, applicationId, debug/release) to `settings.gradle.kts` · deps: - · size: M
  - Red: `:androidApp:assembleDebug` fails (module missing). Green: empty `MainActivity` builds. Refactor: share SDK values with the convention plugin constants.
- **T0.2** Version catalog entries (Compose BOM, activity/navigation/lifecycle compose, biometric, browser, splashscreen, profileinstaller, Roborazzi, ui-test, androidx.test); record the AGP 9.5 built-in Kotlin result (research Q5) · deps: - · size: S
  - Red: catalog rule test flags a literal version in `androidApp/build.gradle.kts`. Green: all versions via `libs`.
- **T0.3** Manifest and resources: only the launcher Activity exported, `allowBackup=false`, `dataExtractionRules`, `usesCleartextTraffic=false`, debug network security config (localhost, 10.0.2.2), permissions limited to INTERNET, ACCESS_NETWORK_STATE, USE_BIOMETRIC · deps: T0.1 · size: M
  - Red: merged-manifest test (release and debug variants) fails on missing attributes and on an extra permission.
- **T0.4** Include `androidApp` in detekt, Spotless/ktlint, Android Lint (`HardcodedText` as error); zero issues on the skeleton · deps: T0.1 · size: S
  - Red: a planted unformatted / hard-coded-text file fails the gate, then is removed.
- **T0.5** `.github/workflows/android.yml` (lint, unit, Roborazzi verify, assembleRelease, emulator job); actions SHA-pinned · deps: T0.1, T0.7 · size: M
  - Red: `CiPinningRulesTest` fails on an unpinned action in the new workflow.
- **T0.6** Konsist rules for `androidApp` (no `infrastructure.*` imports except umbrella-exposed platform types, no `Secret.reveal`, no `Text("literal")`, composables never call `BiometricPrompt`), each with a planted-violation self-test · deps: T0.1 · size: M
  - Red: a planted-violation test for each rule fails first because the rule does not exist.
- **T0.7** Enable `withDeviceTest {}` (androidx.test runner, ext-junit, coroutines-test) in `pafm.kmp-library.gradle.kts` for securestore, platform, persistence, umbrella; smoke device test per module; emulator command in `user-docs/getting-started.md` · deps: - · size: M
  - Red: `connectedAndroidDeviceTest` task absent / smoke test not found. Green: runs on an emulator; `./gradlew check` stays device-free.

## 2a-1 Android device services (shared PR, merges before UI)

Milestone dependencies: T0.7 for device tests; host tests have no dependency

- **T1.1** `KeystoreBackend` seam + `KeystoreSecretStore` over `FakeKeystoreBackend` (androidHostTest); run the shared `SecretStore` contract suite including `namespaces()` · deps: - · size: L
  - Red: the contract suite fails to compile against the missing class. Refactor: extract shared record encoding.
- **T1.2** Real backend: AES-256-GCM key (StrongBox then TEE), `setUserAuthenticationRequired` per policy, atomic file store in `noBackupFilesDir`, exception mapping to Missing/Denied/Invalidated, write results Stored/Failed · deps: T1.1, T0.7 · size: L
  - Red (androidDeviceTest): round trip; new-enrolment invalidation reports `Invalidated`; no plaintext in files or DB.
- **T1.3** `BiometricPromptBiometrics` + `AuthHost`; map outcomes to the five `BiometricResult` values; `Biometrics` contract suite with a scripted prompt shim · deps: - · size: L
  - Red: contract suite plus per-outcome mapping tests (cancel, lockout, no activity, no hardware).
- **T1.4** `ConnectivityManagerConnectivity`: default-network callback to `StateFlow<Boolean>` (validated capability), `close()` unregisters · deps: - · size: M
  - Red: host test with a fake ConnectivityManager wrapper: transitions and unregister on close.
- **T1.5** Persistence emulator test for `openAndroidPersistence` (noBackupFilesDir, recreate-on-corruption, marker sweep) · deps: T0.7 · size: M
  - Red: the device test fails until the harness exists; closes the gap named in its KDoc.
- **T1.6** Konsist / `InfrastructureDeviceRulesTest` update: the secret store names no SharedPreferences, no write to a backed-up dir, no `Log` with a secret · deps: T1.1 · size: S
  - Red: planted-violation tests.
- **T1.7** ADR: Keystore design, `AuthHost`, why not `security-crypto` · deps: T1.2, T1.3 · size: S
  - Doc: entry in `docs/architectural-decision-record.md`.

## 2a-2 Android live graph entry point (shared PR)

Milestone dependencies: T1.1, T1.3, T1.4

- **T2.1** `AndroidLivePlatform` (internal `LivePlatform`) with Keystore store, BiometricPrompt, ConnectivityManager, `openAndroidPersistence`, system clock/id/timezone; `release()` closes connectivity · deps: T1.1, T1.3, T1.4, T1.5 · size: M
  - Red: host test resolving every port as real and asserting `release()` unregisters.
- **T2.2** `AndroidLiveGraph.openLiveGraph(context, allowInsecureLocal, pairingBrowser, settings): AndroidGraphOpen` (`Opened` / `Unavailable`); nothing left open on failure; update the `defaultLivePlatform()` message · deps: T2.1, T2.4 · size: M
  - Red: `LiveAndroidGraphTest` Unavailable-leaves-nothing-open.
- **T2.3** Extend `LiveWiringRulesTest` / `LiveGraphTest` to Android wiring; release-build cleartext refusal (stored `http` address fails with `CertificateInvalid`) · deps: T2.2 · size: M
  - Red: the wiring rule flags a planted fake in an Android `Live*` file.
- **T2.4** `AndroidLiveSettings` (prompt texts, key alias) mirroring `IosLiveSettings` · deps: - · size: S
  - Red: construction / defaults test.

## 2a-3 Shell, design system, first run

Milestone dependencies: 2a-0, 2a-2

- **T3.1** `PaperclipApplication`, `AppEnvironment`, `AppGraphProvider` (Demo / FirstRunDemo / Live), `LaunchConfiguration` (AND-010) · deps: T0.1, T2.2 · size: M
  - Red: provider tests: demo extra only in debug; Unavailable shows the notice and a closed stand-in graph, never fakes.
- **T3.2** `HolderViewModel<S,I>` and lifecycle tests: clear closes the holder, rotation does not (AND-011) · deps: T3.1 · size: M
  - Red: per-holder-type ViewModel lifecycle tests.
- **T3.3** Design system: theme, tokens, `AttentionCard`, `StatusPill`, `SeverityLabel`, `UpdatedLabel`, `DisabledReason`, `ErrorBanner`, `EmptyState`, `Motion`, previews (AND-015) · deps: T0.1 · size: L
  - Red: contrast and 48 dp target tests on tokens and components.
- **T3.4** `strings.xml` + `*Labels.kt`; "every key enum entry has a string" test (AND-016, AND-017) · deps: T3.3 · size: M
  - Red: completeness test fails on the first unmapped entry of each key enum.
- **T3.5** `AppRoute` type-safe routes, `TabShell` (five tabs, per-tab back stack, badge from `deskBadgeCount`), `RootScreen` (AND-012) · deps: T3.2, T3.3 · size: L
  - Red: route table test; Compose test for tab state kept across switches and badge value.
- **T3.6** `AuthHost` registration in `MainActivity`, confirm sheets with lock glyph, `LockScreen`, `PrivacyShield` / `FLAG_SECURE` (AND-013, AND-014) · deps: T1.3, T3.5 · size: L
  - Red: Compose tests with scripted `Biometrics`: cancel or failed means zero mutation calls; lock engages after idle; FLAG_SECURE on lock and pairing screens.
- **T3.7** `CustomTabsPairingBrowser` over `CallbackPairingBrowser`, and the First-run screen (AND-020) · deps: T3.5, T3.6 · size: L
  - Red: demo launch reaches first run; "Try the demo factory" reaches the Inbox shell; both Custom Tab return paths call `finished()`.
- **T3.8** Process death and configuration change: `SavedStateHandle` for route stack and selection, `rememberSaveable` for free text except pairing (AND-019) · deps: T3.5 · size: M
  - Red: recreate-activity and process-death-simulation tests; no secrets restored.
- **T3.9** Roborazzi wiring and light/dark shell baselines · deps: T3.3 · size: S
  - Red: verify task fails with no baseline; then record baselines.

## 2a-4 Factories family (8, 9, 13, 16, Paired devices)

Milestone dependencies: 2a-3

- **T4.1** Factories screen (AND-021) · deps: T3.7 · size: M
  - Red: demo values (Home factory 23 days left, Cloud and Test signed out); switch factory and company.
- **T4.2** Add / Edit factory (AND-022) · deps: T4.1 · size: L
  - Red: each `AddressIssue` message; re-pair opens a Custom Tab intent (fake launcher).
- **T4.3** Manage factories: reorder and remove (AND-023) · deps: T4.1 · size: M
  - Red: remove asks confirmation then device auth; call counters via the demo graph.
- **T4.4** Settings and Paired devices (AND-024) · deps: T4.1 · size: M
  - Red: idle period, sign out and revoke keys flows.
- **T4.5** Diagnostics screen, redacted copy report, logging wrapper (AND-018) · deps: T4.4 · size: M
  - Red: report contains no URL, host, token or key id; wrapper rejects `Secret`, URLs and ids.

## 2a-5 Inbox, Decision, Approval, Budget incident

Milestone dependencies: 2a-3 (independent of 2a-4)

- **T5.1** Inbox (AND-030): groups, filter chips, inline actions, swipe snooze / dismiss with 5 s Undo, pull to refresh, Updated label, web-app fallback · deps: T3.7 · size: L
  - Red: nine Acme Labs cards, badge 3, three Decide-now cards; role and offline variants.
- **T5.2** Decision (AND-040) · deps: T5.1 · size: L
  - Red: effect summaries before tap; destructive option with biometric cancel then approve; required and maxLength inputs.
- **T5.3** Approval (AND-041) · deps: T5.1 · size: M
  - Red: payload by type; Reject with note needs device auth; Request revision; Comment.
- **T5.4** Budget incident sheet (AND-042) · deps: T5.1 · size: M
  - Red: amount above observed spend; server reason on `422`.
- **T5.5** Offline and viewer variants across the family; map main PRD acceptance criteria 5 and 6 · deps: T5.2, T5.3, T5.4 · size: M
  - Red: a viewer sees no actions; offline disables with reasons.

## 2a-6 Factory home, Agents, Agent detail, Run viewer

Milestone dependencies: 2a-3

- **T6.1** Factory home (AND-050) · deps: T3.7 · size: M
  - Red: tiles and spend windows from demo values.
- **T6.2** Agents and Agent detail (AND-051) · deps: T6.1 · size: L
  - Red: Pause cancel and approve paths, Run now, owner-only monthly budget.
- **T6.3** Run viewer (AND-052) · deps: T6.2 · size: L
  - Red: events, auto-scrolling log tail, Cancel with device auth, 5 s refresh while live; main PRD acceptance criterion 8.

## 2a-7 Work list and Issue

Milestone dependencies: 2a-3

- **T7.1** Work list (AND-060) · deps: T3.7 · size: M
  - Red: saved filters, search, create issue.
- **T7.2** Issue: Thread / Plan / Runs / Files (AND-061) · deps: T7.1 · size: L
  - Red: demo values per tab.
- **T7.3** Interaction forms (five kinds), comment failure returns text, unsupported kinds open the instance URL, attachments via the system viewer · deps: T7.2 · size: L
  - Red: live validation per kind; main PRD acceptance criterion 7.

## 2a-8 Polish, accessibility, security hardening (AND-070)

Milestone dependencies: 2a-4 to 2a-7

- **T8.1** Font scale 2.0 semantics and screenshots for the named primary actions (not clipped, at least 48 dp) · deps: T4.5, T5.5, T6.3, T7.3 · size: M
  - Red: semantics assertions fail on truncated actions.
- **T8.2** TalkBack names and order, Compose accessibility checks per screen family; honest ADR for failures · deps: T4.5, T5.5, T6.3, T7.3 · size: M
  - Red: accessibility checks.
- **T8.3** Dark theme, dynamic-color neutrals, 360x640 dp and large phones, reduced motion, haptics, adaptive icon, splash · deps: T4.5, T5.5, T6.3, T7.3 · size: M
  - Red: screenshot baselines.
- **T8.4** Recents and `FLAG_SECURE` behaviour on API 28, 33, latest · deps: T3.6 · size: M
  - Red: device test per API level.
- **T8.5** R8 release build, keep rules, signing, versionCode / versionName, release APK launches in demo and live mode · deps: T0.1 · size: L
  - Red: install-and-launch test of the release APK.
- **T8.6** Baseline profile, Macrobenchmark cold start (Pixel 6, CI emulator), jank guard, `stability_config.conf` · deps: T8.5 · size: M
  - Red: the benchmark misses its budget before the profile.
- **T8.7** Merged-manifest permission / exported-component test; manual Pixel and Samsung pass for BiometricPrompt and StrongBox · deps: T0.3 · size: S
  - Red: merged-manifest test.

## 2a-9 Documentation, screenshots, release gate

Milestone dependencies: 2a-8

- **T9.1** README, product-summary, product-details (Android column naming the proving tests), technical-details, ADRs · deps: T8.1 to T8.7 · size: M
  - Doc gate: the parity ledger has no unlinked gap.
- **T9.2** `user-docs/` Android notes; Roborazzi screenshots to `docs/images/android-*.png` and `user-docs/images/` (demo only, never pairing) · deps: T9.1 · size: M
  - Doc gate.
- **T9.3** Map main PRD acceptance criteria 1 to 9 and 11 to 13 to named Android tests in `status.md` · deps: T8.1 to T8.7 · size: S
  - All mapped tests pass.
- **T9.4** Close status, run `/dev-flow:review-code`, fix findings one commit each, archive the spec · deps: T9.1, T9.2, T9.3 · size: M
  - All five workflows green.

## Stub example

- **P1.1** example: first red test of a milestone · deps: none · S · a named test fails for the stated reason before any production code exists.
