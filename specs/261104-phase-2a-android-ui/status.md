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
- [x] 2a-8 Polish, accessibility, security hardening (AND-070)
  - [x] T8.1 Font scale 2.0 semantics and screenshots for the named primary actions (attention, operations, Work, Issue, shell families; emulator at 2.0 checked on Inbox, Decision, Agents and first run)
  - [x] T8.2 TalkBack names and order, accessibility audit per screen family (ADR-134): role and name defects found and fixed; framework checks unusable under Robolectric and TalkBack not run, recorded
  - [x] T8.3 Dark theme, dynamic-color neutrals, 360x640 dp and large phones, reduced motion, haptics, adaptive icon (placeholder art), splash (ADR-133). Found and fixed two font-scale 2.0 defects: Approval header pill, tab label wrap
  - [x] T8.4 Recents and `FLAG_SECURE` behaviour on API 28, 33, latest (Robolectric on the real activity at 28, 33, 36; emulator check API 37 only: demo Inbox in Recents is a black thumbnail; no API 28 or 33 image is installed)
  - [x] T8.5 R8 release build, keep rules, signing, versionCode / versionName, release APK launches in demo and live mode (demo through the `releaseDemo` type, ADR-132)
  - [x] T8.6 `:androidApp:benchmark` module, baseline profile, Macrobenchmark cold start (ADR-135; emulator numbers only, see the Performance record)
  - [x] T8.7 Merged-manifest permission / exported-component test (exact permission set, three build types, APK check with aapt2); the manual Pixel and Samsung BiometricPrompt and StrongBox pass is NOT done (no such devices here)
  - [x] T8.8 End-to-end demo-flow test (criterion 2): 14 connected tests on the Pixel_9_Pro emulator, all passing (ADR-136)
  - [x] T8.9 Emulator live-graph smoke (criteria 4, 5, 6): everything that needs no server executed and passing; the pairing against the pinned server was NOT executed (Docker daemon would not start here); fingerprint enrolment and the system prompt on a destructive action were NOT proven (ADR-137)
- [ ] 2a-9 Documentation, screenshots, release gate
  - [ ] T9.1 README, product-summary, product-details
  - [ ] T9.2 `user-docs/` Android notes
  - [ ] T9.3 Map main PRD acceptance criteria 1 to 9 and 11 to 13 to named Android tests in `status.md`
  - [ ] T9.4 Close status, run `/dev-flow:review-code`, fix findings one commit each, archive the spec

- 2026-11-04: milestone 2a-7 implemented (T7.1 to T7.3): Work list, Issue (header, subtasks, Thread, Plan, Runs, Files, comment bar, status and assignee menus), the five interaction forms and the web-app door for unsupported kinds, placeholders removed. Gates: spotlessCheck, detekt, lint, konsistTest, allTests, :androidApp:testDebugUnitTest (482, 0 failures), :androidApp:verifyRoborazziDebug (55 baselines, 6 new), assembleDebug and assembleRelease. Emulator smoke on Pixel_9_Pro (demo): Work list and a subtask's Issue. Attachments cannot be opened (no shared port, ADR-064): reported. ADR-131.

- 2026-11-04: milestone 2a-8 completed (T8.6, T8.8, T8.9). `:androidApp:benchmark` with baseline profile and nightly CI job (ADR-135), 14 connected demo-flow tests (ADR-136), live proofs that need no server plus an unrun pairing script (ADR-137). Measured: cold start to the Inbox with content 307 ms median of 10 on the emulator; the 5% jank budget is not met on the emulator. The pairing against the pinned server, fingerprint enrolment and the real prompt on a destructive action were not executed.

## Main PRD acceptance criteria mapping (filled in T9.3)

