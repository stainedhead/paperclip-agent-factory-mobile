# Plan: Phase 1a review fixes

**Date:** 2026-11-01 · **Status:** Planning

## Development approach

TDD, one commit per finding, review each fix, agent teammates and git worktrees for parallel groups (PRD "Guidance"). Builds are serialised.

## Phase breakdown

1. **Group 1 (sequential):** FR-R01, FR-R03, FR-R05, FR-R06, FR-R02 (two commits), FR-R30.
2. **Group 2 (parallel with 3):** state holders and use cases: F-B-2, F-B-3, F-B-4, F-B-6, F-B-7, F-B-9, F-D-2, F-A-3, F-A-6, F-A-8.
3. **Group 3 (parallel with 2):** F-C-1 to F-C-8 and F-C-5/F-D-5.
4. **Group 4 (last):** docs, ADRs, PRD and spec text, remaining P2.

## Critical path

Group 1 -> Group 2 -> Group 4. Group 3 can start after the host-recording fake exists (needed by Group 1 tests).

## Testing strategy

Per requirement: a failing regression test first (named in the PRD), then the fix. After each group: `./gradlew check lint`, coverage regenerated, and for Swift-facing changes `./scripts/build-kit.sh debug && ./scripts/ios-test.sh`.

## Rollout strategy

All fixes land on `feat/phase-1a-shared-contracts` before its pull request is marked ready.

## Success metrics

0 open P0/P1, gates green, coverage at or above current, no new Must Fix in an independent re-review.
