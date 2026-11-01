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
- Domain `Interaction`, `InteractionAnswer`, validation (`InteractionKindTest` 3 and `InteractionValidationTest` 8, 11 tests): compile red (134 errors), then 1 assertion failure (`canBeResolvedBy` ignored `acceptanceBlocked`; code fixed), then green.
- Domain `HeartbeatRun`, `LogTail`, `RunEvents`, `DashboardSummary`, `SpendWindows` (`RunTest` 5 and `DashboardTest` 2): compile red (43 errors). One test line (`needsAttention`) asserted a property the spec never asked for and was removed; then green.
- Fakes for the five new ports (`WorkFakesBehaviourTest`, 9 tests): compile red (83 errors), then green on the first run.
- Application issue reads (`ListIssues`, `SearchIssues`, `LoadIssue`, `LoadIssuePlan`, `ListIssueAttachments`; 12 tests): compile red (63 errors), then green. Application issue mutations (`CommentOnIssue`, `ChangeIssueStatus`, `AssignIssue`, `CreateIssue`; 14 tests): compile red (80 errors), then 1 assertion failure (`expected c2 but was c1`: `maxBy` returned the first of two comments with the same time; fixed to take the last by time), then green.
- Application `LoadInteractions`, `RespondToInteraction` (10 tests), agents (`ListAgents`, `LoadAgent`, `PauseAgent`, `ResumeAgent`, `InvokeHeartbeat`, `SetAgentBudget`; 16 tests), runs (`LoadRun`, `TailRunLog`, `CancelRun`, `ListIssueRuns`; 11 tests), dashboard (`LoadDashboard`, `LoadWindowSpend`; 3 tests): written together, compile red (195 errors), then green except one test that was wrong, not the code (it expected one dashboard call across two loads).
- `WorkRoleMatrixTest` (acceptance 8 and 10 across the ten mutation use cases): passed on the first run; its value is as a regression net. Mutation spot check: removing the `Permissions` line from `CancelRun` made the viewer row fail (checked, then reverted).
- Presentation `WorkFeedbackTest` (new invalid keys): compile red (the new `InvalidInput` variants broke the exhaustive `when`), then green.
- Presentation `FactoryHomeStateHolder` (7 tests): compile red (34 errors), then 1 assertion failure (`expected Failed but was Loading`: offline with nothing loaded never left Loading; fixed in the holder), then green.
- Presentation `AgentsStateHolder` (13 tests): compile red (61 errors), then 1 assertion failure (`expected 1 but was 0`): two row actions sent back to back, the second is correctly ignored while the first is in flight; the test was wrong and now waits between them.
- Presentation `AgentDetailStateHolder` (11 tests), `RunViewerStateHolder` (12 tests), `WorkListStateHolder` (12 tests, including the two regression tests below): compile red (17, 53 and 56 errors), then green on the first run.
- Presentation `IssueStateHolder` (EditsTest 10, LoadTest 5, TabsTest 2, interaction rendering 4 and answers 10 tests): compile red (71 + 246 errors), then green on the first run after the production code was written (one name clash, `CommentUi` already existed for approvals, renamed `IssueCommentUi`). Interaction forms: drafts are `InteractionDraft` (picked options, free text, verdicts); every button builds exactly one `InteractionAnswer` and the domain `validate` gives the live `submitIssue` and `rejectIssue` keys.
- Umbrella wiring (`PaperclipGraphSliceThreeWiringTest`, 2 tests, demo mode through the real graph) and Konsist `DeviceAuthRulesTest`: compile red (45 errors) for the wiring test, then green; the Konsist rule is an equality of two sets, so it cannot pass vacuously.
- Mutation spot check: removing the `Permissions` line from `CancelRun` made 2 `WorkRoleMatrixTest` tests fail; reverted.

## Slice 1a-3 decisions in code

