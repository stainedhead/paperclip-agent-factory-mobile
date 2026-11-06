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
**Consequences.** The UI cannot skip or weaken the check. *Amended (R-004, DEC-1, confirmed by the owner 2026-11-05; ADR-141):* setting a monthly agent budget above the current amount, or to zero, also requires authentication (`Action.RaiseAgentBudget`); a decrease does not. The first version of this ADR said only the budget incident raise did, which contradicted AGENTS.md ("budget raise" is a destructive action).

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
**Decision.** `Permissions` stays the single table. Every slice 1a-3 mutation use case takes a `CompanyContext`, checks `Permissions.allows` first and then follows ADR-019's order. `SetAgentBudget` is owner only (PRD). *Amended (R-004, DEC-1, confirmed by the owner 2026-11-05; ADR-141):* it takes the `DeviceAuthGuard` and asks the device owner when the new amount is above the current one or zero, before its one gateway call; a decrease asks nothing.
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

**Status:** Accepted (2026-11-02, Phase 1b-1, `specs/archive/261102-phase-1b-adapters`)

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

**Status:** Accepted (2026-11-02, Phase 1b-2, `specs/archive/261102-phase-1b-adapters`)

**Context.** SQLite opens a garbage file and fails on the first statement; a file may also come from a newer app version after a downgrade, or lack a table. The database holds only non-secret factory configuration, settings and an Inbox cache. The credentials are in the Keychain or Keystore under `instance/{id}/`.
**Decision.** After opening, the app probes the file (`user_version` not newer than the code; the three tables exist). If the probe shows the file is unusable (not a database, malformed, newer schema, a missing table), the database and its `-wal`/`-shm`/`-journal` files are deleted and a new empty one is created (`PaperclipPersistence.recreated` is true). Any other failure (busy, I/O, disk full, protected data unavailable, a migration that throws) deletes nothing and is reported as `StorageUnavailableException` (corrected by ADR-077). Only a failure of that second open is fatal. No secret column or secret-typed parameter exists: a test pins the exact column list and scans every stored value for `pcp_board_`, and a Konsist rule forbids importing `Secret` or `SecretKey` in the module.
**Consequences.** The person loses the factory list, not access control. *Corrected by ADR-078:* the original text said recreating orphans the items under `instance/{id}/` until the app is reinstalled. That is false on iOS: Keychain items survive app deletion, and nothing would ever have removed them. `SecretStore` can now list the instance ids that hold secrets, and a recreated database triggers a sweep that deletes the namespaces with no factory row; the person is told through `PaperclipGraph.startupNotice`. Failing closed was rejected: it would leave the app unusable with reinstalling as the only way out.

## ADR-043: Persistence layout: payload mirror classes, one serial executor, orphan pruning

**Status:** Accepted (2026-11-02, Phase 1b-2)

**Context.** The domain has no serialization annotations (AGENTS.md). The in-memory JDBC driver shares one connection and cannot run parallel transactions. A cache write can arrive after its factory was removed, and the cache contract requires writes for scopes with no factory row.
**Decision.** (1) The cached Inbox page is one JSON row written through `internal` payload classes in `infrastructure.persistence.payload`, with a `format` column (added by migration 1.sqm) so an unreadable or newer layout is a cache miss that deletes the row. At most 200 items are kept; when a longer page is cut `nextCursor` is cleared, because the server cursor would skip the dropped items. (2) `PaperclipPersistence` runs the three ports on one `Dispatchers.Default.limitedParallelism(1)`, so statements and transactions of one database are ordered whatever the driver does. Deleting a factory removes its configuration and cache rows in one transaction (no reliance on foreign keys, which are off by default). (3) Opening deletes cache rows whose factory no longer exists. (4) The iOS file lives in `Application Support/PaperclipData`, excluded from backup (folder and files, reapplied on every open); Android uses `noBackupFilesDir`. (5) Generated SQLDelight classes are public by the plugin; the module hides them by making every adapter constructor `internal` and by a Konsist rule that no file outside the module names the generated package.
**Consequences.** Reads are not parallel (fine at this size). A domain change to `AttentionItem` needs a payload change and, if the layout changes, a `format` bump.


## ADR-050: Keychain tests run in the iOS app test host, and the iOS tests are signed ad hoc

**Status:** Accepted (2026-11-02, Phase 1b-3, `specs/archive/261102-phase-1b-adapters`)

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


## ADR-071: The first admin of the contract server is created through the server's own sign-up and bootstrap invite

**Status:** Accepted (2026-11-02, Phase 1b-5b)

**Decision.** A fresh authenticated Paperclip server has no user and no instance admin, and only an instance admin can create a company, so the contract test cannot seed anything until one exists. The test does what a person does: it signs up with Better Auth (`/api/auth/sign-up/email`, a fixed obviously fake test account), accepts a bootstrap CEO invite (`POST /api/invites/{token}/accept`, 202) and then approves its own pairing challenge with the session cookie. The invite is the one thing the image does not provide (it runs only the server, not `paperclipai run`, and the CLI needs a config file the container lacks), so `scripts/paperclip-test-server.sh invite` inserts the row `paperclipai auth bootstrap-ceo` would insert, using the image's own `postgres` client through `docker compose exec`: the token is generated on the host, only its sha256 goes into the container, and only the token is printed. This reaches into the database of a throwaway test container, never of a user's server; the INTENT boundary ("no reaching into its database") is about the app.
**Consequences.** The script is coupled to the `invites` table and the embedded database credentials of the pinned release; the nightly `latest` job is where a change shows up. The token is masked in CI (`::add-mask::`) and handed to Gradle through the environment.

## ADR-072: The contract test is a JVM compilation that `check` compiles but does not run

**Status:** Accepted (2026-11-02, Phase 1b-5b)

**Decision.** `shared/infrastructure/api` has a `contract` JVM compilation (associated with `main`, so it can use the internal client for error-shape comparisons) and a `contractTest` task. `check` depends on `compileContractKotlinJvm` only, so the sources cannot rot, but running needs a server. Without `PAPERCLIP_CONTRACT_BASE_URL` the live tests are skipped with a message; CI sets `PAPERCLIP_CONTRACT_REQUIRED=true`, which turns a missing server into a failure so a skip can never look like a pass. The task is never up to date, uses the production adapters on the real OkHttp engine (never MockEngine), a test-only in-memory `SecretStore`, and `http://localhost:3199` because plain http is allowed for `localhost` and `10.0.2.2` only (the allow-list was not widened for `127.0.0.1`).
**Consequences.** A `LauncherSessionListener` prints, at the end of the run, which port operations were verified live and which are covered only by the MockEngine tests, and writes the same table to `build/reports/contract/live-operations.txt`.

## ADR-073: Seeding failures and unverified operations are reported, not guessed

**Status:** Accepted (2026-11-02, Phase 1b-5b)

**Decision.** Seeding the company and the first issue is required; the first runs attempted everything else the server may legitimately refuse (an agent that needs board approval, an approval type, a wake of a process agent) as optional, recording the server's answer in the run report and going on (`Seeder.optional`; `soft` was used only for `observe`). A step is asserted hard once a run has shown how the server behaves, and the stubs were corrected where the real server differed. **Superseded in part by ADR-082:** optional seeds, `soft` and `observe`-without-failure were removed (a refused seed or a mismatch now fails the run), and interactions and budget incidents turned out to be seedable by a board key (a question through `POST /issues/{id}/interactions`; an over-budget cost event through `POST /companies/{c}/cost-events`). Only decisions stay unseedable (agent run context).
**Consequences.** The report is the honest coverage statement; the status and notes files quote the CI run that produced it.

## ADR-074: A resume is followed by one read, because the server answers it mid-transition

**Status:** Accepted (2026-11-02, Phase 1b-5b)

**Decision.** The live contract run showed that Paperclip applies a pause or resume in steps: a resume answers `status paused`, no pause reason, `lifecycleState resuming`, and the very next read shows `idle`. `ResumeAgent` (application layer, a cross-ownership edit made test-first in its own commit) therefore reads the agent once when the reply says `resuming`, and reports that read; if the read fails it reports the server's reply. The resume itself is still sent exactly once. Pause is unchanged (its reply, `paused`/`manual`, is the state the person asked for). The adapter, the shared contract and the fake keep modelling the settled state; the stub has an opt-in `transitions` mode that reproduces the server.
**Consequences.** The screen no longer shows a paused agent under a Resume button that was just pressed. An agent that is still `pausing` or `resuming` when a screen loads is shown with the server's status; a state holder that wants to hide the transition can use `Agent.lifecycleState`, which the mapper already carries (not done here).

## ADR-075: Pairing, pending-key and health calls carry the pins of the factory at that address

**Status:** Accepted (2026-11-02, review1b F-A-1, `specs/archive/261102-phase-1b-adapters`)

**Context.** The fail-closed pin rule (ADR-040) is `request.pins.isNotEmpty() && !pinsEnforced`. Calls with a stored key read the pins from the `Instance`. The calls made before a key exists (health probe, `createChallenge`, `pollChallenge`, `meWithKey`, `listBoardKeysWithKey`) take only a `BaseUri` on the port, so they built `Destination(baseUri)` with no pins and went out over system trust TLS. Those calls carry the new board token, the challenge token and the pending key: the most sensitive traffic.
**Decision.** The ports keep their signatures. `HealthGatewayAdapter` and `AuthGatewayAdapter` look up the pins of the configured factory at the same `BaseUri` (`PinLookup` over `InstanceRepository.observeAll()`, union of the pins of every factory at that address) and put them in the `Destination`, so the same refusal applies. A first pairing of an address no factory uses yet has no pins to apply, which is correct: nothing was pinned. A re-pair or probe of a pinned factory is refused with `CertificateInvalid` while no engine enforces pins. `HealthGatewayAdapter` now takes the `InstanceRepository`.
**Consequences.** One extra local read per pre-key call. When an engine enforces pins the same lookup supplies them.

## ADR-076: The server's approvalUrl is opened only when it is the instance's own origin

**Status:** Accepted (2026-11-02, review1b F-A-3)

**Context.** The pinned server builds `approvalUrl` from `x-forwarded-proto` and `x-forwarded-host` (access.ts:73-80). Behind a TLS-terminating proxy that does not forward the protocol it is `http://`; a different forwarded host gives a foreign origin. The URL carries the challenge token and is opened in a system browser where the person signs in.
**Decision.** `ApprovalOrigin.isOwn` accepts the server value only if it has the scheme, host and port of the `BaseUri` (default ports made explicit, case-insensitive host), no user info, no backslash or space in the authority and no control characters. Because a `BaseUri` already enforces https (or http for `localhost` and `10.0.2.2` in debug), requiring the same scheme enforces the same allow-list. Anything else is ignored and the URL is built from the `BaseUri`, with the challenge id and token percent-encoded.
**Consequences.** A proxy setup that publishes the web UI under a different origin than the one the phone uses will always get the locally built URL, which is the address the person typed.

## ADR-077: A database is wiped only on proof; any other open failure is StorageUnavailable

**Status:** Accepted (2026-11-02, review1b F-B-1)

**Context.** `openRecovering` treated every exception from the open and the probe as corruption and deleted the file. A busy or locked file, a full disk, an I/O error, protected data before the first unlock, and a migration that throws all looked like corruption, so the OS could cause the loss of the factory list.
**Decision.** Wipe only when the failure message in the cause chain says the file is not a database, is malformed or corrupt, is a downgrade or "newer than" the code, or the probe finds a newer `user_version` or a missing table. Every other failure closes the driver, deletes nothing and throws `StorageUnavailableException` (public in the persistence module). The second open after a wipe also throws it. Message matching is used because the three drivers (sqlite-jdbc, Android, sqliter) raise untyped exceptions; each pattern is covered by a test, and the real garbage file and newer-schema file tests run on JVM and iOS.
**Consequences.** The Swift launch path must handle the exception (review1b F-B-5, deferred to the iOS stream). A driver message that changes wording would turn a corrupt file into a persistent "storage unavailable" instead of a wipe; that fails safe.

## ADR-078: A recreated database is announced and its orphaned secrets are swept

**Status:** Accepted (2026-11-02, review1b F-B-2)

**Context.** `PaperclipPersistence.recreated` was never read, so a recreated database looked like a first launch. ADR-042 claimed orphaned Keychain items last until reinstall; on iOS they survive reinstall, so the board tokens of lost factories stayed on the phone forever.
**Decision.** `SecretStore.namespaces()` lists the instance ids that hold secrets (names only, no prompt). `SweepOrphanSecrets` deletes every namespace that has no factory row, continuing past one that cannot be deleted. `createLiveGraph` runs it once when `persistence.recreated` is true and exposes `PaperclipGraph.startupNotice` (`databaseRecreated`, `orphanSecretsRemoved`) for the app to show once; the SwiftUI banner is the iOS stream's job. The notice is per launch and is not stored.
**Consequences.** A person who never opens the app after the recreation does not see the notice; they see an empty factory list. The port gained one method, implemented by the fake, the Keychain store, the unavailable store and the contract suite.

## ADR-079: A declined or invalidated key read is not a 401

**Status:** Accepted (2026-11-02, review1b F-A-2)

**Context.** `StoredCredentials` turned `Denied` and `Invalidated` into `Unauthorized`. `SignedOutMarker` then marked the factory Signed out after a cancelled Face ID prompt, and `KeyRevoker` reported `AlreadyInvalid` and the sign-out deleted the local key while the server key was still live.
**Decision.** Two domain errors: `DeviceAuthDeclined` (nothing changes, try again) and `KeyInvalidated` (re-pair; the server key may be live). `Missing` stays `Unauthorized`. `SignedOutMarker` marks `NeedsRePairing` for `KeyInvalidated` and ignores `DeviceAuthDeclined`. `KeyRevoker` returns `RevokeOutcome.Declined` for a declined read and `Unconfirmed` for an invalidated key. Sign out and remove stop on `Declined` without deleting anything (`SignOutResult.Declined`, `RemoveInstanceResult.NotAuthorized(Cancelled)`); sign out everywhere counts it as failed. Presentation adds `ErrorKey.AuthDeclined`, `ErrorKey.KeyInvalidated` and `ServerKeyOutcome.Declined`.
**Consequences.** Swift code that switches over `ErrorKey` or `ServerKeyOutcome` exhaustively gains cases. A key read can still prompt once per HTTP call (not changed here).

**Addendum, same review (decisions that did not need their own number).**
- *F-C-1.* A protected Keychain write whose access control cannot be created is refused by the backend (`errSecParam`) before `SecItemAdd`; it used to be added with the default, backup-eligible accessibility and reported Stored. Accessibility cannot also be set on a protected item, so refusing is the fail-closed choice. The access-control factory is injectable so a bare Kotlin/Native test can force the failure.
- *F-B-3.* A Keychain replacement adds the new value under a spare account (`...#new`) first, deletes the old item only when that worked, then adds under the real name (two attempts) and always removes the spare. A failed or refused new item leaves the old secret readable. The short step between the delete and the final add can still lose the old secret; it cannot be closed because an update in place keeps the old access control.
- *F-B-4.* The graph closes the dependency container first, then the HTTP client, the database and the platform. A graph that cannot be built releases the platform and database. `IosLivePlatform` opens the database before it starts the network monitor.
- *F-A-4.* `ack()` accepts a 204, a 205 or a JSON body; an HTML or empty 200 is Malformed.
- *F-A-5.* `DomainError.ConnectionLost` (reset, broken pipe, early end of stream, NSURLError -1005, 502 and 504) says the request may have reached the server. Mutations that end in it, in Timeout, Conflict or an unreadable 2xx (Malformed) are read back once and never sent again; reads retry it like Unreachable. A plain 500 or 503 stays a definite ServerError because the server answered itself and the decision use case pins that; a 5xx the server's own proxy produced is therefore only covered for 502 and 504.
- *F-A-6.* `Agent.isResuming` and `Agent.isPausing` live in the domain; `canResume` and `canPause` are false while they hold, so a failed or stale follow-up read can no longer leave a live Resume button.

## ADR-081: The contract test refuses to run against anything but the throwaway test server

**Status:** Accepted (2026-11-02, review1b F-C-8)

