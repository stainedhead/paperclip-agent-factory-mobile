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

- [x] 2a-0 Foundations (androidApp skeleton, catalog, device-test source set, test runner, CI; order T0.1 to T0.4, T0.6, T0.7, T0.8, T0.5)
  - [x] T0.1 Add `:androidApp`
  - [x] T0.2 Version catalog entries
  - [x] T0.3 Manifest and resources: only the launcher Activity exported, `allowBackup=false`, `dataExtractionRul
  - [x] T0.4 Include `androidApp` in detekt, Spotless/ktlint, Android Lint
  - [x] T0.6 Konsist rules for `androidApp`
  - [x] T0.7 Enable `withDeviceTest {}`
  - [x] T0.8 Test runner decision and wiring (JUnit 4 + Robolectric for Compose/Roborazzi; ADR)
  - [x] T0.5 `.github/workflows/android.yml` (after T0.7 and T0.8)
- [x] 2a-1 Android device services (shared PR, merges before UI)
  - [x] T1.1 `KeystoreBackend` seam + `KeystoreSecretStore` over `FakeKeystoreBackend`
  - [x] T1.2 Real backend: AES-256-GCM key
  - [x] T1.3 `BiometricPromptBiometrics` + `AuthHost`
  - [x] T1.4 `ConnectivityManagerConnectivity`: default-network callback to `StateFlow<Boolean>`
  - [x] T1.5 Persistence emulator test for `openAndroidPersistence`
  - [x] T1.6 Konsist / `InfrastructureDeviceRulesTest` update: the secret store names no SharedPreferences, no wr
  - [x] T1.7 ADR: Keystore design, `AuthHost`, why not `security-crypto`
- [x] 2a-2 Android live graph entry point (shared PR)
  - [x] T2.1 `AndroidLivePlatform`
  - [x] T2.2 `AndroidLiveGraph.openLiveGraph(context, allowInsecureLocal, pairingBrowser, settings): AndroidGraph
  - [x] T2.3 Extend `LiveWiringRulesTest` / `LiveGraphTest` to Android wiring
  - [x] T2.4 `AndroidLiveSettings`
