# Implementation notes: Phase 2i iPhone UI

**Date:** 2026-11-02

Record decisions, edge cases, deviations and lessons as the work lands, with commit hashes. Update after every task.

## Technical decisions

- Shared (a9f1135): `FakeBiometrics.script` and `PaperclipGraph.demoControls`. Red: both tests failed to compile (unresolved references). Gate: spotlessApply and check lint green.
- swift-format config (ba4303f): `multiElementCollectionTrailingCommas: false` so it agrees with SwiftLint.
- Swift red evidence: all new test files failed to compile (`cannot find 'HolderModel'/'AppEnvironment'/'Palette' in scope`, 232 errors) before any production Swift; then green: 84 tests. One test bug found and fixed (a model must be observed before reading its mirrored state).
- Design tokens come from the decoded mock pages (colors #F4F4F1, #FFFFFF, #15171C, #5B606B, #2649C9, #B3261E, #A84300, #1F7A4D; radii 14/10/16; spacing 14/20; type 32/16/14/13/12). Dark values are derived and contrast-tested (WCAG AA).
- Container identifiers: `.accessibilityIdentifier` on a plain container overwrites its children's ids; containers use `.accessibilityElement(children: .contain)`.
- Decisions: ADR-042, 039, 040 (renumber if stream 1b also added 038).

## Edge cases and solutions

- Default launch is first run, not demo (ADR-043), contrary to the brief wording; matches acceptance criterion 8 and the existing UI test.
- The Swift Biometrics adapter is not built: the graph has no injection point; real Face ID comes with Phase 1b's Kotlin adapter.

## Deviations from plan

- `UpdatedLabel` uses the wall clock (env value `referenceDate`); the demo clock is frozen, so wiring a graph clock is needed with the Inbox milestone.
- First full UI run after a cold simulator failed 4 tests on a 15 s tab-bar wait; timeout raised to 30 s, then stable twice.

## Milestone 2i-1 (first run, Factories, Manage, Edit/Add, Settings)

Commits: 013e2e9 (shared: scripted pairing approval, first-run demo graph, graph clock), 9423bfc and 9296a92 (platform
`ApprovalPageOpener` bridge, then callback form), 7c4cc09 (Swift pairing browser, providers, screen model ownership),
26a6968 (First run), b2de778 (Factories), e5afc7e (Manage), f071f31 (Edit/Add), 4cbaac7 (Settings), plus the Dynamic
Type fix commit after it. Decisions: ADR-045, 046, 047 (and the 2i-0 ADRs renumbered to 042 to 044).

Red evidence: each Swift group's tests first failed to compile for the missing types (`useFirstRunDemo`,
`ASWebAuthSessionPairingBrowser`, the new `CatalogKey` conformances, `FactoriesModel`, `ManageModel`, `EditModel`,
`SettingsModel`). Shared: `DemoPairingTest` (re-pair with `approvePairings` off timed out on both tests),
`FakeAuthApprovalTest`, `DemoControlsTest.nowEpochMillis` and `OpenerPairingBrowserTest` failed on unresolved
references. The callback refactor of `ApprovalPageOpener` changed test and implementation together (no separate red).

Findings from reading simulator screenshots: the second company chip wrapped under the card (FlowLayout rounding, fixed
with a tolerance); at accessibility-extra-extra-extra-large the factory name wrapped one letter per line and Settings
rows were squeezed (fixed with `AdaptiveStack`). A first-run flow bug: saving the factory made `hasFactories` true and
swapped the flow for the tab shell half way; `firstRunInProgress` keeps the screen until Done.

Deviations from the mocks and brief
- Sign out, revoke and sign out everywhere ask a plain confirmation, not Face ID (FR-014 does not list them; ADR-047).
  The mock's lock glyph on Sign out everywhere is not shown. Raise with the owner if they should be gated.
- Manage rows carry no key expiry, so Cloud factory reads "Signed out", not "Key expired"; the status label from
  status plus expiry is a Swift lookup (`FactoryStatusPresentation`) and belongs in shared if Android needs it.
- Factory cards show the address, not "Tailscale, authenticated, private": the row state has no exposure.
- Not built: QR scan, manual key paste, per-factory sign out in Settings, the Notifications row (Phase 3).
- The Edit form hides the tab bar (audit contrast under the floating bar); the mock shows it.
- Disabled buttons are muted grey instead of dimmed (contrast audit).
- The first-run Check address is an explicit button; Continue stays disabled until the check is accepted.
- The Swift browser's real `ASWebAuthenticationSession` and real Face ID are not exercised in tests.

Accessibility audit (performAccessibilityAudit, each screen asserts nothing beyond this list)
- First run: one "Text clipped" without an element label.
- Factories: nav-bar "Edit" contrast and Dynamic Type note (system glass), "Add a factory" contrast (half under the
  floating tab bar), "Text clipped" on address lines.
- Manage: "Done" contrast (glass), header and footer "nearly passed", Dynamic Type notes, "Text clipped".
- Edit: "Cancel"/"Save" Dynamic Type notes, "Text clipped" without a label.
- Settings: "Sign out everywhere" and the help text contrast (under the floating tab bar), "Text clipped".

## Lessons learned

- `xcodebuild test` output piped through `grep` shows nothing until it ends; tee to a file.
- A `.accessibilityIdentifier` on a `Text` inside a combined element is not reachable as a static text; query with
  `app.element(id)` (descendants of any type).
- Keyboard return key labelled by `submitLabel`; type "\n" instead of tapping a named key.

## UI test stabilisation: text entry (2026-11-02)