**Context.** `PAPERCLIP_CONTRACT_BASE_URL` accepted any url. The run signs up a fixed account, accepts an invite, pairs devices, creates companies and agents, posts cost events and cancels runs: against an operator's own Paperclip (one runs on port 3100 on the developer machine) a mistyped variable would have created admin-capable state there.
**Decision.** Before the first request the run requires all of: `PAPERCLIP_CONTRACT_I_KNOW_THIS_MUTATES=true` (contract.yml sets it, the documented run line shows it); plain `http` on `localhost` or `127.0.0.1` (never a remote host, a lookalike host or userinfo); the port of the test compose (3199, or `PAPERCLIP_TEST_PORT`); and, after the first read-only probe, a `/api/health` that reports authenticated and private. Every refusal names the missing condition and echoes no part of the url's credentials. The pure checks are unit tested in `ContractHelpersTest` (which runs without a server); the opt-in variable is forwarded by the `contractTest` task like the other variables.
**Consequences.** The throwaway compose has no health field that identifies it, so the "marker" is the combination above, not a server-side flag; a developer who maps a real server to port 3199 and sets the opt-in defeats it. An HTTPS test server is not supported by the guard.

## ADR-082: A live run verifies an operation only with data, and fails on anything else

**Status:** Accepted (2026-11-02, review1b F-C-2, F-C-3, F-C-9)

**Context.** The report counted an operation as verified live when the adapter returned `Success`, even for an empty list or empty log (about a fifth of the 46); seed refusals, `observe` mismatches and early `return@live` all ended green, so the nightly "API drift turns red" promise was partly false; ownership was proven live only on the success path.
**Decision.** `LiveLedger` records per operation: data (a non-empty decoded reply; sticky over empty whatever the test order), empty (reachable, not verification), error path, failed (sticky), skipped (with reason), and MISMATCH (counted, printed as `MISMATCH n` even at 0). The verdict fails the run for every failed call, mismatch, skip not on the skip allow list, operation never run, operation that only ever decoded an empty reply (unless on the empty allow list) and error path alone. The two allow lists live in `LiveOperations.kt`, each entry with its reason; today only the three `DecisionGateway` operations are listed (decisions need an agent run context, pinned dist `routes/decisions.js`). The report ends with `VERDICT: PASS|FAIL`; a `doLast` on `contractTest` turns FAIL (or no verdict against a required server) into a failed build, so the `pinned` and the nightly `latest` job both go red. Seeds are required, not optional. Seeded for data: window spend (a cost event), run events and log (an `echo` process agent whose issue is assigned to it), `forIssue` and `forAgent`, `live` (a `sleep` agent), attachments (multipart upload), a pending question (and its answer), linked issues and approval comments, a budget incident (an agent over a 100 cent limit; resolved with keep-paused, which the server records as `dismissed`). A second company proves the ownership checks live on failure paths: reads and writes of its issue, approval, agent, run and question through the first company's scope are `NotFound`, and a raw read afterwards shows nothing changed; the company-scoped run list answers an empty list for a foreign agent (the server filters by the company in the path).
**Consequences.** A server change that makes a seed fail or a field disappear fails the run instead of shrinking the count quietly. The run is longer (polling for runs, up to 90 s per wait) and depends on the process adapter running `echo` and `sleep` in the container image.

## ADR-083: Key invalidation is `item not found` plus a marker; everything else is Denied

**Status:** Accepted (2026-11-02, review1b F-B-7, F-B-10)

**Context.** The re-pair path (PRD FR-015) assumes iOS answers a read after a biometric-set change with `errSecItemNotFound` while the `#protected` marker survives. Nobody has seen that on a device (the simulator has no Face ID); the review feared that `errSecAuthFailed` could be what a device returns, which would loop on Denied.
**Decision.** Keep the mapping: only `errSecItemNotFound` plus the marker is `Invalidated`; `errSecAuthFailed`, `errSecInteractionNotAllowed`, `errSecUserCanceled` and `errSecNotAvailable` are `Denied`, which deletes nothing and offers a retry. This is pinned by tests (`F-B-7`) and a device check list in `docs/technical-details.md` says what to verify by hand; if step 4 of the list loops on Denied, the mapping changes in its own commit. A delete removes the marker first and the item second, so a failure between them leaves a readable secret, never a marker without an item (which would read as `Invalidated`).
**Consequences.** The invalidation path stays unverified on hardware until the check list is run and recorded.

## ADR-084: New throwing launch entry points instead of changing the existing signatures

**Status:** Accepted (2026-11-02, review1b F-B-5, F-B-8, F-B-9)

**Context.** `createLiveGraph` throws `StorageUnavailableException` or `IllegalStateException` on a bad data folder; thrown into Swift without `@Throws` that aborts the app on every launch. Adding `@Throws` to the existing functions would make every Swift caller (the stream building the iPhone UI, and `LiveGraphInteropTests`) fail to compile at once; default arguments and `Result` are not allowed on the Swift surface. The pairing browser also showed the approval page on a background thread and kept it up after a cancelled pairing.
**Decision.** Add, do not change: `PaperclipCore.openLiveGraph(allowInsecureLocal, pairingBrowser)` and `LiveIosGraph.openLiveGraph(allowInsecureLocal, pairingBrowser, settings)` with `@Throws(Exception::class)` (Swift `throws`); `PairingBrowsers.onMainThread(show:dismiss:)` which shows and dismisses on the main dispatcher and dismisses when the pairing is cancelled; `CallbackPairingBrowser` takes an optional dismiss and dispatcher (its one-argument form is unchanged). The existing `createLiveGraph` and `fromCallback` keep working and are documented as the non-throwing, background forms. `openIosPersistence` ignores a sidecar file that vanishes between the check and the call and closes the database when protecting a file fails. The app target declares `NSFaceIDUsageDescription` (English text; no localized InfoPlist exists yet), tested through the generated plist.
**Consequences.** The iPhone UI stream should call `openLiveGraph` and `onMainThread`, show a retry screen on the error and may then delete the old forms. ADR-070 and the other stream's `ApprovalPageOpener` (ADR-045) describe two callback mechanisms for the same Swift limitation: one will be dropped at merge. No exhaustive Swift `switch` over a shared type changed in this group.


## ADR-085: A pending-sweep marker, written before the wipe, outlives the launch until the orphan sweep completed

**Status:** Accepted (2026-11-02, rereview1b F-R-1)

**Context.** `PaperclipPersistence.recreated` existed only in memory of the process that wiped the file. If the reopen after the wipe failed, or the process died before `SweepOrphanSecrets` ran, the next launch found an empty valid file, `recreated = false`, no startup notice and no sweep: the lost factories' board keys stayed in the Keychain for good.

**Decision.** `openRecovering` takes a `SweepMarker` (a file `paperclip.db.sweep-pending` next to the database on JVM, Android `noBackupFilesDir` and iOS `PaperclipData`; in memory for in-memory databases). The marker is set BEFORE the wipe (a marker that cannot be written stops the wipe with `StorageUnavailableException`), and a healthy open reports `recreated = marker.isSet()`. `LiveGraph` clears it (`PaperclipPersistence.sweepCompleted()`) only after `SweepOrphanSecrets` returned a `SweepResult` with `failed == 0`; a sweep that threw or left a namespace behind keeps the marker and the next launch tries again (the notice is then shown again, which is the honest outcome). `SweepOrphanSecrets` now returns `SweepResult(removed, failed)` instead of an `Int`.

**Consequences.** One tiny file with no data. A normal launch with factories never has a marker and sweeps nothing. The notice can repeat until a sweep completes.

## ADR-086: A key read that stays denied has an explicit "here only" way out; declined and locked are not told apart

**Status:** Accepted (2026-11-02, rereview1b F-R-2)

**Context.** After F-A-2 a `Denied` key read (cancelled prompt, locked device, `errSecAuthFailed`, lockout) no longer counted as a 401, so sign out and remove did nothing while it lasted. If a device reports an invalidated biometric-set key as `errSecAuthFailed` (unverified, ADR-083) the person could neither use, sign out nor remove the factory.

**Decision.** Two new use cases, both behind `DeviceAuthGuard(Action.RemoveFactory)` and never calling the server: `RemoveInstanceHereOnly` (every secret, configuration and cache of the factory) and `SignOutInstanceHereOnly` (local key dropped, factory marked signed out). A factory that was signed in reports `RevokeOutcome.Unconfirmed` (the key stays live on the server; the screen shows the paired-devices hint). `RemoveInstance` now answers a denied read with the typed `RemoveInstanceResult.KeyReadDenied` (was `NotAuthorized(Cancelled)`); `SignOutResult.Declined` is unchanged. Presentation turns the denial into the offer: Manage factories sets `RemovalConfirmationUi.hereOnly` and `ErrorKey.AuthDeclined`, Settings sets `SettingsConfirmation.SignOutHereOnly`, the Edit screen sets `EditConfirmation.SignOutHereOnly` or `RemoveHereOnly`; confirming runs the here-only use case, dismissing deletes nothing. `SettingsState` and `ManageFactoriesState` gained `deviceAuthIssue`/`error` for a refused second authentication. The Keychain read keeps one `Denied` result: `mapReadFailure` collapses every status except item-not-found, so "declined" and "device locked" are not distinguishable to the app (`errSecInteractionNotAllowed` and `errSecUserCanceled` would be, but `SecretReadResult.Denied` carries no reason). `ErrorKey.AuthDeclined` is documented as "declined or device locked".

**Consequences.** The person is never trapped. A second device authentication is asked (the first one guarded the attempt that failed). Splitting locked from declined would add a case to `SecretReadResult` and every `when`; deferred until a device check shows it matters.

## ADR-087: Canonical forms and a positive identity for the live run

**Status:** Accepted (2026-11-02, rereview1b F-R-3, F-R-4, F-R-5, F-R-6)

**Context.** (3) `https://h` and `https://h:443` were two `BaseUri`s, so a pinned factory probed as `:443` was sent unpinned. (4) The live guard of ADR-081 had no positive identity: with `PAPERCLIP_TEST_PORT=3100` plus the opt-in, or an ssh tunnel to local 3199, any authenticated private server passed. (5) The last redaction regex removed lower case snake and hyphen words of 24 or more characters from server reasons. (6) An `InstanceId` such as `a/b` would be listed by the secret store as the namespace `a`.

