# Implementation notes: Phase 2i iPhone UI

**Date:** 2026-11-02

Record decisions, edge cases, deviations and lessons as the work lands, with commit hashes. Update after every task.

## Technical decisions

- Shared (a9f1135): `FakeBiometrics.script` and `PaperclipGraph.demoControls`. Red: both tests failed to compile (unresolved references). Gate: spotlessApply and check lint green.
- swift-format config (ba4303f): `multiElementCollectionTrailingCommas: false` so it agrees with SwiftLint.
- Swift red evidence: all new test files failed to compile (`cannot find 'HolderModel'/'AppEnvironment'/'Palette' in scope`, 232 errors) before any production Swift; then green: 84 tests. One test bug found and fixed (a model must be observed before reading its mirrored state).
- Design tokens come from the decoded mock pages (colors #F4F4F1, #FFFFFF, #15171C, #5B606B, #2649C9, #B3261E, #A84300, #1F7A4D; radii 14/10/16; spacing 14/20; type 32/16/14/13/12). Dark values are derived and contrast-tested (WCAG AA).
- Container identifiers: `.accessibilityIdentifier` on a plain container overwrites its children's ids; containers use `.accessibilityElement(children: .contain)`.
- Decisions: ADR-038, 039, 040 (renumber if stream 1b also added 038).

## Edge cases and solutions

- Default launch is first run, not demo (ADR-039), contrary to the brief wording; matches acceptance criterion 8 and the existing UI test.
- The Swift Biometrics adapter is not built: the graph has no injection point; real Face ID comes with Phase 1b's Kotlin adapter.

## Deviations from plan

- `UpdatedLabel` uses the wall clock (env value `referenceDate`); the demo clock is frozen, so wiring a graph clock is needed with the Inbox milestone.
- First full UI run after a cold simulator failed 4 tests on a 15 s tab-bar wait; timeout raised to 30 s, then stable twice.

## Lessons learned

(none yet)
