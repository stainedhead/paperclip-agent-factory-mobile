# Tasks: Phase 1a shared contracts

**Date:** 2026-11-01 · **Status:** Planning (slice-level; detailed tasks are written per slice before it starts)

## Progress summary

0 / 12 tasks complete.

## Task format

`P<phase>.<n>` · dependencies · duration (S under half a day, M a day, L several days) · acceptance criteria.

## Phase 0: specification

- **P0.1** Run `/dev-flow:review-spec` and resolve findings. · deps: none · S · findings fixed or recorded.
- **P0.2** Update the PRD for the server differences (spec section 5). · deps: P0.1 · S · FR-030, FR-032, FR-063 and the health field name corrected.
- **P0.3** Resolve research Q1 (badge and "Decide now"). · deps: none · S · decision recorded in the ADR.

## Phase 1: slices

- **P1.1** Slice 1a-1: instances, secrets, settings, auth, roles; screens 15, 8, 16, 9, 13. · deps: Phase 0 pull requests merged · L · state-holder flow tests pass; coverage gates met.
- **P1.2** Slice 1a-2: attention, decisions, approvals, budget incidents; screens 1, 2, 3, 12. · deps: P1.1, P0.3 · L
- **P1.3** Slice 1a-3: work, agents, runs, factory; screens 4, 10, 5, 6, 11, 7. · deps: P1.1 · L
- **P1.4** Slice 1a-4: complete demo data, contract suites, polling policy. · deps: P1.2, P1.3 · M
- **P1.5** Swift-interop review of the exported surface, recorded in `implementation-notes.md`. · deps: P1.4 · S

## Phase 2: completion

- **P2.1** Docs updated (`docs/product-details.md`, `technical-details.md`, ADR). · deps: each slice · S per slice
- **P2.2** Archive to `specs/archive/` once the acceptance criteria hold. · deps: P1.5 · S

### Example stub

- **P1.x** Example: `Role` and `Permissions`. · deps: none · M · a table-driven test covers every (role, action) pair; viewer cannot mutate; owner-only actions are owner-only.