- Cause 1: `clearAndTypeText` sent `value.count` deletes from wherever the focus tap put the caret, so the tail of
  "Home factory" survived ("HomStudio factory"). Cause 2: `focus()` lived in FirstRunUITests and `typeText` still
  failed on CI with "no keyboard focus" when the software keyboard had not appeared (a connected hardware keyboard
  hides it). Cause 3 (found while fixing): the Edit name field's placeholder equals the demo name, so
  `value == placeholderValue` cannot distinguish "empty" from "real text"; a first helper that skipped the clear in
  that case typed after "Home factory".
- Fix: one helper in `Tests/iosAppUITests/TextEntry.swift` (`focus`, `enterText`, `clearAndTypeText`): tap with retries
  and keyboard waits, caret to the end (right-edge tap), `count + 5` deletes in up to 3 passes, then verify the field
  shows the intended text. `scripts/ios-test.sh` now sets `ConnectHardwareKeyboard` off, boots the simulator
  (`simctl bootstatus -b`) and switches off autocorrect, prediction and auto-capitalisation. The workflow runs that
  script, so it needed no change.
- Evidence: see the commit message / PR for the run counts.

## 2026-11-02 CI flake: DemoPairingTest "pairing the signed out Cloud factory again works twice"

Root cause (a fake defect, not wall-clock speed): `FakeAuthGateway.revokeCurrentKey` put the instance in a permanent revoked set, so after the first sign out every `me` call answered 401 even after a new pairing issued a new key. In the second cycle the post-pair access load (`LoadInstanceAccess`) got a 401 and marked the factory Signed out right after the holder published `Idle` + `SignedIn`. `StateFlow` is conflated, so the test's `await { Idle && SignedIn }` passed only when its collector woke inside that tiny window (always locally, not on the 3-core CI runner), otherwise it timed out. Red evidence: with the new wait for loaded companies and the fix stashed the test times out deterministically.
Fix (test first): `FakeBehaviourTest` gained two tests (a new pairing on the same host re-enables a revoked factory; another host does not). The fake records the host at revoke time and `createChallenge` clears revocations for that host (or an unknown host); the demo graph wires `hostOf`. `DemoPairingTest` now waits for the loaded companies before signing out (no stale access load racing the sign out), and every umbrella test safety-net timeout is 60 s (they wait for states, not durations).

Follow-up the same day: the fake clears a revocation when a poll answers Approved (not on `createChallenge`), so a cancelled or expired pairing leaves the factory revoked (tests added). The CI Xcode UI step (Xcode 26.6, iOS 26.5 runtime) failed 7 of 32 tests on f9c9c34 while all 32 pass locally on Xcode 27.2 / iOS 27.2, plain (395 s) and under heavy CPU load (510 s; CI took 804 s). The CI job never uploaded the result bundle, so there is no screenshot or hierarchy to read: `scripts/ios-test.sh` now accepts `RESULT_BUNDLE` and `ios.yml` uploads `build/ios-ui.xcresult` on failure. No product Swift was changed without evidence.

CI evidence from the uploaded xcresult of run 41483173299 (iPhone-class simulator 402x874 pt, Xcode 26.6, iOS 26.5; local runs use iPhone Air, 420x912, Xcode 27.2): (1) testThePinToggleOpensThePinEntry: the pin switch row sits at y 862..900, below the 874 pt fold, so the tap missed; the test now scrolls it hittable first. (2) Settings and Manage audits flag "Contrast failed" on text under the floating tab bar or list header and "Hit area is too small | Device name" (the field already has a 44 pt minimum height); only reproducible on that runtime, not fixed yet. (3) ShellUITests badge: the Inbox tab button exposes label "Inbox" with no value on iOS 26.5, so the badge "3" is not readable through `value` there; the recording ends on the launch splash. Not fixed yet.

Run 41489302465 (a259f7b): the pin switch is reported hittable although it sits at y 862..900 under the tab bar, so the earlier scroll loop did nothing; the test now scrolls until the row is 120 pt clear of the window bottom. Typing dropped a character ("Studio fctory") on the runner, so `enterText` clears and retypes up to twice before asserting. A HolderModel test (typing while observing never shows an older state after a newer one) passes, so the model does not echo stale states. The Inbox badge check also accepts the tab label. Still open and only reproducible on iOS 26.5: the Edit/Manage/Settings accessibility-audit contrast and hit-area findings. Unrelated flake seen on `shared` (ubuntu): AtomicGuardsTest "concurrent DismissTapped dismisses the card once" (Phase 1a, fixed 20 ms delay).

CI iteration log, 2026-11-02 (runner: macos-latest with Xcode 26.0 to 26.6 installed, newest iOS runtime 26.5; iOS 27 is not available there, so the UI tests must pass on 26.5). Run 41508267168 (last pushed code): 27 of 32 UI tests pass; open: (1) Manage audit "Contrast failed | 3 factories" and Settings audit "Contrast failed | Paired devices and keys" (the same screens pass on a local 402x874 iPhone 18 Pro and on the Air; text colours are textSecondary 5B606B on F4F4F1, about 5.7:1, so the finding looks like the runtime's pixel sampling, not the palette); (2) Edit-form audit "Audit failed to complete in time" (-56); (3) the Inbox badge test: the recording shows the lock screen still up long after launch and the Inbox empty-state afterwards, so the demo graph's auto-unlock and first Inbox load are slow or lost on the runner (unverified why); (4) testTheAppLockToggleAndLockAfterChange reads "1" after the toggle tap (a tap that did not register, seen once). Fixed and verified on CI: AtomicGuardsTest root cause (card could be dismissed again; shared run green), pin toggle under the tab bar, dropped keystroke retype, add-form open.
