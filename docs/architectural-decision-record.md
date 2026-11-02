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

## ADR-042: Swift mirrors a state holder through one generic `HolderModel`, owned by an explicit owner

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-001/UI-002)

**Context.** Every screen needs an `@Observable` adapter over a Kotlin holder's `StateFlow`. Writing one per screen invites stale reads (an intent returns before the flow emits) and holders closed from a view's `.task` ending.

**Decision.** `StateHolderProtocol` (conformed to by all 16 holders in one line each) and `HolderModel<Holder>`: `send` applies `holder.state.value` straight after the intent; `observe()` catches up then follows the `SkieSwiftStateFlow` until its task is cancelled, and can restart; cancelling never closes the holder; `close()` is idempotent and called only by an owner (`AppEnvironment` for the shell models, `ModelStore` keyed by route for screens). Everything is `@MainActor`.

**Consequences.** A screen model is `HolderModel<XStateHolder>` plus an extension of typed intent helpers. Tests cover mirroring, no stale read, cancel and restart, and exactly-once close.

## ADR-043: A launch without `-demo` starts at first run on the live seam; the demo graph is opt-in, with scripted Face ID through `DemoControls`

**Status:** Accepted (2026-11-02, UI-001/UI-004)

**Context.** The brief suggested demo by default, but acceptance criterion 8 and the existing first-run UI test require a launch without arguments to show the empty first-run flow. UI tests also need to script Face ID results and count prompts without a device.

**Decision.** `AppGraphProvider` has `DemoGraphProvider` (`-demo` or "Try the demo factory") and `LiveGraphProvider`, a placeholder marked `isAvailable == false` that returns today's fake-backed `createGraph` (ADR-037) until Phase 1b. `PaperclipGraph.demoControls` (demo graph only, null otherwise) scripts `BiometricResult`s and counts prompts; the app reads launch arguments `-demo-biometrics`, `-demo-idle`, `-demo-diagnostics`.

**Consequences.** Real Face ID (LocalAuthentication) arrives with Phase 1b's adapter; until then the graph's `Biometrics` is the fake.

## ADR-044: Destructive Face ID gating stays inside the shared use case; the Swift sheet only forwards and reports

**Status:** Accepted (2026-11-02, UI-004)

**Context.** AGENTS.md puts the `Biometrics` port inside the use case. A Swift sheet that also prompted would double-prompt and could be skipped by another view.

**Decision.** `ConfirmDestructiveSheet` shows the lock glyph, calls `onConfirm` (the screen's confirm intent) and displays the holder's `DeviceAuthIssue`. A Swift test proves that a cancelled scripted prompt leaves the agent unpaused.

**Consequences.** One gate for every platform. Catalog keys are `<namespace>.<caseName>` for each exported key enum, verified by a completeness test (ADR for localisation scheme: this one).

## ADR-045: A Swift shell shows the pairing page through `ApprovalPageOpener`, a callback port, because Swift cannot read a `Secret`

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-010)

**Context.** `PairingBrowser.open(approvalUrl: Secret)` needs the link, but `Secret.reveal` is hidden from Objective-C on purpose and a Konsist rule limits it to infrastructure and fakes. A Swift conformance to a Kotlin `suspend` interface also needs a `__open` name that SwiftLint and swift-format reject, and an error thrown from Swift into the holder's coroutine would end the app.

**Decision.** `shared/infrastructure/platform` offers `ApprovalPageOpener.open(url: String, onShown: () -> Unit)` (implemented by the shell, never throws) and `OpenerPairingBrowser`, the only place the link leaves its `Secret`. The Swift `ASWebAuthSessionPairingBrowser` opens only `https` links (plain `http` to a local host in debug builds), never logs or keeps the link, and calls back once the page is on screen. Closing the sheet is not a failure: it is how a person finishes approving, and the shared use case finds out by polling.

**Consequences.** Phase 1b passes `OpenerPairingBrowser(opener)` to `createLiveGraph`; only the factory call changes. A sheet that could not be shown cancels the pairing on screen. Real `ASWebAuthenticationSession` behaviour is not exercised on CI.

## ADR-046: Pairing completes on fakes through `approvePairings`, a first-run demo graph, and the graph clock

**Status:** Accepted (2026-11-02, UI-010)

**Context.** On the demo graph a pairing could never finish: the challenge expired at a 2026-11-01 instant while the demo clock was 2026-11-04, polls answered Pending forever, and a used challenge is wiped so it cannot be reused. Updated labels were measured against the wall clock.

