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

## Slice 1a-2 TDD log (red seen first, per group)

Process correction for this slice: every group below was written test first, run, seen failing, then implemented. "Compile red" = the first test of a new type failed on a missing symbol; "assertion red" = the type existed as a stub that returned a wrong value (never `TODO()`) and the tests failed on assertions.
- Domain `Severity`, `Money`, `DecideBy`, `AttentionRules`, `InboxGrouping`, source/approval/interaction kinds, `ResolverAudience`: compile red, then stubs for `isDecideNow`/`isNewToday`/`group`/`InboxGrouping.group` gave 6 assertion failures (`expected true but was false`, `expected [DecideNow, Whenever]`), then green. One test row was wrong, not the code (on a UTC Sunday the next day is already next week); corrected with the reason in the row label.
- Domain `InlineActionPolicy`, `Decision` model and `validateInputs`, `ApprovalActions`, `BudgetIncident.validate`, `SnoozeTimes`: compile red (255 errors), then stubs gave 24 assertion failures, then green.
- Fakes: `AttentionCacheContract` + `FakeAttentionCacheContractTest` red (missing `AttentionFixtures`/`FakeAttentionCache`), then green. The gateway fakes carry the server's rules and have `AttentionFakesBehaviourTest`.
- Application `RefreshInbox`, `LoadInbox`, `LoadMoreInbox`: compile red (52 errors), then stubs returning `Failure(Malformed)` / an empty flow gave 9 assertion failures (casts of a failure to success, `expected 1 but was 0`, `Expected item but found Complete`), then green.
- Application `SnoozeItem`, `DismissItem`, `UndoDismiss`, `SetDecideBy` (+ `MutationResult`): compile red (54 errors), then stubs returning `Failed(Malformed)` gave 15 assertion failures (`expected Denied but was Failed`, window and time-zone cases), then green.
- Application `LoadDecision`, `DecideDecision`, `DismissDecision`, `PollDecisionExecution`: compile red (70 errors), then stubs gave 24 assertion failures (device-auth, ordering, reconcile and polling-interval cases), then green.
- Application `LoadApproval`, `ApproveApproval`, `RejectApproval`, `RequestRevision`, `CommentOnApproval`: compile red, then stubs gave 26 assertion failures, then green.
- Application `LoadBudgetIncident`, `ResolveBudgetIncident`: compile red, then stubs gave 15 assertion failures (operator cannot raise, 422 reason surfaced, reconcile when the incident left the active list), then green.
- Konsist `UseCaseBoundaryRulesTest`: the presentation-gateway rule was checked by temporarily importing `AttentionGateway` into `Common.kt` (the rule failed), then reverted.

## Slice 1a-2 decisions in code

