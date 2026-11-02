# Tasks: Phase 1a shared contracts

> **Corrections after review (2026-11-01).** Tasks are ticked to match status.md and the code (review finding F-D-3). P1.1 to P1.3 were split into the per-slice task lists in `status.md`.

**Date:** 2026-11-01 · **Status:** Planning (slice-level; detailed tasks are written per slice before it starts)

## Progress summary

10 / 10 tasks complete (corrected 2026-11-01; P0.2 closed by review fix F-D-4, P2.2 archived).

## Task format

`P<phase>.<n>` · dependencies · duration (S under half a day, M a day, L several days) · acceptance criteria.

## Phase 0: specification

- [x] **P0.1** Run `/dev-flow:review-spec` and resolve findings. · deps: none · S · findings fixed or recorded.
- [x] **P0.2** Update the PRD for the remaining server differences (spec section 5). · deps: P0.1 · S · FR-063 (sixth interaction kind), source kinds and the health field name corrected. FR-030 and FR-032 are done.
- [x] **P0.3** ~~Resolve research Q1 (badge and "Decide now")~~ Done 2026-11-01: match the web app; recorded as ADR-010. · deps: none · S

## Phase 1: slices

- [x] **P1.1** Slice 1a-1: instances, secrets, settings, auth, roles; screens 15, 8, 16, 9, 13. · deps: Phase 0 pull requests merged · L · state-holder flow tests pass; coverage gates met.
- [x] **P1.2** Slice 1a-2: attention, decisions, approvals, budget incidents; screens 1, 2, 3, 12. · deps: P1.1 · L
- [x] **P1.3** Slice 1a-3: work, agents, runs, factory; screens 4, 10, 5, 6, 11, 7. · deps: P1.1 · L
- [x] **P1.4** Slice 1a-4: complete demo data, contract suites, polling policy. · deps: P1.2, P1.3 · M · done 2026-11-01
- [x] **P1.5** Swift-interop review of the exported surface, recorded in `implementation-notes.md`. · deps: P1.4 · S

## Phase 2: completion

- [x] **P2.1** Docs updated (`docs/product-details.md`, `technical-details.md`, ADR). · deps: each slice · S per slice
- [x] **P2.2** Archive to `specs/archive/` once the acceptance criteria hold. · deps: P1.5 · S

### Example stub

- **P1.x** Example: `Role` and `Permissions`. · deps: none · M · a table-driven test covers every (role, action) pair; viewer cannot mutate; owner-only actions are owner-only.