**Decision.** `FakeAuthGateway.approvePairings(from)` hands out a fresh challenge per call that expires relative to the graph clock and approves every poll. `PaperclipCore.createFirstRunDemoGraph()` starts with no factory. `PaperclipGraph.nowEpochMillis()` lets the app measure "Updated" against the graph clock. `-demo-first-run` selects that graph in the app.

**Consequences.** First-run, pairing and re-pair flows run in demo mode and UI tests with no server. The live graph uses the wall clock.

## ADR-047: Sign out, revoke and sign out everywhere ask for confirmation but not Face ID

**Status:** Accepted (2026-11-02, UI-010)

**Context.** The Settings mock shows a lock glyph on Sign out everywhere. FR-014 lists the actions that always need the device owner (pause, cancel, destructive decisions, reject, budget raise), and AGENTS.md adds removing a factory; the shared use cases gate exactly those.

**Decision.** The Swift screens ask for a plain confirmation (`ConfirmSheet`, no lock glyph) for sign out, revoke and sign out everywhere, and the lock glyph appears only where the use case prompts (`ConfirmDestructiveSheet`, remove factory).

**Consequences.** Nothing claims a prompt that does not happen. If the owner wants these gated, that is a shared change (use case plus tests) first; the sheets then switch.


## ADR-080: The system accessibility audits must pass locally and report, without blocking, in CI

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui)

**Context.** `performAccessibilityAudit` samples screenshots, and its findings depend on the OS: local development runs Xcode 27.2 on the iOS 27.2 simulator, the macOS runner image stops at Xcode 26.6 and the iOS 26.5 runtime (checked with a diagnostics step; no newer Xcode is installed). Across five CI runs of the same product code the audit tests failed differently each time: "Contrast failed" on black text on a white card (Edit form, Settings rows, Manage's "3 factories" caption, Settings' "Paired devices and keys"), "Hit area is too small | Device name" on a field that already has a 44 pt minimum height, and "Audit failed to complete in time" (error -56). Several of those passed on the next run, and none reproduced locally, where all six audits pass on the iPhone Air. The palette's secondary text is about 5.7:1 on its background, so the contrast findings are not a palette defect.

**Decision.** The audits stay real tests, are not deleted and get no new allow-list entries for the label-based known findings. They must pass locally. `ios.yml` runs the functional UI tests and Swift Testing as the blocking step (`TEST_SET=functional`) and the audits as a separate step (`TEST_SET=audits`, `continue-on-error: true`) with a summary and the xcresult uploaded. The audit helper ignores only findings whose element lies under the floating tab bar, because the bar's glass is what the audit measures there; those are printed as `ACCESSIBILITY-AUDIT-UNDER-TAB-BAR`.

**Consequences.** A real accessibility regression shows in the audit step's summary and locally, not as a red check, until the runner has an Xcode whose audit agrees with local development. Revisit when the runner image gains Xcode 27.

## ADR-090: The Inbox screen shows the one app-wide Inbox holder; the refresh session stays with the environment

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-020)

**Context.** The Inbox feeds the tab badge, so its holder must exist whenever a factory and company are selected, not only while the tab is visible. A second holder (or a second `RefreshSession`) created by the view would double the polling and an approve on screen would not move the badge.

**Decision.** `InboxView` renders `AppEnvironment.inbox` (the same `HolderModel` that feeds the badge). The environment owns the single refresh session; foreground, background and offline are the shared policy's (`lifecycle.setForeground`). The screen only asks for one more refresh when it comes back into view (a tab re-selected, a pushed screen popped), never on first appearance.

**Consequences.** The refresh timer runs while the app is in the foreground on any tab, which keeps the badge current (ADR-010). Pull to refresh sends `Refresh` and waits for `isRefreshing` to end.

## ADR-091: The demo Inbox behaves like a server: resolving removes the card, the first read is 12 s old, Side Project is empty, and the phone can go offline

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-020)

**Context.** UI tests and screenshots need the Inbox to react to approve, keep paused and refresh the way the real feed does, and to show empty and offline states.

**Decision.** `FakeApprovalGateway` and `FakeBudgetGateway` call an `onResolved` hook; the demo wires it to `FakeAttentionGateway.resolveSubject`, so the feed (and the badge, computed by the fake from its items) stops returning the card. The first feed read reports `generatedAt` 12 s before the frozen demo clock, later reads are current, so "Updated 12s ago" becomes "Updated 0s ago" on a refresh. Company `company-side` has an empty feed. `DemoControls.setOnline` drives `FakeConnectivity`; the app's `-demo-offline` launch option applies it once the Inbox has content, so the cached cards are kept (going offline at launch would show the offline-empty state).

