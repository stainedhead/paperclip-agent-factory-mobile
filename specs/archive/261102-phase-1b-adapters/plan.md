# Plan: Phase 1b shared adapters

**Date:** 2026-11-02 · **Status:** Planning

## Development approach

TDD (red, green, refactor) with one commit per task. Adapters are developed against the existing contract suites first (red), with MockEngine standing in for the server. The contract test against the real container is written alongside and verified in CI. Work is split by module so milestones can run in parallel in separate git worktrees.

## Phase breakdown

1. 1b-1 client, errors, health, auth.
2. 1b-2 persistence (parallel with 1b-1).
3. 1b-3 device services (parallel with 1b-1).
4. 1b-4 remaining gateways (after 1b-1).
5. 1b-5 live graph, contractTest, docs.

## Critical path

1b-1 -> 1b-4 -> 1b-5. 1b-2 and 1b-3 must be done before 1b-5.

## Testing strategy

Per adapter: the shared contract suite through a MockEngine-backed server stub; mapper and error tests per endpoint; persistence and Keychain tests on JVM/simulator; a secrets audit; contractTest in CI against the pinned container.

## Rollout strategy

One pull request per phase from a feature branch off `main`. Nothing is merged without green workflows.

## Success metrics

All acceptance criteria in `spec.md` section 9 met with evidence; gates green; coverage not reduced.
