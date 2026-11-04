# Status: Phase 2a Android UI

**Created:** 2026-11-04 · **Spec dir:** `specs/261104-phase-2a-android-ui/`

Update this file after every task and phase. It is the single source of progress.

## Overall progress

| Phase | State |
|---|---|
| Phase 0 Specification | Complete (review run 2026-11-04, gaps resolved in the spec files) |
| Phase 1 Research and data model | Not Started (initial `research.md` and `data-dictionary.md` drafted) |
| Phase 2 Architecture and plan | Not Started (drafts exist) |
| Phase 3 Task breakdown | Not Started (draft in `tasks.md`) |
| Phase 4 Implementation | Not Started |
| Phase 5 Completion and archive | Not Started |

## Phase 0 checklist

- [x] Spec created from the PRD
- [x] Research questions identified
- [x] Phase files initialized
- [x] `/dev-flow:review-spec` run (2026-11-04): verdict Implementation-ready after revisions; see Recent activity

## Milestones and tasks

- [ ] 2a-0 Foundations (androidApp skeleton, catalog, device-test source set, test runner, CI; order T0.1 to T0.4, T0.6, T0.7, T0.8, T0.5)
  - [x] T0.1 Add `:androidApp`
  - [x] T0.2 Version catalog entries
  - [x] T0.3 Manifest and resources: only the launcher Activity exported, `allowBackup=false`, `dataExtractionRul
  - [x] T0.4 Include `androidApp` in detekt, Spotless/ktlint, Android Lint
  - [ ] T0.6 Konsist rules for `androidApp`
  - [ ] T0.7 Enable `withDeviceTest {}`
  - [ ] T0.8 Test runner decision and wiring (JUnit 4 + Robolectric for Compose/Roborazzi; ADR)
  - [ ] T0.5 `.github/workflows/android.yml` (after T0.7 and T0.8)
- [ ] 2a-1 Android device services (shared PR, merges before UI)
  - [ ] T1.1 `KeystoreBackend` seam + `KeystoreSecretStore` over `FakeKeystoreBackend`
  - [ ] T1.2 Real backend: AES-256-GCM key
  - [ ] T1.3 `BiometricPromptBiometrics` + `AuthHost`
  - [ ] T1.4 `ConnectivityManagerConnectivity`: default-network callback to `StateFlow<Boolean>`
  - [ ] T1.5 Persistence emulator test for `openAndroidPersistence`
  - [ ] T1.6 Konsist / `InfrastructureDeviceRulesTest` update: the secret store names no SharedPreferences, no wr
  - [ ] T1.7 ADR: Keystore design, `AuthHost`, why not `security-crypto`
- [ ] 2a-2 Android live graph entry point (shared PR)
  - [ ] T2.1 `AndroidLivePlatform`
  - [ ] T2.2 `AndroidLiveGraph.openLiveGraph(context, allowInsecureLocal, pairingBrowser, settings): AndroidGraph
  - [ ] T2.3 Extend `LiveWiringRulesTest` / `LiveGraphTest` to Android wiring
  - [ ] T2.4 `AndroidLiveSettings`
- [ ] 2a-3 Shell, design system, first run
  - [ ] T3.1 `PaperclipApplication`, `AppEnvironment`, `AppGraphProvider`
  - [ ] T3.2 `HolderViewModel<S,I>` and lifecycle tests: clear closes the holder, rotation does not
  - [ ] T3.3 Design system: theme, tokens, `AttentionCard`, `StatusPill`, `SeverityLabel`, `UpdatedLabel`, `Disab
  - [ ] T3.4 `strings.xml` + `*Labels.kt`
  - [ ] T3.5 `AppRoute` type-safe routes, `TabShell`
  - [ ] T3.6 `AuthHost` registration in `MainActivity`, confirm sheets with lock glyph, `LockScreen`, `PrivacyShi
  - [ ] T3.7 `CustomTabsPairingBrowser` over `CallbackPairingBrowser`, and the First-run screen
  - [ ] T3.8 Process death and configuration change: `SavedStateHandle` for route stack and selection, `rememberS
  - [ ] T3.9 Roborazzi wiring and light/dark shell baselines
- [ ] 2a-4 Factories family (8, 9, 13, 16, Paired devices)
  - [ ] T4.1 Factories screen
  - [ ] T4.2 Add / Edit factory
  - [ ] T4.3 Manage factories: reorder and remove
  - [ ] T4.4 Settings and Paired devices
  - [ ] T4.5 Diagnostics screen, redacted copy report, logging wrapper
- [ ] 2a-5 Inbox, Decision, Approval, Budget incident
  - [ ] T5.1 Inbox
  - [ ] T5.2 Decision
  - [ ] T5.3 Approval
  - [ ] T5.4 Budget incident sheet
  - [ ] T5.5 Offline and viewer variants across the family
- [ ] 2a-6 Factory home, Agents, Agent detail, Run viewer
  - [ ] T6.1 Factory home
  - [ ] T6.2 Agents and Agent detail
  - [ ] T6.3 Run viewer
- [ ] 2a-7 Work list and Issue
  - [ ] T7.1 Work list
  - [ ] T7.2 Issue: Thread / Plan / Runs / Files
  - [ ] T7.3 Interaction forms
- [ ] 2a-8 Polish, accessibility, security hardening (AND-070)
  - [ ] T8.1 Font scale 2.0 semantics and screenshots for the named primary actions
  - [ ] T8.2 TalkBack names and order, Compose accessibility checks per screen family
  - [ ] T8.3 Dark theme, dynamic-color neutrals, 360x640 dp and large phones, reduced motion, haptics, adaptive i
  - [ ] T8.4 Recents and `FLAG_SECURE` behaviour on API 28, 33, latest
  - [ ] T8.5 R8 release build, keep rules, signing, versionCode / versionName, release APK launches in demo and l
  - [ ] T8.6 `:androidApp:benchmark` module, baseline profile, Macrobenchmark cold start
  - [ ] T8.7 Merged-manifest permission / exported-component test
  - [ ] T8.8 End-to-end demo-flow test (criterion 2)
  - [ ] T8.9 Emulator live-graph smoke (criteria 4, 5, 6)
- [ ] 2a-9 Documentation, screenshots, release gate
  - [ ] T9.1 README, product-summary, product-details
  - [ ] T9.2 `user-docs/` Android notes
  - [ ] T9.3 Map main PRD acceptance criteria 1 to 9 and 11 to 13 to named Android tests in `status.md`
  - [ ] T9.4 Close status, run `/dev-flow:review-code`, fix findings one commit each, archive the spec

## Main PRD acceptance criteria mapping (filled in T9.3)

| Main PRD criterion | Android test | State |
|---|---|---|
| 1 to 9, 11 to 13 | [TBD] | Not Started |

## Performance record (T8.6)

[TBD] cold start median of 10 runs: Pixel 6 and CI emulator.

## Blockers

None.

## Recent activity

- 2026-11-04: spec created from the PRD (`/dev-flow:create-spec`).
- 2026-11-04: `/dev-flow:review-spec`. Added T0.8 (test runner), T8.8, T8.9; reordered 2a-0; benchmark module added to the module tables; Keystore Missing-on-undecryptable and `namespaces()` tests added to T1.1/T1.2; edge-case, decisions and open-question sections added to spec.md; traceability tables added to tasks.md; PRD "28 Swift files" corrected to 24. Task count 57.