| Main PRD criterion | Android test | State |
|---|---|---|
| 5 A decision with three options shows each option's effects; choosing one calls `decide`, then shows per-effect results | `DecisionScreenTest.everyOptionShowsItsEffectsBeforeAnythingIsChosen`, `aNonDestructiveChoiceGoesThroughWithoutAskingTheDeviceOwnerAndShowsWhatHappened`, `aDestructiveChoiceWaitsForTheDeviceOwnerAndDoesNothingWhenTheyCancel`; `DecisionVariantsTest.aPartialRunShowsEachEffectWithItsOwnStatusAndWhyOneWasSkipped` | Passing (2a-5) |
| 6 Every inline Inbox action resolves the item and the badge count updates without a manual refresh | `InlineActionsBadgeTest.approvingKeepingPausedAndRejectingEachResolveTheirCardAndTheBadgeFollows`; `BudgetSheetTest` (raise and keep paused from the sheet) | Passing (2a-5) |
| 7 An `ask_user_questions` interaction can be answered on the phone and the answer appears in the web app thread | `InteractionFlowTest.answeringValidatesLiveOneQuestionAtATimeAndAChoiceReplacesTheOneBeforeIt`, `aTypedAnswerInPlaceOfAnOptionCompletesTheSecondQuestionAndKeepsEveryCharacter`, `sendingTheAnswersMarksTheCardAnsweredAndTheQuestionLeavesTheInboxFeed` (demo graph, real holder, the demo `onAnswered` hook); `InteractionCardTest` (the other four kinds and the unsupported kind) | Android half passing (2a-7): the answer goes through the shared holder to the gateway, the card reads Answered and the Inbox feed drops the question. "Appears in the web app thread" needs a real server: T8.9 and the shared contract tests for `respond` |
| 1 to 4, 8, 9, 11 to 13 | [TBD] | Not Started |

Wording check (2a-7): the main PRD's criterion 7 is the `ask_user_questions` one above; tasks.md T7.3 points at it. The Android PRD's own criterion 7 is about lifecycle (holders closed on leaving, kept on rotation) and belongs to T3.2 and T8.x, not to the Work and Issue milestone.

## Performance record (T8.6)

