# Implementation notes: Phase 1a shared contracts

**Date:** 2026-11-01

Record, as the work happens: decisions made in code, edge cases and how they were solved, where reality departed from the plan, and lessons. Update after every task; keep entries short and dated.

## Technical decisions

- 2026-11-01 (slice 1a-1): `Clock` uses `kotlin.time.Instant` from the standard library (stable in Kotlin 2.4). kotlinx-datetime is not added yet; slice 1a-2 adds it when it needs UTC day boundaries.
- 2026-11-01: `Secret` is a plain class over a `CharArray` so it can be wiped; `reveal()` and `startsWith()` exist, equality is by value, no `copy`. `reveal()` is `@HiddenFromObjC`. A Konsist rule limits `.reveal()` to `shared/infrastructure`, `shared/testing` and `Secret.kt`.
- 2026-11-01: ports are interfaces in `domain.port`; port value types (`SecretKey`, `SecretReadResult`, ...) live in `domain` so the "fake per port" rule can look at interfaces only.
- 2026-11-01: `SecretStore.get` returns a sealed `SecretReadResult` (`Found`, `Missing`, `Invalidated`, `Denied`) instead of `kotlin.Result`; `Invalidated` is the FR-015 path.
- 2026-11-01: the probe is not retried (interactive); the other reads (`LoadInstanceAccess`, `ListBoardKeys`) retry 3 times with 500 ms, 1 s, 2 s backoff. "Max 3" in FR-096 is read as 3 retries after the first attempt.
- 2026-11-01: `KeyEnrollment` (public, not a use case) is shared by pairing and manual entry so a key is stored one way. Pairing stores then reads `me`; if `me` fails the stored token is deleted. Manual entry validates with `meWithKey` first.
- 2026-11-01: `SignOutInstance` always deletes local credentials and reports `RevokeOutcome.Unconfirmed` when the server could not confirm; `RemoveInstance` revokes first, then deletes secrets inside `NonCancellable`.
- 2026-11-01: setting an agent's monthly budget (`SetAgentBudget`) is owner-only but not on FR-014's device-authentication list, so `DeviceAuthPolicy` does not include it. Only the budget raise is.
- 2026-11-01: state holders split work into internal helper classes (`FirstRunActions`, `EditInstance...`, `Settings...`) because detekt allows 11 functions per class. Public API is still `state`, `onIntent`, `close`.
- 2026-11-01: native test names cannot contain commas; test names avoid them.
- 2026-11-01: use-case bundles (`FirstRunUseCases` etc.) are data classes so the Koin wiring stays under detekt's parameter limit.

## Swift interop review (slice 1a-1)

The exported surface used from Swift is only the six state holders, their state/intent types and the graph. `GraphInteropTests` exercises `createAppStateHolder()`, `createSettingsStateHolder()`, `createEditInstanceStateHolder(instanceId: nil)`, `createFactoriesStateHolder()`, `createManageFactoriesStateHolder()` and `FirstRunIntentDemoSelected.shared` from Swift 6 with no warnings. Not done: calling `close()` from the Swift models (a `@MainActor` model's `deinit` cannot touch the non-Sendable holder under strict concurrency); the Phase 2i screens must close holders from an explicit `onDisappear` path.

## Edge cases and solutions

- Changing a factory's address deletes its stored key and marks it Signed out, so a key is never sent to a host that did not issue it (ADR-012).
- The pending board token arrives at challenge creation; it is wiped on cancel, expiry, local timeout, failure and coroutine cancellation (tested with the pending token object).
- A flaky network or rate limit while polling a challenge keeps waiting; a hard error ends the pairing.
- A poll interval of 0 from the server is clamped to 250 ms.
- Name uniqueness is trimmed and case-insensitive; a factory may keep its own name when edited.
- `advanceUntilIdle()` ignores `backgroundScope`; state-holder tests use a `StandardTestDispatcher` scope instead.

## Deviations from plan

- 2026-11-01: TDD was followed per layer for the domain (tests first, seen failing to compile), but application and presentation code was written in the same pass as their tests and then checked by running them and by mutation spot checks (for example removing the device-auth check from `RemoveInstance` fails its tests). This is a process deviation to be aware of.
- 2026-11-01: `PaperclipCore.createGraph(allowInsecureLocal:)` keeps its signature and now builds a fakes-backed graph; the spec's live/demo mode parameter is deferred to Phase 1b (spec section 9).
- 2026-11-01: `UpdateInstance` and `ObserveInstances` gained a sibling `GetInstance` use case, and `CheckCredentials`, `LoadInstanceAccess` and `KeyEnrollment` exist beyond the spec's list, because FR-015, FR-022 and the shared enrollment needed them.

- 2026-11-01: this spec lives in `specs/261101-phase-1a-shared-contracts/`; the product PRD stays at the repository root instead of being moved into it, because it is shared by every phase and linked from the README, AGENTS.md and CLAUDE.md.

## Lessons learned

(none yet)