**Decision.** (3) `BaseUri.parse` drops the default port of the scheme (443 for https, 80 for http) and leading zeros of any port; every stored row is parsed again on read, so older rows need no migration. (4) The compose file cannot echo a marker (anonymous `/api/health` exposes no environment value), so identity is proved by what only the test container holds: a server that is `bootstrap_pending` must accept the invite minted inside the container by `scripts/paperclip-test-server.sh invite` (without it the run refuses before any call that creates something); a server already in use must accept sign-in with the fixed `TESTONLY` account, and the run never signs up on it. Port 3100 is refused even when `PAPERCLIP_TEST_PORT` names it. (5) The 24+ run is removed only when it holds a digit, a capital letter or `+/=`. (6) `InstanceId` rejects `/`, `\` and control characters.

**Consequences.** A server that already holds the fixed TESTONLY account could still be mutated; that account exists only where an earlier run of this test created it. Hex or base64 runs without any digit, capital or `+/=` (24 or more lower case letters only) are no longer redacted; real tokens carry digits.
## ADR-088: Swift mirrors a state holder through one generic `HolderModel`, owned by an explicit owner

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-001/UI-002)

**Context.** Every screen needs an `@Observable` adapter over a Kotlin holder's `StateFlow`. Writing one per screen invites stale reads (an intent returns before the flow emits) and holders closed from a view's `.task` ending.

**Decision.** `StateHolderProtocol` (conformed to by all 16 holders in one line each) and `HolderModel<Holder>`: `send` applies `holder.state.value` straight after the intent; `observe()` catches up then follows the `SkieSwiftStateFlow` until its task is cancelled, and can restart; cancelling never closes the holder; `close()` is idempotent and called only by an owner (`AppEnvironment` for the shell models, `ModelStore` keyed by route for screens). Everything is `@MainActor`.

**Consequences.** A screen model is `HolderModel<XStateHolder>` plus an extension of typed intent helpers. Tests cover mirroring, no stale read, cancel and restart, and exactly-once close.

## ADR-089: A launch without `-demo` starts at first run on the live seam; the demo graph is opt-in, with scripted Face ID through `DemoControls`

**Status:** Accepted (2026-11-02, UI-001/UI-004)

**Context.** The brief suggested demo by default, but acceptance criterion 8 and the existing first-run UI test require a launch without arguments to show the empty first-run flow. UI tests also need to script Face ID results and count prompts without a device.

**Decision.** `AppGraphProvider` has `DemoGraphProvider` (`-demo` or "Try the demo factory") and `LiveGraphProvider`, a placeholder marked `isAvailable == false` that returns today's fake-backed `createGraph` (ADR-037) until Phase 1b. `PaperclipGraph.demoControls` (demo graph only, null otherwise) scripts `BiometricResult`s and counts prompts; the app reads launch arguments `-demo-biometrics`, `-demo-idle`, `-demo-diagnostics`.

**Consequences.** Real Face ID (LocalAuthentication) arrives with Phase 1b's adapter; until then the graph's `Biometrics` is the fake.

## ADR-090: Destructive Face ID gating stays inside the shared use case; the Swift sheet only forwards and reports

**Status:** Accepted (2026-11-02, UI-004)

**Context.** AGENTS.md puts the `Biometrics` port inside the use case. A Swift sheet that also prompted would double-prompt and could be skipped by another view.

**Decision.** `ConfirmDestructiveSheet` shows the lock glyph, calls `onConfirm` (the screen's confirm intent) and displays the holder's `DeviceAuthIssue`. A Swift test proves that a cancelled scripted prompt leaves the agent unpaused.

**Consequences.** One gate for every platform. Catalog keys are `<namespace>.<caseName>` for each exported key enum, verified by a completeness test (ADR for localisation scheme: this one).

## ADR-091: (superseded by ADR-120) A Swift shell shows the pairing page through `ApprovalPageOpener`, a callback port, because Swift cannot read a `Secret`

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-010)

**Context.** `PairingBrowser.open(approvalUrl: Secret)` needs the link, but `Secret.reveal` is hidden from Objective-C on purpose and a Konsist rule limits it to infrastructure and fakes. A Swift conformance to a Kotlin `suspend` interface also needs a `__open` name that SwiftLint and swift-format reject, and an error thrown from Swift into the holder's coroutine would end the app.

**Decision.** `shared/infrastructure/platform` offers `ApprovalPageOpener.open(url: String, onShown: () -> Unit)` (implemented by the shell, never throws) and `OpenerPairingBrowser`, the only place the link leaves its `Secret`. The Swift `ASWebAuthSessionPairingBrowser` opens only `https` links (plain `http` to a local host in debug builds), never logs or keeps the link, and calls back once the page is on screen. Closing the sheet is not a failure: it is how a person finishes approving, and the shared use case finds out by polling.

**Consequences.** Superseded on the 1b merge: 1b's `PairingBrowsers.onMainThread(show, dismiss)` is used instead and `ApprovalPageOpener`/`OpenerPairingBrowser` were deleted (the platform module may not see a `Secret`, a Konsist rule). Original text: Phase 1b passes `OpenerPairingBrowser(opener)` to `createLiveGraph`; only the factory call changes. A sheet that could not be shown cancels the pairing on screen. Real `ASWebAuthenticationSession` behaviour is not exercised on CI.

## ADR-092: Pairing completes on fakes through `approvePairings`, a first-run demo graph, and the graph clock

**Status:** Accepted (2026-11-02, UI-010)

**Context.** On the demo graph a pairing could never finish: the challenge expired at a 2026-11-01 instant while the demo clock was 2026-11-04, polls answered Pending forever, and a used challenge is wiped so it cannot be reused. Updated labels were measured against the wall clock.

**Decision.** `FakeAuthGateway.approvePairings(from)` hands out a fresh challenge per call that expires relative to the graph clock and approves every poll. `PaperclipCore.createFirstRunDemoGraph()` starts with no factory. `PaperclipGraph.nowEpochMillis()` lets the app measure "Updated" against the graph clock. `-demo-first-run` selects that graph in the app.

**Consequences.** First-run, pairing and re-pair flows run in demo mode and UI tests with no server. The live graph uses the wall clock.

## ADR-093: Sign out, revoke and sign out everywhere ask for confirmation but not Face ID

**Status:** Accepted (2026-11-02, UI-010)

**Context.** The Settings mock shows a lock glyph on Sign out everywhere. FR-014 lists the actions that always need the device owner (pause, cancel, destructive decisions, reject, budget raise), and AGENTS.md adds removing a factory; the shared use cases gate exactly those.

**Decision.** The Swift screens ask for a plain confirmation (`ConfirmSheet`, no lock glyph) for sign out, revoke and sign out everywhere, and the lock glyph appears only where the use case prompts (`ConfirmDestructiveSheet`, remove factory).

**Consequences.** Nothing claims a prompt that does not happen. If the owner wants these gated, that is a shared change (use case plus tests) first; the sheets then switch.


## ADR-094: The system accessibility audits must pass locally and report, without blocking, in CI

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui)

**Context.** `performAccessibilityAudit` samples screenshots, and its findings depend on the OS: local development runs Xcode 27.2 on the iOS 27.2 simulator, the macOS runner image stops at Xcode 26.6 and the iOS 26.5 runtime (checked with a diagnostics step; no newer Xcode is installed). Across five CI runs of the same product code the audit tests failed differently each time: "Contrast failed" on black text on a white card (Edit form, Settings rows, Manage's "3 factories" caption, Settings' "Paired devices and keys"), "Hit area is too small | Device name" on a field that already has a 44 pt minimum height, and "Audit failed to complete in time" (error -56). Several of those passed on the next run, and none reproduced locally, where all six audits pass on the iPhone Air. The palette's secondary text is about 5.7:1 on its background, so the contrast findings are not a palette defect.

**Decision.** The audits stay real tests, are not deleted and get no new allow-list entries for the label-based known findings. They must pass locally. `ios.yml` runs the functional UI tests and Swift Testing as the blocking step (`TEST_SET=functional`) and the audits as a separate step (`TEST_SET=audits`, `continue-on-error: true`) with a summary and the xcresult uploaded. The audit helper ignores only findings whose element lies under the floating tab bar, because the bar's glass is what the audit measures there; those are printed as `ACCESSIBILITY-AUDIT-UNDER-TAB-BAR`.

**Consequences.** A real accessibility regression shows in the audit step's summary and locally, not as a red check, until the runner has an Xcode whose audit agrees with local development. Revisit when the runner image gains Xcode 27.

## ADR-095: The Inbox screen shows the one app-wide Inbox holder; the refresh session stays with the environment

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-020)

**Context.** The Inbox feeds the tab badge, so its holder must exist whenever a factory and company are selected, not only while the tab is visible. A second holder (or a second `RefreshSession`) created by the view would double the polling and an approve on screen would not move the badge.

**Decision.** `InboxView` renders `AppEnvironment.inbox` (the same `HolderModel` that feeds the badge). The environment owns the single refresh session; foreground, background and offline are the shared policy's (`lifecycle.setForeground`). The screen only asks for one more refresh when it comes back into view (a tab re-selected, a pushed screen popped), never on first appearance.

**Consequences.** The refresh timer runs while the app is in the foreground on any tab, which keeps the badge current (ADR-010). Pull to refresh sends `Refresh` and waits for `isRefreshing` to end.

## ADR-096: The demo Inbox behaves like a server: resolving removes the card, the first read is 12 s old, Side Project is empty, and the phone can go offline

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-020)

**Context.** UI tests and screenshots need the Inbox to react to approve, keep paused and refresh the way the real feed does, and to show empty and offline states.

**Decision.** `FakeApprovalGateway` and `FakeBudgetGateway` call an `onResolved` hook; the demo wires it to `FakeAttentionGateway.resolveSubject`, so the feed (and the badge, computed by the fake from its items) stops returning the card. The first feed read reports `generatedAt` 12 s before the frozen demo clock, later reads are current, so "Updated 12s ago" becomes "Updated 0s ago" on a refresh. Company `company-side` has an empty feed. `DemoControls.setOnline` drives `FakeConnectivity`; the app's `-demo-offline` launch option applies it once the Inbox has content, so the cached cards are kept (going offline at launch would show the offline-empty state).

**Consequences.** Decisions are not wired the same way (the Decision screen arrives in 2i-3). All of it is in shared fakes and covered by `DemoInboxFlowTest`.

## ADR-097: The Inbox renders the state's order and kinds; cards are a List with swipe actions and a combined text block

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-020)

**Decision.** Sections, cards and chips are shown in the order the state gives (shared grouping sorts Critical first, so the budget alert precedes the HIGH approval, unlike the mock; chips are in kind order). The state has no sub-kind ("Plans queue", "Hire agent"), so the kind line is the kind and identifier. A `List` is used because only list rows support swipe actions; Snooze opens a confirmation dialog listing `snoozeOptions`. The card's text block is one button with a combined spoken label; the action buttons stay separate elements. Kinds the app cannot show open `item.webUrl` in `SFSafariViewController`, only for http(s). Decision, approval, budget-incident, issue, run and agent targets map to routes or a sheet that are labelled placeholders until later milestones.

**Consequences.** Mock-fidelity gaps (order, sub-kind) are shared-state decisions; changing them is a shared change.

## ADR-098: Detail screens own their model by route, refresh while visible, and take their hints from the card that opened them

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-030)

**Context.** The Decision, Approval and Budget screens each have a shared holder that polls (15 s while the item can change, none once final) and that needs, for a decision, facts only the Inbox card knows (agent name, decide-by, snooze key).

**Decision.** `AppEnvironment` creates one model per `ScreenKey` (`decision`, `approval`, `budget`) in the existing `ModelStore` and closes it when its route leaves the stack; the Budget sheet's key is kept while the sheet is presented and released when it is dismissed (`AppRouter.present` and `dismissSheet` now report the change). A view starts `graph.refresh.start(target: holder.refreshTarget)` on appear and stops it on disappear (`refreshWhileVisible`), so the state's `refreshIntervalSeconds` is the only timer. The Inbox card's facts reach the Decision holder through `rememberDecisionHints` (the route carries ids only), mapped back to the strings the shared parser accepts (`DecisionHintMapping`). Dismissing the Budget sheet refreshes the Inbox so a resolved incident's card goes.

**Consequences.** No Swift timers, no leaked holders (tested in `DetailScreenRoutingTests`). A screen opened without a card (a launch option) has no Snooze, as the shared rule says.

## ADR-099: The demo can show a server that moved on: resolved elsewhere, offline after the detail loaded, the server's own spend

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-030)

**Decision.** `DemoControls` gains `resolveDecisionElsewhere`, `resolveApprovalElsewhere`, `resolveIncidentElsewhere` (the Inbox feed still lists the card, as in a feed read a moment earlier) and `setBudgetServerSpend` (the server's spend is above what the incident showed, so a raise that clears the shown spend gets the server's 422 reason). Launch options: `-demo-resolved-elsewhere decision,approval,budget`, `-demo-offline-after-detail` (offline once the first detail screen has content, because all three holders refuse to load while offline), `-demo-budget-server-spend <cents>`, and `-demo-open decision:<id>|approval:<id>|budget:<id>`. Deciding or dismissing a decision removes its Inbox card through `FakeDecisionGateway.onResolved`; dec-1183 carries a required input with a 40 character limit; effect results carry the server's effect type.

**Consequences.** All behaviour is in shared fakes with tests (`DemoDecisionFlowTest`); no UI-only fake state.

## ADR-100: A hire approval's manager is named by the shared load, not by the view

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-030)

**Context.** The payload holds only `reportsToAgentId`; the mock shows "Reports to CTO". Resolving an id to a name in Swift would be a rule in the app.

**Decision.** `LoadApproval` reads the agents list best effort and `ApprovalDetail.agentNames` carries the hire's manager; `ApprovalPayloadUi.HireAgent.reportsToName` exposes it (the id remains the fallback). Role words ("qa" reads "QA") and money ("$150" for 15000 cents, cents kept when not whole) are presentation lookups in Swift (`ApprovalPresentation.roleName`, `MoneyFormat`), tested; the same formatter parses the amount typed on the Budget sheet into minor units.

**Consequences.** Android will need the same two lookups; if they should be shared they move to presentation with the existing tests.

## ADR-101: Face ID is the use case's; destructive controls show the lock, a plain Confirm does not; disabled cards are muted, not dimmed

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-030)

**Decision.** No screen asks Face ID itself and none adds a second confirmation sheet for a destructive option or a budget raise: the confirm button (lock glyph only when the state says `confirmNeedsDeviceAuth` or `submitNeedsDeviceAuth`; the mock shows the lock on every Confirm, a deviation) sends the intent, and a cancelled prompt comes back as `feedback.deviceAuth` and is shown, with nothing changed. A reject or revision note is asked in a sheet that stays open after a cancelled prompt (`notePrompt` stays set). Selectable cards use `CardSelectButtonStyle`, which mutes a disabled card with secondary text instead of the system's dimming (which failed the contrast audit offline). Detail screens hide the tab bar and keep their action bar outside the scroll view; the UI audit helper reports elements that reach below the action bar's top as `DETAIL-AUDIT-BEHIND-BAR` (scrolled content the bar hides) and returns every other finding.

**Consequences.** One gate for every platform. A reader of the state can tell why nothing happened.

## ADR-102: Runs and agent rows name their issue through a best-effort shared read, not a Swift guess

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-040)

**Context.** A run carries only an issue id. The mocks show "PAP-1204" and "Add retries to webhook ingest" on Factory home, Agents rows, Agent detail and the Run viewer. Upper-casing the id would work for the demo and be wrong for a real server.

**Decision.** `LoadIssueRefs` (application) reads each distinct issue once, best effort (an unreadable issue is left out, never fails a screen). `LoadDashboard`, `ListAgents`, `LoadAgent` and `LoadRun` use it; `RunRowUi` gains `issueIdentifier`, `issueTitle`, `finishedAtEpochMillis`; `AgentRowUi` gains `currentRun`; `AgentsState` gains `canSetBudget` and `budgetDisabledReason`. The Agents list reads again after a successful Run now.

**Consequences.** One extra read per distinct live issue (N+1, small N). A cheaper batch read would need an upstream change. Android gets the same fields.

## ADR-103: Agent rows follow the shared rules, not the mock: a budget pause offers Budget, View run replaces Run now, Budget is the owner's

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-040)

**Decision.** The Agents row offers exactly the state's actions plus two navigation buttons: View run (when the agent has a run in progress; it replaces Run now, as the mock's CTO row) and Budget (only when `canSetBudget`; it opens Agent detail, where "Set monthly budget" lives). A budget-paused agent (Engineer 2) has no Resume because the shared rule says a budget pause is lifted by the budget incident (research A1); the mock's Resume is a deviation. Pause is the only row action with the lock glyph (it is the one that asks Face ID; Resume and Run now do not). The mock's "Next heartbeat in 8m" and "last run timed out" are not in the state; rows show "last heartbeat 2m ago".

## ADR-104: Factory home and Agents models belong to a company; Run viewer and Agent detail to their route

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-040)

**Decision.** `ScreenKey` gains `factoryHome` and `agents` (keyed by factory and company, kept while selected, closed when the company changes) and `agent(id)`, `run(id)` (closed when the route leaves the stack). Views start the shared refresh policy while visible (`refreshWhileVisible`); the Run viewer's 5 s interval while a run is live is the state's. Auto-scroll is the state's: the log toggle and a drag that leaves the bottom send `UserScrolled`/`JumpToLatest`. The cancelled Face ID result is shown as feedback on the screen (the confirm sheet closes when the intent is sent).

## ADR-105: The demo is honest about runs: Run now starts one, and a launch option breaks the reads

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-040)

**Decision.** `FakeAgentGateway.invokeHeartbeat` puts the agent to work and reports the run (`onRunStarted`); the demo wiring adds it to the run store. `DemoControls.failReads` makes the Factory, Agents and Run reads fail with a server error; launch options `-demo-server-error` and `-demo-open run:<id>|agent:<id>` expose them. The demo's Running-now data, events and log are the mock's.

## ADR-106: The functional iOS tests run as three parallel CI jobs by test class

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-040)

**Context.** The functional step took 40.5 minutes on the runner for the 2i-3 head (run 41542545380, 52 minutes in all, 60 minute timeout) and 2i-4 adds about 13 UI tests plus Swift Testing cases.

**Decision.** `scripts/ios-test.sh` takes `SHARD=1|2|3` with `TEST_SET=functional` (a fixed list of test classes per shard, and a check that fails the script if a UI test class is in no shard). `ios.yml` runs a matrix `ios (1)`, `ios (2)`, `ios (3)` and `ios (audits)`; lint and the Kotlin/Native tests run in shard 1 only; the audits job is `continue-on-error` (ADR-094). Every `uses:` stays SHA-pinned. Required status checks named `ios` must be renamed to the four matrix names (audits not required).

**Consequences.** Each shard repeats the setup and the XCFramework build (about 5 minutes) but the longest shard is about a third of the tests.

## ADR-107: The Inbox tab exposes its badge as its accessibility value; the demo refresh timer can be paused for tests

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui)

**Context.** On the CI runner (Xcode 26.6, iOS 26.5) the Inbox tab button's accessibility element had label "Inbox" and no value, although the badge was painted (screenshot: red "3") and the state held badge=3. SwiftUI's `.badge` is drawn by the tab bar but not part of the button's accessibility element, so VoiceOver users and UI tests could not read it. Separately, `testPullToRefreshMakesTheUpdatedLabelCurrent` read "Updated 0s ago" instead of "12s": the shared refresh policy refreshes the visible Inbox every 15 s, a slow runner start (19 s to first read) let the timer fire before the test looked, and the demo clock is frozen so the refreshed read is exactly "0s".

**Decision.** The count is set as the tab button's accessibility value, "N waiting" (catalog key `label.tabBadgeValue`, empty without a badge), a real accessibility improvement; the label stays "Inbox". A `.accessibilityValue` on the SwiftUI `tabItem` did not reach the button (CI run 41557971050 family: hierarchy unchanged), so a zero-size `UIViewControllerRepresentable` inside each tab finds the `UITabBarController` and sets `UITabBarItem.accessibilityValue` (`TabBarAccessibility`). UIKit rebuilds the bar's items without SwiftUI telling us (a detail screen hiding and showing the bar, a sheet closing) and a live button only picks a changed value up when its badge changes, so the helper also sets the values on the view controllers' items, re-assigns the badge text when it changes a value, and re-checks twice a second. UI tests keep reading the badge and now assert on that value. A launch option `-demo-no-auto-refresh` skips starting the Inbox refresh session (pull to refresh and returning to the screen still refresh) and that one test uses it, so its first read is deterministic. No assertion was weakened.

**Consequences.** The Shell test now requires the tab to expose the count instead of accepting its absence. The timer is still covered by the shared RefreshVisibleScreen tests.

## ADR-108: Work list and Issue are built on the existing holders; text input stays in the view

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-050)

**Context.** The shared holders for both screens existed. The demo answered a question without telling the Inbox feed and stamped a new comment with the epoch, so it sorted first in the thread.

**Decision.** Swift renders the state and forwards intents; the only shared edits are the demo ones above, test first. Search is sent on submit (no per-keystroke requests, no timers), the comment box and the cards' text fields keep their own text and send change intents, and a failed comment is put back in the box. Create-issue opens the new issue through `createdIssueId`. No Face ID is added: none of these actions is on the destructive list in AGENTS.md.

**Consequences.** Tab counts ("Runs 3") appear once a tab has been read, because the holder reads those tabs lazily; the mock shows them at once.

## ADR-109: The new Work and Issue audits are blocking-free but honest

**Status:** Accepted (2026-11-02, specs/archive/261102-phase-2i-iphone-ui, UI-050)

**Decision.** `WorkAuditUITests` asserts the audit returns no findings with plain assertions (no expected failure, no allow-list) and is listed in the audit set of `scripts/ios-test.sh` (non-blocking in CI, ADR-094). The new functional classes `WorkUITests` and `IssueUITests` join shard 3.


## ADR-110: The tab bar's accessibility values follow key-value observation, not a timer

**Status:** Accepted (2026-11-03, specs/archive/261102-phase-2i-iphone-ui, UI-060)

**Context.** ADR-107 set the Inbox badge's accessibility value on the `UITabBarItem`s. UIKit rebuilds those items at moments SwiftUI gives no signal for (a sheet closing, a detail screen's bar returning), so the first version re-checked twice a second with a `Timer`. AGENTS.md and the brief forbid polling timers in views.

**Decision.** The invisible `Applier` controller observes with KVO the bar's `items` and `isHidden`, the bar layer's `position` and the tab controller's `viewControllers`, and re-applies the values (idempotent) on each change, in addition to layout and appearance callbacks. The timer and the 0.6 s one-shot are gone.

**Consequences.** All 50 UI tests of the Inbox, Decision, Approval, Budget and Shell classes that read the badge pass locally on iOS 27.2. Whether UIKit posts these KVO changes on every rebuild of the iOS 26.5 runtime used by CI was verified only by the CI run (see implementation-notes.md); if a rebuild bypasses KVO the badge test is the detector.

## ADR-111: Factory home and Agents audit findings are reported honestly, not expected

**Status:** Accepted (2026-11-03, specs/archive/261102-phase-2i-iphone-ui, UI-060)

**Context.** Three audits (Factory home, offline Factory home, Agents) used a non-strict `XCTExpectFailure` for "Dynamic Type font sizes are partially unsupported" and "Text clipped" on text in the lower part of the screen (y about 575 to 775 of 874 pt).

**Decision.** No `XCTExpectFailure` remains anywhere (the last dead helper that called it was deleted in the review-fix round, C4). The audits assert an empty finding list like the others and stay in the non-blocking audit set (ADR-094), so CI reports their findings instead of hiding them. Investigated without effect: making the tile and row buttons single elements (`accessibilityElement(.ignore)` on the button, the pattern the Work rows use and which pass), and `contentMargins(.bottom)` for the floating tab bar. The findings track screen position (a tile row is clean at y 540 and flagged at y 620), not a font: the Work list, built the same way, passes. Cause not found.

**Consequences.** The three tests fail locally and in the audit job until the cause is found; they do not block the merge. Findings: Factory home (2 to 4 texts of the Approvals and Budget incidents tiles), offline Factory home (all tiles), Agents (the CTO row, Pause, section header).

## ADR-112: The live seam stays a provider; the real graph is plugged in when Phase 1b merges

**Status:** Accepted (2026-11-03, specs/archive/261102-phase-2i-iphone-ui, G5)

**Decision.** `createLiveGraph`, `openLiveGraph` and `LivePlatform` exist only on `feat/phase-1b-adapters` (PR #7), so this branch does not call them (the Kotlin would not compile into the framework). `LiveGraphProvider` already has the Swift half (pairing browser, debug-only insecure local) and its `makeGraph()` is the single line to change. The exact steps are the "Merge checklist" in implementation-notes.md.

**Consequences.** A launch without `-demo` used to show the first-run flow on empty fakes with Face ID that always approved; ADR-114 replaced that with a "not available in this build" screen.

## ADR-113: Hit targets and reduce motion are checked by UI test and environment, not by a shared rule

**Status:** Accepted (2026-11-03, specs/archive/261102-phase-2i-iphone-ui, UI-060)

**Decision.** `HitTargetUITests` reads every button, switch and text field on each tab root and asserts at least 43.9 x 43.9 pt (rounding tolerance). Explicit animations read `accessibilityReduceMotion`. These are presentation rules with no shared state, so no Kotlin changed.

**Consequences.** The tests were characterisation tests: they passed on first run, no red step. The review-fix round (ADR-118) made the test fail when it measures nothing, and say which controls it cannot see.

## ADR-114: A launch whose real graph cannot open says so and runs nothing; demo flags exist in Debug builds only

**Status:** Accepted (2026-11-03, specs/archive/261102-phase-2i-iphone-ui, review A3, A7); amended on the 1b merge (ADR-120).

**Context.** Before Phase 1b the iPhone app could only build the graph on fakes. `LiveGraphProvider.isAvailable` was never read, so a Release launch ran first-run pairing on `FakeBiometrics` (always approves). All `-demo*` flags and the diagnostics overlay worked in every build.

**Decision.** The rule lives in shared code: `StartupNotice.forLaunch(demo, liveOpened)` returns `LiveGraphUnavailable` for a live launch whose graph did not open. `AppEnvironment` asks it once; on failure it holds a closed stand-in graph (no holder, refresh or biometric port runs) and `RootView` shows the notice with Try again. `LaunchConfiguration` takes `allowsDemoControls`, true only when `DEBUG` is defined, and ignores every `-demo*` argument otherwise; `DiagnosticsOverlay` and the first-run "Try the demo factory" button are Debug only.

**Consequences.** A Release build can reach only the real graph or the notice. The stand-in graph is still constructed (the environment needs a graph object) and closed before any holder runs; no UI path reaches it.

## ADR-115: Typed money is read by one strict shared parser; the app passes only the decimal separator and the currency's digits

**Status:** Accepted (2026-11-03, specs/archive/261102-phase-2i-iphone-ui, review A4, A11, B-19, B-06)

**Context.** Swift parsed the budget amount: it dropped every character that was not a digit, the locale's decimal separator or a minus sign, so "250.50" typed in a comma-decimal locale became 25,050.00 (100 times too much), and the result was passed to a Face ID prompt that does not show it.

**Decision.** `MoneyInput.parse(text, decimalSeparator, fractionDigits)` in `shared/presentation` is the only reader. The other of "." and "," is the grouping mark and is accepted only in groups of exactly three; a mark in the wrong place, two decimal marks, too many decimals, more than 12 whole digits, zero or a negative amount is refused with an `InvalidKey` (`AmountNotNumeric`, `AmountTooManyDecimals`, `AmountTooLarge`, `AmountNotPositive`), never dropped or rounded. The Budget incident and Agent detail holders take the text (`AmountTyped`, `BudgetAmountTyped`), keep the parsed amount and the issue in state, and Agent detail reports a saved budget with `budgetSavedCount` so the form closes only on success. The raise button shows the amount that will be sent.

**Consequences.** An ambiguous input ("250,50" in a point-decimal locale) is refused with a message instead of guessed. The Face ID reason string does not carry the amount: the `Biometrics` port takes a fixed reason; the amount is on the button the person taps.

## ADR-116: Relative times are a function of a clock the view receives, not a value computed once

**Status:** Accepted (2026-11-03, specs/archive/261102-phase-2i-iphone-ui, review A1, B-01, B-09)

**Context.** "Updated 12s ago", card ages and a run's elapsed time were computed once in `body`, so an offline or final screen showed the age of its last render.

**Decision.** `TickingReference` gives each age-showing view a "now": the pinned clock when there is one (the demo's frozen clock, previews, tests), otherwise the date of a `TimelineView(.periodic)` (15 s for ages, 1 s for a running run). The views hold no timer; the schedule is display only. `RelativeAge` uses `Duration.UnitsFormatStyle`: narrow for the compact text, wide for VoiceOver.

**Consequences.** Offline and final screens keep ageing. Unit tests move the clock (`ClockDrivenLabelTests`); no UI test can, because the demo clock is pinned.

## ADR-117: A screen left while its mutation is in flight is closed after the outcome is recorded

**Status:** Accepted (2026-11-03, specs/archive/261102-phase-2i-iphone-ui, review A6)

**Context.** `ModelStore` closed a pushed screen's model when its route left the stack, and closing cancels the holder's coroutines. A back swipe during a request or a Face ID prompt dropped the outcome and the reconcile read.

**Decision.** Each holder says what counts as work (`StateHolderProtocol.isWorking`: submitting, busy, cancelling). `ModelStore.retain` keeps a working model, follows its flow until it is idle, then closes it unless its key came back; `closeAll` (shutdown) still closes at once.

**Consequences.** The back button is not blocked. A model can outlive its screen by one request; a Face ID prompt left unanswered keeps it until the prompt ends.

## ADR-118: Audits set aside only the system tab bar's buttons, and say how many

**Status:** Accepted (2026-11-03, specs/archive/261102-phase-2i-iphone-ui, review C1 to C4)

**Context.** The audit helpers dropped any finding whose element reached below the top of the tab bar or of the app's own fixed action bar, which included the bar's own buttons. `HitTargetUITests` passed when it measured nothing.

**Decision.** One helper, `auditScreen`, returns every finding except those on a button of the system tab bar (matched by identifier and label, read when the audit runs); those are counted, printed and attached as "system-owned". Content under the bar and the app's own bars are findings. The dead `XCTExpectFailure` helper is deleted. `HitTargetUITests` waits for the screen, uses the bar position from its buttons when its own frame is infinite, fails when fewer controls than a per-tab minimum were measured, and prints how many controls it could not see (below the fold or under the bars).

**Consequences.** Audit findings that were hidden are now reported in the non-blocking audit set; their counts are in implementation-notes.md. `ios.yml` lets only the audit step fail without failing its leg, and fails the leg if no audit test ran.

## ADR-119: What the accessibility audit reports on scrolling screens, and what was changed because of it

**Status:** Accepted (2026-11-03, specs/archive/261102-phase-2i-iphone-ui, round 3). The audit job is still non-blocking (ADR-094).

**Context.** After ADR-118 made the audits honest, 16 of 24 audit tests failed. A bisect on the Agents screen found two causes.

**Evidence and decisions.**
1. *Lazy containers.* "Dynamic Type partially unsupported" and "Text clipped" on mid-screen text disappeared when `LazyVStack` and `LazyVGrid` were replaced by `VStack` and non-lazy rows (Agents 7 findings to 3, Factory home 10 to 5, offline 21 to 7). A lazy container realises and drops views as the audit changes the text size and the audit reads that as unsupported scaling. Agents, Work and the Factory home tiles are now eager (their lists are short or paged).
2. *Text near or under the bottom bar.* A throwaway screen of 30 plain `Text` rows in a `ScrollView`, no custom styling, fails the audit with "Contrast failed" on the rows whose frames lie at y 796 to 868 on an 874 pt screen. Eight white cards under the same bar pass only while the cards that reach that band do not exist; with a page background behind them, the one card at y 835 fails. A solid tab bar appearance (`UITabBarAppearance` opaque), `toolbarBackground(.visible)`, hiding the tab bar, `scrollEdgeEffectHidden`, `clipped()` and bottom padding all left the findings unchanged, and combining a card into one accessibility element does not hide its text from the audit (11 findings instead of 7). The audit measures every realised text view, including ones clipped by a scroll view or covered by a bar.
3. *Elements with no element* (Inbox and Shell, frame zero): the issue carries no element at all (`Element:(null)`), so it cannot be traced; the test prints the full issue and the hierarchy.

**Consequences.** What remains (local, Large text): Approval 1 and offline 1, Factories 4, Inbox 3 and offline 9, Manage 5, Agents 3, Factory home 5 and offline 7, Run offline 1, Settings 2, Shell 3, Issue 2 and offline 2, Work 3 and offline 2. Almost all are text in the bottom band or clipped by a scroll view, for which the evidence in 2 applies. They are not hidden, filtered or allow-listed; the tests fail. Clearing them needs either screens whose content ends above the bottom band when audited (a scenario change, not a fix) or giving up an audit-wide empty assertion; that choice is the owner's.

## ADR-120: The iPhone app opens the real graph through `LiveIosGraph.openLiveGraph` and a throwing provider

**Status:** Accepted (2026-11-03, specs/archive/261102-phase-2i-iphone-ui, 1b integration)

**Context.** Phase 1b (merged) provides `LiveIosGraph.openLiveGraph(allowInsecureLocal, pairingBrowser, settings)` (throws instead of aborting), `PairingBrowsers.onMainThread(show, dismiss)` and new outcomes: `AuthDeclined`, `KeyInvalidated`, `ServerKeyOutcome.Declined`, and the "here only" sign-out and removal when the key cannot be read.

**Decision.** `AppGraphProvider.makeGraph()` throws; `LiveGraphProvider` passes `LiveSettings` (the board-key Keychain service, `Application Support/Paperclip`, the localised Face ID reasons) and a main-thread pairing browser that presents the approval page with `ASWebAuthSessionPairingBrowser` and closes it on cancel. The Edit factory sheets and the Manage removal sheet have "here only" variants that say the key stays live on the server. New errors and outcomes have catalog entries and are covered by the completeness tests.

**Consequences.** Not verified from the app against a live Paperclip server; the adapters are covered by Kotlin and contract tests and the launch path by `LiveGraphLaunchTests` and `AppEnvironmentTests` (real graph on a temporary folder).

## ADR-121: The Android app's unit tests run on JUnit 4 + Robolectric, pure tests on JUnit 5 through the Vintage engine

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T0.8)

**Context.** AGENTS.md lists JUnit 5, Compose UI test and Roborazzi for Android UI tests. Compose UI test rules (`createComposeRule`), Robolectric and Roborazzi are JUnit 4 (`@RunWith(AndroidJUnit4::class)`), and instrumented tests (`AndroidJUnitRunner`) are JUnit 4 by nature. Pure tests (labels, presentation helpers, the route table, key-to-string completeness) gain nothing from Robolectric and read better in JUnit 5.

**Decision.** `androidApp/src/test` has one Gradle test task on the JUnit Platform (`unitTests.all { useJUnitPlatform() }`). The pinned platform (catalog `junit` 6.1.3) has a matching Vintage engine (`org.junit.vintage:junit-vintage-engine:6.1.3`, catalog `junit-vintage-engine`), so JUnit 4 + Robolectric tests (Compose, Roborazzi) and JUnit 5 tests execute in the same `testDebugUnitTest` run; verified with `PureJvmSmokeTest` (JUnit 5), `ComposeSmokeTest` and `RoborazziSmokeTest` (JUnit 4 + Robolectric). `androidTest` stays JUnit 4. `shared/**` tests stay on kotlin.test / JUnit 5 as AGENTS.md says; the AGENTS.md "Where tests live" Android UI row is amended in T9.1.

**Consequences.** Robolectric 4.17 does not ship API 37 (the app's `compileSdk`/`targetSdk`), so `src/test/resources/robolectric.properties` pins `sdk=36` for unit tests only. On JDK 21 Robolectric needs `--add-opens=java.base/java.io=ALL-UNNAMED` and `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` on the test JVM (set in `androidApp/build.gradle.kts`). The Compose and View libraries must be app dependencies, not test-only, or their `R` classes are missing at test time. Roborazzi baselines live in `androidApp/src/test/screenshots` (`recordRoborazziDebug`, `verifyRoborazziDebug`; verify fails when a baseline is missing).

## ADR-122: Android build foundations: built-in Kotlin, one shared SDK constant, opt-in device tests, merged-manifest rules

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T0.1 to T0.7)

**Context.** `:androidApp` is the first `com.android.application` module; the shared modules use the KMP library plugin. The Android SDK values, the instrumented-test setup and the manifest security posture each need one place to live.

**Decision.** (1) AGP 9.5.0 compiles the app's Kotlin with its built-in Kotlin support: the module applies `com.android.application` and the Compose compiler plugin and no `org.jetbrains.kotlin.android`. (2) `PafmAndroid` in `build-logic` holds `compileSdk`, `minSdk` and the namespace root for both `pafm.kmp-library` and `pafm.android-app`. (3) `withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }` is enabled only in modules that have `src/androidDeviceTest`; `check` never depends on a `connected*` task. Device test methods use camelCase names because DEX 039 rejects spaces. (4) `AndroidManifestRulesTest` (Konsist) checks the merged debug and release manifests (`processDebugManifest`, `processReleaseManifest` are `konsistTest` inputs): no backup, no cleartext, three permissions, only the launcher exported, debug cleartext for `localhost` and `10.0.2.2` only. (5) Android Lint runs with `warningsAsErrors` and `HardcodedText` as an error; version-currency checks are ignored in `androidApp/lint.xml` because Dependabot owns them.

**Consequences.** A library that adds a permission or an exported component (for example `profileinstaller` in T8.6, `biometric` in T1.3) fails the manifest rule until the rule's allow list is changed in review (T8.7). `konsistTest` now runs two manifest merges before it starts.

## ADR-123: Android device services: an AES-GCM Keystore store with file records, `AuthHost` for the prompt, no `security-crypto`

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T1.1 to T1.7)

**Context.** Phase 2a needs Android actuals for `SecretStore`, `Biometrics` and `Connectivity`. The iOS store leans on the Keychain, which holds items, prompts and survives the app. Android has no such item store: the Keystore holds keys, not values, and `BiometricPrompt` needs a resumed `FragmentActivity` while the port is called from a use case.

**Decision.**
1. *Store.* `KeystoreSecretStore` keeps one AES-256-GCM record per secret path in `noBackupFilesDir/secrets` (magic, protection flag, IV, ciphertext with tag); the path is the GCM associated data, so a record renamed or copied to another name does not decrypt. The key is a non-exportable Keystore key, one per path and mode (`:u` unprotected, `:p` user-authentication), generated in StrongBox when available and in the TEE otherwise (`StrongBoxUnavailableException` is the probe). Writes go to a temporary file, are synced and renamed atomically, all store calls run under one mutex. File names are the percent-encoded path, so `namespaces()` lists factories without touching a `Cipher` (no authentication prompt). All Keystore calls are behind an internal `KeystoreBackend` (fake in `androidHostTest`, `AndroidKeystoreBackend` on the emulator).
2. *Read mapping.* Found; no key, wrong tag, truncated or foreign record = `Missing` (never plaintext, never `Found`); `KeyPermanentlyInvalidatedException` or `UnrecoverableKeyException` from loading the key (R-010) = `Invalidated`, any other `GeneralSecurityException` while loading it = `Denied`; a refused prompt (no cipher authorised), lockout, `UserNotAuthenticatedException` at the last step or any other Keystore failure = `Denied` (ADR-139) (changes nothing, never reads as missing). Failed writes are `Failed` and leave the old record. A protected write that the person refuses is `Failed`; there is no fallback to an unprotected key.
3. *Protected keys.* **Corrected by ADR-139 (R-001): the first version used a 5 second window, which was wrong and is withdrawn.** They use `setUserAuthenticationRequired(true)` with `setUserAuthenticationParameters(0, BIOMETRIC_STRONG | DEVICE_CREDENTIAL)` (per use) and `setInvalidatedByBiometricEnrollment(true)`; the store asks an injected `authenticate` function to authorise one initialised `Cipher` through `BiometricPrompt.CryptoObject`. Originally proposed as a 5 second window, which any recent unlock or other app's authentication could satisfy (the claim that it "opens only through a prompt the store itself asked for" was false). They need API 30 and a secure lock screen; below that sealing fails closed (the non-deprecated API does not exist on 28 and 29, and the project does not suppress deprecation warnings).
4. *Prompt.* `AuthHost.current()` returns the resumed `FragmentActivity` the main activity registers; `null` (background) means no prompt and `Unavailable`. `BiometricPromptBiometrics` asks `canAuthenticate` first, uses strong biometrics or device credential (weak plus credential on API 28 and 29, where the library rejects strong plus credential), maps the `ERROR_*` codes to the five `BiometricResult` values and fails unknown codes closed. A new prompt is shown on every call. `ReasonTexts` moved to common code.
5. *Why not `androidx.security:security-crypto`.* It is deprecated, wraps the same Keystore AES key, adds a preferences file we do not want (`SharedPreferences` is banned for secrets), and hides the failure modes (invalidation, undecryptable files) that this port must tell apart.
6. *Connectivity.* `registerDefaultNetworkCallback` into a `StateFlow<Boolean>`; online means a default network that declares internet access, validated or not (DEC-2, ADR-142). The first value is read before the callback; a refused registration is offline; `close()` unregisters once.
7. *Findings recorded.* (a) On the Pixel 9 Pro emulator (API 37) removing the lock screen deletes user-authentication keys instead of invalidating them, so that path reads `Missing`; real `KeyPermanentlyInvalidatedException` after a new fingerprint could not be produced without a person at the screen, so its mapping is tested on the exception type and the end-to-end path is a manual check (T8.9). (b) The Android SQLite framework deletes an unreadable database and silently opens an empty one, which skipped the recreation marker and the secret sweep; `onCorruption` is now a no-op so `openRecovering` decides. (c) `androidx.biometric` adds `USE_FINGERPRINT`, removed in the app manifest (minSdk 28 never needs it); Compose's `profileinstaller` receiver is removed too and the debug-only Compose test host activity is allowed in the debug manifest rule.

**Consequences.** Secrets are never in SQLDelight, preferences or plaintext files (emulator test scans the app data folder, the database included). A restored record without its key reads as signed out. A device without a secure lock screen cannot store a protected secret. The 5 s authorization window of the first version was withdrawn in ADR-139 (per-use `CryptoObject`). `AuthHost` registration and the `authenticate` wiring are done in 2a-2 and 3.6.

## ADR-124: The Android live graph is opened through `AndroidLiveGraph.openLiveGraph`, with a process-wide `AndroidAuthHost`

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T2.1 to T2.4)

**Context.** The shared graph needs a `LivePlatform`, but Android's database and Keystore need a `Context` and the prompt needs the resumed activity, so `PaperclipCore.createLiveGraph(allowInsecureLocal, pairingBrowser)` cannot serve Android. The entry point the PRD fixes has no parameter for the activity.

**Decision.**
1. `AndroidLiveGraph.openLiveGraph(context, allowInsecureLocal, pairingBrowser, settings)` returns the sealed `AndroidGraphOpen` (`Opened(graph)` or `Unavailable(cause)`), the Android twin of `LiveIosGraph.openLiveGraph` (ADR-084, ADR-120). Any failure while building the platform or the graph is `Unavailable`; the graph builder already closes the HTTP client, the database and the platform on failure and `AndroidLivePlatform` opens the database before it registers the network monitor, and if the network monitor cannot be created (a missing `ACCESS_NETWORK_STATE`, for instance) the platform closes the database it already opened before it rethrows, so nothing stays open.
2. `AndroidLivePlatform` stays internal (database and HTTP types never reach the app). Its real parts are built by `androidLivePlatform(context, settings, authHost)`; the constructor takes the secret store, biometrics and two opener functions so host tests can drive failure paths without Android classes.
3. The activity registry is `AndroidAuthHost`, a public object with `register(activity)` / `unregister(activity)` over a weak `ResumedReference`; the main activity calls them while resumed (T3.6). A late `unregister` of an older activity does not clear a newer one.
4. The Keystore store's prompt callback is `BiometricPromptBiometrics.authenticateWithText`, so every prompt of the app (unlock, destructive actions, protected key reads and writes) is shown and mapped to a `BiometricResult` by one class and fails closed the same way. The public `KeystoreSecretStore` constructor gained a `keyAliasPrefix` (default `pafm.secret/`), exposed as `AndroidLiveSettings.keyAliasPrefix`.
5. `PaperclipCore.createLiveGraph(allowInsecureLocal, pairingBrowser)` keeps refusing on Android and its message names `AndroidLiveGraph.openLiveGraph`.

**Consequences.** A locked database or an unusable Keystore is a screen with Try again, not a crash loop. The success path of the platform needs the Android SQLite classes, so it is proven on the emulator (`AndroidLiveGraphDeviceTest`), the failure paths on the host. The registry is process-global state; with one `MainActivity` that is the right scope, and a second prompt host would need a different design.

## ADR-125: The Android shell: one process-wide environment owns the graph and the long-lived holders; screens own theirs through `HolderViewModel`

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T3.1 to T3.9)

**Context.** The iPhone `AppEnvironment` owns the graph and the app, first-run and Inbox models; screen models live in a store keyed by route. Android needs the same split, survive rotation, and never fall back to fakes when the live graph cannot open.

**Decision.**
1. `PaperclipApplication` owns one `AppEnvironment`, created lazily by the first activity (the launch intent is visible there; nothing is opened in `Application.onCreate`). Its `GraphSession` owns the graph and the app, first-run and Inbox holders (the Inbox follows the selected factory and company and feeds the tab badge with the server count). An unavailable live graph is a session with no graph and no holders and `StartupNotice.LiveGraphUnavailable`; Try again re-opens once per tap. This deviates from the iPhone stand-in graph: a null graph cannot run anything, so there is nothing to close and nothing that could be faked.
2. Screen holders are wrapped in `ScreenHolder` and owned by `HolderViewModel`, which closes the holder in `onCleared` (after the outcome when a mutation is in flight, ADR-117); rotation keeps the view model.
3. Demo launch extras (`demo`, `demo_first_run`, `demo_biometrics`) are honoured only when `BuildConfig.DEBUG`; "Try the demo factory" is offered only then.
4. Compose renders shared state and sends intents. The device-owner prompt is never shown from a screen; `LockScreen` sends `UnlockRequested` once per engagement and only while the activity is resumed, after `AuthHostRegistration` has registered it. `FLAG_SECURE` is set while the lock or the pairing step shows; the recents thumbnail is hidden always (API 33 setting, secure while paused below).
5. The pairing page opens in a Custom Tab (plain browser fallback, none ends the attempt and cancels the pairing); the shared wait ends when the person comes back, because a process left behind a browser may be frozen.
6. Saved state: `rememberSaveable` and the navigation controllers' saved state (the same registry as `SavedStateHandle`), never anything secret (Konsist rule).

**Consequences.** One graph per process; rotation and recreation reuse it. A Custom Tab cannot be closed from the app. Spotless needs the Composable naming exception repeated in the Spotless block, and detekt ignores composables for naming and parameter and function counts.

## ADR-126: Diagnostics are typed values kept by the app, redacted by construction, copied and never sent

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T4.5)

**Context.** AND-018 asks for a local diagnostics screen with a copyable report that holds no URL, host, token, key id, company or user data, and for one logging wrapper. The shared core offers no diagnostics source.

**Decision.** `DiagnosticsReport` holds only enums and the app's own checked version name; fields the app cannot observe are `Recorded.NotRecorded`. A failed graph open is classified by the types in its cause chain (never its text). The session feeds the recorder from the shared app and first-run states and the edit screen from its own. "Copy report" writes text built from enum names only to the clipboard. `AppLog` is off in release and rejects secrets, addresses, tokens and ids in debug; Konsist keeps `android.util.Log` inside it.

**Consequences.** Server compatibility, and Keystore errors that no screen reports, show "Not recorded" until the shared core exposes them (reported as a gap). `StorageUnavailableException` is matched by name because the app cannot import the persistence module.

## ADR-127: Reorder by long-press drag with accessibility moves; a re-pair from Edit is secure and cancellable like first run

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T4.2, T4.3)

**Decision.** `ReorderableColumn` follows the finger locally and sends one `Reordered(allIds)` on drop; one-step moves are TalkBack custom actions. `GraphSession.cancelPairingAttempts` ends every waiting pairing (first run and edit) when the approval page cannot be shown, and `ReportPairingWait` makes the window secure while a re-pair waits, so no baseline or screenshot shows it.

**Consequences.** The shared core remains the authority on whether an order is accepted; the next list it publishes replaces the local one.


## ADR-128: The Inbox renders the session's holder, a decision carries what its card knew on its route, and a sheet owns its holder through a sheet-scoped store

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T5.1 to T5.4)

**Context.** The iPhone Inbox model belongs to the app environment: it feeds the tab badge and owns the refresh session, so the Inbox screen only renders it. The Decision endpoint has neither the agent name nor the decide-by (ADR-018), and Snooze is offered only when the screen was opened from an Inbox card (ADR-025); the iPhone app keeps those hints in an in-memory table. A budget incident is a sheet, not a back stack entry, so a `HolderViewModel` created inside it would be scoped to the screen under it and outlive the sheet.

**Decision.**
1. `InboxScreen` collects `GraphSession.inbox` and renders that holder. It never closes it and never starts a second refresh session (the session already starts one when the selection changes and stops it when it changes again). It sends one `Refresh` when the screen returns to the foreground (not on first composition) and when the budget sheet closes, because the incident may have been resolved in it. Detail screens (Decision, Approval, Budget) start `graph.refresh.start(holder.refreshTarget)` when they start and stop it when they stop (`RefreshWhileVisible`), unless the session has refresh off. `ScreenHolder` gained `refreshTarget` and an in-flight check for the three holders (ADR-117).
2. `AppRoute.Decision(id, originAgentName, decideBy, dismissalKey, identifier)` carries the hints, with `decideBy` written back in the shared parser's words (`today`, `this_week`, `whenever`, an ISO date). They are plain strings, they survive process death with the route (the in-memory table would lose Snooze), and a route opened another way has a null `dismissalKey`, so no Snooze.
3. `WithSheetHolders` gives a sheet its own `ViewModelStore` and clears it when the sheet leaves the composition, so the budget holder follows the same `HolderViewModel` rule as a screen (closed after the outcome if a mutation is in flight). Which incident is open is `rememberSaveable` in the Inbox, so it survives rotation and the sheet re-reads.
4. A destructive step is never taken from a composable: Reject (inline and on the approval), a destructive Decision option and Raise and resume send their intent and the shared use case asks `Biometrics`; a declined prompt comes back as feedback and nothing is called. The note sheet is open exactly while the state carries a prompt.

**Consequences.** The Inbox tab and the badge share one holder, as on iPhone. A route is longer than "ids only" but still plain data. The demo graph has no viewer, so viewer variants are rendered from states built in the test (the states are what the shared holders produce for that role; their rules are tested in `shared/presentation`). `DemoControls` has no decide, approve or resolve counters, so a declined prompt is proven by the prompt count, the unchanged screen and the absence of a result, not by call counts (reported as a gap).

## ADR-129: Swipes only send intents and TalkBack has the same actions; the Undo offer, the payloads and the money are plain renderings of shared state

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T5.1 to T5.4)

**Context.** An Inbox card can be snoozed or dismissed with a five-second Undo. On Android a `SwipeToDismissBox` can leave a row displaced after the gesture, a swipe is not available to TalkBack, and a Material `Snackbar` has its own timer that would disagree with the shared one.

**Decision.** Swiping toward the end opens the snooze presets, toward the start dismisses; `confirmValueChange` sends the intent and returns false, so a card never stays swiped (it goes, or comes back after Undo, with the shared state). Snooze and Dismiss are also custom accessibility actions of the card's open target. Swipes and actions are off while the shared state says actions are unavailable (offline, viewer). The Undo offer is a bar shown exactly while `state.undo` is set; the five seconds are the holder's. Approval payloads are plain text (the iPhone app renders inline Markdown; no Markdown library is added for it). Money uses `NumberFormat` with the currency's own digits and drops zero cents; a typed amount is passed to the holder with the locale's decimal separator and the currency's digits and read by shared code. An input's `maxLength` is shown as a counter and never enforced by the field, so the shared `InputTooLong` rule stays the only one. Only `https` addresses are opened for an unknown kind.

**Consequences.** Android renders payload text without emphasis. A long press and drag on a card is not used, so it does not clash with scrolling. The Undo bar is not a Material `Snackbar`, so it does not queue or time itself.

## ADR-130: The operations screens are tab roots and pushed routes over stateless contents; the log follows its end by scroll position; the 5 second refresh is the holder's and is proven in virtual time

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T6.1 to T6.3)

**Context.** Factory home and Agents are tab roots that can be drawn before the app holder has a selected factory and company, so the throwing `selection()` used by pushed screens does not fit. A Factory home tile must show another tab, but a screen only has its tab's navigator. The Run viewer's log is long and grows while the run is live: it has to follow its end without a programmatic scroll being mistaken for the person scrolling away. The shared holders decide the refresh interval (5 seconds while a run is live, none once it has finished) and run on `Dispatchers.Default`, so a test cannot inject a dispatcher into them. The iPhone app replaces Run now with View run on a row whose agent has a live run; the holders still offer Run now there.

**Decision.**
1. `FactoryHomeScreen` and `AgentsScreen` read the selection reactively (`rememberSelection`, a spinner while it is null) and key their `HolderViewModel` by session, factory and company. `AgentDetailScreen` and `RunViewerScreen` are routes (`AppRoute.Agent`, `AppRoute.Run`) and key by the route's id. All four call `RefreshWhileVisible`; none keeps a timer. Every screen is a thin function over a stateless `*Content(state, send, ...)`, so role, offline and unknown-outcome variants are tested by giving the content a state, as in 2a-5.
2. `TabNavigator` gains `select(tab)` with an empty default (existing navigators and tests are unchanged); `TabShell` supplies it. Factory home's tiles use it.
3. The log panel has a fixed height and its own scroll. It follows the end with `snapshotFlow { maxValue }` while the state's `autoScroll` is on. A drag is recognised by `DragInteraction.Start` on the scroll state's interaction source; when the scroll settles after one, the holder gets `UserScrolled(atBottom)` (the end is within 4 pixels). A scroll made by code never raises a drag interaction, so it never turns following off. The rule that following stops is the holder's.
4. The refresh interval tests and main PRD acceptance criterion 8 run the shared `RefreshVisibleScreen` policy in a `TestScope` (virtual time) against the real holder from the demo graph, with a counting wrapper around the holder's `refreshTarget`. The holder's own work still happens on real threads, so the test waits for it in real time and asserts that no virtual time passed between the cancel tap and the run reading Cancelled (well inside one 5 second cycle), that one more cycle later it is still Cancelled, and that the timer then stops (no refresh in 60 further virtual seconds).
5. `AgentRowUi.visibleActions()` keeps the iPhone parity rule (View run in place of Run now while a live run exists) in the app, unit-tested, until it moves into the holder (reported as a shared-core gap). Set monthly budget is a sheet shown only while the state says `canSetBudget`. **Corrected by ADR-141 (R-004): a raise or zero now asks the device owner, and the Save button carries the lock glyph while `saveBudgetNeedsDeviceAuth` says so; a decrease still asks nothing.**

**Consequences.** Tab roots survive a late selection. The tile navigation needs no new shell concept. The log follows its end without a timer or a flag of our own and does not fight the person's finger. Criterion 8 as first written was proven only for a cancel made from the viewer, whose own response sets the run to Cancelled at once, so it could not show the policy discovering a cancel made elsewhere. **Corrected by R-020 (specs/archive/261104-phase-2a-android-ui-auto-review): `FakeRunGateway.cancelElsewhere` and `DemoControls.cancelRunElsewhere` change the run on the server side only, and `RunRefreshTest` shows the run still Running until the 5 second timer fires and Cancelled after it; the test fails when the live interval is changed to 0.** The timer runs in virtual time; the holder's own work (`Dispatchers.Default`) is awaited in real time. The one view-level rule (View run for Run now) can drift from the iPhone's if the holders change; the unit test names it. Agents has no `isRefreshing` in its state, so pull to refresh shows no spinner (the indicator hides at once; the list refreshes). The factory switcher line of the iPhone screens is not ported (the Factories tab switches factory and company), as in 2a-5.


## ADR-131: Work and Issue are a tab root and a pushed route over stateless contents; typed text stays in the screen; the Files tab lists but cannot open; the web app opens https only

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T7.1 to T7.3)

**Context.** The shared holders for the Work list and Issue (filters, search, paging, create, comment, status, assignee, subtasks, the five interaction forms with live validation) already exist and the iPhone renders them (ADR-108). The Android screens must hold no rule of their own. The holders render on `Dispatchers.Default`, so a text field bound to their state drops characters. The brief asks for attachments to open through the system viewer using the shared ranged read and for unsupported request kinds to open the instance in the web app on `https` only.

**Decision.**
1. `WorkListScreen` is the Work tab root and `IssueScreen` the `AppRoute.Issue` destination, each over a stateless content (`WorkListContent`, `IssueContent(state, send, ...)`), so viewer, offline, failed and unknown-outcome variants are tested by giving the content a state, as in 2a-5 and 2a-6. The Work holder is keyed by session, factory and company; the Issue holder by the route's id. `AppTabs` no longer has a placeholder, and both `when` blocks are exhaustive.
2. Text the person types stays in `rememberSaveable` fields and only change intents reach the holder. The comment box (and its reopen and interrupt switches) is cleared only when `commentSentCount` differs from the value at first composition; the New issue and subtask sheets close on `createdIssueId` (acknowledged, then the issue is opened) and `subtaskCreatedCount`. A refusal or a failure therefore keeps what was typed, and the banner shows the reason. While a sheet is open the outcome is shown in it, not also on the page.
3. Interaction cards carry the card id in every tag (`interaction-<id>-primary`), so two cards never share one. The card disables the main and reject buttons from the state's `submitIssue` and `rejectIssue` and shows the shared `InvalidKey` text; it computes nothing.
4. `WebOpener` is the one door for a web address from the shared state: it passes `acceptablePage(url, allowsInsecureLocal = false)` (https only, a host required, no other scheme and not `http://localhost`) and only then asks a Custom Tab, else the browser. No header, extra or token is sent; a refused or unopenable address is said on the card.
5. The Files tab lists name, type and size and says files cannot be opened yet. `IssueGatewayAdapter.attachmentContent` is adapter-only (ADR-064) and no port reaches it; importing it from the app would break the layering that Konsist enforces, and adding a port is a shared change outside this milestone. It is reported as a shared-core gap rather than worked around.
6. A row of the Work list is one clickable card whose children are cleared and replaced by one sentence for TalkBack; the same pattern is used where nested merge boundaries (status pills, selectable choices) would otherwise hide text from the parent.
7. Tests that touch a sheet wait until its node is displayed (`awaitDisplayed`): a sheet slides in while the holders answer on other threads, so it is not on screen at the moment the tap returns. Three older tests asserted it immediately and failed intermittently in the full run; they now wait too.

**Consequences.** The Work list and Issue need no Android business rule; the comment and answer flows keep the person's words through every failure. Files cannot be opened from Android until the shared core has a port (the iPhone has the same gap and the same note). The only app-side lookups are colours (`IssueStatusUi.tone()`, `IssuePriorityUi.tone()`), the chip order (`IssueFilterOrder`) and the "https only" door. The Issue screen does not render inline markdown (the iPhone does): comment and plan text is shown as written.

## ADR-132: The release build is R8-minified and signed from the environment; the demo entry exists only in a separate `releaseDemo` type

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T8.5)

**Context.** Release was unminified and unsigned, so it could not be installed. The PRD asks for R8, `Log` stripped, keep rules in `androidApp/proguard-rules.pro`, `versionCode` from the commit count, `versionName` from the catalog, and a release key that is a CI secret. It also asks for the release APK to be launched in demo mode, while a shipping release deliberately ignores demo launch extras and hides "Try the demo factory" (a shipped app must not be steerable onto fakes).

**Decision.**
1. `release` sets `isMinifyEnabled` and `isShrinkResources` with `proguard-android-optimize.txt` and the app's rules. The only rule so far strips `android.util.Log` (the PRD's requirement). No keep rule was needed: R8 full mode produced no missing-class errors and the launched APK ran Inbox, Decision and every tab without a crash or class-loading error in logcat, so serialization, SQLDelight, Koin and Ktor keep rules come from the libraries. A rule is added only for a reproduced failure, and `AndroidReleaseBuildRulesTest` forbids blanket keeps.
2. Signing reads `PAFM_UPLOAD_STORE_FILE`, `PAFM_UPLOAD_STORE_PASSWORD`, `PAFM_UPLOAD_KEY_ALIAS`, `PAFM_UPLOAD_KEY_PASSWORD`. Without them the release APK is signed with the debug key so it installs on a test device; that APK is not a distribution artifact. **Amended by R-015 (specs/archive/261104-phase-2a-android-ui-auto-review): the task `verifyReleaseSigning` runs before `packageRelease`. A half-set upload configuration (the store file without the three other variables) fails everywhere; with no store file, a build with `CI` set fails unless `PAFM_ALLOW_DEBUG_SIGNED_RELEASE=1` (`android.yml` sets it, its release job is a smoke build), and a local build only warns. Lint and debug builds never run it.** No key or password is in the repository (`*.jks`, `*.keystore` are ignored and a Konsist rule scans for key files).
3. `versionName` is the catalog's `android-app` version; `versionCode` is `git rev-list --count HEAD` (1 when git is unavailable). CI must fetch full history for a correct number.
4. `releaseDemo` is `release` plus `BuildConfig.DEMO_ENTRY = true`, application id suffix `.demo`, debug-signed. It runs the identical R8 pipeline and code, so launching it proves the minified demo graph. The shipping `release` type never has the flag (Konsist rule). The alternative, honouring demo extras in `release`, would have weakened the guarantee that a shipped app cannot be put on fakes.
5. `scripts/android-release-smoke.sh live|demo` builds, installs, launches, and fails on a missing process, a fatal exception, a class-loading error or a missing expected screen (the lock prompt or first run for live; the Inbox for demo).

**Consequences.** One extra build type exists and must never be distributed. The live check proves the minified live graph opens (Keystore, database, Koin) up to the lock; it does not prove a pairing or a real server call under R8 (T8.9 and the pinned server). Baseline-profile and benchmark builds (T8.6) will add their own type.

## ADR-133: Polish: dynamic colour reaches the neutral roles only, motion follows the system animation setting, two haptic feelings, a splash through AndroidX, and the worst-case layouts are baselined

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T8.3)

**Context.** AND-070 asks for dark theme, dynamic colour for neutrals only, 360 x 640 dp and large phones, reduced motion, haptics, an adaptive icon and a splash screen. Composables read `AppTheme.colors` directly, so putting dynamic colour only into the Material `ColorScheme` would leave half the UI unchanged.

**Decision.**
1. `Neutrals` takes the page, card, muted, hairline, border and the two text roles from `dynamicLightColorScheme` / `dynamicDarkColorScheme` (Android 12 and later) and `AppColors` uses them when given; the accent, destructive, warn and success tones (and their soft backgrounds) are never replaced, so a critical item looks critical on every wallpaper. `PaperclipTheme(dynamicNeutrals)` defaults to off, so baselines and tests do not depend on the host wallpaper; `MainActivity` turns it on. `PolishTest` proves the brand and severity colours are identical and the text keeps 4.5:1 on page, card and muted for the light and dark schemes.
2. Reduced motion is the system's: an animator duration scale of 0 (Remove animations). Compose already scales its own animations by that setting; the one explicit animation the app owns, the fade between screens, is `EnterTransition.None` / `ExitTransition.None` under it. The setting is observed while the app runs.
3. Haptics are two feelings behind an injectable `Haptics`: confirm (an approval resolved, a decision final, a comment sent, each only when it happens while the screen is shown, never when a finished item is opened) and reject (a failure banner appears, including the device owner declining). Triggers are state transitions in the screen's effect, not click handlers, so a failed or declined action does not vibrate as success.
4. The launcher activity's theme is `Theme.Paperclip.Starting` (AndroidX `core-splashscreen`, so API 28 to 30 look like 31 and later): the page colour with the clip mark, brand blue in light and white in dark, handing over to `Theme.Paperclip`. The adaptive icon already has background, foreground and monochrome layers; the artwork is a placeholder.
5. `LayoutVariantsScreenshotTest` baselines Inbox, Decision, Approval, Agents and Work at 360 x 640 dp and at 430 x 932 dp, and Inbox, Decision, Approval, Agents, Work, Issue, Agent detail and Run viewer at 360 x 640 dp with font scale 2.0, asserting the named primary actions are displayed and at least 48 dp tall.

**Defects found by looking at the worst case and fixed here.** (a) The Approval header put the type line and the status pill in one row: at font scale 2.0 the pill was squeezed to one letter per line, and the 2a-5 font-scale baseline had recorded exactly that as correct. It now uses `AdaptiveRow` and the baseline is re-recorded. (b) At 2.0 the bottom bar broke "Factories" into "Factori" and "es" on the emulator (411 dp wide); Robolectric's default text measurement did not show it, only native graphics did. Tab labels now stop growing at 1.3 (Material's navigation bar guidance), stay on one line, and the test runs in native graphics mode.

**Consequences.** Everything except the tab labels follows the person's font setting. Dynamic neutrals are proven by unit test and on the emulator, not by a baseline (the wallpaper decides the colours). The Compose animation of bottom sheets and system pull-to-refresh follows the platform setting and is not separately tested. The splash and icon artwork are placeholders; their structure is tested.

## ADR-134: Accessibility is audited from the semantics tree on every screen family; the framework's own checks do not run under Robolectric and TalkBack was not run

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T8.2)

**Context.** T8.2 asks for TalkBack names and order and Compose accessibility checks per screen family, with an honest record of what fails. The environment has Robolectric and one emulator without TalkBack.

**Decision.**
1. `AccessibilityTest` renders Inbox, Decision, Approval, Factory home, Agents, Agent detail, Run viewer, Work, Issue, Factories, Edit factory, Settings and the lock on the demo graph and audits the merged semantics tree (`AccessibilityAudit.kt`): every clickable control has a name (description, text or typed text), a role (text fields excepted: the platform announces them as edit boxes) and a touch target of at least 48 dp (the touch bounds, which include the platform's own minimum), and the tree order (the reading order) follows the layout from top to bottom, left to right. A screen with a planted unnamed, role-less control proves the audit can fail. A second test proves every status tone has its own symbol and shows its words, so no state is told by colour alone.
2. Fixed because the audit found them: clickable cards and rows in Work, Agents, Agent detail, Run viewer, Factories, Manage factories, Settings and Factory home had no role (TalkBack would not say "button"); the four switches (app lock twice, destructive-action note, certificate pinning) and every `FormField` had no accessible name, because the visible title was a separate `Text`. Switches and fields now carry the title as their content description.
3. The framework's checks (`enableAccessibilityChecks()`, `ui-test-junit4-accessibility`) were tried and removed: under Robolectric they passed a planted 16 dp unlabelled control, so a green result would prove nothing. They need an instrumented test on a device; `androidApp` has no `androidTest` source set yet, and this is not done.
4. Not proven here and not claimed: what TalkBack actually speaks (in particular that a text field with a content description still reads the typed text and the description in the order intended), focus order with a real accessibility service, Switch Access, and contrast of text over the wallpaper-tinted neutrals (the stock schemes are proven at 4.5:1 in `PolishTest`; a dynamic scheme is not).

**Consequences.** The same audit runs on every screen added later by calling `audited`. Any new clickable that lacks a role, a name or a 48 dp target fails a unit test. The device-level checks remain an open item for the release gate.


## ADR-135: Performance is guarded by an on-demand Macrobenchmark module with two extra build types, a committed baseline profile and an emulator suppression confined to that module

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T8.6)

**Context.** The Android PRD sets a cold start budget (to the cached Inbox, median of 10 `COLD` runs) and at most 5% janky frames over 200 Inbox cards. Macrobenchmark needs a non-debuggable, profileable app that was built like the release, a test module that instruments it, and a baseline profile whose rules name real classes (R8 renames the others). The shipping `release` ignores the demo extras, so it opens the lock screen or first run, not an Inbox. The demo Inbox holds nine cards, not 200. The profile installer's receiver, which Macrobenchmark broadcasts to, is removed from every shipping manifest (T8.7).

**Decision.**
1. `:androidApp:benchmark` is a `com.android.test` module (convention plugin `pafm.android-test`) that targets `:androidApp`, builds only the `benchmark` and `nonMinified` variants, and holds `StartupBenchmark` (COLD, `StartupTimingMetric`, median of 10, with and without the profile) and a `FrameTimingMetric` fling over 209 cards. It is compiled by `check` and run only by `scripts/run-benchmarks.sh`, nightly and on dispatch in the `benchmark` job of `android.yml`; no `check` task depends on a `connected*` task.
2. The app gets two build types, both `releaseDemo` underneath (R8 on, demo entry, debug-signed, never distributed, never debuggable): `benchmark` (application id suffix `.benchmark`) and `nonMinified` (R8 off, real class names for profile recording). `src/benchmark/AndroidManifest.xml` adds `<profileable android:shell="true"/>` and puts the profile installer's receiver back, exported and guarded by `android.permission.DUMP`, which only the shell holds. `AndroidBenchmarkManifestRulesTest` allows exactly that receiver in exactly those two variants; `release` and `releaseDemo` still must have none.
3. The baseline profile is generated by `BaselineProfileGenerator` against `nonMinified` (`scripts/generate-baseline-profile.sh`) and committed as `androidApp/src/main/baseline-prof.txt`; AGP packages it as `assets/dexopt/baseline.prof` in every non-debuggable build. The AndroidX baseline-profile Gradle plugin was not used: it adds its own build types per existing type, which would multiply the variants the manifest and release rules cover.
4. Two small launch options exist so the benchmark can run on the demo graph: `PaperclipCore.createLargeInboxDemoGraph(generatedCards)` (shared, tested in `DemoLargeInboxTest`) and the launch extra `demo_inbox_cards`, honoured only where demo controls are allowed (tested in `LaunchConfigurationTest`). The Inbox calls `ReportDrawnWhen` when it shows content, so `timeToFullDisplayMs` is the time to the Inbox with content (`InboxFullyDrawnTest`).
5. Macrobenchmark refuses emulators. `scripts/run-benchmarks.sh` and `scripts/generate-baseline-profile.sh` pass `androidx.benchmark.suppressErrors=EMULATOR` as an instrumentation argument, in this module's runs only; nothing in the module's build file sets it, so a bare Gradle run on an emulator fails loudly. `PAFM_BENCHMARK_ALLOW_EMULATOR=0` removes it for a real device.

**Consequences.** Numbers from an emulator are a regression guard, not the PRD's Pixel 6 target, and status.md says so next to every number. The `benchmark` and `nonMinified` variants add two manifest merges to `konsistTest`. Regenerating the profile takes about six minutes on a device and produces a diff of rules, not data. The profile was recorded as a startup profile (`includeInStartupProfile`), whose lines carry the `H`/`S`/`P` flags AGP accepts as a baseline profile.

## ADR-136: The demo flows are walked on a device by one test per step, each in its own process through the Android Test Orchestrator

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T8.8)

**Context.** Main PRD acceptance criterion 2 lists a dozen flows on the demo graph. Robolectric tests already cover each screen, but none drives the real activity and tab shell on a device. `PaperclipApplication` creates its `AppEnvironment` once per process from the first launch intent, and the first-run flow and the seeded demo are different graphs; a test that resolved a decision also changes what the next test sees. `androidApp` had no `androidTest` source set (ADR-134 names that gap).

**Decision.** `androidApp/src/androidTest` holds 14 JUnit 4 tests on the Compose rule over `ActivityScenario<MainActivity>`, grouped in three classes (attention, operations, shell) over a `FlowTest` base. Each test is one step of the criterion, so a failure names the step. `testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"` and the runner argument `clearPackageData=true` run every test in a fresh process with cleared app data; the orchestrator is an `androidTestUtil` dependency from the catalog. No test-only reset seam was added to production code. Device-owner answers are scripted through the existing `DemoControls` once the shell shows, and prompt counts are read from it, as in the Robolectric tests. Espresso 3.7.0 is declared for the tests because Compose resolves 3.5.0, which fails on API 37. The tests run only through `connectedDebugAndroidTest`; `check` does not depend on any `connected*` task.

**Consequences.** One process start per test makes the run slower (about a minute for 14 tests on the arm64 emulator) but removes ordering effects. The Robolectric tests stay the fast per-screen check; these prove the same steps through the real activity, shell, navigation and Keystore-free demo graph. They do not show the system `BiometricPrompt` (the demo scripts it), so the real prompt remains a T8.9 and manual-device item.

## ADR-137: The live proof on Android is split into what runs without a server and a pairing script that needs Docker; unverified steps are recorded as not proven

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T8.9)

**Context.** Acceptance criteria 4 to 6 ask for a live-graph proof on an emulator: the real graph at first run, a pairing against the pinned server, the board key only in the Keystore-wrapped record, no token in any file, log or backup, and a release build refusing a stored `http` address. The pinned server needs Docker, whose daemon was not available where this was built. Some steps also need a device state the emulator does not have (an enrolled fingerprint). The app's own data directory is reachable from outside only on a debuggable build, and the connected-test task uninstalls the app when it ends.

**Decision.** (1) Everything that needs no server is executed and automated: `scripts/android-live-smoke.sh` for the running debug app and the release manifest, and the existing and extended umbrella and securestore device tests for the real Keystore store, the real SQLite file, the whole data directory, the log, and the release configuration's refusal of a stored `http` address. The log scan covers the board key by its value in the device test and by the `pcp_board_` prefix in the script; the script writes the pattern as `pcp_boar[d]_` so that the adb command line, which the platform logs, does not match itself. (2) The pairing is a script, `scripts/android-live-pairing.sh`, not an instrumented test: pairing in the app writes the key under a device-owner prompt that needs the real activity, so the script drives the real app by hand, and automates the part that cannot be done headlessly, the browser's approval, with the board user's session over HTTP (as the live contract test does), reading the approval URL from the activity manager. The device reaches the server as `http://localhost:3199` through `adb reverse`. (3) What was not run is written down as not run, with the reason, in `status.md`; no number or claim stands in for it.

**Consequences.** Criterion 6's release refusal is proven as the release configuration plus the release manifest, not as a planted record in the release APK. The pairing script and its approval trick are unverified until someone with Docker runs it once; the first run is also its test. Enrolling a fingerprint and the real prompt on a destructive action remain open until a device with them is used.


## ADR-138: Android documentation images are copies of the Roborazzi baselines, demo graph only

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui, T9.2)

**Context.** AGENTS.md asks for real screenshots from the Roborazzi (Android) runs once the app exists, captured from demo mode, never showing real data or the pairing screen. The baselines in `androidApp/src/test/screenshots` already are such captures, each checked by `verifyRoborazziDebug`, and they are 720 px wide with the full screen height, so they show a whole screen without a device.

**Decision.** (1) The documents use copies of selected light-theme baselines (plus one dark, one offline and one font scale 2.0 image), named `android-<screen>.png`, in `docs/images/` and, for the user guides, `user-docs/images/`. They are copied by hand when a screen changes, in the same pull request as the baseline; a copy that differs from its baseline is a docs gap. (2) The pairing screen has no Roborazzi baseline and none is added: the Custom Tab approval page is the browser's, not ours, and the first-run address step stands for the flow. (3) The images come from the demo graph only, so they hold the shared demo data set's values (for example the device name "Alex's iPhone" in Settings), which are not read from the phone.

**Consequences.** No second screenshot pipeline and no device needed to refresh the documentation. The copies can drift from the baselines until someone re-copies them; the markdown link check catches a missing image but not a stale one. The 720 px images are large (about 2 to 3 MB per ten images), so the docs show them at 180 to 260 px wide.

## ADR-139: Protected Keystore keys are authorised per use through a `CryptoObject`; the 5 second window of ADR-123 is withdrawn

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui-auto-review, T-001 / R-001). Supersedes item 3 of ADR-123.

**Context.** ADR-123 item 3 chose `setUserAuthenticationParameters(5 s, ...)` and said the window "opens only through a prompt the store itself asked for". That is false. Keystore authentication tokens are system-wide: unlocking the phone, or a biometric or credential check in any other app, inside the last 5 seconds opened the window for this app's key, so a protected `get()` could return `Found` with no prompt, and an approval stayed reusable for 5 seconds by anything that could reach `open()` in the process. That breaks FR-011 ("protected means the person is asked") and INTENT principle 1.

**Decision.** (1) A protected key is generated with `setUserAuthenticationParameters(0, BIOMETRIC_STRONG | DEVICE_CREDENTIAL)`: every use needs its own authentication (API 30 and later, as before; below that a protected write fails closed). (2) `KeystoreBackend.seal` and `open` return `NeedsAuthentication(cipher)` carrying an initialised, unused `javax.crypto.Cipher` and do no cryptography with a protected key; `finishSeal` and `finishOpen` complete the operation with the cipher the prompt authorised. (3) The store's `authenticate` is `suspend (reason, Cipher) -> Cipher?`; `BiometricPromptBiometrics.authenticateWithCipher` shows the prompt with `BiometricPrompt.CryptoObject(cipher)` (strong biometrics or the device credential, the only authenticators a crypto prompt accepts) and answers the cipher in `result.cryptoObject`, or `null` when the person did not approve. `javax.crypto` stays in `securestore` and `platform`; the activity never enters the store. (4) A protected key made before this change (validity not 0) is not trusted: reading it is `Invalidated`, so the factory asks for pairing again, and writing a protected value replaces it. (5) `UserNotAuthenticatedException` at the last step is `Unreadable` (`Denied`), not "ask again".

**Consequences.** Cost to watch: the board token of a factory is a protected secret when app lock is on (`KeyEnrollment` sets `requireUserPresence` from `appLockEnabled`), and `StoredCredentials.call` reads it for every gateway call. With the 5 second window one prompt covered the calls made within 5 seconds; now each read of the protected token is its own prompt (serialised, one at a time), so a refresh that issues several calls can ask the person several times. *Amended 2026-11-05 (ADR-145):* the owner chose a short in-memory token for the foreground unlock session; it is held by a decorator above the secret store, so the Keystore and Keychain stores themselves stay per-use as described here, and the per-call cost described in this paragraph now applies to the store, not to a refresh. Also: two consecutive protected reads prompt twice; `SecretStoreContract` has a protected case run on the fake-backed store, and device tests assert `KeyInfo.userAuthenticationValidityDurationSeconds == 0` and the old-key behaviour on the emulator. The real prompt with an enrolled fingerprint and the "another app authenticated a moment ago" step stay manual checks (R-009 evidence). Existing installs with a protected board key re-pair once. The iOS Keychain store is unaffected.

## ADR-140: The device-owner prompt never hangs a caller and is never shown with the secret store's lock held

**Status:** Accepted (2026-11-04, specs/archive/261104-phase-2a-android-ui-auto-review, T-002 / R-002)

**Context.** `ActivityPromptLauncher` read the resumed activity on the IO thread and posted the prompt to the main thread; if the app was backgrounded in between, `BiometricPrompt.authenticate` logged and returned without ever calling the callback (biometric 1.1.0, "Called after onSaveInstanceState()"), and an exception in the posted lambda crashed the app and left the coroutine unresumed. `KeystoreSecretStore` ran `authenticate` inside its single mutex, so one open prompt blocked `namespaces()`, `delete`, `deleteNamespace` and the startup orphan sweep, and a destructive use case hung on its spinner.

**Decision.** (1) The launcher works through a `PromptScreen` seam. In the posted block it checks the caller is still active and that the activity is not finishing or destroyed and its fragment state is not saved (androidx `isStateSaved`, the same check biometric 1.1.0 applies before it silently refuses) (`canShowNow`), otherwise it answers `Unavailable`; it catches `IllegalStateException` and `IllegalArgumentException` from `show` and answers `Unavailable`; a caller cancelled before the post never shows the prompt; cancelling the caller later cancels the system prompt. (2) The store's lock covers only the file and key steps. A protected read or write takes a first step under the lock (open or seal, which yields the unused cipher), asks the person with the lock free (one prompt at a time through a separate `promptLock`), and finishes under the lock again. The finishing step re-validates: a read compares the record bytes with those the cipher was made for and answers `Denied` (ask again) when the secret was replaced, `Missing` when it was deleted; a write that overlapped a `delete` or `deleteNamespace` (a deletion counter) is `Failed` and does not bring the secret back.

**Consequences.** The prompt can stay open for as long as the person likes without blocking other secret operations. The dropped check `host.current() === activity` of the PRD text is replaced by `canShowNow` because `AuthHost.current()` is suspending and cannot run on the main thread; the test seam covers the same case. Not proven here: a real activity stopped between `current()` and the post (device tests need a person at the screen).

## ADR-141: A budget raise on an agent asks the device owner (DEC-1, confirmed by the owner 2026-11-05)

**Status:** Accepted (2026-11-04 as a run assumption, specs/archive/261104-phase-2a-android-ui-auto-review, T-004 / R-004); confirmed by the owner 2026-11-05. Amends ADR-013, the `SetAgentBudget` entry of ADR-021 and FR-014.

**Context.** AGENTS.md Security Rules list "budget raise" among the destructive actions that go through `Biometrics` before the use case runs, and INTENT principle 1 puts security over convenience. `SetAgentBudget` raised any agent's spend ceiling with no prompt, on an unlocked phone, on both platforms; ADR-013 and FR-014 limited the prompt to the budget incident. No owner was available during the run to decide.

**Decision.** (1) `Action.RaiseAgentBudget` joins `DeviceAuthPolicy`; `DeviceAuthPolicy.budgetSetAction(current, new)` maps a monthly budget change to it when `new` is above `current` or zero (zero stops the agent in practice), otherwise to `Action.SetAgentBudget`, which needs nothing. (2) `SetAgentBudget` takes the `DeviceAuthGuard`; order: permission, currency, device authentication, one gateway call, one re-read. Without an approved prompt the result is `NotAuthorized` and the gateway is never called. (3) `AgentDetailState.saveBudgetNeedsDeviceAuth` is computed in shared presentation from the typed amount, so Android and iPhone show the same lock glyph on the save button. (4) The owner confirmed this on 2026-11-05, so AGENTS.md keeps "budget raise" as a destructive action and nothing is reverted.

**Consequences.** Behaviour change on the iPhone: raising a budget shows Face ID or the passcode (the prompt comes from the shared `Biometrics` port) and the Save button carries a lock glyph. `DeviceAuthRulesTest` lists `SetAgentBudget` as a use case that depends on the guard.

## ADR-142: Android counts as online when the default network declares internet access, validated or not (DEC-2, confirmed by the owner 2026-11-05)

**Status:** Accepted (2026-11-04 as a run assumption, specs/archive/261104-phase-2a-android-ui-auto-review, T-012 / R-012); confirmed by the owner 2026-11-05. Amends item 6 of ADR-123.

**Context.** `ConnectivityManagerConnectivity` required `NET_CAPABILITY_VALIDATED`. A home LAN, split-DNS network, captive portal, Tailscale-only path or a VPN whose probe fails is not validated, so the app showed offline and blocked mutations although the home factory was reachable, which is INTENT principle 7's primary case. INTENT principle 2 asks to show the server's truth.

**Decision.** (1) Online is a default network that declares `NET_CAPABILITY_INTERNET`; no default network, or one without INTERNET, is offline (`isUsable`). (2) An unvalidated network is "online, unknown": requests are attempted and the real success or typed failure surfaces. (3) Mutations are not blocked by an unvalidated network and are still never auto-retried. (4) A callback the system refuses to register still leaves the value offline. No shared API change: `Connectivity` stays a `StateFlow<Boolean>`. The owner confirmed this on 2026-11-05; nothing is reverted.

**Consequences.** On a captive portal the app tries and fails with a typed network error instead of announcing offline. The Tailscale-only case is part of the R-009 evidence (pair over Tailscale when available).

## ADR-143: Opening an issue's attachment: a gateway port, an app-private cache that is never named by a secret, and a share through the platform's file provider (design only, R-038)

**Status:** Proposed (2026-11-04, specs/archive/261104-phase-2a-android-ui-auto-review, T-038 / R-038). Design only: no code in this phase. Builds on ADR-064 and ADR-131.

**Context.** Both apps list an issue's attachments (name, type, size) and say they cannot be opened. ADR-064 made the content an adapter-only ranged call (`IssueGatewayAdapter.attachmentContent`) with no port, so nothing above infrastructure can reach it. The endpoint needs the board key, so a URL cannot be handed to a browser or another app. Looking at an attachment before deciding is part of INTENT's "decide": a screenshot or a log file often is the evidence for an approval.

**Decision (for the two follow-up pull requests).** (1) Shared first: a domain port `AttachmentGateway.open(scope, issueId, attachmentId)` that answers `AttachmentFile(contentType, sizeBytes, bytes)` or a typed `DomainError` (`NotFound`, `NotAuthorized`, `TooLarge`, `Unreachable`), a use case `OpenAttachment` that checks the role and the connection and refuses a file above a limit (proposed 25 MiB, a constant with a test) before downloading the body, a fake in `shared/testing` and the real adapter that wraps the ADR-064 call. The use case never retries (it is a read, but a large one; the person taps again). (2) The bytes are written once, by a platform `AttachmentCache` actual, to an app-private cache folder (`cacheDir/attachments/<random id>/<sanitised display name>`; `NSTemporaryDirectory` with `NSFileProtectionComplete` on iOS). The path never holds the token, the server address, the company or issue id, and the display name is stripped of path separators and control characters; a contract test on the port and a test on the cache path assert this. (3) The cache is emptied on sign-out of that factory, on app lock and on process start. (4) Android shares the cached file through a `FileProvider` (not exported, `file_paths` limited to the cache subfolder, a read-only URI grant to the chosen app) with `ACTION_VIEW` and the content type from the server's `Content-Type` header, never from the file name; iOS previews it with QuickLook inside the app. Because Android hands the file to another app, the sheet says so before it does. (5) The follow-up changes the Konsist manifest rule that today allows only `androidx.startup.InitializationProvider`, with its own planted-violation test, and `AndroidManifestRulesTest` keeps the provider non-exported.

**Consequences.** Not built in this phase: the Files tab still lists and says "open it in the web app", on both platforms (`docs/product-details.md` parity ledger, `user-docs/troubleshooting.md`). The feature lands as two pull requests, shared first and then each platform (AGENTS.md); the follow-up work items are listed in `status.md` of this spec and have not been filed as GitHub issues. Open design questions for the owner: the size limit, whether Android should offer "open in other app" at all (the file leaves the app's control) or only an in-app preview of images and text, and whether the cache should be encrypted at rest instead of relying on the app-private folder.

## ADR-144: With app lock on, how often may a refresh ask for the fingerprint? (resolved by ADR-145: option C)

**Status:** Resolved 2026-11-05: the owner chose option C; implemented and recorded in [ADR-145](#adr-145-the-board-token-is-held-in-memory-per-factory-for-the-foreground-unlock-session-resolves-adr-144-option-c). Found in Group 1 of specs/archive/261104-phase-2a-android-ui-auto-review (consequence of ADR-139). The text below is the question as it was put to the owner.

**Context.** When app lock is on, `KeyEnrollment` stores the board token as a protected secret (`requireUserPresence = appLockEnabled`). `StoredCredentials.call` reads the token for every gateway call. Since ADR-139 each protected read is its own system prompt, shown one at a time. A screen refresh that issues more than one gateway call therefore asks for a fingerprint more than once in a row, and the 15 second refresh timer makes that recurring. How many calls each screen makes was not counted, and the prompt count per refresh has not been observed on a device (R-009 checklist item on the per-use prompt count).

**Options.**
- **A. Keep it as it is (per call).** Strongest: a stolen unlocked phone cannot make a single authenticated request without the owner. Cost: unusable with auto-refresh and several calls; the person will turn app lock off, which is weaker than any of the options below.
- **B. One authorisation per refresh.** Read the token once when a refresh starts, keep it in memory for that refresh only, drop it when the refresh ends. Per-use semantics per user action, a few prompts less. Needs a shared `Credentials` scope around a refresh and a rule that nothing outlives it.
- **C. A short in-memory token for the foreground unlock session.** Authorise once when the app unlocks, keep the token in memory until the app goes to the background, the idle lock fires or a short timer (for example five minutes) ends. Best usability; the token sits in process memory for that time (contradicts the review's "AD-001: nothing caches the token") and any code in the process can use it without a fresh prompt. Destructive actions would still ask for the device owner through the `Biometrics` port, so a stolen unlocked phone can read but not pause, cancel, reject or raise.

**Trade-offs.** The prompt is the only thing that stands between an unlocked phone and the factory's data in option A, while B and C trade that for a usable app. All three keep the secret out of storage; B and C add a time-bound copy in memory. The iPhone has the same read path (`StoredCredentials` is shared); how often the Keychain asks per read there was not measured, and the same decision would apply if it asks per call.

**Decision needed from the owner (answered: C, no timer, see ADR-145):** A, B or C, and for C the time bound. Then a change to ADR-139's consequences and the shared credentials path, test-first, with the prompt count recorded on a device. See `docs/technical-details.md` known limitations and `docs/android-verification-checklist.md`.

## ADR-145: The board token is held in memory per factory for the foreground unlock session (resolves ADR-144, option C)

**Status:** Accepted, owner decision 2026-11-05 (specs/261105-adr144-unlock-session-token). Resolves ADR-144 with its option C. Amends ADR-139's consequences and supersedes, for this one place, the review's AD-001 statement "nothing caches the token".

**Context.** ADR-139 made every read of a protected token its own system prompt, and `StoredCredentials.call` reads the token for every gateway call, so with app lock on a refresh asked for the fingerprint once per call (ADR-144). The owner chose option C: authorise once, keep the token in memory for the foreground session, still ask for destructive actions.

**Decision.**
1. *Above the store.* `SessionCachingSecretStore` (application, `auth`) decorates the `SecretStore` port. The graph binds the device store under `RawPort` and hands everything else the decorator (like the `SignedOutAware*` gateways). `KeystoreSecretStore` and `KeychainSecretStore` stay per-use, so ADR-139 is still true of them and of `SecretStoreContract`'s protected cases. The decorator is shared Kotlin, so the iPhone gets it too.
2. *What it holds.* Only `SecretName.BoardToken`, per `InstanceId`, in a `SessionTokenMemory` (an atomic cell; no file, no `SavedStateHandle`, no log). Every read hands out a fresh `Secret.copy()` because callers wipe what they are given (without this the second call would send an empty token and sign the factory out); eviction wipes the held copy. `Secret.reveal` rules are unchanged, `toString` still redacts.
3. *When it is emptied.* `put`, `delete` and `deleteNamespace` drop that factory's entry before and after the change (re-pair, sign out, remove, address change; a failed put too). Everything is dropped when the app goes to the background (`AppLifecycleBridge.setForeground(false)`, before the lock is told), when it returns to the foreground (for a read that began while it was away) and when an app shell is created (it starts locked). A foreground signal that changes nothing, such as the iOS scene turning active when the Face ID sheet closes, does not drop it; iOS maps only `.background` and Android only the process `ON_STOP` to the background, so a system prompt does not empty the cache. A 401 or a `KeyInvalidated` seen by `SignedOutMarker` drops that factory, so after a re-pair the next call sends the new token and never the refused one. A read that began before any of these does not repopulate the cache (a generation per factory and an epoch for "all").
4. *The idle lock.* In `AppStateHolder` the lock engages at process start, for a newly created shell (the bridge clears when it attaches one) and in `relock()` on coming back to the foreground, which needs `backgroundedAt`; the background clear therefore always comes first. There is no foreground idle timer. No separate lock hook and no timer were added: the bound is the background and the app lock.
5. *Single flight.* A per-factory mutex covers the miss: concurrent calls of one refresh cause one protected read (one prompt) and the others receive the cached copy. A refusal (`Denied`, `Invalidated`, `Missing`) is never cached but is shared with the callers that were waiting for it, so a cancelled prompt does not repeat for every queued call, unless the factory's token changed since that read began (a refusal from before a re-pair is not handed on, or the fresh pairing would be marked Signed out); the next call after it asks again. A cancelled read caches nothing and the next caller retries.
6. *Destructive actions are unchanged.* `DeviceAuthGuard` through `Biometrics` runs before the use case whatever the cache holds (tested for pause, cancel, reject and budget raise with a warm cache). A stolen unlocked phone can read, not pause, cancel, reject or raise.
7. *The contract.* `SecretStoreContract` runs against the decorator with `createProtected` left null on purpose: the contract's protected cases say an approval is never reused, which is the point of ADR-139 and not of this cache. Prompt counts of the decorator have their own tests. A Konsist rule (`SessionCacheRulesTest`) keeps the cache memory only (no persistence, serialization, files, settings, logging; never `reveal`) and constructed only by the composition root. This replaces the AD-001 statement deliberately; the kdoc of `StoredCredentials` and of `ProtectedSecretStoreCases` say so.

**Unlock: what the person sees (superseded by ADR-146, which makes the unlock one prompt).** With the cache, one unlock costs two prompts when the token is protected: the lock prompt (`Biometrics` `UnlockApp`, which has no `CryptoObject`) and then the first protected read (a `CryptoObject` prompt). Making the unlock prompt itself the read needs a way for the store to attest that a specific read authenticated the person: a plain `Found` proves nothing, because it can come from an unprotected token (app lock turned on after pairing: `UpdateAppSettings` does not re-wrap tokens) or from a cache hit, and treating it as an unlock would bypass the app lock. That is a change to the `SecretStore` port, both real stores, the fake and the contract, and it would make the Android unlock stricter (strong biometric or screen lock instead of weak). It was not done in this change; it is reported to the owner as a follow-up decision. Also: switching to another factory prompts once for that factory; pairing with the lock on prompts for the write and then once for the first read (the write clears the entry). Returning from the background asks once on the first call, even inside the lock grace period, because the background clears the cache.

**iOS.** `SecurityKeychain.read` builds a new `LAContext` for every read with no reuse duration, so by code reading a protected Keychain item prompted on every call before this change too. The decorator reduces that to one prompt per foreground session on the iPhone as well. Not observed on a device or simulator with enrolled biometrics.

**Consequences.** Cost: while the app is in the foreground and unlocked, the board token sits in process memory and any code in the process can use it without a fresh prompt (until the background or a 401). It is cached even when the token is not protected (app lock off), where it only saves a Keystore decryption. The per-refresh prompt count on a real phone is still a device measurement (checklist item 4); the code reduces it to the unlock-time prompts above plus one per factory switch.

## ADR-146: With app lock on, the protected read of the active factory's token is the unlock (SecretStore.readAttested)

**Status:** Accepted, owner decision 2026-11-05 (specs/261105-adr144-unlock-session-token). Supersedes the "Unlock: what the person sees" paragraph of ADR-145 (two prompts at unlock). Amends ADR-145 decision 6 only in that the unlock prompt can now be the key prompt.

**Context.** ADR-145 left an unlock at two prompts when the token is protected: the lock prompt (`Biometrics` `UnlockApp`, no `CryptoObject`) and then the first protected read. A plain `Found` cannot stand in for the lock prompt: the token may be unprotected (app lock turned on after pairing; `UpdateAppSettings` does not re-wrap tokens) or come from memory, and treating either as an unlock would open the app without the owner (INTENT principle 1).

**Decision.**
1. *Attestation, not inference.* `SecretStore.readAttested(key, reason)` returns `AttestedReadResult`: `Authenticated(secret)` only when this very read opened a user-authentication-bound secret after a prompt the person approved; `NotAttested` (unprotected, missing, or any failure that did not come from the prompt); `Denied` (the prompt this read showed was refused or failed); `Invalidated`. `Found` is not overloaded. The port default is `NotAttested`, so a store that does not implement it can never unlock the app.
2. *Implementations.* Android `KeystoreSecretStore`: attested only when the `CryptoObject`-bound `finishOpen` ran after a successful `authenticate`; a read that never prompted attests nothing. iOS `KeychainSecretStore`: attested only for an item that has its protected marker (written and removed together with the access control) and was read through its `LAContext`; a refused prompt on a protected item is `Denied`, on an unprotected one nothing. `FakeSecretStore` is honest: it records which paths were stored with `requireUserPresence` (`put`, `seed(protected = true)`) and attests only those. `SecretStoreContract` has the same obligations for fake and real stores (unprotected and missing attest nothing; a protected read is exactly one prompt; refused is `Denied`; invalidated is `Invalidated`), and `KeychainSecretStoreTest` covers the Keychain through its fake backend.
3. *The cache never attests.* `SessionCachingSecretStore.readAttested` goes to the device store under the factory's lock whatever the cache holds, and only an `Authenticated` answer seeds the cache (same generation rules, copies). A refresh that starts at the same time waits on that lock and shares the result, so one prompt serves both.
4. *Unlock flow.* `UnlockSession(UnlockApp, SecretStore, SignedOutMarker)` is called by `AppStateHolder` with the selected factory. `Authenticated` is the unlock (one prompt, no second one). No active factory, no token, an unprotected token and `NotAttested` fall back to the plain `UnlockApp` prompt, so the lock always asks the owner. `Denied` keeps the app locked (the existing Cancelled issue; a retry asks again). A Konsist rule (`AttestedReadRulesTest`) lets only this use case call `readAttested`, apart from the cache and the store layers.
5. *Invalidated key: deviation from "stay locked".* The owner's brief said an invalidated key leaves the app locked and offers re-pairing. That would lock the person out of the app they need to re-pair in (a new fingerprint on Android invalidates the key). So `Invalidated` marks the factory Needs re-pairing (through `SignedOutMarker`, which also drops any cached token) and then asks for the plain unlock prompt. The lock still requires the owner, and the factory shows its re-pair state once the app opens.
6. *Android authenticators.* The key prompt uses strong biometric or the device credential (API 30 and later, the only levels with protected keys), which are exactly the authenticators of the plain unlock prompt on those versions. Nothing stricter is demanded, and a person with only a weak biometric unlocks with the device credential. A test pins that the two are equal.

**Consequences.** With app lock on and the active factory's token protected, an unlock is one prompt; a factory paired before app lock was turned on keeps one plain prompt at unlock plus no key prompt (its token is unprotected). Other factories keep one prompt on first use. Not observed on a device: the real Keystore and Keychain prompts with enrolled credentials (checklist item 4).

## ADR-148: iOS UI tests wait for the exact condition, instead of reading the screen once or retrying the run

**Status:** Accepted.

**Context.** The `ios (1/2/3)` jobs failed intermittently, each time on a different UI test, in pull requests that did not touch iOS code. The failures were: typed text read before the accessibility tree caught up (`Clearing edit-address left "http" behind`, a wrong `edit-name` value), the lock screen appearing after the 3 s window `waitUntilUnlocked` allowed it, a tap on a row that was still scrolling or a screen still transitioning (`the run never opened`), the five-second Undo offer expiring before a slow runner tapped it, and `PrivacyShieldTests` counting every window above alerts, which includes the shield of the app hosting the tests. None reproduced on a fast local machine (135 of 135 executions of the suspect classes passed with six busy loops running), so they are timing assumptions of the 3-core CI runners.

**Decision.** Fix at the helper level and keep every assertion. `enterText` waits up to 4 s for the field to show exactly the typed text before it declares a dropped key; `scroll(to:)` waits for the row to come to rest; `tapOpening` taps a navigation row again only when the target screen has not appeared and the row is still on screen; `waitUntilUnlocked` waits up to 10 s for the lock screen to appear; value reads after a navigation use `waitUntil`; the shield tests check the controller's own `window`. We did not add `-retry-tests-on-failure` or `-test-iterations`: a retry would also hide a deterministic failure.

**Consequences.** Every wait is bounded and still fails when its condition never holds. The Undo offer is a five-second wall-clock window in shared Kotlin (`UndoDismiss.UNDO_WINDOW`); the two Undo tests still race it on a very slow runner. Making the window injectable for demo mode is a shared-code change and is left as a follow-up. `PrivacyShieldController.window` and `AppEnvironment.privacyShield` are now readable by tests (no behaviour change).


## ADR-147: `AppButton` is drawn directly, not as a Material `Button`, because a card composes two or three of them per scroll step

**Status:** Accepted (2026-11-06, specs/archive/261104-phase-2a-android-ui-auto-review, R-008 follow-up, round 2)

**Context.** The 200-card Inbox fling still missed the 5% budget on the emulator (ADR-135, R-008). A profile of the benchmark traces (Perfetto, `benchmark` build, 15 flings) showed that the UI thread is not the limit: it averages about 3 ms a frame, Compose composition and layout together are about 150 ms over a 52 s run, and 58 of 509 frames over 12 ms spent about 1007 of 1012 ms waiting in `postAndWait` for the RenderThread. About 92% of the frames are FrameTimeline "Buffer Stuffing" and 2 to 3% are "App Deadline Missed". What the app does control is the work per new card, which a fling pays for as each card scrolls in. Counting composable invocations (a `CompositionTracer` test) showed an approval card runs 210, and its three Material `Button`s were most of them: each is a `Surface` (a `graphicsLayer`, a clip, a shape background), a `buttonElevation` with an `animateElevation` `LaunchedEffect`, and a content-colour provider.

**Decision.** `AppButton` keeps its signature, looks, 48 dp minimum, `Role.Button`, ripple colour, disabled colours and the border of the outlined kinds, but is drawn as one `Row` (border, background, clip, `clickable`, padding) instead of a Material `Button`. The modifier order copies the `Surface`'s, so all 73 Roborazzi baselines verify unchanged. It lives in the design system, so every screen's buttons change, not only the Inbox's. The hoisting of the per-card `ActionRow` lambda lists and the TalkBack action lists into `remember` was measured and not done: a card in a fling is composed once and is not recomposed (`InboxScrollCompositionTest`), so a `remember` adds work to the one composition it gets.

**Consequences.** An approval card composes 133 composables (210 before) and 3 `LaunchedEffect`s (9 before); `InboxCardCostTest` fails if a card gets more than 133. The emulator frame metric did not move beyond its noise (the emulator is bound by the GPU and the display path), so the gain is a CPU-side one that only a phone can show. A Material `Button` feature the app does not use (elevation, tonal elevation, a custom interaction source) is gone from `AppButton`; add it back only with a screen that needs it. Round 2 numbers: `specs/archive/261104-phase-2a-android-ui-auto-review/implementation-notes.md`.
