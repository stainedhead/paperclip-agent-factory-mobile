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
2i-1 done: see implementation-notes.md (ADR-045 to 047); counts are in the milestone report.

2i-3 done: Decision, Approval and Budget incident sheet (ADR-095 to 098); see implementation-notes.md.

2i-4 done: Factory home, Agents, Agent detail, Run viewer (ADR-100 to 103); see implementation-notes.md.

2i-5 done: Work list and Issue (ADR-106, 107); see implementation-notes.md.

2i-6 done: polish, KVO tab bar, honest audits, screenshots, docs, merge checklist for 1b (ADR-108 to 111).

## Blockers

1. The live graph is not wired: `LiveGraphProvider.isAvailable` is false until Phase 1b (PR #7) merges. A build without `-demo` shows "Live factories are not available yet" (ADR-112). Merge checklist: implementation-notes.md.
2. Accessibility audits fail honestly: the Phase 2i audit set reports findings on most screens (counts in implementation-notes.md, "Audit results"). They do not block CI (ADR-080) but 2i-6 is not accepted while they stand.
3. (Cleared in the second fix round) the `security` workflow failed on one historical gitleaks finding; now allowlisted.
4. ADR numbers 042 to 047 and 080 collide with 1b (C15).
5. `/dev-flow:review-spec` was never run.

## Recent activity

- 2026-11-02: spec created from the PRD.
- 2026-11-02: milestone 2i-0 (shell, design system, catalog, lock) implemented.
- 2026-11-02: milestone 2i-1 (First run, Factories, Manage, Edit/Add, Settings, pairing browser) implemented.
- 2026-11-02: milestone 2i-2 (Inbox screen, demo Inbox behaviour, Inbox UI tests) implemented; ADR-090 to 092.
- 2026-11-02: milestone 2i-3 (Decision, Approval, Budget incident sheet, shared demo changes, detail UI tests) implemented.
- 2026-11-02: milestone 2i-4 (Factory home, Agents, Agent detail, Run viewer, shared run/issue enrichment, demo run data) implemented.
- 2026-11-02: milestone 2i-5 (Work list, Issue, demo answer and comment fixes) implemented.
- 2026-11-02: fixed the iOS 26.5 CI failures (tab badge accessibility value, demo refresh timer pause); ADR-105.
- 2026-11-03: milestone 2i-6 (polish, accessibility, screenshots, live seam notes, docs) implemented.

- 2026-11-03: review-fix round for reviews A, B and C (no P0); new ADR-112 to 116; status and docs corrected.
