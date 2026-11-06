# Tasks: Phase 2a Android review fixes

**Date:** 2026-11-04 · **Status:** Planning

## Progress summary

0 / 40 tasks complete. P0: 0 · P1: 9 (R-001..R-009) · P2: 31 (R-010..R-040).

## Task format

`T-<nnn>` matches `R-<nnn>`. Each task is one commit (one finding). Fields: requirement, priority, group, dependencies, size (S under half a day, M a day), red-first test (from the PRD), iPhone impact where shared code changes, acceptance (the PRD requirement's list).

## Run assumptions

- **DEC-1 (R-004), owner may overturn:** a budget raise asks for the device owner via `DeviceAuthGuard`.
- **DEC-2 (R-012), owner may overturn:** online whenever a default network declares `NET_CAPABILITY_INTERNET`, validated or not.

## iOS gate (run after every commit marked below, before starting the next)

`./scripts/build-kit.sh debug && swiftlint --strict && xcrun swift-format lint --recursive --strict iosApp/Sources iosApp/Tests iosApp/Packages/PaperclipKit/Package.swift && ./scripts/ios-test.sh` under the build lock (`flock /tmp/pafm-build.lock`). A commit that only touches `infrastructure/*` androidMain or `androidApp` needs no iOS gate.

## Implementation order and parallel groups

### Group 1, shared core (first, sequential, owns `shared/`)

- [ ] **T-001** R-001 (P1) Protected Keystore keys are not bound to the prompt; ADR-123's 5 second window claim is false
  - Group 1 · deps: none · size M · commit: one commit for R-001
  - Red-first test: make `FakeKeystoreBackend` authorization single-use (consume `authenticated` on every `open` and `seal`); add `two_consecutive_protected_reads_prompt_twice`. Add an `androidDeviceTest`: read, then read again within 5 seconds must prompt again.
  - iPhone impact / iOS gate: none at runtime; allTests (includes iosSimulatorArm64); run `./scripts/build-kit.sh debug` once after the contract change
  - Acceptance: the PRD R-001 acceptance criteria

- [ ] **T-002** R-002 (P1) A biometric prompt can hang forever with the store mutex held; the lock is held across the user's prompt
  - Group 1 · deps: R-001 test commit · size M · commit: one commit for R-002
  - Red-first test: a seam in `ActivityPromptLauncher` for "activity not resumed at post time"; the test asserts `Unavailable` returns within `withTimeout`. A store test with a never-completing `authenticate`: `namespaces()` must still complete while the prompt is pending.
  - iPhone impact / iOS gate: none at runtime; same as R-001
  - Acceptance: the PRD R-002 acceptance criteria

- [ ] **T-004** R-004 (P1, DEC-1 run assumption, owner may overturn) `SetAgentBudget` raises money with no device-owner prompt, contradicting AGENTS.md
  - Group 1 · deps: none · size M · commit: one commit for R-004
  - Red-first test: `SetAgentBudgetTest` with a fake `Biometrics` that cancels: a raise returns `NotAuthorized` (the existing device-auth result) and `setBudget` call count is 0; with approval it succeeds with exactly one prompt and one call; a decrease succeeds with zero prompts and one call; a set-to-zero asks for the prompt.
  - iPhone impact / iOS gate: BEHAVIOUR CHANGE on iPhone: a budget raise shows Face ID / passcode, lock glyph on `AgentDetailView.swift:206`; update iPhone agent-detail and budget tests; full iOS gates
  - Acceptance: the PRD R-004 acceptance criteria

- [ ] **T-007** R-007 (P1) An in-flight inline approve/reject is cancelled when the Inbox holder closes (ADR-117 not applied)
  - Group 1 (shared half (`isWorking`) in Group 1, app half in Group 3) · deps: none · size S · commit: one commit for R-007
  - Red-first test: a `GraphSession` test with a fake gateway that suspends on approve, a selection change mid-flight, and an assertion that the holder is not closed until the outcome is recorded. A second test: closing the graph while working cancels the idle waiter.
  - iPhone impact / iOS gate: new public `InboxState.isWorking` (additive); update Swift tests that construct or compare `InboxState`; full iOS gates
  - Acceptance: the PRD R-007 acceptance criteria

- [ ] **T-024** R-024 (P2, decided: stays P2; it concerns money) The raise amount can be hidden from the field but still sent
  - Group 1 (shared half (holder clears amount) in Group 1, app half in Group 2) · deps: none · size S · commit: one commit for R-024
  - Red-first test: `BudgetSheetTest`: type an amount, choose Keep paused, then Raise again; assert the field text equals the state's amount, or that Submit is disabled and not labelled with an amount. Add the shared `commonTest` for the holder.
  - iPhone impact / iOS gate: iPhone `BudgetIncidentSheet.swift` keeps `amountText`; must stay consistent with the cleared holder amount; update `BudgetModelTests.swift`; full iOS gates
  - Acceptance: the PRD R-024 acceptance criteria

- [ ] **T-019** R-019 (P2) Business rules live in androidApp (principle 5, "build once")
  - Group 1 (shared parts in Group 1) · deps: none · size M · commit: one commit for R-019
  - Red-first test: a `commonTest` "a live run replaces Run now with View run"; one per moved rule.
  - iPhone impact / iOS gate: iPhone has its own copies; switch to the shared rule in the same PR or a recorded follow-up; new public types Swift-friendly (no generics, defaults, `Result`); Swift tests per consumed rule; full iOS gates; one commit per moved rule
  - Acceptance: the PRD R-019 acceptance criteria

- [ ] **T-010** R-010 (P2) `AndroidKeystoreBackend.open()` does not wrap `getKey`
  - Group 1 · deps: none · size S · commit: one commit for R-010
  - Red-first test: a `throwOnGetKey` option on the backend seam; `get` returns `Denied` or `Invalidated`, not an exception.
  - iPhone impact / iOS gate: none at runtime; same as R-001
  - Acceptance: the PRD R-010 acceptance criteria

- [ ] **T-011** R-011 (P2) The protected path and the real prompt path have no automated proof
  - Group 1 · deps: none · size S · commit: one commit for R-011
  - Red-first test: the new contract cases themselves; run on the fake first (expected to fail until the fake's single-use behaviour from R-001 lands).
  - iPhone impact / iOS gate: test-only; allTests
  - Acceptance: the PRD R-011 acceptance criteria

- [ ] **T-012** R-012 (P2) Connectivity counts "online" only when the network is VALIDATED
  - Group 1 · deps: none · size S · commit: one commit for R-012
  - Red-first test: an INTERNET-but-not-VALIDATED network capability fake reads as online (fails today).
  - iPhone impact / iOS gate: none (androidMain only); no iOS gate
  - Acceptance: the PRD R-012 acceptance criteria

- [ ] **T-013** R-013 (P2) `RecordFiles` robustness
  - Group 1 · deps: none · size S · commit: one commit for R-013
  - Red-first test: a record directory containing `bad%zz.rec`: `namespaces()` returns the valid names and does not throw.
  - iPhone impact / iOS gate: none at runtime; same as R-001
  - Acceptance: the PRD R-013 acceptance criteria

### Group 2, androidApp text and input fixes (after Group 1; parallel authoring with Group 3, serialised builds)

- [ ] **T-003** R-003 (P1) Typed text is lost on state change and on rotation: approval comment, reject and revision notes, budget amount
  - Group 2 · deps: Group 1 merged · size S · commit: one commit for R-003
  - Red-first test: `ApprovalScreenTest`: type a comment, `app.controls.setOnline(false)` then `true`, assert the text is unchanged. Stateless `ApprovalContent` test: flip `isSubmitting` true then false with `Failed` feedback and assert the text is still present. `StateRestorationTester` (or `scenario.recreate()`) tests for the comment, the reject note and the amount.
  - Acceptance: the PRD R-003 acceptance criteria

- [ ] **T-023** R-023 (P2) `FormField` is single-line and strips newlines (iPhone parity)
  - Group 2 · deps: Group 1 merged · size S · commit: one commit for R-023
  - Red-first test: type "a\nb" into `note-field` and assert the intent carries "a\nb".
  - Acceptance: the PRD R-023 acceptance criteria

- (second half of T-024: shared half (holder clears amount) in Group 1, app half in Group 2)

- [ ] **T-025** R-025 (P2) `FormField` TalkBack semantics hide typed text and the validation issue
  - Group 2 · deps: Group 1 merged · size S · commit: one commit for R-025
  - Red-first test: assert the field node exposes `EditableText` and that the issue is part of its merged semantics.
  - Acceptance: the PRD R-025 acceptance criteria

- [ ] **T-026** R-026 (P2) Nothing that appears asynchronously is announced to TalkBack
  - Group 2 · deps: Group 1 merged, R-025 wording agreed · size S · commit: one commit for R-026
  - Red-first test: assert `SemanticsProperties.LiveRegion` on `feedback-banner`, `inbox-undo` and `note-issue`.
  - Acceptance: the PRD R-026 acceptance criteria

- [ ] **T-027** R-027 (P2) System Back on the first-run Pair step does not cancel pairing
  - Group 2 · deps: Group 1 merged · size S · commit: one commit for R-027
  - Red-first test: a FirstRun test that presses system back on the Pair step and asserts `step == Address` and `pairing == Idle`.
  - Acceptance: the PRD R-027 acceptance criteria

- [ ] **T-028** R-028 (P2) Manage factories reorder can show an order the store never accepted
  - Group 2 · deps: Group 1 merged · size S · commit: one commit for R-028
  - Red-first test: a stateless ManageFactories test: move a row, then set `reorderRejected = true` with the same rows, assert the original order.
  - Acceptance: the PRD R-028 acceptance criteria

- [ ] **T-029** R-029 (P2) Locale glue in strings built in code
  - Group 2 · deps: Group 1 merged · size S · commit: one commit for R-029
  - Red-first test: a pseudo-locale (`en-XA`) test asserting these strings come from resources.
  - Acceptance: the PRD R-029 acceptance criteria

- [ ] **T-033** R-033 (P2) Typing after Send is wiped and the failure banner can be off screen (Issue comment)
  - Group 2 · deps: Group 1 merged · size S · commit: one commit for R-033
  - Red-first test: a Compose test that appends text after the tap, bumps the count, expects only the sent text removed.
  - Acceptance: the PRD R-033 acceptance criteria

- [ ] **T-034** R-034 (P2) Interaction drafts can disagree with the holder after process death
  - Group 2 · deps: Group 1 merged · size S · commit: one commit for R-034
  - Red-first test: a restoration test with a typed answer; assert the holder's draft equals the visible field.
  - Acceptance: the PRD R-034 acceptance criteria

- [ ] **T-036** R-036 (P2, a11y, reviewer P3) Lock glyph has no description and row buttons have no disabled state
  - Group 2 · deps: Group 1 merged · size S · commit: one commit for R-036
  - Red-first test: assert the Pause button's merged semantics mention the device-owner prompt; assert disabled while mutating.
  - Acceptance: the PRD R-036 acceptance criteria

- [ ] **T-037** R-037 (P2, reviewer P3) Work search keeps unsent text; blank-title buttons enabled
  - Group 2 · deps: Group 1 merged · size S · commit: one commit for R-037
  - Red-first test: a Work test: type a search, tap a chip, assert the field and the applied search agree; assert Create is disabled for blank.
  - Acceptance: the PRD R-037 acceptance criteria

### Group 3, androidApp navigation, lifecycle, Inbox and Run (parallel authoring with Group 2)

- [ ] **T-005** R-005 (P1, verify first) The Run viewer log tail may silently stop following
  - Group 3 · deps: Group 1 merged · size S · commit: one commit for R-005
  - Red-first test: a Robolectric test that holds a drag with `touchDown`, appends log text, releases, appends again, and asserts `scroll.value == scroll.maxValue`. **If the test passes on the unfixed code, demote R-005 to a P2 note and close it with that evidence; do not change code.**
  - Acceptance: the PRD R-005 acceptance criteria

- [ ] **T-006** R-006 (P1) A pushed detail screen survives a factory or company switch
  - Group 3 · deps: Group 1 merged · size S · commit: one commit for R-006
  - Red-first test: `TabShellTest`: push `Run(x)` in the Agents tab, change the selection through the app holder, assert the Agents controller is back at its root (or the screen's holder key is unchanged and still addresses factory A).
  - Acceptance: the PRD R-006 acceptance criteria

- (second half of T-007: shared half (`isWorking`) in Group 1, app half in Group 3)

- [ ] **T-008** R-008 (P1) The Inbox fling misses the PRD jank budget (67% janky frames vs 5%)
  - Group 3 · deps: Group 1 merged · size M · commit: one commit for R-008
  - Red-first test: a Compose test with a counting recomposition probe asserting that a clock tick recomposes no `InboxCard`; then the Macrobenchmark as the acceptance measurement.
  - Acceptance: the PRD R-008 acceptance criteria

- [ ] **T-018** R-018 (P2) Graph opening runs on the main thread in `onCreate`
  - Group 3 · deps: Group 1 merged · size S · commit: one commit for R-018
  - Red-first test: a test with a graph provider that suspends: `environment()` returns immediately with a Loading destination and `StrictMode` (or a main-thread check) is not tripped.
  - Acceptance: the PRD R-018 acceptance criteria

- [ ] **T-021** R-021 (P2, verify first) Locking probably disposes the tab shell without clearing back-stack entry ViewModels
  - Group 3 · deps: Group 1 merged · size S · commit: one commit for R-021
  - Red-first test: a Robolectric test that pushes a Decision, flips app state to locked, and asserts `holder.isClosed`. If it already passes, close with that evidence.
  - Acceptance: the PRD R-021 acceptance criteria

- [ ] **T-022** R-022 (P2) Inbox "Open in web" door bypasses `WebOpener`, can crash with no browser, and can be an inert button
  - Group 3 · deps: Group 1 merged · size S · commit: one commit for R-022
  - Red-first test: an Inbox test with `LocalWebOpener` set to a `RecordingLauncher`: a `WebOnly` card opens through it; a refusing launcher shows the error; a card with `webUrl = null` and one with `http://...` show no enabled web button.
  - Acceptance: the PRD R-022 acceptance criteria

- [ ] **T-032** R-032 (P2) The Agents and Work lists are not lazy
  - Group 3 · deps: Group 1 merged · size S · commit: one commit for R-032
  - Red-first test: a Compose test with 500 rows asserting that off-screen rows are not composed.
  - Acceptance: the PRD R-032 acceptance criteria

### Group 4, tests, build and CI (parallel authoring; baselines re-recorded once, last, by the lead)

- [ ] **T-014** R-014 (P2) The Konsist merged-manifest rules can pass stale locally on a dependency bump
  - Group 4 · deps: Group 1 merged · size S · commit: one commit for R-014
  - Red-first test: a Gradle TestKit or CI check that changes only the catalog and asserts `konsistTest` re-executes.
  - Acceptance: the PRD R-014 acceptance criteria

- [ ] **T-015** R-015 (P2) A release can silently ship debug-signed
  - Group 4 · deps: Group 1 merged · size S · commit: one commit for R-015
  - Red-first test: a Konsist text rule asserting the fallback branch sits behind the guard (with a planted violation).
  - Acceptance: the PRD R-015 acceptance criteria

- [ ] **T-016** R-016 (P2) CI concurrency group lets the nightly benchmark and `push` runs cancel each other
  - Group 4 · deps: Group 1 merged · size S · commit: one commit for R-016
  - Red-first test: extend `CiPinningRulesTest` (or a sibling) with a rule that the group expression contains `github.event_name`.
  - Acceptance: the PRD R-016 acceptance criteria

- [ ] **T-017** R-017 (P2) Konsist guards are weaker than they read
  - Group 4 · deps: Group 1 merged · size S · commit: one commit for R-017
  - Red-first test: planted multi-line and `handle.set("token", token)` violations that the current rule misses.
  - Acceptance: the PRD R-017 acceptance criteria

- [ ] **T-020** R-020 (P2) A mutable-state rule is checked by tests that cannot fail
  - Group 4 · deps: Group 1 merged · size S · commit: one commit for R-020
  - Red-first test: mutate production (stop the 5 s refresh) and confirm the new test fails.
  - iPhone impact / iOS gate: test-only; allTests
  - Acceptance: the PRD R-020 acceptance criteria

- [ ] **T-030** R-030 (P2) Test gaps against the Android PRD acceptance criteria
  - Group 4 · deps: Group 1 merged · size S · commit: one commit for R-030
  - Red-first test: the existing planted-violation pattern for the zero-size control.
  - iPhone impact / iOS gate: test-only; allTests
  - Acceptance: the PRD R-030 acceptance criteria

- [ ] **T-031** R-031 (P2) Roborazzi uses the default pixel-change threshold
  - Group 4 · deps: Groups 2 and 3 visual changes landed (baselines recorded last) · size S · commit: one commit for R-031
  - Red-first test: change one label colour in a throwaway commit and confirm `verifyRoborazziDebug` fails.
  - Acceptance: the PRD R-031 acceptance criteria

- [ ] **T-035** R-035 (P2) TDD evidence for security code is weaker than the notes imply
  - Group 4 · deps: Group 1 merged · size S · commit: one commit for R-035
  - Red-first test: n/a (process); the evidence is the git history of the fixes.
  - Acceptance: the PRD R-035 acceptance criteria

### Group 5, docs and status (last) plus the owner-hardware track

- [ ] **T-039** R-039 (P2) Documentation claims that are false or stale
  - Group 5 · deps: Group 1 merged · size S · commit: one commit for R-039
  - Red-first test: where a check exists, a Konsist or script check (for example `scripts/check-adr-identifiers.py` extended with the ADR range, a manifest rule for the back-callback attribute).
  - Acceptance: the PRD R-039 acceptance criteria

- [ ] **T-040** R-040 (P2) `status.md` is stale and the acceptance mapping is `[TBD]`
  - Group 5 · deps: Group 1 merged · size S · commit: one commit for R-040
  - Red-first test: n/a (docs); a small script or Konsist check that every test class named in `status.md` exists.
  - Acceptance: the PRD R-040 acceptance criteria

- [ ] **T-038** R-038 (P2, design only in this phase) Attachments are not openable on either platform
  - Group 5 · deps: R-039 · size S · commit: one commit for R-038
  - Red-first test: a gateway contract test (fake and real): the bytes return with the right content type and no token appears in the cache path or file name.
  - Acceptance: the PRD R-038 acceptance criteria

- [ ] **T-009** R-009 (P1) Acceptance evidence that has never been produced (needs the owner's hardware and CI)
  - Group 5 · deps: R-001, R-004 applied; R-008 for the frame metric · size M · commit: one commit for R-009
  - Red-first test: evidence runs on owner hardware and CI (PRD R-009 items 1 to 8), recorded dated in the archived Android spec status.md; no automated red test
  - Acceptance: the PRD R-009 acceptance criteria

## Traceability

Each requirement R-001..R-040 has exactly one task T-001..T-040. Merged findings in the PRD (R-002, R-003, R-006, R-022, R-030) are already one requirement each.