**Consequences.** Decisions are not wired the same way (the Decision screen arrives in 2i-3). All of it is in shared fakes and covered by `DemoInboxFlowTest`.

## ADR-092: The Inbox renders the state's order and kinds; cards are a List with swipe actions and a combined text block

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-020)

**Decision.** Sections, cards and chips are shown in the order the state gives (shared grouping sorts Critical first, so the budget alert precedes the HIGH approval, unlike the mock; chips are in kind order). The state has no sub-kind ("Plans queue", "Hire agent"), so the kind line is the kind and identifier. A `List` is used because only list rows support swipe actions; Snooze opens a confirmation dialog listing `snoozeOptions`. The card's text block is one button with a combined spoken label; the action buttons stay separate elements. Kinds the app cannot show open `item.webUrl` in `SFSafariViewController`, only for http(s). Decision, approval, budget-incident, issue, run and agent targets map to routes or a sheet that are labelled placeholders until later milestones.

**Consequences.** Mock-fidelity gaps (order, sub-kind) are shared-state decisions; changing them is a shared change.

## ADR-095: Detail screens own their model by route, refresh while visible, and take their hints from the card that opened them

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-030)

**Context.** The Decision, Approval and Budget screens each have a shared holder that polls (15 s while the item can change, none once final) and that needs, for a decision, facts only the Inbox card knows (agent name, decide-by, snooze key).

**Decision.** `AppEnvironment` creates one model per `ScreenKey` (`decision`, `approval`, `budget`) in the existing `ModelStore` and closes it when its route leaves the stack; the Budget sheet's key is kept while the sheet is presented and released when it is dismissed (`AppRouter.present` and `dismissSheet` now report the change). A view starts `graph.refresh.start(target: holder.refreshTarget)` on appear and stops it on disappear (`refreshWhileVisible`), so the state's `refreshIntervalSeconds` is the only timer. The Inbox card's facts reach the Decision holder through `rememberDecisionHints` (the route carries ids only), mapped back to the strings the shared parser accepts (`DecisionHintMapping`). Dismissing the Budget sheet refreshes the Inbox so a resolved incident's card goes.

**Consequences.** No Swift timers, no leaked holders (tested in `DetailScreenRoutingTests`). A screen opened without a card (a launch option) has no Snooze, as the shared rule says.

## ADR-096: The demo can show a server that moved on: resolved elsewhere, offline after the detail loaded, the server's own spend

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-030)

**Decision.** `DemoControls` gains `resolveDecisionElsewhere`, `resolveApprovalElsewhere`, `resolveIncidentElsewhere` (the Inbox feed still lists the card, as in a feed read a moment earlier) and `setBudgetServerSpend` (the server's spend is above what the incident showed, so a raise that clears the shown spend gets the server's 422 reason). Launch options: `-demo-resolved-elsewhere decision,approval,budget`, `-demo-offline-after-detail` (offline once the first detail screen has content, because all three holders refuse to load while offline), `-demo-budget-server-spend <cents>`, and `-demo-open decision:<id>|approval:<id>|budget:<id>`. Deciding or dismissing a decision removes its Inbox card through `FakeDecisionGateway.onResolved`; dec-1183 carries a required input with a 40 character limit; effect results carry the server's effect type.

**Consequences.** All behaviour is in shared fakes with tests (`DemoDecisionFlowTest`); no UI-only fake state.

## ADR-097: A hire approval's manager is named by the shared load, not by the view

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-030)

**Context.** The payload holds only `reportsToAgentId`; the mock shows "Reports to CTO". Resolving an id to a name in Swift would be a rule in the app.

**Decision.** `LoadApproval` reads the agents list best effort and `ApprovalDetail.agentNames` carries the hire's manager; `ApprovalPayloadUi.HireAgent.reportsToName` exposes it (the id remains the fallback). Role words ("qa" reads "QA") and money ("$150" for 15000 cents, cents kept when not whole) are presentation lookups in Swift (`ApprovalPresentation.roleName`, `MoneyFormat`), tested; the same formatter parses the amount typed on the Budget sheet into minor units.

**Consequences.** Android will need the same two lookups; if they should be shared they move to presentation with the existing tests.

## ADR-098: Face ID is the use case's; destructive controls show the lock, a plain Confirm does not; disabled cards are muted, not dimmed

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-030)

