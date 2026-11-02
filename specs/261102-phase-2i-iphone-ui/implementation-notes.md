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