- 2026-11-01: unknown server enum values map to an `Unknown` entry on `IssueStatus`, `IssuePriority`, `AgentStatus`, `RunStatus`, `InteractionStatus` (same convention as `ApprovalStatus`); unknown interaction kinds become `UnsupportedInteraction` carrying the raw kind.
- 2026-11-01: the thread re-read after a timed-out comment (`CommentOnIssue`) returns the newest comment as `Reconciled.current`; the holder reloads the whole thread, which is the truth. A create has no entity to read, so `CreateIssue` reports `Failed(Timeout)` and the Work list reloads (it is the re-read).
- 2026-11-01: interactions have no device authentication step: FR-014's list has no interaction answer. The use case order is permission, addressee (`ResolverAudience.allowsUser`), the answer against the interaction, one call.
- 2026-11-01: detail screens do not cache (ADR-017); "Updated" is the read time. Offline with nothing loaded is a `Failed` phase with `isOffline`; offline after loading keeps the content and disables actions.
- 2026-11-01: `refreshIntervalSeconds` is exposed per state (15, 5 for a live run or an agent with a run, 0 for a finished run) for the slice 1a-4 polling policy.
- 2026-11-01: only one change is in flight per screen (`busy`), so two different actions in quick succession on the same screen are serialised: the second is ignored, not queued.
- 2026-11-01: `InvokeHeartbeat` re-reads the agent's runs after a timeout (a started run means the wake worked); `Reconciled.current` is `Started(run)` or `Skipped`.
- 2026-11-01: the server rejects a viewer on every mutation (`assertCompanyAccess`, `routes/authz.ts:108-118`), contrary to the first research report's "gap" note for cancel run and budgets; the app still gates by role first. Only the operator-sets-budget difference is real (ADR-021).
- 2026-11-01: detekt shaped the code: `IssueStateHolder` delegates to `IssueLoader`, `IssueEdits` and `IssueInteractions` over an `IssueSession`; `WorkEnumsUi.kt` and `InteractionUi.kt` split out UI enums; Koin wiring is in `SliceThreeModules.kt`.

## Swift interop review (slice 1a-3)

`GraphInteropTests` creates all six holders through `graph.work`, `graph.agents`, `graph.runs`, `graph.factory` and sends typed intents (filter, search, create, tab, comment, status, verdict, pause, budget, scroll, cancel) from Swift 6 with no warnings. Asserting on loaded state from Swift is racy (holders load on `Dispatchers.Default`), so the interop tests assert only on stable initial values.

## Deviations (slice 1a-3)

- `LoadIssuePlan` is a thin read of the issue's plan that the Plan tab uses to refresh; `LoadIssue` already returns the plan for the first render.
- `Interaction` withdraw, dismiss and cancel endpoints are not modelled; only accept, reject, respond, verdicts and skip (the PRD lists the first four per kind).
- "Updated" for the new screens uses the `Clock` at read time; there is no cache for these screens (ADR-017).
- Names of assignees and comment authors come from the agent list; a person who is not an agent shows as "me" or without a name until the Phase 1b adapter can resolve board users.

## Slice 1a-3 review fixes (each its own commit, regression test first)

- Work list load more used the number of rows left after the device dropped non-urgent rows as the next offset, so under Critical and high the next page repeated rows. Red: `load more under critical and high pages by the server's rows...` failed with `expected 25 distinct but was 80` (110 bulk issues, every fifth urgent). Fix: `IssuePage.serverRowCount` (asserted in `ListIssuesTest`) and a `serverRows` offset in the holder.
- Changing the filter or search while offline updated the chip but not the rows (the reload was skipped). Red: `expected AllOpen but was Blocked` in the offline acceptance-11 test. Fix: the holder ignores `FilterSelected` and `SearchChanged` offline.
- Swift interop assertions that raced with the holder's first load (`phase == .loading` on the new holders) were replaced with stable values (`filter`, `refreshIntervalSeconds`, `confirmCancel`, `confirmPause`). The slice 1a-1 and 1a-2 interop tests still assert `phase == .loading` on holders created in the same way; they were not touched.
- Research note checked: `getAccessibleResource` is the authz helper (`routes/authz.ts:182`, imported at `routes/agents.ts:139` and `routes/costs.ts:34`), so the viewer rejection for cancel run and budgets in `research.md` holds.

## Slice 1a-3 test counts (from the JUnit XML, jvmTest)

New classes: domain 39, application 69, presentation 88, testing 9, umbrella 3 (wiring 2, Konsist 1): 208 tests.

## Slice 1a-4 TDD log

Order of work: Snooze carry-over, demo data, contract suites, live events and refresh policy, traceability, Swift interop. Each group's red step, as it was actually seen:

- **Decision Snooze (carry-over a).** `SnoozeItemTest` changed first to pass `item.dismissalKey`: 5 compile errors (`String` where `AttentionItem` was expected). `DecisionSnoozeTest` (5 tests) written next: compile red (`canSnooze`, `snoozeOptions`, `SnoozeRequested` and the fifth `DecisionTarget` argument unresolved). Green after the holder, renderer and use case change.
- **Demo data (acceptance 5).** `DemoIds` plus a stub `createDemoGraph()` that returned the unseeded graph, then `DemoGraphTest` (14 tests): all 14 failed on assertions or 10 s timeouts (`NoSuchElementException` for PAP-1204, `Element differ at index` for the Inbox titles, timeouts for ids the unseeded fakes do not hold). After seeding, 12 passed and 2 needed a decision: the Inbox order (the mock lists the approval second, FR-030 sorts a group by severity, so the test now asserts the production order and records the deviation) and a race in the test itself (it sent `Refresh` to the Factories holder before the holder had read the instances; it now waits for the rows first).
- **Contract suites (acceptance 4).** The fakes already behaved, so there was no natural red. Four suites needed the fakes to be more honest and those fake changes were made with the suites (per-address health, key and revocation state in the auth fake, idempotency-key replay in the decision fake), not red first. Mutation check instead: I broke four fake rules and ran the suites. `FakeApprovalContractTest` failed with "rejecting an approved approval must be rejected with a reason but was Success", `FakeBudgetContractTest` with "a raise that does not exceed spend must be rejected", `FakeRunWriteContractTest` with "cancelling a run that already stopped must fail with Conflict" and `FakeAuthAccountContractTest` with "me after the key was revoked must fail with Unauthorized"; three older behaviour tests failed too. Reverted. After the final run all contract subclasses pass (one per port suite, 27 test classes).
- **Live events and invalidation (FR-091).** `InvalidationRulesTest` (5): compile red (`LiveEvent`, `InvalidationRules` unresolved), then green.
- **RefreshVisibleScreen (FR-090).** `RefreshVisibleScreenTest` and `RefreshOnLiveEventsTest` (12, virtual time): compile red (`RefreshTarget`, `RefreshVisibleScreen` unresolved); the first implementation used the experimental `flatMapConcat` (compiler error, replaced with `transform`), then green.
- **Holders as refresh targets.** `ScreenRefreshTargetTest` (6): compile red (`refreshTarget` unresolved on ten holders), then green. `DemoRefreshWiringTest` (3, umbrella): compile red (`graph.refresh`, `graph.lifecycle`, `resolve`), then green; the Android host run showed one flake (the live event was sent before the session had subscribed, events are not replayed) and the test now sends until it is seen.
- **Traceability and rules.** `RequirementsTraceabilityTest` red: FR-002, FR-013 and FR-097 had no tag (see below). The contract-per-port rule was red for `InteractionGateway`, `AgentGateway` and `DashboardGateway` (their suites did not name the port; docs fixed). The reserved-names rule extended to properties was red for `BudgetResolution.RaiseAndResume.newAmount` (renamed `amount`). The refresh-target rule was red for `FactoriesStateHolder`, which has a manual refresh but is not polled (explicit exemption).
- **Swift.** The tests were written against the built header; they passed on the first run (they check behaviour that the Kotlin demo tests already cover), so there is no Swift red step. Their value is the interop proof: values, `for await` on a state flow, `refreshTarget` and `graph.refresh`.

## Slice 1a-4 decisions in code