**Decision.** No screen asks Face ID itself and none adds a second confirmation sheet for a destructive option or a budget raise: the confirm button (lock glyph only when the state says `confirmNeedsDeviceAuth` or `submitNeedsDeviceAuth`; the mock shows the lock on every Confirm, a deviation) sends the intent, and a cancelled prompt comes back as `feedback.deviceAuth` and is shown, with nothing changed. A reject or revision note is asked in a sheet that stays open after a cancelled prompt (`notePrompt` stays set). Selectable cards use `CardSelectButtonStyle`, which mutes a disabled card with secondary text instead of the system's dimming (which failed the contrast audit offline). Detail screens hide the tab bar and keep their action bar outside the scroll view; the UI audit helper reports elements that reach below the action bar's top as `DETAIL-AUDIT-BEHIND-BAR` (scrolled content the bar hides) and returns every other finding.

**Consequences.** One gate for every platform. A reader of the state can tell why nothing happened.

## ADR-100: Runs and agent rows name their issue through a best-effort shared read, not a Swift guess

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-040)

**Context.** A run carries only an issue id. The mocks show "PAP-1204" and "Add retries to webhook ingest" on Factory home, Agents rows, Agent detail and the Run viewer. Upper-casing the id would work for the demo and be wrong for a real server.

**Decision.** `LoadIssueRefs` (application) reads each distinct issue once, best effort (an unreadable issue is left out, never fails a screen). `LoadDashboard`, `ListAgents`, `LoadAgent` and `LoadRun` use it; `RunRowUi` gains `issueIdentifier`, `issueTitle`, `finishedAtEpochMillis`; `AgentRowUi` gains `currentRun`; `AgentsState` gains `canSetBudget` and `budgetDisabledReason`. The Agents list reads again after a successful Run now.

**Consequences.** One extra read per distinct live issue (N+1, small N). A cheaper batch read would need an upstream change. Android gets the same fields.

## ADR-101: Agent rows follow the shared rules, not the mock: a budget pause offers Budget, View run replaces Run now, Budget is the owner's

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-040)

**Decision.** The Agents row offers exactly the state's actions plus two navigation buttons: View run (when the agent has a run in progress; it replaces Run now, as the mock's CTO row) and Budget (only when `canSetBudget`; it opens Agent detail, where "Set monthly budget" lives). A budget-paused agent (Engineer 2) has no Resume because the shared rule says a budget pause is lifted by the budget incident (research A1); the mock's Resume is a deviation. Pause is the only row action with the lock glyph (it is the one that asks Face ID; Resume and Run now do not). The mock's "Next heartbeat in 8m" and "last run timed out" are not in the state; rows show "last heartbeat 2m ago".

## ADR-102: Factory home and Agents models belong to a company; Run viewer and Agent detail to their route

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-040)

**Decision.** `ScreenKey` gains `factoryHome` and `agents` (keyed by factory and company, kept while selected, closed when the company changes) and `agent(id)`, `run(id)` (closed when the route leaves the stack). Views start the shared refresh policy while visible (`refreshWhileVisible`); the Run viewer's 5 s interval while a run is live is the state's. Auto-scroll is the state's: the log toggle and a drag that leaves the bottom send `UserScrolled`/`JumpToLatest`. The cancelled Face ID result is shown as feedback on the screen (the confirm sheet closes when the intent is sent).

## ADR-103: The demo is honest about runs: Run now starts one, and a launch option breaks the reads

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-040)

**Decision.** `FakeAgentGateway.invokeHeartbeat` puts the agent to work and reports the run (`onRunStarted`); the demo wiring adds it to the run store. `DemoControls.failReads` makes the Factory, Agents and Run reads fail with a server error; launch options `-demo-server-error` and `-demo-open run:<id>|agent:<id>` expose them. The demo's Running-now data, events and log are the mock's.

## ADR-104: The functional iOS tests run as three parallel CI jobs by test class

**Status:** Accepted (2026-11-02, specs/261102-phase-2i-iphone-ui, UI-040)

**Context.** The functional step took 40.5 minutes on the runner for the 2i-3 head (run 41542545380, 52 minutes in all, 60 minute timeout) and 2i-4 adds about 13 UI tests plus Swift Testing cases.

**Decision.** `scripts/ios-test.sh` takes `SHARD=1|2|3` with `TEST_SET=functional` (a fixed list of test classes per shard, and a check that fails the script if a UI test class is in no shard). `ios.yml` runs a matrix `ios (1)`, `ios (2)`, `ios (3)` and `ios (audits)`; lint and the Kotlin/Native tests run in shard 1 only; the audits job is `continue-on-error` (ADR-080). Every `uses:` stays SHA-pinned. Required status checks named `ios` must be renamed to the four matrix names (audits not required).

**Consequences.** Each shard repeats the setup and the XCFramework build (about 5 minutes) but the longest shard is about a third of the tests.
