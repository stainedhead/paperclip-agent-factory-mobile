# Tasks: Phase 1b shared adapters

**Date:** 2026-11-02 · **Status:** Planning (milestone-level; each milestone is broken into tasks before it starts)

## Progress summary

1 / 5 milestones complete (1b-1 done).

## Task format

`T<n>` · dependencies · size (S, M, L) · acceptance criteria.

## Milestones

- **T1 (DONE 2026-11-02)** 1b-1 Ktor client, error mapping, health and auth/pairing gateways (AD-001 to AD-005, AD-010, AD-011) · deps: none · size: L · acceptance: see PRD acceptance criteria and the FRs of this milestone
- **T2** 1b-2 SQLDelight persistence and repositories (AD-020 to AD-022) · deps: T1 · size: L · acceptance: see PRD acceptance criteria and the FRs of this milestone
- **T3** 1b-3 iOS Keychain, biometrics, connectivity and system services; Android/JVM fail-closed actuals (AD-030 to AD-033) · deps: T2 · size: L · acceptance: see PRD acceptance criteria and the FRs of this milestone
- **T4 (DONE 2026-11-02)** 1b-4 Attention, decision, approval, budget, issue, interaction, agent, run and dashboard gateway adapters (AD-012 to AD-014) · deps: T3 · size: L · acceptance: see PRD acceptance criteria and the FRs of this milestone
- **T5** 1b-5 Live graph, contract test against the pinned server in CI, documentation (AD-040, AD-041, AD-050, AD-051) · deps: T4 · size: L · acceptance: see PRD acceptance criteria and the FRs of this milestone

## Stub example

- **P1.1** example: first red test of the first milestone · deps: none · S · a named test fails for the stated reason before any production code exists.
