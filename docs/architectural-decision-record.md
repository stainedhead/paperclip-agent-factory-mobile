# Architectural decision record

One entry per significant decision, numbered in order, newest last. Every entry has the three parts **Context**, **Decision** and **Consequences**. A decision is not rewritten after the fact; a later change is a new entry, and a correction of a name or a reference is added as a **Note** under the entry it concerns.

## ADR-001: iPhone first in the UI, shared core first in the build

**Context.** The PRD suggests building Android first when there is one UI stream. The request was an iPhone app that reuses the core across UIs.
**Decision.** Build the shared core (domain, application, presentation, fakes) before any screen, then the iPhone UI against it. Android follows and reuses everything below the UI.
**Consequences.** Slower feedback on the core than an Android-first order; covered by JVM tests and demo mode. No SwiftUI screen waits on a server.

## ADR-002: Ship the core to iOS as an XCFramework in a local Swift package

**Context.** The core must reach Xcode either through a Gradle build phase or as a prebuilt framework.
**Decision.** `scripts/build-kit.sh` builds `PaperclipKit.xcframework`; `iosApp/Packages/PaperclipKit` wraps it as a binary target.
**Consequences.** Xcode resolves it natively and CI is deterministic. The framework must be built before the Xcode project resolves.

## ADR-003: Modular shared Kotlin with inward dependencies, enforced by Konsist

**Context.** Android and the push relay should reuse the core without UI code.
**Decision.** Separate Gradle modules per layer and per infrastructure concern; Konsist tests fail the build on a wrong import.
**Consequences.** More modules to maintain; the layering cannot erode unnoticed.

## ADR-004: Poll by default; the WebSocket is an optional adapter

**Context.** Paperclip 2026.1023.0 does not accept board API keys on its live-events WebSocket.
**Decision.** A shared use case refreshes the visible screen on a timer; a WebSocket adapter is built later and enabled only if the server accepts board keys.
**Consequences.** Slightly slower updates; an upstream fix changes one adapter. The policy itself is ADR-027.

## ADR-005: Targets are jvm, android (AGP KMP library plugin) and iOS

**Context.** AGENTS.md named `androidUnitTest`; AGP 9's KMP library plugin names host tests `androidHostTest` and device tests `androidDeviceTest`. A `jvm` target is also needed for fast tests and the relay.
**Decision.** Use the new plugin, add `jvm`, and update AGENTS.md.
**Consequences.** Kover measures the JVM runs of the shared tests; Android host tests still run in `allTests`.

## ADR-006: XcodeGen owns the Xcode project

**Context.** A committed `.xcodeproj` produces noisy merges and hides settings.
**Decision.** `iosApp/project.yml` is committed; `iosApp.xcodeproj` is generated and git-ignored.
**Consequences.** Contributors install XcodeGen; scripts generate the project before building.

## ADR-007: Coverage gates are two Kover variants

**Context.** The PRD wants one aggregate 90% gate over domain + application and a separate one for presentation. Kover rules cannot filter by package.
**Decision.** Two custom variants (`core`, `presentation`) with package filters, each with its own verify rule.
**Consequences.** `koverVerify` alone checks nothing meaningful; use `koverVerifyCore koverVerifyPresentation` (both run in `check`).

## ADR-008: Contract tests run against a pinned Docker image in authenticated mode

**Context.** The developer's local Paperclip is in `local_trusted` mode, which the app refuses by design, and changing it would affect their web app.
**Decision.** A throwaway container at the catalog's `paperclip-server` version on host port 3199.
**Consequences.** Docker is required for contract tests; the version bump is its own pull request.

## ADR-009: Swift-facing names avoid Objective-C reserved prefixes

**Context.** Kotlin/Native renamed `newFirstRunStateHolder` to `doNewFirstRunStateHolder` in Swift.
**Decision.** A Konsist rule rejects public functions starting with `new`, `alloc`, `copy`, `mutableCopy` or `init` in presentation and umbrella.
**Consequences.** Factory methods are named `create...`.

## ADR-010: The inbox badge is the server's `deskBadgeCount`, not a number the app computes

**Context.** The PRD assumed a `decideNowCount` that the server does not provide. The feed returns `deskBadgeCount` (distinct items new today, by UTC day, or with a `decideBy` due or overdue), and that is what the web app shows. The web app's "Decide now" shelf is `decideBy` due today or past only.
**Decision.** Owner decision on 2026-11-01: match the web app. The tab badge and app icon badge show `deskBadgeCount`. The domain mirrors the server's `isDecideNow` and `isNewToday` so the app's groups agree with the number. Severity orders items and never changes their group.
**Consequences.** The number on the phone equals the number on the desk. The Inbox header reads "<n> new or due today", and the Decide now group can hold fewer cards than the badge. If the server changes its rule, one domain function and its tests change.

## ADR-011: Company roles map fail-closed from the server's membership roles

**Context.** The server has five human membership roles (`owner`, `admin`, `operator`, `viewer`, `member`) plus an instance-admin flag, and enforces only "viewers are read-only". The PRD has three personas and makes budget raise owner-only, which the server does not enforce (research Q6/Q9).
**Decision.** Instance admin, `owner` and `admin` map to Owner; `operator` to Operator; `viewer`, `member` and any unknown value to Viewer. `Permissions` is a single Role x Action table in the domain; local factory actions (sign out, rename, remove, settings) are open to every role because they never call a company mutation.
**Consequences.** An unknown future role can only lose abilities in the app, never gain them. The server stays the authority; a 403 after a role change returns `Forbidden`.

## ADR-012: Pairing holds the token in memory only until approval, and changing a factory's address drops its credentials

**Context.** The server returns the board API token when the challenge is created, before the user approves in the browser. The challenge token and the approval URL embed a secret. A board key must never be sent to a host other than the one that issued it.
**Decision.** `PairInstance` keeps the pending token and challenge token as `Secret` values (which redact `toString()` and can be wiped), writes the token to the `SecretStore` only on `approved`, and wipes every in-memory copy on every exit. Editing a factory's address deletes its secrets and marks it Signed out. `SignOutInstance` always deletes local secrets even when the server revoke is unconfirmed, and reports that fact.
**Consequences.** Cancelled or expired challenges leave nothing behind. Re-pairing is required after an address change.

## ADR-013: Device authentication is a domain policy applied inside the use case

**Context.** FR-014 lists the actions that need device authentication and says it cannot be turned off.
**Decision.** `DeviceAuthPolicy.requires(action)` is a pure domain function with no settings input. Use cases call `DeviceAuthGuard` (application) before any gateway or repository call; if the `Biometrics` port does not approve, nothing else runs. `RemoveFactory` joins the list in the PRD's design rules.
**Consequences.** The UI cannot skip or weaken the check. Setting a monthly agent budget is not on the PRD's list and does not require authentication; only the budget raise does.

## ADR-014: Contract suites are plain classes in `shared/testing`, run by thin test subclasses

**Context.** `shared/testing` ships inside `PaperclipKit.xcframework` through the umbrella, so it must not depend on kotlin-test.
**Decision.** Port contract suites are abstract classes with `suspend fun runAll()` that use `check`; each implementation (the fake now, the real adapter in Phase 1b) has a one-line `@Test` in its own test source set.
**Consequences.** The same assertions guard the fake and the adapter without a test framework in a shipped module.

## ADR-015: The Inbox models `decideBy` as a sealed type and groups with the server's UTC rules

**Context.** FR-030 names three groups but the server only sends a `decideBy` string (`today`, `this_week`, `whenever`, `YYYY-MM-DD`, or null) and mirrors `isDecideNow`/`isNewToday` with UTC day boundaries (research Q1, ADR-010).
**Decision.** `DecideBy` is a sealed type (`Today`, `ThisWeek`, `Whenever`, `OnDate`, `None`) parsed from the server string; anything unparseable (an unknown preset, an impossible date) is `None`, the same as the server's bucket 2. `AttentionRules.isDecideNow` mirrors the server exactly, including that `this_week` is "Decide now" on a UTC Sunday (the end of the week is the end of that day). Grouping: **Decide now** = `isDecideNow`; **This week** = `ThisWeek`, or a date after today and on or before the UTC Sunday; **Whenever** = `Whenever`, `None`, and a date beyond this week (FR-030 is silent on it; a distant deadline is not urgent). Severity only orders items inside a group. An unknown severity maps to `Medium`, unknown source kinds, detail kinds, effect types and approval types map to `Unsupported(raw)`; nothing throws.
**Consequences.** The group counts agree with `deskBadgeCount` wherever the badge counts due items. The Sunday and midnight edges are in the table-driven tests. If the server changes its rule, `AttentionRules` and its table change together.