- 2026-11-01: mutation use cases return `MutationResult` (`Done`, `Denied`, `Invalid`, `NotAuthorized`, `Failed`, `Reconciled`). `Reconciled` means the call failed with a timeout or conflict and the entity was read once to learn the truth; for approvals a 422 `Rejected` also re-reads because the server answers "already resolved" with 422, not 409. For a budget incident that left the active list the use case reports status `Unknown` ("no longer active").
- 2026-11-01: order inside every mutation use case: permission, status and input validation, device authentication, one gateway call, optional re-read. A viewer never reaches biometrics; a missing note never triggers a prompt.
- 2026-11-01: `FakeApprovalGateway` exposes its `store` map instead of a `put` function because detekt allows 10 functions per class.
- 2026-11-01: `PollDecisionExecution` reads up to ten times, one second apart (virtual time in tests), and returns the last read even if it is still running; the state holder decides what to show.
- Application `ResolveCompanyContext`, `AvailableSnoozePresets`, `ReadCachedInbox` and the domain `AttentionItem.budgetIncident()`: tests first, stubs with wrong values gave assertion failures (2 + 2 + 2 + 1), then green. The approval use cases changed from taking an `Approval` to `(id, status)` so an Inbox card can act without loading the approval: the existing tests were edited first (compile red), then the signature changed.
- Presentation `AttentionUi` and `FeedbackUi` mappers: the files were first written ahead of their tests by mistake; they were moved out, `AttentionUiTest`/`FeedbackUiTest` written and run (compile red), then the files restored. These two test classes therefore saw compile red but no separate assertion red.
- Presentation `InboxStateHolder` (+ `InboxSession`, `InboxRenderer`, action helpers): 30 tests written first; compile red (336 errors), then a holder stub that ignored every intent gave 30 assertion failures (`expected Content but was Loading`, `expected 1 but was 0`, ...), then green. One test needed a fix, not the code: `advanceUntilIdle()` also ran the 5 s undo timer, so the undo tests use `runCurrent()`.
- Presentation `DecisionStateHolder`: 22 tests first; compile red (101 errors), stub holder gave 22 assertion failures, then green. A real bug was caught by `polling that gives up leaves the execution running and not polling`: the first implementation re-entered polling from `show()` forever while the server stayed `running`; fixed by separating "apply a decision" from "apply and poll". Four tests also read `state` right after `onIntent` without `runCurrent()` (the state is derived asynchronously) and were fixed in the test.
- Presentation `ApprovalStateHolder`: 17 tests first; compile red (81 errors), stub gave 17 assertion failures, then green with no test changes.
- Presentation `BudgetIncidentStateHolder`: 16 tests first; compile red (115 errors), stub gave 16 assertion failures; the first implementation read `canRaise`/`canSubmit` from the rendered state (updated asynchronously) so the submit tests failed on assertions (`expected 1 but was 0`); fixed by computing the rule from the model synchronously.
- 2026-11-01: `DecisionStateHolder` takes a `DecisionTarget` (company + decision id + the Inbox card's agent-name and decide-by hints) because detekt allows at most 6 constructor parameters. Decision-detail Snooze (top bar, design convention) is deferred: it needs the Inbox item's dismissal key, which a decision id alone cannot give without a lookup the PRD does not specify.
- 2026-11-01: detail screens (Decision, Approval, Budget incident) are not cached; they stamp "Updated" with the time of their read. Only the Inbox feed has the cache port (ADR-017). Offline, a detail screen with nothing loaded is `Failed` with `isOffline`.
- 2026-11-01: Inbox "Raise and resume" is navigation, not a mutation: the card offers it and the platform UI opens the Budget incident sheet (`open` target `BudgetIncident`), where the amount is typed.
- 2026-11-01 (interop): slice 1a-2 screens are created through `graph.attention` (`AttentionScreens`), not on `PaperclipGraph` itself, because detekt allows 10 functions per class. `GraphInteropTests` creates all four holders and sends typed intents from Swift 6 with no warnings.
- 2026-11-01 (gate): the first full `check` failed on detekt (`TooManyFunctions` on the graph and module file) and two Konsist rules (a read-only use case without a `CompanyContext`, a public generic helper in presentation); fixed by splitting `AttentionScreens`/`SliceTwoModules`, narrowing the rule to mutations, and making the helper `internal`.

## Slice 1a-3 TDD log (red seen first, per group)

Same convention as slice 1a-2: "compile red" is the first test of a new type failing on a missing symbol, "assertion red" is a stub that returns a wrong value (never `TODO()`).
- Domain ids, `IssueStatus`, `IssuePriority`, `AgentStatus`, `RunStatus`, `InteractionStatus` (`WorkEnumsTest`): compile red (121 errors), then stub enums with `fromServer = Unknown` and false flags gave 6 assertion failures, then green.
- Domain `Agent`, `AgentGroup`, `AgentGrouping`, `PauseReason` (`AgentTest`): compile red (33 errors), then a stub model (`group = Idle`, flags false, grouping empty) gave 8 assertion failures, then green.
- Domain `Issue`, `IssueFilter`, `IssueQuery` (`IssueFilterTest`): compile red (36 errors), then green.
- Domain `Interaction`, `InteractionAnswer`, validation (`InteractionTest`, 14 tests): compile red (134 errors), then 1 assertion failure (`canBeResolvedBy` ignored `acceptanceBlocked`; code fixed), then green.
- Domain `HeartbeatRun`, `LogTail`, `RunEvents`, `DashboardSummary`, `SpendWindows` (`RunAndDashboardTest`): compile red (43 errors). One test line (`needsAttention`) asserted a property the spec never asked for and was removed; then green.
- Fakes for the five new ports (`WorkFakesBehaviourTest`, 9 tests): compile red (83 errors), then green on the first run.
- Application issue reads (`ListIssues`, `SearchIssues`, `LoadIssue`, `LoadIssuePlan`, `ListIssueAttachments`; 11 tests): compile red (63 errors), then green. Application issue mutations (`CommentOnIssue`, `ChangeIssueStatus`, `AssignIssue`, `CreateIssue`; 12 tests): compile red (80 errors), then 1 assertion failure (`expected c2 but was c1`: `maxBy` returned the first of two comments with the same time; fixed to take the last by time), then green.
- Application `LoadInteractions`, `RespondToInteraction` (9 tests), agents (`ListAgents`, `LoadAgent`, `PauseAgent`, `ResumeAgent`, `InvokeHeartbeat`, `SetAgentBudget`; 15 tests), runs (`LoadRun`, `TailRunLog`, `CancelRun`, `ListIssueRuns`; 14 tests), dashboard (`LoadDashboard`, `LoadWindowSpend`; 3 tests): written together, compile red (195 errors), then green except one test that was wrong, not the code (it expected one dashboard call across two loads).
- `WorkRoleMatrixTest` (acceptance 8 and 10 across the ten mutation use cases): passed on the first run; its value is as a regression net. Mutation spot check: removing the `Permissions` line from `CancelRun` made the viewer row fail (checked, then reverted).
- Presentation `WorkFeedbackTest` (new invalid keys): compile red (the new `InvalidInput` variants broke the exhaustive `when`), then green.
