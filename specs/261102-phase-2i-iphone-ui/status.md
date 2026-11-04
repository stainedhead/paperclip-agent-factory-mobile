# Status: Phase 2i iPhone UI

**Created:** 2026-11-02 · **Spec dir:** `specs/261102-phase-2i-iphone-ui/`

Update this file after every task and phase. It is the single source of progress.

## Overall progress

| Phase | State |
|---|---|
| Phase 0 Specification | Done, except the `/dev-flow:review-spec` run (never run) |
| Phase 1 Research and data model | Done (`research.md`, `data-dictionary.md`) |
| Phase 2 Architecture and plan | Done (`architecture.md`, `plan.md`) |
| Phase 3 Task breakdown | Done (`tasks.md`) |
| Phase 4 Implementation | Done for the demo graph (2i-0 to 2i-6) and the review-fix round; live factories not wired (blocker 1) |
| Phase 5 Completion and archive | Not started: blocked |

## Milestones

- [x] 2i-0 Shell: environment switch, StateHolderModel pattern, design system, String Catalog, tab shell, app lock and Face ID gating (UI-001 to UI-007)
- [x] 2i-1 First run, Factories, Add/edit factory, Manage factories, Settings with pairing browser (UI-010)
- [x] 2i-2 Inbox (UI-020)
- [x] 2i-3 Decision, Approval, Budget incident sheet (UI-030)
- [x] 2i-4 Factory home, Agents, Agent detail, Run viewer (UI-040)
- [x] 2i-5 Work list and Issue with interactions (UI-050)
- [ ] 2i-6 Polish, accessibility, screenshots, live seam, documentation (UI-060): built, not accepted. The accessibility audits fail (blocker 2), the live seam is unwired (blocker 1).
- [x] Review-fix round (reviews A, B, C): see implementation-notes.md, "Review-fix round"

## Notes

2i-0 done: 84 Swift Testing cases, 7 XCUITests (see implementation-notes.md).
2i-1 done: see implementation-notes.md (ADR-091 to 093); counts are in the milestone report.

2i-3 done: Decision, Approval and Budget incident sheet (ADR-098 to 101); see implementation-notes.md.

2i-4 done: Factory home, Agents, Agent detail, Run viewer (ADR-102 to 105); see implementation-notes.md.

2i-5 done: Work list and Issue (ADR-108, 109); see implementation-notes.md.

2i-6 done: polish, KVO tab bar, honest audits, screenshots, docs, merge checklist for 1b (ADR-110 to 113).

## Blockers

1. (Cleared on the 1b merge) The live graph is wired: a launch without `-demo` opens the real graph (ADR-120). Not exercised from the app against a live Paperclip server.
2. Accessibility audits: 16 of 24 audit tests fail locally (21 on the iOS 26.5 CI runtime at e432599; ADR-117). They are shown in the non-blocking `ios (audits)` job by decision of the owner (option C, ADR-119); nothing is filtered. The blocking guard for large text is `LargeTextUITests` (15 tests, accessibility XXXL). 2i-6 is not accepted while they stand.
3. (Cleared in the second fix round) the `security` workflow failed on one historical gitleaks finding; now allowlisted.
4. (Cleared on the 1b merge) the 2i ADRs were renumbered to ADR-088 to ADR-119 after 1b's ADR-087 (mapping in implementation-notes.md).
5. `/dev-flow:review-spec` was never run.

## Recent activity

- 2026-11-02: spec created from the PRD.
- 2026-11-02: milestone 2i-0 (shell, design system, catalog, lock) implemented.
- 2026-11-02: milestone 2i-1 (First run, Factories, Manage, Edit/Add, Settings, pairing browser) implemented.
- 2026-11-02: milestone 2i-2 (Inbox screen, demo Inbox behaviour, Inbox UI tests) implemented; ADR-095 to 097.
- 2026-11-02: milestone 2i-3 (Decision, Approval, Budget incident sheet, shared demo changes, detail UI tests) implemented.
- 2026-11-02: milestone 2i-4 (Factory home, Agents, Agent detail, Run viewer, shared run/issue enrichment, demo run data) implemented.
- 2026-11-02: milestone 2i-5 (Work list, Issue, demo answer and comment fixes) implemented.
- 2026-11-02: fixed the iOS 26.5 CI failures (tab badge accessibility value, demo refresh timer pause); ADR-107.
- 2026-11-03: milestone 2i-6 (polish, accessibility, screenshots, live seam notes, docs) implemented.

- 2026-11-03: review-fix round for reviews A, B and C (no P0); new ADR-114 to 118; status and docs corrected.
- 2026-11-03: Phase 1b (PR #7) merged into this branch; live graph wired; ADRs renumbered; 1b error, outcome and here-only cases added to the app.