## ADR-016: `Money` is integer minor units, USD by default, and observed spend rounds up

**Context.** Budget amounts are cents (`billed_cents`). The server stores observed spend as `numeric(24,7)` so it can be fractional, and no currency field exists on incidents (research Q5).
**Decision.** `Money(minorUnits: Long, currency: String)` has no floating-point member. Mappers create it from cents with currency `USD` (the web app's default). The adapter rounds a fractional observed amount **up** to whole cents, so the app's "new amount must exceed observed spend" pre-check is never more permissive than the server's.
**Consequences.** Formatting is a presentation concern. If Paperclip adds a currency, the mapper passes it through without a model change.

## ADR-017: The Inbox is cache-first through an `AttentionCache` port

**Context.** FR-095: read screens render the cache first, labelled "Updated <time>", then refresh. The cache must be per factory and company (item ids are unique only inside one instance, research Q7).
**Decision.** A small port `AttentionCache` (`read`, `write`, `clear`, keyed by `CompanyScope`) stores the **first page** of the feed. `LoadInbox` returns a `Flow` that emits the cached feed (marked from cache) and then the result of `RefreshInbox` (network, retried as a read, written to the cache on success); on failure it emits the error and the screen keeps the cached feed. Later pages are never cached, so a stale cursor can never be replayed. "Updated" is the feed's `generatedAt`, the server's own timestamp. The cache is not the source of truth for dismissals: an optimistic dismiss removes the item from the screen only; the next refresh decides.
**Consequences.** Persistence (SQLDelight, Phase 1b) implements three functions and passes a shared contract suite. An empty cache with no network shows an explanatory empty state, never a blank screen.

## ADR-018: Decisions: inputs belong to the decision, `decideBy` and the agent name are hints, and decide carries an idempotency key

**Context.** The PRD attached `inputs[]` to options; the server attaches them to the decision and validates them whichever option is chosen. `GET /decisions/{id}` has neither `decideBy` (triage data) nor the agent name (research Q4, Q8).
**Decision.** `Decision.inputs` lives on the decision. `Decision.decideBy` and `Decision.originAgentName` are optional; the Decision screen is opened with the feed item's values as hints and the Phase 1b adapter may fill them. `DecideDecision` takes the `DecisionId`, re-reads the decision (ADR-032) and sends an `idempotencyKey` made with the `IdGenerator`, so a manual re-tap after a timeout replays instead of running the effects twice; the use case itself never retries. `PollDecisionExecution` re-reads until the execution has finished or the decision is no longer open, waiting with coroutine `delay` for `INTERVAL_MS` between at most `MAX_READS` reads (ADR-027: timers are coroutine time, not the `Clock` port). Originally proposed as polling "on the `Clock`" with a capped number of attempts.
**Consequences.** A timeout after decide re-reads the decision (FR-096) and shows what really happened. A 409 re-reads and shows the final state with no options.

## ADR-019: Inline actions are server flag AND app support AND role; mutations run in a fixed order

**Context.** The server's `inlineResolvable` is true for eight source kinds; the app only has inline actions for approvals and hard-stop budget alerts (FR-033). The brief requires that a viewer never reaches a gateway and that nobody authenticates only to be told a field is empty.
**Decision.** One pure domain function (`InlineActionPolicy`) decides the actions of an item. Every mutation use case takes a `CompanyContext(scope, role)` and runs in this order: (1) permission, (2) input and note validation, (3) device authentication when `DeviceAuthPolicy` requires it, (4) one gateway call, (5) on `Timeout` or `Conflict` a re-read of the entity. Offline gating stays in the state holders (existing convention, acceptance 11): the use cases have no `Connectivity`.
**Consequences.** A viewer, a missing note, a cancelled biometric prompt: zero gateway calls, each tested. A decision can say what is allowed without knowing the server.

## ADR-020: Snooze presets use the device time zone through a `DeviceTimeZone` port

**Context.** "Tonight", "tomorrow" and "next week" depend on the person's local time; `Clock` is UTC only. The server rejects a `snoozedUntil` that is not in the future.
**Decision.** A `DeviceTimeZone` port (with `FakeDeviceTimeZone`) supplies the zone. Presets: one hour = now + 1 h; tonight = 20:00 local today; tomorrow = 08:00 local tomorrow; next week = 08:00 local on the next Monday (a Monday means seven days ahead). `SnoozeItem` refuses a time that is not after now (for example "tonight" after 20:00, which the holder does not offer). Grouping stays UTC.
**Consequences.** Daylight-saving changes are handled by kotlinx-datetime; tests fix the zone.

## ADR-021: A request that the server refuses to a viewer is still blocked in the app, and owner-only budget stays client-side

**Context.** Research A2/W4: the server rejects every non-GET for a `viewer` membership, but it lets an operator set an agent budget (no permission key) and never asks for device authentication.
**Decision.** `Permissions` stays the single table. Every slice 1a-3 mutation use case takes a `CompanyContext`, checks `Permissions.allows` first and then follows ADR-019's order. `SetAgentBudget` is owner only (PRD) and has no device authentication (FR-014 lists "raising a budget" for incidents only; see ADR-013).
**Consequences.** A viewer or an operator never reaches the gateway for a budget; a role change mid-session is handled by the server's 403 (`Forbidden`).

## ADR-022: Saved filters (`IssueFilter`) map to server params, except priority which is filtered on the client

**Context.** Research W1: the server has no `priority` filter and the web has none of the PRD's five saved views by those names.
**Decision.** `IssueFilter` has `AllOpen` (`status=backlog,todo,in_progress,in_review,blocked`), `Blocked` (`attention=blocked`), `InReview` (`status=in_review`), `AssignedToMe` (`assigneeUserId=me`, restricted to open statuses) and `CriticalAndHigh` (all open statuses from the server, then urgent priority on the device by `IssueFilter.matches`). Search sends `q` and keeps the filter. The list uses `view=compact` and pages by `limit` and `offset`; the gateway takes an `IssueQuery` so Phase 1b builds the URL. Originally proposed as `SavedFilter` and `SavedFilter.matches`.
**Consequences.** `CriticalAndHigh` can show fewer than a page; the holder keeps loading while a page was full. The fake applies the same rules as the server.

## ADR-023: Interactions are a sealed domain type with five supported kinds; anything else is `UnsupportedInteraction` and is never sent

**Context.** Research W7/W8: six server kinds with large payloads; `connection_intent` has other endpoints; the resolver rule for a board user is addressee plus creator plus viewer role.
**Decision.** `Interaction` is a sealed interface: `SuggestTasks`, `AskUserQuestions`, `RequestConfirmation`, `RequestCheckboxConfirmation`, `RequestItemVerdicts` (only the fields a form renders) and `UnsupportedInteraction` (also for `connection_intent`). The extension `Interaction.validate` checks a typed `InteractionAnswer` and returns an `AnswerIssue` (or null), covering required answers, reasons and selection counts on the device; `RespondToInteraction` maps a valid answer to exactly one gateway call and never takes an `UnsupportedInteraction`. `ResolverAudience.allowsUser` decides who may answer; an interaction that is not for the user, not pending, or has an `acceptanceBlocker` is read only. Originally proposed as `Unsupported(kind)` and `Interaction.answerFor`.
**Consequences.** The mobile app cannot send an invalid payload; the server's 422 remains the backstop.

## ADR-024: Names are joined on the device, "current issue" comes from live runs, and the tail is by byte offset and sequence

**Context.** Research W2/A1/R1: the issue, comment and agent rows carry ids, not names; there is no current-issue field; logs are read by byte offset and events by sequence number.
**Decision.** Gateways return domain objects with the ids; `LoadIssue` and `LoadAgent` join assignee and author names from `AgentGateway`. An agent's current issue is the `issueId` of its active live run. `RunGateway.log` reads from a byte offset and returns a `LogChunk` (`content`, `nextOffset`); `TailRunLog` takes and returns a bounded `LogTail`, and `RunGateway.events` reads by sequence number. The Run viewer keeps `autoScroll` on until the user scrolls up (`UserScrolled` intent) and turns it back on at `JumpToLatest`. Originally proposed as a `LogCursor(offset)`.
**Consequences.** The polling policy of slice 1a-4 only has to call the holders' `Refresh` intent at the right interval.

## ADR-025: Decision detail offers Snooze only when it was opened from an Inbox card

**Context.** The Decision mock has Snooze in the top bar and the PRD says it uses FR-034's presets and endpoint. That endpoint (`inbox-dismissals`) needs the Inbox card's `dismissalKey`; the decision endpoint does not return it, and a decision opened from anywhere else (a link, the Work list) has no card.
**Decision.** `InboxItemUi` carries `dismissalKey`; the screen passes it to `createDecisionStateHolder` inside `DecisionHints(originAgentName, decideBy, dismissalKey)`. With a key the Decision state has `canSnooze` and `snoozeOptions`, and the `SnoozeRequested(preset)` intent calls `SnoozeItem` (which now takes the key, not the whole item); without a key `canSnooze` is false and the intent does nothing. After a snooze `isSnoozed` is true and the screen closes. A viewer, an offline phone and a decision that is no longer open cannot snooze.
**Consequences.** Both apps hide the Snooze button when `canSnooze` is false. No server lookup of the key is needed. A later deep link into a decision shows no Snooze until the Inbox tracks the card.

## ADR-026: The demo data set is its own set of objects, on a mid-week clock

**Context.** The mocks need exact figures (All 9, Decisions 3, badge 3, $1,240 of $2,000, ...) and earlier slices' tests depend on the slice fixtures, which differ from the mocks. The fake attention gateway computes the counts and the badge from its items, and the grouping rule puts `this_week` items on a Sunday into "Decide now".
**Decision.** `shared/testing/demo` holds `DemoIds`, `DemoFactories`, `DemoInbox`, `DemoAttentionDetails`, `DemoWork` and `DemoAgents`; the old fixtures are untouched. `PaperclipCore.createDemoGraph()` builds the graph on fakes seeded from them (`DemoModules.kt`), with the clock frozen at Wednesday 2026-11-04T12:00Z. The nine cards are seeded so the counts follow from the rules: three due today and created before today (badge 3), and the other six not new today. Money is in minor units. The seven mocks fix the values: device "Alex's iPhone", person "Alex Rivera" (Owner of Acme Labs, Operator of Side Project Co), Home factory `https://paperclip.tail1234.ts.net`, Cloud factory `https://paperclip.example.com`, Test factory `http://localhost:3100`. Only the Home factory has a (fake `pcp_board_TESTONLY...`) key in the secret store, ending a91f with 23 days left; Cloud factory is Signed out with an expired key, Test factory is Signed out with none. `createGraph(allowInsecureLocal)` is unchanged.
**Consequences.** Every company of the demo shows the Acme Labs data (the fakes ignore the company id). The mock lists the approval second in "Decide now"; FR-030 sorts a group by severity, so the critical budget stop is second (the rule wins over the mock). Gaps the model cannot show are in implementation-notes.

## ADR-027: The refresh policy runs on coroutine time, is driven by each holder's `refreshTarget`, and ends when the session stops

**Context.** FR-090 (15 s, 5 s on a running run, none in the background or offline, none for a finished run) and FR-091 (live events invalidate by type). The demo `FakeClock` is frozen, so a loop that waits for `Clock.now()` would never fire.
**Decision.** `RefreshVisibleScreen(connectivity, lifecycle, liveEvents)(scope, target)` returns a `RefreshSession` with `stop()`. It combines online and foreground into one `active` flow; while active it refreshes every `target.refreshIntervalSeconds` (a flow, so a run that starts or finishes restarts the timer, and 0 means no timer), refreshes at once when the app returns to the foreground or the phone comes back online (not at start: the holder has just loaded), and refreshes when `InvalidationRules.invalidationsFor(event)` names something `target.isAffectedBy` (at most once a second, newer events replace older ones). Timers use `delay`; the `Clock` port stays for timestamps. Each of ten state holders exposes `refreshTarget` (a `ScreenRefreshTarget` over its own state and `Refresh` intent), so holders import nothing new and the apps pass `holder.refreshTarget` to `graph.refresh.start`. New ports: `LiveEvents` (no real adapter, board keys fail on the WebSocket) and `AppLifecycle` (the shell reports it through `graph.lifecycle.enterBackground()` and `enterForeground()`). Inbox, Decision, Approval and Budget states gained `refreshIntervalSeconds`.
**Consequences.** Tests run in virtual time. A holder cannot be refreshed twice at once by the policy because the holders ignore a `Refresh` while one is loading. When the WebSocket opens to board keys, only a `LiveEvents` adapter is added.

## ADR-028: Every port has a contract suite, and every Phase 1a requirement id is named by a test

**Context.** Acceptance criteria 1 and 4. The fakes passed their own behaviour tests but several ports had no suite a real adapter could reuse, and nothing checked the requirement ids.
**Decision.** Plain contract classes (ADR-014) now exist for every domain port, grouped by size (for example `IssueReadContract` and `IssueWriteContract`); they use only port calls and abstract handles (ids the subclass guarantees), never a fake's `calls` or `store`, and cite the research note they encode. Konsist rules check that every port is named by a suite in `shared/testing/contract` and that every FR id of spec section 4 appears in a `commonTest` source (`RequirementsTraceabilityTest`), with an explicit exemption map that must be empty unless the status file explains it.
**Consequences.** Phase 1b adapters subclass the suites. The fakes were made more honest where a suite needed it (per-address health, key state in the auth fake, idempotency-key replay in the decision fake).

## ADR-029: A budget incident that is gone from the active list is "no longer active", not resolved or dismissed

**Context.** Carry-over from slice 1a-2: can the app tell resolved from dismissed after the fact?
**Decision.** No. `GET /api/companies/{c}/budgets/overview` lists only incidents with status `open` (`svc/budgets.ts:931`), and the resolve response is the only place the status is returned. The Budget screen shows `KeptPaused` or `Raised` from its own resolve response and `NoLongerActive` for an incident that disappeared (resolved here earlier, resolved on the web, or dismissed by the server). The data is not invented.
**Consequences.** Documented as a known limitation in `docs/technical-details.md`; an upstream endpoint for one incident would remove it.


## ADR-030: A probe result is bound to the address it was taken for, and the same address may be added more than once

**Status:** Accepted (2026-11-01, review fixes F-B-1 and F-A-4)

**Context.** `AddInstance` and `UpdateInstance` trusted a `HealthReport` the caller passed, and the holders applied a probe result that arrived after the address had been edited. An unprobed, possibly `local_trusted`, address could be saved.

**Decision.** `ProbeResult` (Accepted, Refused, Failed) carries the `baseUri` it was taken for. `AddInstance` takes a `ProbeResult.Accepted` and answers `ProbeRequired` when its `baseUri` is not the validated address; it still refuses a `local_trusted` report. `UpdateInstance` answers `ProbeRequired` for an address change unless `InstanceEdit.probed` is an accepted probe of exactly the new address. The first-run and edit holders keep the probe job, cancel it when the address changes and drop a result whose `baseUri` is not the current address, so the rules live in the application layer and the UI flags only reflect them.

**Duplicate addresses.** The same address is allowed more than once. Names are unique (ignoring case), but two factories may point at one server, for example two accounts or a work and a personal board key on one instance; each has its own `InstanceId`, secret namespace and key. Rejecting a repeated address would block that and protect nothing, because the key, not the address, is the credential.

**Consequences.** A late probe can no longer be applied elsewhere; the check costs one value comparison. Wrapping a bare `HealthReport` for `AddInstance` is no longer possible outside tests.

## ADR-031: A 401 marks the factory Signed out in one place: decorators over the company-scoped gateways

**Status:** Accepted (2026-11-01, review fix F-A-3)

**Context.** PRD FR-023 says a 401 marks the factory Signed out and keeps its configuration. Only the access and key-list use cases did it; the other ~30 use cases returned `Unauthorized` and left `Instance.status` at `SignedIn`.

**Decision.** `SignedOutAware<X>Gateway` decorators (application layer) wrap the nine company-scoped gateways and call `SignedOutMarker` on every `DomainError.Unauthorized`, so no use case repeats the check. The composition root binds adapters (or fakes) under the `RawPort` qualifier and gives use cases the decorated bindings. The marker writes only when the factory is not already Signed out, so repeated 401s cost one status write. The access and key-list use cases keep their explicit calls (they also go through `AuthGateway`, which is not company-scoped and is not decorated).

**Consequences.** Real Phase 1b adapters must map HTTP 401 to `DomainError.Unauthorized` (and 429 to `RateLimited`); the decorator only sees domain results. A new company-scoped gateway needs a decorator and a `RawPort` binding.

## ADR-032: A mutation may read the state before its one gateway call; a timeout is reconciled against it, never guessed

**Status:** Accepted (2026-11-01, review fixes F-A-6 and F-A-8)

**Context.** "One gateway call per mutation, never retried" (ADR-019) left reconciliation after a timeout to a single re-read. For run now and comments that read cannot tell whether THIS tap took effect: a live run that was already running, or another person's newest comment, looked like success, and a missing run was reported as `Skipped`, a verdict the server never gave. Separately, destructiveness and eligibility were decided from an entity the caller loaded earlier.

**Decision.** The rule is "one gateway call for the MUTATION". A use case may read before it, never retries the mutation, and never makes a second mutation. `InvokeHeartbeat`, `CommentOnIssue` and `CommentOnApproval` read the live run ids or comment ids before the call; after a timeout or conflict they read once more and report `Reconciled` only for a NEW live run, or a new comment by this person with this text. Otherwise the result is `MutationResult.Unknown(error)`: the call may or may not have happened. If the read before the call failed there is nothing to compare, so the result is also `Unknown`; if the read afterwards fails the original error is reported as `Failed`. Presentation maps `Unknown` to `FeedbackKind.Unknown` (a refresh prompt, no success, no "skipped") and reloads the thread. Re-reading before deciding device authentication (F-A-8) uses the same allowance.

**Consequences.** Run now and comments cost one extra read per tap. `InvokeOutcome.Skipped` stays in the domain for the server's own verdict, which the reconciliation path no longer produces.

**Scope of the re-read (F-A-8).** `DecideDecision` is the only mutation whose device-authentication requirement is derived from an entity (`option.isDestructive`), so it alone takes an id and reads the decision first. For `PauseAgent`, `CancelRun`, `ResolveBudgetIncident` and the approval actions the requirement is fixed by the action (`DeviceAuthPolicy`), so a stale entity can at worst fail an eligibility pre-check that the server repeats and the use case reconciles; those keep their entity parameters and avoid a second read per tap.

## ADR-033: Configuration and credential changes of one factory are serialised by a per-factory lock

**Status:** Accepted (2026-11-01, residual race of review fix F-A-1)

**Context.** An address edit (`UpdateInstance`, which deletes the credentials of the old address) and a key enrollment (`KeyEnrollment`, which writes the key and then the status) were each correct alone, but interleaved they could revert the address to the old one with the status Signed in and no key.

**Decision.** `InstanceLocks` (application) hands out one `Mutex` per `InstanceId`. `UpdateInstance`, `KeyEnrollment` (put, re-read and update), the local half of sign out and remove (`deleteCredentialsLocally`, taken inside `NonCancellable`) and `SignedOutMarker` run their read-modify-write under it. A lock is never held across a server call. The composition root creates one `InstanceLocks` and passes it to those use cases; there is no global state, so tests build their own.

**Consequences.** A concurrent edit waits for an enrollment to finish and then wins or loses cleanly: either the new key on the old address is kept, or the address changes and the key is removed with the namespace. The use case constructors take the lock as an argument.

## ADR-034: Read retries share a per-use-case budget, stop when offline, and never repeat a certificate failure

**Status:** Accepted (2026-11-01, review fix F-A-11)

**Context.** Every read retried on its own (500 ms, 1 s, 2 s), so a use case with three reads waited up to 10 s before showing a failure, an offline phone waited the full series, and a certificate or name failure, which can never succeed, was retried as if it were a dropped connection.

**Decision.** `RetryBudget` holds the 3.5 s a use case may spend waiting across ALL its reads; `retryingRead(budget)` stops retrying when the budget is spent or `Connectivity` says offline. `LoadApproval`, `LoadIssue` and `LoadInstanceAccess` (the multi-read use cases) take `Connectivity` and share one budget per call; single-read use cases keep a fresh budget. `DomainError.CertificateInvalid` is split from `Unreachable` and is not transient, so it reaches the screen at once (`ErrorKey.CertificateInvalid`).

**Consequences.** Phase 1b adapters must map TLS and name-verification failures to `CertificateInvalid` and plain connection failures to `Unreachable`. Mutations are still never retried.

## ADR-035: The demo graph, the fakes and the contract suites ship inside the framework

**Status:** Accepted (2026-11-01, review fixes F-C-13 and F-D-12)

**Context.** `shared/umbrella` depends on `:shared:testing` so that `createDemoGraph()` can run the whole app on fakes without a server. The consequence is that the fakes, the contract suites and the demo data set (with fake test-only keys) are part of `PaperclipKit.xcframework` and the Android library.

**Decision.** Accepted for Phase 1a because demo mode is a product feature of the walking skeleton and the screens are built against it. The fake keys are obviously fake (`pcp_board_TESTONLY...`), no real secret can be in them, and `createGraph` (the real graph) does not reference them. Whether the demo graph stays in a store build is a Phase 2 decision (PRD FR-009 "demo factory" argues for keeping it); splitting it into its own artifact is then a build change, not a code change. Until then this ADR is the record: they ship, in every build.

**Consequences.** Release size includes the fakes until that split. Konsist keeps `Secret.reveal()` out of everything except infrastructure and `shared/testing`, so shipping the fakes does not widen where a secret can be read. `FakePairingBrowser` records the approval URLs it is asked to open (the pairing contract needs to read them back), so the shipped fake holds whatever link the demo graph gives it; the demo set contains only test-only values, and the real graph wires the real browser bridge in Phase 1b.

## ADR-036: A test claims only what it exercises

**Status:** Accepted (2026-11-01, review fixes F-C-4, F-C-5 and F-D-5)

**Context.** Several checks counted as evidence without being able to fail: platform contract suites that read back what they had just scripted, and a traceability rule satisfied by an `FR-` id in a comment.

**Decision.** (1) A contract suite must assert an obligation a real adapter could break; suites that cannot (Connectivity, AppLifecycle: a `StateFlow` already guarantees them) are deleted and listed as exempt with the reason in `PortAndSecretRulesTest`. (2) A requirement is "named by a test" only when its id is in the backtick name of a `@Test` function in a `commonTest` source set; a requirement whose Phase 1a test covers only part of it is also listed in the `partial` map of `RequirementsTraceabilityTest` with what is left. (3) A guard (Konsist rule, CI check) needs a negative case or a mutation that makes it fail, recorded in the commit that adds it.

**Consequences.** Fewer contract classes, each real. Acceptance criterion 4 reads "fake-only; real adapters in Phase 1b".

## ADR-037: `createGraph` is backed by fakes until Phase 1b, and the seam is the port bindings

**Status:** Accepted (2026-11-01, review fix F-B-10)

**Context.** `PaperclipCore.createGraph(allowInsecureLocal)` is the graph the iPhone app starts with, yet every port in it is a fake: an in-memory `SecretStore`, a `Biometrics` that always approves, a `Clock` frozen at 2026-11-01T12:00Z, gateways that answer from memory. The demo graph freezes its clock too (Wednesday 2026-11-04T12:00Z). Neither can exercise the idle lock, the five-second undo window, key expiry, "Decide now" grouping or snooze presets, and the biometric failure paths cannot be triggered from either graph.

**Decision.** This is accepted for Phase 1a, which has no adapters, and is stated where a reader looks: the KDoc of `createGraph`, `docs/technical-details.md` ("The graph") and this ADR. The seam is the Koin module that binds the ports (`portsModule()` for `createGraph`, `demoPortsModule()` for the demo): Phase 1b replaces the bindings there with real adapters, and platform-owned ports (secure store, biometrics, connectivity, pairing browser, time zone, lifecycle) arrive from the app. The shape of that hand-over is decided in Phase 1b; `createGraph` is not renamed now because the Swift app calls it and the name disappears with the fakes.

**Consequences.** A build that reaches `createGraph` with the fakes still bound must not be released: Phase 1b's first acceptance criterion is a test that the graph binds no `FakeBiometrics` and no `FakeSecretStore`. Time-dependent rules are tested at the state-holder and use-case level with an advanceable `FakeClock` (the app lock re-engaging after the idle period is covered through the graph in `DemoGraphDeviceTest`), not by waiting in either graph.

## ADR-038: HTTP statuses outside the PRD list map to `Rejected`, `Timeout` or `Malformed`, and the status always wins

**Status:** Accepted (2026-11-02, Phase 1b-1, `specs/261102-phase-1b-adapters`)

**Context.** The server answers 400 for validation failures, malformed ids and a `revoke-current` without a board key (`access.ts:2815, 2852, 2959, 2972`; `error-handler.ts:225-243`), and a reverse proxy can answer anything, including a 200 or 502 HTML page. `DomainError` has no bad-request case and the PRD lists only 401, 403, 404, 409, 422, 429 and 5xx.
**Decision.** One `ErrorMapper` decides. The status wins over the body: 401 `Unauthorized`, 403 `Forbidden`, 404 `NotFound`, 409 `Conflict`, 429 `RateLimited`, 408 `Timeout`, any other 4xx (including 400 and 422) `Rejected(reason)` with the server's `{"error"}` text cut to 200 characters (`http_<status>` when there is none), every 5xx `ServerError` (so a 502 HTML page is a server error, not Malformed). A 2xx whose body is not JSON, is empty or does not fit the DTO is `Malformed`. 1xx, 3xx and statuses above 599 are `Malformed`. A key id that is not a UUID is `NotFound` without a request, because the server would answer 400 for an id that cannot exist. An unknown challenge `status` is `Malformed`: the app does not guess.
**Consequences.** The UI shows `Rejected` reasons as the server wrote them and nothing else from a body. The mapping has one test per status and body shape (`ErrorMapperTest`).

## ADR-039: The HTTP client sends once, never caches a key, follows no redirect and never copies an exception message

**Status:** Accepted (2026-11-02, Phase 1b-1)

**Context.** Ktor's `Auth` plugin caches tokens and re-sends after a 401; `HttpRequestRetry` would replay mutations; redirects carry the `Authorization` header to the new location; Ktor exception messages contain the request URL and a pairing poll carries its challenge token in the query (`access.ts:2812`). `retryingRead` is `internal` to `application`, which infrastructure may not import.
**Decision.** `PaperclipHttpClient` installs only `HttpTimeout` (15 s connect, 30 s read, 60 s request), sets `expectSuccess = false` and `followRedirects = false`, and sets `Authorization` per request from a `Secret` the gateway reads from the `SecretStore` for that call and wipes afterwards. It sends each request exactly once: reads are retried by the use cases through `retryingRead` (they already are), mutations never. Failures become a `DomainError` from the exception type only, never its message; `CancellationException` is rethrown while the caller is active. There is no Ktor `Logging` plugin; an optional trace hook receives method, host, path and status. `http` is allowed only for `localhost` and `10.0.2.2` when `allowInsecureLocal` is set, enforced in the client as well as in `BaseUri`, and a refusal is `CertificateInvalid` with no request sent. The one `catch (Throwable)` of the codebase lives in this class and is allowed by name in `detekt.yml`.
**Consequences.** "A board key never appears in a URL" is tested; "no secret in any URL" cannot be literally true because the challenge token is a query parameter by the server's design, so the test asserts it reaches only the issuing host and only in the poll query.

## ADR-040: Certificate pins are stored and fail closed until an engine enforces them

**Status:** Accepted (2026-11-02, Phase 1b-1). Enforcement deferred (spec stretch goal).

**Context.** Ktor's Darwin engine has a `CertificatePinner` (SPKI sha256 per host pattern, installed through `handleChallenge`, `ktor-client-darwin-3.6.1` sources) and OkHttp has `CertificatePinner`, but both are configured per engine for a list of hosts, while pins belong to instances that can be added and edited while the app runs. Wiring them needs the live graph (1b-5) and a TLS test server on the simulator.
**Decision.** `PaperclipHttpClient` takes `pinsEnforced` (false for now). A request to an instance whose `pins` list is not empty fails with `CertificateInvalid` and sends nothing while no engine enforces pins, so a stored pin can never silently be ignored. Turning enforcement on is one constructor argument plus the engine configuration.
**Consequences.** No UI sets pins yet, so no user is affected. Real enforcement and its simulator test are listed as deferred.

## ADR-041: Server fixtures are Kotlin strings, not resource files

**Status:** Accepted (2026-11-02, Phase 1b-1)

**Context.** The spec asks for fixtures under `src/commonTest/resources`. A Kotlin/Native test has no classpath, so a resource file cannot be read by `iosSimulatorArm64Test`, and the adapter tests must run on JVM, Android host and iOS simulator.
**Decision.** `ServerFixtures` builds the JSON bodies of the pinned server's routes as Kotlin raw strings, each commented with the server file and line it follows; `StubPaperclipServer` serves them. The CI contract test (1b-5) checks the same shapes against the real container.
**Consequences.** Fixtures run on every target. They are derived from server source, not recorded, until the contract test exists.

## ADR-042: An unusable database is recreated empty; secrets are never in it

**Status:** Accepted (2026-11-02, Phase 1b-2, `specs/261102-phase-1b-adapters`)

**Context.** SQLite opens a garbage file and fails on the first statement; a file may also come from a newer app version after a downgrade, or lack a table. The database holds only non-secret factory configuration, settings and an Inbox cache. The credentials are in the Keychain or Keystore under `instance/{id}/`.
**Decision.** After opening, the app probes the file (`user_version` not newer than the code; the three tables exist). If opening or the probe fails, the database and its `-wal`/`-shm`/`-journal` files are deleted and a new empty one is created (`PaperclipPersistence.recreated` is true so the composition root can tell the person). Only a failure of that second open is fatal. No secret column or secret-typed parameter exists: a test pins the exact column list and scans every stored value for `pcp_board_`, and a Konsist rule forbids importing `Secret` or `SecretKey` in the module.
**Consequences.** The person loses the factory list, not access control. Because `SecretStore` cannot list keys, recreating orphans the items under `instance/{id}/` until the same ids are reused (they are not: new ids are generated) or the app is reinstalled; they stay protected by the Keychain/Keystore and are never read. Failing closed was rejected: it would leave the app unusable with reinstalling as the only way out.

## ADR-043: Persistence layout: payload mirror classes, one serial executor, orphan pruning

**Status:** Accepted (2026-11-02, Phase 1b-2)

**Context.** The domain has no serialization annotations (AGENTS.md). The in-memory JDBC driver shares one connection and cannot run parallel transactions. A cache write can arrive after its factory was removed, and the cache contract requires writes for scopes with no factory row.
**Decision.** (1) The cached Inbox page is one JSON row written through `internal` payload classes in `infrastructure.persistence.payload`, with a `format` column (added by migration 1.sqm) so an unreadable or newer layout is a cache miss that deletes the row. At most 200 items are kept; when a longer page is cut `nextCursor` is cleared, because the server cursor would skip the dropped items. (2) `PaperclipPersistence` runs the three ports on one `Dispatchers.Default.limitedParallelism(1)`, so statements and transactions of one database are ordered whatever the driver does. Deleting a factory removes its configuration and cache rows in one transaction (no reliance on foreign keys, which are off by default). (3) Opening deletes cache rows whose factory no longer exists. (4) The iOS file lives in `Application Support/PaperclipData`, excluded from backup (folder and files, reapplied on every open); Android uses `noBackupFilesDir`. (5) Generated SQLDelight classes are public by the plugin; the module hides them by making every adapter constructor `internal` and by a Konsist rule that no file outside the module names the generated package.
**Consequences.** Reads are not parallel (fine at this size). A domain change to `AttentionItem` needs a payload change and, if the layout changes, a `format` bump.


## ADR-050: Keychain tests run in the iOS app test host, and the iOS tests are signed ad hoc

**Status:** Accepted (2026-11-02, Phase 1b-3, `specs/261102-phase-1b-adapters`)

**Context.** The Keychain needs an app's entitlements. A Kotlin/Native `iosSimulatorArm64Test` binary is a bare executable: run standalone it gets `errSecNotAvailable` (-25291, no `securityd`), run on a booted simulator it gets `errSecMissingEntitlement` (-34018); signing it with a `keychain-access-groups` entitlement makes `simctl spawn` refuse it ("Security policy issue"). The app test host also gets -34018 when it is built with `CODE_SIGNING_ALLOWED=NO` (what `scripts/ios-test.sh` used), and succeeds when built ad-hoc signed.
**Decision.** The store's decisions (which protection, replace on duplicate, status to result mapping, marker, wiping) run in Kotlin/Native tests against a scripted `KeychainBackend`, together with the shared `SecretStoreContract`. The real Keychain runs in Swift Testing tests in the app test host (`iosApp/Tests/iosAppTests`), which call the store through `PlatformServices` in the umbrella, run the same contract on the real Keychain and check the stored attributes with `SecItemCopyMatching`. `scripts/ios-test.sh` builds with `CODE_SIGN_IDENTITY=- CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=YES`. Each test uses its own Keychain service and cleans up.
**Consequences.** The real-Keychain tests run only through `ios-test.sh` / the iOS workflow, not through `./gradlew allTests`. The `PlatformServices` factory and the contract runner ship in the framework (the umbrella already ships the fakes for demo mode). Whether ad-hoc signing works on the CI macOS runner is confirmed only by the PR run.

## ADR-051: Keychain item layout, protection and the port's four read results

**Status:** Accepted (2026-11-02, Phase 1b-3)

**Context.** `SecretReadResult` has Found, Missing, Invalidated and Denied; `SecretWriteResult` has Stored and Failed. `CheckCredentials` turns Missing into "pair again" and Invalidated into `NeedsRePairing`, so a transient error must never be reported as either. A `.biometryCurrentSet` item disappears when the enrolled biometrics change, which looks like "not found".
**Decision.** One generic-password item per secret: service = a fixed id, account = `instance/{id}/{name}`, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, and `kSecAttrSynchronizable = false` on every query (add, read, delete, list). With `requireUserPresence` the item is stored with `.biometryCurrentSet`, falling back to `.userPresence` when that is refused (no biometrics enrolled); if neither works the write is `Failed` and nothing is stored: never an unprotected copy. Accessibility is then part of the access control (the two attributes are never both set). A protected write also stores an unprotected marker item `{account}#protected` (one byte, no secret); a read that finds nothing but finds the marker is `Invalidated`, otherwise `Missing`. Mapping: success -> Found; `errSecItemNotFound` -> Missing or Invalidated as above; everything else on a read (device locked `errSecInteractionNotAllowed`, cancelled `errSecUserCanceled`, `errSecAuthFailed`, any storage error, or a marker check that fails) -> Denied; any failed write or a duplicate that cannot be replaced -> Failed. A duplicate is replaced by delete then add so a changed protection takes effect. A delete the Keychain refuses throws (like the fake), never silently. `deleteNamespace` lists accounts with an attributes-only query (no value read, no prompt) and deletes those starting with `instance/{id}/`, so `instance/a/` never touches `instance/ab/`. Blocking calls run on `Dispatchers.Default`.
**Consequences.** The port is not extended (domain is outside this stream). "Locked" and "storage failure" on read share Denied with "declined". How an item looks after a real biometric-set change cannot be produced on a simulator: the marker logic is tested by removing the item directly. `Secret.reveal()` makes a `String` that cannot be wiped; the byte and `NSMutableData` copies made by the store are zeroed, the framework-owned `NSData` returned by a read is not.

## ADR-052: Biometrics and connectivity adapters

**Status:** Accepted (2026-11-02, Phase 1b-3)

**Context.** The simulator can evaluate the device-owner policy (it pretends to have a passcode), so a live prompt waits for a person; a bare Kotlin/Native test binary has no LocalAuthentication daemon and gets an XPC error (code 4099, not an `LAError`).
**Decision.** `LocalAuthenticationBiometrics` uses `LAPolicyDeviceOwnerAuthentication` (Face ID or Touch ID with passcode fallback), a fresh `LAContext` per prompt, `canEvaluatePolicy` first. Mapping: success -> Approved; user/system/app cancel and user fallback -> Cancelled; authentication failed and every unknown code -> Failed; biometry lockout -> LockedOut; passcode not set, biometry not enrolled or not available, not interactive, and any error outside `LAErrorDomain` -> Unavailable. No passcode therefore means no approval (fail closed). The prompt text comes from the app (`ReasonTexts`, localized by the app; the default is English). Cancelling the coroutine invalidates the context. `NwConnectivity` wraps `nw_path_monitor` on its own serial dispatch queue; it is online until the first path arrives (optimistic, like the JVM), then follows `nw_path_status_satisfied`, and `close()` cancels the monitor. The AppLifecycle needs no new adapter: the umbrella's `AppLifecycleBridge` is driven by the app. Android and the JVM use `UnavailableBiometrics` and `OptimisticConnectivity` until their app phase.
**Consequences.** Real Face ID, lockout and an enrolled-biometrics prompt are not verifiable on a simulator or in CI. `NSFaceIDUsageDescription` must be in the app's Info.plist (iosApp, deferred to the UI stream) or Face ID is refused on a device.

## ADR-053: System services and the fail-closed Android and JVM actuals

**Status:** Accepted (2026-11-02, Phase 1b-3)

**Decision.** `SystemClock` (`kotlin.time.Clock.System`), `UuidIdGenerator` (`kotlin.uuid.Uuid.random()`, lower-case v4) and `SystemDeviceTimeZone` (`TimeZone.currentSystemDefault()`, which on Darwin resets and re-reads `NSTimeZone.systemTimeZone` on every call, so a zone change is seen) are plain common classes, proven by the shared `ClockContract`, `IdGeneratorContract` and `DeviceTimeZoneContract` on JVM, Android host and iOS. `UnavailableSecretStore` (writes `Failed`, reads `Missing`, holds nothing) and `UnavailableBiometrics` (always `Unavailable`) are common classes used on Android and the JVM, where no Keystore or BiometricPrompt bridge exists yet; a test pins that nothing is retained, not even by a second instance. They deliberately do not satisfy `SecretStoreContract`. They are plain classes rather than `expect`/`actual` pairs because there is nothing platform-specific in them.
**Consequences.** An Android build of the app cannot store a board key until the Keystore adapter exists (a later phase), which is the safe default.

## ADR-054: Server money is read as an exact decimal literal and rounded up to whole cents

**Status:** Accepted (2026-11-02, Phase 1b-4a)

**Context.** Budget amounts are cents, but `amountObserved` is a `numeric(24,7)` column read as a JS number (`packages/db/src/schema/budget_incidents.ts:21`), so the JSON can be `9999.0000001`. Approval payloads and feed metadata carry the same numbers. A `Double` cannot hold a cent-exact comparison.
**Decision.** DTOs keep amounts as `JsonPrimitive` and `ceilToCents(literal)` parses the text: digits, an optional `.digits` fraction, at most 15 whole digits; a sign, an exponent, a second dot or no digits is null. Any non-zero fraction rounds up (ADR-016), so the app never shows less spend than the server holds. The currency is always `USD`: no server type has a currency field. A raise is refused locally (`Rejected`, nothing sent) for another currency or for more than 2,147,483,647 cents, the schema's maximum (`validators/budget.ts:24`).
**Consequences.** The domain's "must exceed observed" pre-check is conservative by up to one cent; the server stays the authority. A literal in exponent form (JS prints numbers of 1e21 and above that way) is Malformed or an absent payload field, never a wrong amount.

## ADR-055: Open-ended parts of a reply are read by hand and never fail the whole reply

**Status:** Accepted (2026-11-02, Phase 1b-4a)

**Context.** Detail kinds, effect types, approval payloads and subject metadata are polymorphic, redacted by the server, and extended by newer servers. A kotlinx sealed discriminator throws on an unknown type; a typed payload DTO fails on one wrongly typed field.
**Decision.** Those parts are `JsonObject`s read by the small helpers in `dto/JsonReading.kt` (`text`, `cents`, `whole`, `texts`, `obj`, ...): a field of the wrong type is null. Known types are always mapped even with fields missing (a `cancel_issue_tree` can never degrade to an unknown effect and escape the destructive check); unknown types become `Unsupported`; unknown enum values use the domain's `fromServer` (Medium, Unknown, ...). A response that is not JSON, is empty, is not the expected top-level shape, or lacks the identity of the thing asked for is `Malformed`.
**Consequences.** A redacted hire payload shows fewer fields instead of an error. The cost is that a server renaming a field silently shows less; the contract test in CI against the pinned container (1b-5) is where that is caught.

## ADR-056: A budget incident is resolved only while it is still active

**Status:** Accepted (2026-11-02, Phase 1b-4a)

**Context.** `resolveIncident` (`services/budgets.ts:951-1052`) looks the incident up by id with no status filter: `keep_paused` updates it again and a raise re-runs against the policy, so resolving twice answers 200. The port and the shared contract say a second resolve is `NotFound`, because the incident is "no longer active". There is no single-incident endpoint.
**Decision.** `resolve` reads `budgets/overview` first and returns `NotFound`, with no mutation sent, when the id is not in `activeIncidents`; only then does it post once. `incident` is the same read. The stub deliberately models the permissive server so the test proves the guard.
**Consequences.** One extra read per resolve, and a race window of one round trip in which another operator can resolve it first (the server then answers 200 again, which is harmless: keep paused is idempotent and a raise to the same amount is rejected as not exceeding spend).

## ADR-057: Id-addressed approvals and decisions are checked against the scope's company

**Status:** Accepted (2026-11-02, Phase 1b-4a)

**Context.** `/api/approvals/{id}` and `/api/decisions/{id}` carry no company in the path. A key with access to two companies could be handed company B's id under company A's scope, and the server would answer it. A company with no membership answers 404 there (`getAccessibleResource`, `authz.ts:182-195`) but 403 on company paths (`assertCompanyAccess`). Server ids are UUID columns, so a non-UUID id is a database error on the server, not a 404.
**Decision.** Every read checks the `companyId` of the row it got: another company is `NotFound`, a missing `companyId` is `Malformed` (tenancy is not guessed). Every write reads the resource first, checks it, and only then posts, once. An id that is not a UUID is `NotFound` without a request (like board key ids in 1b-1, see implementation-notes). Lists (comments, linked issues) are `NotFound` when any row names another company.
**Consequences.** A write costs a read. An empty list cannot prove ownership; the id is checked by the preceding use-case read.

## ADR-058: Port gaps are recorded, not patched in the adapter

**Status:** Accepted (2026-11-02, Phase 1b-4a)

**Decision.** Four things the port or the wire cannot express are left as they are and listed as deviations: (1) `AttentionGateway` has no list-dismissals method although `GET inbox-dismissals` exists, so it is not called; (2) there is no poll method: polling is the use case calling `DecisionGateway.get` again; (3) `get` returns `originAgentName = null` and `decideBy = None`, because the decision row has neither and filling them would cost a second and third request; the use case takes them from the feed card; (4) an `assign_issue` effect names the assignee by id only, so `assigneeName` is null until a later phase resolves names from the agent list.
**Consequences.** None of these changes a domain port, which this stream does not own.

## ADR-059: One unreadable feed card fails the page

**Status:** Accepted (2026-11-02, Phase 1b-4a)

**Decision.** A card without an id, source kind, dismissal key, subject id or readable `activityAt`/`createdAt` makes the whole page `Malformed`. Skipping it would hide something that needs a decision and make the page disagree with its own `totalCount`; the Inbox shows its error state and the cached first page. Missing optional parts (verbs, detail, dismissal, resolver audience) never fail a card. `countsBySourceKind` keeps unknown kinds as `Unsupported` keys. The cursor is never inspected or validated: it is passed back as received and the server's rejection arrives as `Rejected` with its own reason.
**Consequences.** A server that adds a required-looking field breaks nothing; a server that drops an identity field breaks the Inbox loudly instead of quietly.

## ADR-060: The pinned release's compiled dist is the source of truth, and the stub follows it

**Status:** Accepted (2026-11-02, Phase 1b-4b)

**Decision.** Server facts for the adapters are read from the pinned release 2026.1023.0 as installed (`@paperclipai/{server,shared,db}/dist`), not from a newer development working tree. Before the 1b-4b gateways were built, every 1b-4a assumption was re-read there; the differences were fixed test first (research, "1b-4a corrections vs pinned release"). Each fixture and stub header names the dist file it follows. The stub server follows the server's permissiveness and quirks, not the port's strictness, so a test can only pass if the adapter itself compensates (a cancel of an ended run answers 200 with the run unchanged; a log read omits `nextOffset` on its last chunk; comments come newest first; a create may be answered `deduplicated`).
**Consequences.** The one production defect found was an exponent number literal (`5e-7`) that `ceilToCents` refused; the rest were stub differences. Facts that cannot be read from source (the status after `verdicts`, the reply of a pause) are listed as not verified and wait for the container run in CI (1b-5).

## ADR-061: Every id-addressed call proves the company first; lists prove it row by row

**Status:** Accepted (2026-11-02, Phase 1b-4b); extends ADR-057

**Decision.** Issues, agents, runs, attachments and interaction answers are addressed by id with no company in the path, and a board key that belongs to several companies can read all of them. So each call is made under the scope's company like this: an id that is not a uuid is NotFound without a request; a single-resource read checks the `companyId` of the reply; every mutation is preceded by a read of the resource (a board key must not be able to pause, comment on or cancel the work of a company the person did not select); a reply to a mutation that names another company is not trusted. A list whose rows each name their company (comments, attachments, interactions) is checked row by row, and an empty list, which proves nothing, asks the issue once (`provingOwnership`). Lists of rows that do not name a company (`issues/{id}/runs`) are preceded by the issue read. The audit test calls every method of the five gateways against a Globex resource under an Acme scope and asserts NotFound, no write and no body read.
**Consequences.** A mutation costs one more read; a thread that really is empty costs one more read per poll. Both are cheap next to a wrong-company write.

## ADR-062: Runs: the guard is ours, the log offset is counted in bytes

**Status:** Accepted (2026-11-02, Phase 1b-4b)

**Decision.** Cancelling a run that has already ended is answered 200 with the run unchanged by the pinned server, so `cancel` reads the run first and returns Conflict for one that is not active without sending a mutation; a reply that comes back neither active nor cancelled is also Conflict, a `null` body is NotFound. The log is read in chunks of 64 KiB by byte offset. The server sends `nextOffset` only when more bytes follow, so on the last, short chunk the adapter computes the next offset as the start plus the UTF-8 byte length of the content (not its character count), and an empty read has none; a 404 on the log of a run that was just read means "no log written yet" and is an empty chunk. Events are read in pages of 500 after the last sequence number and returned oldest first without repeats.
**Consequences.** A chunk boundary can fall inside a multi-byte character; the server decodes both halves with replacement characters. This is accepted: the tail is for reading progress, and the replacement shows at most once per 64 KiB.

## ADR-063: Issue requests are written for the pinned server's rules

**Status:** Accepted (2026-11-02, Phase 1b-4b)

**Decision.** The list uses `view=compact` with the saved filter's parameters and never claims a description (the server sends a 1200-character preview). Comments are asked `order=asc` (the default is newest first) and deleted comments, which come back empty, are left out. Create always sends `allowDuplicate: true`, because the default silently returns an existing open issue with the same title and parent created in the last 48 hours; a `deduplicated` reply anyway is a Conflict. Assignee changes send both assignee fields every time (the other one as an explicit null) because the server merges with the stored values and refuses two assignees. A status the app does not know and an agent id that is not a uuid are refused before any request. The server's own 422 texts (`blocked` needs a blocker or a pending card, `in_progress` needs an assignee) arrive as the reason. Detail is two reads: the issue, then its subtasks by `parentId`.
**Consequences.** Two quick taps on Create make two issues; the presentation layer disables the action while it is pending.

## ADR-064: Attachment content is an adapter-only, ranged call

**Status:** Accepted (2026-11-02, Phase 1b-4b)

**Decision.** `IssueGateway` has no method for file content, and adding one is a domain change outside this stream. The adapter therefore has a public, non-port method `IssueGatewayAdapter.attachmentContent(scope, issueId, attachmentId, start, endInclusive)` that sends `Range: bytes=a-b` through a new byte path of the shared client (`sendBytes`, which never decodes the body as text). The attachment must be in the issue's own list, which proves its company before any byte is asked for. 206 is read from `Content-Range`; a 200 (the server ignored the range) is the whole file from byte zero; 416 is Rejected with a reason. Its result type is nested in the adapter so that the Konsist rule about the public surface of `api` still holds.
**Consequences.** Nothing above infrastructure can reach it until a port is added (recorded as deferred); the byte path is tested at the client (non-UTF-8 bytes survive, no key or query in the trace) and at the adapter.

## ADR-065: Interactions: five kinds are answered, everything else is shown and never sent

**Status:** Accepted (2026-11-02, Phase 1b-4b)

**Decision.** `suggest_tasks`, `ask_user_questions`, `request_confirmation`, `request_checkbox_confirmation` and `request_item_verdicts` are mapped to their domain types. `connection_intent`, a kind from a newer server, a confirmation that carries `toolAction`, `secretProposal` or `connectionAuthorization` (it gates a tool call, a secret or an external login that a phone prompt cannot describe), and a supported kind whose payload cannot be read all become `UnsupportedInteraction` with their real kind; one bad payload never hides the rest of the thread. An answer first reads the thread: an unknown interaction is NotFound, an unsupported one is refused, an answer that does not fit the kind (a plain accept for tasks would accept every task) or an empty task selection is refused, and one for an interaction that is no longer pending is a Conflict, each without a mutation. The answer then goes once to its endpoint (`accept`, `reject`, `respond`, `verdicts`, `skip`). `required` is true only when the server says so, `allowOther` false only when it says so. The resolver audience is read with the attention mapper's `resolverAudienceOf`.
**Consequences.** The server stays the authority on everything the app cannot know (the creator rule, the addressee, a closed issue): its 403 and 409 arrive as `Forbidden` and `Conflict`.

## ADR-066: Dashboard and agents: tiles and money are all or nothing

**Status:** Accepted (2026-11-02, Phase 1b-4b)

**Decision.** A dashboard with a missing, negative or non-integer count, or money that is not a number, is Malformed, not a tile of zeros. Window spend prefers `costCentsExact` (text) over the double next to it, rounds each row up to whole cents, and keeps rows of windows the app does not know for the domain to ignore. The agent list sends no query (the server answers 400 to any), maps an unknown status to `Unknown`, and fails as a whole if one row is unreadable (ADR-059). A wake answers 202 with either the started run or `{status:"skipped"}`; anything else is Malformed. A budget over 2,147,483,647 cents or in another currency is refused before any request, as for incidents. The reply of a pause or resume is mapped as sent; whether its month spend is the hydrated figure is not verified.
**Consequences.** After a pause the Agents tab should reload the list for the month figure.

## ADR-067: The live graph is built on an internal `LivePlatform`, and the stub server is shared by copying its sources

**Status:** Accepted (2026-11-02, Phase 1b-5a)

**Decision.** `PaperclipCore.createLiveGraph(allowInsecureLocal, pairingBrowser)` builds the graph from an internal `LivePlatform` (clock, ids, time zone, `SecretStore`, `Biometrics`, `Connectivity`, `PaperclipPersistence`, the HTTP client factory and `release()`). The public overload gets its platform from `defaultLivePlatform()` (`expect`/`actual`); an internal overload takes one explicitly, which is the test seam and what `LiveIosGraph` uses. The interface is internal because it carries database and Ktor types that must not appear in the Swift header. The live-graph tests need the pinned-server stand-in that lives in the API module's test source set: the `sharedStubSources` task in `shared/umbrella/build.gradle.kts` copies only `Stub*.kt` and `*Fixtures.kt` into the umbrella's JVM test compilation. The stub no longer calls the adapter's `isServerId` (it has `isStubUuid`) and offers `newEngine()` because a client now closes the engine it is given.
**Consequences.** The stub is compiled twice and the live flows are JVM tests (`jvmTest`); on Android host and iOS they are not repeated. Moving the stub to a published test-fixtures module would need its internal types made public and is not worth it yet. Rejected: a public platform interface (leaks types to Swift); copying the whole API test source set (the adapter suites would run again inside the umbrella).

## ADR-068: No fake in the live graph, polling instead of live events, and one close that runs once

**Status:** Accepted (2026-11-02, Phase 1b-5a)

**Decision.** The live ports module binds only adapters and platform pieces. `LiveEvents` is a "polling only" object with an empty flow, because the pinned server rejects board keys on the WebSocket; the refresh policy then works from its timer and from coming back online or to the foreground (`LiveRefreshTest`). `signalPorts()` (which bound `FakeLiveEvents`) was split: `lifecyclePorts()` is shared, the fake event binding stays with the fake graphs. Two checks keep fakes out: a Konsist rule that no `Live*.kt` file imports `shared.testing` or calls `portsModule`, `demoPortsModule`, `sliceThreePorts` or `signalPorts` (with a self-test that the files are selected and the predicate flags a synthetic import), and `LiveGraphTest`, which resolves ports from a live graph and checks none belongs to the testing package. `PaperclipGraph.close()` is idempotent and releases the HTTP client (now also its engine), the database, then the platform, each once, even if one step throws. An empty database is the first-run state; nothing is seeded (AD-041).
**Consequences.** A file named `PlatformServices.kt` (the iOS Keychain contract hook, which imports the testing module on purpose) is deliberately outside the rule. Holders must be closed before the graph.

## ADR-069: Platform actuals: iOS real, JVM fails closed, Android refused

**Status:** Accepted (2026-11-02, Phase 1b-5a)

**Decision.** iOS builds `KeychainSecretStore`, `LocalAuthenticationBiometrics`, `NwConnectivity`, the system clock, UUID ids, the device time zone and `openIosPersistence` (default folder `Application Support/PaperclipData`, excluded from backups). Its first argument set comes from `IosLiveSettings` (Keychain service, data folder, localized prompt texts) through `LiveIosGraph.createLiveGraph`; the two-argument public call uses the app's service, the default folder and English texts. The JVM default is `UnavailableSecretStore`, `UnavailableBiometrics`, `OptimisticConnectivity` and an in-memory database: pairing fails (the key cannot be stored) and no destructive action is approved. Android has no `Context` and no Keystore yet, so `createLiveGraph` throws `IllegalStateException` there instead of running on a pretend secret store.
**Consequences.** An Android build that calls it crashes at start-up, which is the right signal until the Android app phase supplies its platform. The device-service decisions were already tested in 1b-3; the new tests prove only the wiring.

## ADR-070: The pairing browser is supplied as a callback

**Status:** Accepted (2026-11-02, Phase 1b-5a)

**Decision.** `PairingBrowser` is a Kotlin suspend interface and `Secret.reveal()` is hidden from Swift, so a Swift class could neither read the approval address nor implement the method without the `__open` name SKIE generates (which fails SwiftLint and swift-format). `CallbackPairingBrowser` (in `infrastructure/api`, the only place that may reveal the address) turns a closure `(approvalUrl: String, finished: () -> Unit)` into the port; `PairingBrowsers.fromCallback` exposes it through the umbrella. The `finished` call ends the wait, cancelling the pairing cancels it, and a second call is harmless. The address is a secret, so the app opens it and does not log or keep it.
**Consequences.** `CallbackPairingBrowser` is on the API module's public-surface allow-list. The real `ASWebAuthenticationSession` implementation belongs to the iPhone UI stream.

