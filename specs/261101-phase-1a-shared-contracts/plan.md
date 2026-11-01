# Plan: Phase 1a shared contracts

**Date:** 2026-11-01 · **Status:** Planning

## Development approach

TDD throughout: failing test, minimum code, refactor. Each slice is a vertical feature set (domain to state holder), merged as its own shared-module pull request; the iPhone screen for it follows in a separate pull request.

## Phase breakdown

1. **1a-1 Instances, secrets, settings, auth, roles.** Screens 15, 8, 16, 9, 13. First because every other slice needs "which factory and company am I in, and what may I do".
2. **1a-2 Attention, decisions, approvals, budget incidents.** Screens 1, 2, 3, 12. Needs research Q1 resolved.
3. **1a-3 Work, agents, runs, factory.** Screens 4, 10, 5, 6, 11, 7.
4. **1a-4 Completion.** Demo data matches the mocks, contract suites, `RefreshVisibleScreen`, interop review.

## Critical path

Roles and `DeviceAuthPolicy` (1a-1) -> attention and decisions (1a-2) -> demo data (1a-4). Slice 1a-3 can run in parallel with 1a-2 once 1a-1 is merged.

## Testing strategy

`commonTest` with kotlin.test, Kotest, Turbine and a fake clock; run on JVM, Android host and iOS simulator. Per screen, a scripted flow test. Contract suites in `shared/testing`. Konsist rules extended for fakes and the public Swift surface.

## Rollout strategy

Each slice merges to `main` behind no flag (nothing user-visible changes until the UI uses it). The iOS app switches to the demo graph in the Phase 2i-0 pull request.

## Success metrics

Coverage gates at 90% or more; every PRD FR in spec section 4 has a named test; `check lint` and all workflows green; interop review recorded.