Measured 2026-11-04 on the Pixel_9_Pro AVD (`sdk_gphone64_arm64`, API 37, arm64, the Mac's host GPU), with `scripts/run-benchmarks.sh`, on the R8-minified `benchmark` build type (demo graph) with the committed baseline profile (`CompilationMode.Partial`). This is the **emulator regression guard, not the Pixel 6 target**: Macrobenchmark refuses emulators and was run with `androidx.benchmark.suppressErrors=EMULATOR` for this module only (ADR-135). No Pixel 6 and no x86_64 API 34 CI emulator was available here, so neither PRD environment has a number yet; the nightly `benchmark` job in `android.yml` has not run on GitHub.

| Metric | Result | PRD budget | Read |
|---|---|---|---|
| Cold start, time to initial display, median of 10, with baseline profile | 238.9 ms (min 168.6, max 246.2) | none stated for TTID | |
| Cold start to the Inbox with content (`timeToFullDisplayMs`), median of 10, with baseline profile | 307.4 ms (min 245.7, max 339.1) | under 1.5 s | Within budget on this emulator. The demo Inbox is in memory, so no network or database read is included. |
| Same, no profile (`CompilationMode.None`) | TTID 228.6 ms, full display 356.3 ms | | The profile saves about 49 ms (14%) at full display; TTID is within noise. |
| Fling over 209 Inbox cards, 5 runs of 10 flings, `FrameTimingMetric` | 2502 frames; 67% overran their deadline, 55% took over 16.7 ms of CPU, 11% over 33.3 ms; CPU P50 17.0 ms, P90 34.0, P99 42.6 | at most 5% janky | **Not met on this emulator.** An emulator renders through the host GPU stack and says little about a phone, but the figure is a regression baseline and an open item for the Pixel 6 run and the release gate. Not investigated further. |

## End-to-end demo flows (T8.8, main PRD acceptance criterion 2)

`:androidApp:connectedDebugAndroidTest` (`androidApp/src/androidTest/.../flows`) drives the real `MainActivity` and the real tab shell on the demo graph on a device, one test per step, each in a fresh process (Android Test Orchestrator with `clearPackageData`, because the app fixes its graph when the process starts). Run 2026-11-04 on the Pixel_9_Pro AVD (API 37): 14 tests, 0 failures. `check` does not run them. The Robolectric counterpart (same tags, screen by screen) is in the right-hand column.

| Step of criterion 2 | Connected test (real activity and shell) | Robolectric counterpart |
|---|---|---|
| First run to the Inbox | `ShellFlowsTest.theFirstRunFlowIsWalkedFromTheAddressToTheInbox`, `theDemoFactoryCanBeTriedFromTheFirstRunScreen` | `MainActivityTest.theFirstRunFlowCanBeWalkedToItsEnd`, `aDemoLaunchWithNoFactoryReachesFirstRunAndTryTheDemoFactoryReachesTheInboxShell` |
| Switch factory and company | `ShellFlowsTest.anotherCompanyAndAnotherFactoryAreSwitchedTo` | `FactoriesScreenTest.choosingAnotherCompanySwitchesTheAppToIt`, `tappingAFactorySwitchesTheAppToIt` |
| Decide, non-destructive: no prompt, per-effect results | `AttentionFlowsTest.aNonDestructiveDecisionGoesThroughWithoutAskingTheDeviceOwner` | `DecisionScreenTest.aNonDestructiveChoiceGoesThroughWithoutAskingTheDeviceOwnerAndShowsWhatHappened` |
| Decide, destructive: biometric cancelled, then approved | `AttentionFlowsTest.aDestructiveDecisionWaitsForTheDeviceOwnerAndRunsOnlyAfterTheyApprove` | `DecisionScreenTest.aDestructiveChoiceWaitsForTheDeviceOwnerAndDoesNothingWhenTheyCancel` |
| Approve a hire | `AttentionFlowsTest.aHireIsApprovedWithoutTheDeviceOwner` | `ApprovalScreenTest.approvingResolvesItWithoutAskingTheDeviceOwner` |
| Reject a hire with a note (cancelled, then approved) | `AttentionFlowsTest.aHireIsRejectedWithANoteAndOnlyAfterTheDeviceOwnerApproves` | `ApprovalScreenTest.rejectingNeedsANoteThenTheDeviceOwnerAndStaysOpenIfTheyCancel` |
| Resolve the budget incident | `AttentionFlowsTest.theBudgetIncidentIsKeptPausedFromItsSheet` | `BudgetSheetTest.keepingPausedResolvesItWithoutTheDeviceOwnerAndTheCardGoesFromTheInbox`, `theAmountMustBeAboveWhatWasSpentAndTheRaiseWaitsForTheDeviceOwner` |
| Answer the agent's two questions | `AttentionFlowsTest.theAgentsTwoQuestionsAreAnsweredAndTheCardReadsAnswered` | `InteractionFlowTest.sendingTheAnswersMarksTheCardAnsweredAndTheQuestionLeavesTheInboxFeed` |
| Pause an agent (cancelled, then approved) | `OperationsFlowsTest.anAgentIsPausedAfterTheDeviceOwnerApprovesAndNothingChangesWhenTheyCancel` | `AgentDetailScreenTest.pauseAsksFirstThenWaitsForTheDeviceOwnerAndChangesNothingWhenTheyCancel` |
| Cancel a run (cancelled, then approved) | `OperationsFlowsTest.aRunIsCancelledAfterTheDeviceOwnerApprovesAndNothingChangesWhenTheyCancel` | `RunViewerScreenTest.cancelMovesTheRunToCancelledWhenTheDeviceOwnerApprovesAndTheButtonGoes`, `cancelAsksFirstThenWaitsForTheDeviceOwnerAndStopsNothingWhenTheyCancel` |
| Create an issue | `OperationsFlowsTest.anIssueIsCreatedWithItsPriorityAndOpened` | `WorkListScreenTest.aNewIssueIsCreatedWithItsPriorityAndOpened` |
| Dismiss with Undo | `AttentionFlowsTest.aCardIsDismissedAndUndoBringsItBack` | `InboxScreenTest.dismissingFromTheCardsActionRemovesItAndUndoBringsItBack` |
| Snooze with Undo | `AttentionFlowsTest.aCardIsSnoozedAfterChoosingAPresetAndUndoBringsItBack` | `InboxScreenTest.snoozingAsksForAPresetFirstThenRemovesTheCardWithAnUndo` |

The connected tests count device-owner prompts through the demo graph's `DemoControls` (the same counter the Robolectric tests read). The real system `BiometricPrompt` is not shown on the demo graph, which scripts its answers; see T8.9 for what the real prompt proof covers.

## Live graph proofs (T8.9, ADR-137)

Run 2026-11-04 on the Pixel_9_Pro AVD (API 37, arm64, PIN 1234 set).

**Executed and passing**

| Claim | Proof | Result |
|---|---|---|
| A launch without the demo flag opens the real graph at first run (criterion 6) | `scripts/android-live-smoke.sh`, step 1: debug app after `pm clear`, `uiautomator dump` shows the first-run or lock screen and none of the demo data. Also `AndroidLiveGraphDeviceTest.theRealGraphOpensAtTheEmptyFirstRunState` and `theDevicePortsAreTheRealAdaptersAndNoneIsAFake` (real Keystore store, real prompt adapter, real connectivity, no fake) | OK |
| The board key is only in the Keystore-wrapped record: not in SQLDelight, shared preferences, no_backup, cache or any file (criterion 5) | `SecretsAbsentDeviceTest.theBoardKeyIsInNoFileOfTheAppNotEvenTheDatabase`: real Keystore store and real SQLite file side by side, every file under the data directory scanned (database, WAL, `shared_prefs`, `no_backup`, cache, secret records), and the record read back | OK |
| ... nor in the log | `SecretsAbsentDeviceTest.theBoardKeyIsNotInTheLogAfterItIsStoredAndRead` (new): `logcat -d` after the store and read; plus `scripts/android-live-smoke.sh` step 2 on the running app (`run-as` over 13 files plus the whole log, pattern `pcp_board_`) | OK |
| The Keystore key is AES-256, not exportable; records live in `noBackupFilesDir`; flipped bytes and a missing key read as Missing | `theKeyIsAnAes256KeyThatCannotBeExported`, `theRecordsLiveInNoBackupFilesDir`, `aRecordWithFlippedBytesReadsAsMissing`, `aRecordRestoredWithoutItsKeyReadsAsMissing` (`:shared:infrastructure:securestore:connectedAndroidDeviceTest`, 12 passed, 3 skipped, see below) | OK |
| A backup export holds no token | `allowBackup=false` in the debug and release manifests; `bmgr backupnow com.stainedhead.paperclipmobile` answered "Backup is not allowed"; `adb backup` waits for a confirmation on the device and exported 0 bytes within its 15 s | OK, with the caveat that `adb backup` was not confirmed on the device, so the archive was empty rather than inspected |
| A release build refuses a stored `http` address (criterion 6) | `AndroidLiveGraphDeviceTest.aReleaseGraphRefusesAStoredHttpAddressBeforeAnyRequest`: an `http://localhost:3100` instance and key stored through the real stores, the graph opened with `allowInsecureLocal = false` (the release value of `LiveGraphProvider`), the first read answers `CertificateInvalid` before any request. And `scripts/android-live-smoke.sh` step 4: the release APK's manifest has `usesCleartextTraffic=false` and no network security config, so the OS refuses cleartext as well. This is the release **configuration** and the release **manifest**; a stored record cannot be planted into the release APK's own data without root | OK |

**NOT executed**

- **Pairing against the pinned Paperclip server (criterion 4 and the live half of 5).** The Docker daemon would not start in this session (`open -a Docker` started no process; `docker info` could not reach `docker.sock`; Podman has no machine, and the pinned image is amd64 only). The pinned image exists on ghcr.io (`2026.1023.0`, manifest fetched), but it was not pulled or run. No live-server number or claim is made. `scripts/android-live-pairing.sh` is written for the owner or a runner with Docker: it starts the server, makes the first board user, routes the device with `adb reverse` to `http://localhost:3199` (the origin the server trusts; `10.0.2.2` would hit INVALID_ORIGIN and is cleartext-allowed in debug only), approves the app's pairing challenge as the board user (the browser's part, since the Custom Tab cannot be driven headlessly), and after you pair by hand checks the Keystore record, the files, the log and `bmgr`. The script itself has not run once; expect to fix it on first use.
- **Enrolling a new fingerprint makes the next read report re-pair-needed.** Needs a Settings UI walk with an enrolled fingerprint on a device with the Keystore bound to biometric enrolment; the emulator has a PIN only. Covered at the logic level by the `FakeKeystoreBackend` tests (T1.1) and by the securestore phase tests `invalidationPhaseOneCreatesAUserAuthenticationKeyUnderALockScreen` and `invalidationPhaseTwoFindsNoUsableKeyAfterTheLockScreenWasRemoved`, which **skipped** (3 of 15 skipped in the securestore device run) because they need an orchestrated lock-screen change. Not proven on a device.
- **The system biometric prompt on a destructive action with enrolled credentials.** The demo graph scripts the device-owner answers; the real prompt needs a paired factory. Not shown on the emulator.
- **Manual Pixel and Samsung pass** (T8.7): no such devices.

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