- [x] 2a-3 Shell, design system, first run
  - [x] T3.1 `PaperclipApplication`, `AppEnvironment`, `AppGraphProvider`
  - [x] T3.2 `HolderViewModel<S,I>` and lifecycle tests: clear closes the holder, rotation does not
  - [x] T3.3 Design system: theme, tokens, `AttentionCard`, `StatusPill`, `SeverityLabel`, `UpdatedLabel`, `Disab
  - [x] T3.4 `strings.xml` + `*Labels.kt`
  - [x] T3.5 `AppRoute` type-safe routes, `TabShell`
  - [x] T3.6 `AuthHost` registration in `MainActivity`, confirm sheets with lock glyph, `LockScreen`, `PrivacyShi
  - [x] T3.7 `CustomTabsPairingBrowser` over `CallbackPairingBrowser`, and the First-run screen
  - [x] T3.8 Process death and configuration change: `SavedStateHandle` for route stack and selection, `rememberS
  - [x] T3.9 Roborazzi wiring and light/dark shell baselines
- [x] 2a-4 Factories family (8, 9, 13, 16, Paired devices)
  - [x] T4.1 Factories screen
  - [x] T4.2 Add / Edit factory
  - [x] T4.3 Manage factories: reorder and remove
  - [x] T4.4 Settings and Paired devices
  - [x] T4.5 Diagnostics screen, redacted copy report, logging wrapper
- [x] 2a-5 Inbox, Decision, Approval, Budget incident
  - [x] T5.1 Inbox
  - [x] T5.2 Decision
  - [x] T5.3 Approval
  - [x] T5.4 Budget incident sheet
  - [x] T5.5 Offline and viewer variants across the family
- [x] 2a-6 Factory home, Agents, Agent detail, Run viewer
  - [x] T6.1 Factory home
  - [x] T6.2 Agents and Agent detail
  - [x] T6.3 Run viewer
- [x] 2a-7 Work list and Issue
  - [x] T7.1 Work list
  - [x] T7.2 Issue: Thread / Plan / Runs / Files
  - [x] T7.3 Interaction forms (attachments listed but not opened: shared-core gap, see implementation-notes.md and ADR-131)
- [ ] 2a-8 Polish, accessibility, security hardening (AND-070)
  - [x] T8.1 Font scale 2.0 semantics and screenshots for the named primary actions (attention, operations, Work, Issue, shell families; emulator at 2.0 checked on Inbox, Decision, Agents and first run)
  - [ ] T8.2 TalkBack names and order, Compose accessibility checks per screen family
  - [x] T8.3 Dark theme, dynamic-color neutrals, 360x640 dp and large phones, reduced motion, haptics, adaptive icon (placeholder art), splash (ADR-133). Found and fixed two font-scale 2.0 defects: Approval header pill, tab label wrap
  - [x] T8.4 Recents and `FLAG_SECURE` behaviour on API 28, 33, latest (Robolectric on the real activity at 28, 33, 36; emulator check API 37 only: demo Inbox in Recents is a black thumbnail; no API 28 or 33 image is installed)
  - [x] T8.5 R8 release build, keep rules, signing, versionCode / versionName, release APK launches in demo and live mode (demo through the `releaseDemo` type, ADR-132)
  - [ ] T8.6 `:androidApp:benchmark` module, baseline profile, Macrobenchmark cold start
  - [x] T8.7 Merged-manifest permission / exported-component test (exact permission set, three build types, APK check with aapt2); the manual Pixel and Samsung BiometricPrompt and StrongBox pass is NOT done (no such devices here)
  - [ ] T8.8 End-to-end demo-flow test (criterion 2)
  - [ ] T8.9 Emulator live-graph smoke (criteria 4, 5, 6)
- [ ] 2a-9 Documentation, screenshots, release gate
  - [ ] T9.1 README, product-summary, product-details
  - [ ] T9.2 `user-docs/` Android notes
  - [ ] T9.3 Map main PRD acceptance criteria 1 to 9 and 11 to 13 to named Android tests in `status.md`
  - [ ] T9.4 Close status, run `/dev-flow:review-code`, fix findings one commit each, archive the spec

- 2026-11-04: milestone 2a-7 implemented (T7.1 to T7.3): Work list, Issue (header, subtasks, Thread, Plan, Runs, Files, comment bar, status and assignee menus), the five interaction forms and the web-app door for unsupported kinds, placeholders removed. Gates: spotlessCheck, detekt, lint, konsistTest, allTests, :androidApp:testDebugUnitTest (482, 0 failures), :androidApp:verifyRoborazziDebug (55 baselines, 6 new), assembleDebug and assembleRelease. Emulator smoke on Pixel_9_Pro (demo): Work list and a subtask's Issue. Attachments cannot be opened (no shared port, ADR-064): reported. ADR-131.

## Main PRD acceptance criteria mapping (filled in T9.3)

| Main PRD criterion | Android test | State |
|---|---|---|
| 5 A decision with three options shows each option's effects; choosing one calls `decide`, then shows per-effect results | `DecisionScreenTest.everyOptionShowsItsEffectsBeforeAnythingIsChosen`, `aNonDestructiveChoiceGoesThroughWithoutAskingTheDeviceOwnerAndShowsWhatHappened`, `aDestructiveChoiceWaitsForTheDeviceOwnerAndDoesNothingWhenTheyCancel`; `DecisionVariantsTest.aPartialRunShowsEachEffectWithItsOwnStatusAndWhyOneWasSkipped` | Passing (2a-5) |
| 6 Every inline Inbox action resolves the item and the badge count updates without a manual refresh | `InlineActionsBadgeTest.approvingKeepingPausedAndRejectingEachResolveTheirCardAndTheBadgeFollows`; `BudgetSheetTest` (raise and keep paused from the sheet) | Passing (2a-5) |
| 7 An `ask_user_questions` interaction can be answered on the phone and the answer appears in the web app thread | `InteractionFlowTest.answeringValidatesLiveOneQuestionAtATimeAndAChoiceReplacesTheOneBeforeIt`, `aTypedAnswerInPlaceOfAnOptionCompletesTheSecondQuestionAndKeepsEveryCharacter`, `sendingTheAnswersMarksTheCardAnsweredAndTheQuestionLeavesTheInboxFeed` (demo graph, real holder, the demo `onAnswered` hook); `InteractionCardTest` (the other four kinds and the unsupported kind) | Android half passing (2a-7): the answer goes through the shared holder to the gateway, the card reads Answered and the Inbox feed drops the question. "Appears in the web app thread" needs a real server: T8.9 and the shared contract tests for `respond` |
| 1 to 4, 8, 9, 11 to 13 | [TBD] | Not Started |

Wording check (2a-7): the main PRD's criterion 7 is the `ask_user_questions` one above; tasks.md T7.3 points at it. The Android PRD's own criterion 7 is about lifecycle (holders closed on leaving, kept on rotation) and belongs to T3.2 and T8.x, not to the Work and Issue milestone.

## Performance record (T8.6)

[TBD] cold start median of 10 runs: Pixel 6 and CI emulator.

## Blockers

None.

## Recent activity

- 2026-11-04: spec created from the PRD (`/dev-flow:create-spec`).
- 2026-11-04: milestone 2a-0 implemented (T0.1 to T0.8, T0.5 last). Gates observed: spotlessCheck, detekt, lint, allTests, konsistTest, check, assembleDebug/Release, testDebugUnitTest, verifyRoborazziDebug green; the 4 smoke device tests passed on the Pixel_9_Pro AVD (API 37 arm64). android.yml has not run on GitHub yet. Decisions in implementation-notes.md, ADR-121 and ADR-122.
- 2026-11-04: `/dev-flow:review-spec`. Added T0.8 (test runner), T8.8, T8.9; reordered 2a-0; benchmark module added to the module tables; Keystore Missing-on-undecryptable and `namespaces()` tests added to T1.1/T1.2; edge-case, decisions and open-question sections added to spec.md; traceability tables added to tasks.md; PRD "28 Swift files" corrected to 24. Task count 57.
- 2026-11-04: milestone 2a-1 implemented (T1.1 to T1.7). Gates observed: spotlessCheck, detekt, lint, allTests, konsistTest (85 tests), koverVerifyCore, koverVerifyPresentation, :androidApp:assembleDebug, connectedAndroidDeviceTest on the Pixel_9_Pro emulator (securestore 15 with 2 phase tests skipped, platform 6, persistence 5, umbrella 2, 0 failures). Found and fixed: the Android SQLite framework silently replaced a corrupt database (T1.5); the merged-manifest rule was already red on profileinstaller. Not proven on the emulator: a real KeyPermanentlyInvalidatedException after a new fingerprint, the system BiometricPrompt UI, and StrongBox hardware (see ADR-123, T8.9).
- 2026-11-04: milestone 2a-3 implemented (T3.1 to T3.9). Gates observed: spotlessCheck, detekt, lint, konsistTest (90), allTests, :androidApp:testDebugUnitTest (125), verifyRoborazziDebug (7 baselines), assembleDebug, assembleRelease. Emulator smoke on Pixel_9_Pro: demo Inbox shell with badge 3, demo first run, live launch lock screen (FLAG_SECURE). ADR-125.
- 2026-11-04: milestone 2a-4 implemented (T4.1 to T4.5). Gates: spotlessCheck, detekt, lint, konsistTest (92), :androidApp:testDebugUnitTest (222), verifyRoborazziDebug (22 baselines). Emulator smoke on Pixel_9_Pro (demo). ADR-126, ADR-127.
- 2026-11-04: milestone 2a-5 implemented (T5.1 to T5.5): Inbox, Decision, Approval, Budget incident sheet, offline and viewer variants. Gates: spotlessCheck, detekt, lint, konsistTest, :androidApp:testDebugUnitTest, verifyRoborazziDebug (baselines for inbox, inbox offline, decision, approval and budget in light, dark and font scale 2.0). ADR-128, ADR-129.
- 2026-11-04: milestone 2a-6 implemented (T6.1 to T6.3): Factory home, Agents, Agent detail and Run viewer, with the Factory and Agents tab roots and the Agent and Run routes wired, `TabNavigator.select`, refresh while visible, and main PRD acceptance criterion 8 in virtual time. Gates: spotlessCheck, detekt, lint, konsistTest, :androidApp:testDebugUnitTest (405), :androidApp:verifyRoborazziDebug (49 baselines: Factory home, Agents, Agent detail and Run viewer in light, dark and font scale 2.0). ADR-130.