- 2026-11-01: `SnoozeItem.invoke` takes the `dismissalKey` instead of the whole item so the Decision screen can snooze without holding the card (ADR-025). `DecisionHints` replaced three positional arguments of `createDecisionStateHolder` (detekt's six-parameter limit).
- 2026-11-01: the demo clock is Wednesday 2026-11-04T12:00Z. On the fixture clock (Sunday 2026-11-01) the rule `isDecideNow(this_week)` is true, so "This week" would be empty (ADR-026).
- 2026-11-01: `refreshTarget` is a property over the holder's own state, not an interface the holders implement: the Decision holder was already at ten functions and detekt allows eleven.
- 2026-11-01: `RefreshVisibleScreen` refreshes on resume and on coming back online but not at start (the holder has just loaded); an interval of 0 means no timer; event-driven refreshes are at most one per second and newer events replace older ones (`conflate`).
- 2026-11-01: `FakeIssueGateway`, `FakeRunGateway` and `FakeInteractionGateway` gained constructor parameters for seeded comments, plans, attachments, events, log text and other issues' interactions; the old defaults are unchanged. `FakeSecretStore.seed` stores a token without the port so the demo Home factory starts paired.
- 2026-11-01: test names must not contain commas on Kotlin/Native (the iOS simulator compiler rejects them); the new tests avoid them.

## Swift interop review (slice 1a-4, acceptance criterion 6)

Built with `./scripts/build-kit.sh debug`; read `iosApp/Frameworks/PaperclipKit.xcframework/ios-arm64-simulator/PaperclipKit.framework/Headers/PaperclipKit.h` (15,108 lines) and the SKIE `swiftinterface`. All 16 state holders export exactly `state`, `onIntent(intent:)` and `close()`, and the ten polled ones add `refreshTarget`.

| Check | Finding | Action |
|---|---|---|
| `state` flows | Exported as `SkieSwiftStateFlow<State>` (an `AsyncSequence`); `.value` works. `for await state in holder.state` is covered by `aStateFlowIsAnAsyncSequence`. `refreshIntervalSeconds` is a `Flow<Int>` and becomes `SkieSwiftFlow<KotlinInt>`. | none |
| Generics | No state holder, state or intent type declares type parameters (Konsist `PresentationSurfaceRulesTest`). Generic Kotlin types appear only as SKIE flow wrappers and Objective-C lightweight generics (`NSArray<...>`) on collections. | none |
| Default arguments | Kotlin defaults do not exist in the header, so Swift must pass every argument (state data classes have defaults that Swift never sees; they are only built in Kotlin). `DecisionHints` and the graph functions have none. | none |
| `Result` | No `Result` or `DomainResult` in any presentation or umbrella signature (Konsist). The `DomainResult` in domain ports is exported but Swift never calls ports. | none |
| Names Objective-C renames | One: the getter `BudgetResolution.RaiseAndResume.newAmount` was exported as `doNewAmount` (Swift name kept). | Fixed: renamed `amount`; the Konsist reserved-name rule now covers properties of domain, application, presentation and umbrella. Data class `copy` is exported as `doCopy` (322 times); harmless, Swift never copies a state. |
| Sealed hierarchies | Every sealed type reaches Swift with an `onEnum(of:)` function (126 in the interface); intents are `data object` (`X.shared`) or `data class` (initialiser). No hierarchy is unexported: domain, application and presentation are all exported through the umbrella. | none |
| `Sendable` and `@MainActor` | The header has no `Sendable` conformances and no actor annotations. Holders are plain reference types: the tests keep them on `@MainActor`, which builds with `SWIFT_STRICT_CONCURRENCY: complete` and no warnings. | Rule for the apps: create and use a holder on the main actor; never pass one to another actor. |
| Flows that are not `AsyncSequence` | None on a holder. Domain ports expose `Flow` (`observeAll`, `isOnline`, `events`) which SKIE also wraps; the apps do not use them. | none |
| Nullable numbers | 21 properties of state and UI types are `Long?` or `Int?` and arrive as `KotlinLong?` or `KotlinInt?` (for example `updatedAtEpochMillis`, `expiry.daysLeft`), read with `.int64Value` or `.intValue`. A `StateFlow<Boolean>` value is a `KotlinBoolean` (`.boolValue`). | Deferred: replacing them with sentinel values would touch most state classes and tests. |
| Public initialisers of holders | Every holder exposes an initialiser that takes use cases and a `CoroutineScope`, so Swift could build one outside the graph. | Deferred: the graph is the only supported way; hiding the initialisers needs `internal` constructors and a test-only factory. |
| Suspend functions | Use cases exported from the application layer have `suspend operator fun invoke` (`async throws` in Swift). The apps call holders, not use cases. | none |
| Testing module | `shared/testing` is not exported (no `Fake*` in the header). `PaperclipCore.createDemoGraph()` is the only demo entry. | none |

Swift tests: `DemoGraphInteropTests.swift` has one case per holder family (factories, attention, work, agents, run and factory home, flow and refresh), each reading real mock values after waiting for the settled state (`eventually`), 13 cases. The earlier `GraphInteropTests` no longer assert a loading phase (carry-over c): `appStateStartsLockedAndUnloaded` became `appStateIsLockedOnceItsSettingsHaveLoaded`, and the inbox, decision and approval cases wait for the settled phase or assert only values that never change. `./scripts/ios-test.sh` ran green with zero Swift warnings; `swiftlint --strict iosApp` and `swift-format lint --recursive --strict` report nothing.

## Deviations and gaps (slice 1a-4)

- The Inbox "Decide now" order is decision, budget stop, approval (severity, FR-030); the mock shows the approval second.
- Not in the model, so not in the demo state: an agent's next heartbeat ("CEO idle, next heartbeat in 8m", the server does not return it in the agent list, research A1) and the network label ("Tailscale", derived by the UI from the `.ts.net` address). The Approval screen carries `reportsToAgentId`, not the name; the card's server text ("reports to CTO") is the `whyNow`. A name join for the Approval detail is deferred.
- Runs 3 and Files 2 on the Issue tabs are available after the person opens those tabs (`runsLoaded`, `filesLoaded`), as designed in slice 1a-3.
- The demo fakes ignore the company id: Side Project Co shows the Acme Labs data.
- The events and the log of the demo Run viewer are the same for every run (the run fake holds one event list and one log).
- The timer uses coroutine time, not the `Clock` port (ADR-027).
- Carry-over (b): resolved versus dismissed cannot be read from the server (ADR-029, research B1); documented as a known limitation, no data invented.
- CI workflows (`ios.yml`, `contract.yml`, `security.yml`, `shared.yml`) were not run: no pull request exists for the branch. The local equivalents are green.

## Slice 1a-4 test counts

New test functions: domain 5, application 12, presentation 14, testing 23, umbrella 23 (14 demo graph, 3 refresh wiring, 6 Konsist rules), Swift 13 new in `DemoGraphInteropTests` plus 10 existing cases kept (6 edited). JUnit XML totals on the JVM after the final run: domain 135, application 241, presentation 266, testing 49, umbrella 25 (716), plus 23 Konsist tests. Kover, fresh `reportCore.xml` and `reportPresentation.xml`: see the status file.

