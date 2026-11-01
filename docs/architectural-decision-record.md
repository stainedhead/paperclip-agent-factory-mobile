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
**Decision.** `Decision.inputs` lives on the decision. `Decision.decideBy` and `originAgentName` are optional; the Decision screen is opened with the feed item's values as hints and the Phase 1b adapter may fill them. `DecideDecision` sends an `idempotencyKey` made with the `IdGenerator` so a manual re-tap after a timeout replays instead of running the effects twice; the use case itself never retries. Polling (`PollDecisionExecution`) re-reads until `executionStatus` is `succeeded`, `partial` or `failed` (or the decision is `expired`/`cancelled`), on the `Clock`, with a capped number of attempts.
**Consequences.** A timeout after decide re-reads the decision (FR-096) and shows what really happened. A 409 re-reads and shows the final state with no options.
**Note.** `PollDecisionExecution` waits with coroutine `delay` (up to 10 reads, 1 s apart), not with the `Clock` port, as ADR-027 later fixed for all timers.

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

## ADR-022: Saved filters map to server params, except priority which is filtered on the client

**Context.** Research W1: the server has no `priority` filter and the web has none of the PRD's five saved views by those names.
**Decision.** `SavedFilter` = `AllOpen` (`status=backlog,todo,in_progress,in_review,blocked`), `Blocked` (`attention=blocked`), `InReview` (`status=in_review`), `AssignedToMe` (`assigneeUserId=me`, restricted to open statuses) and `CriticalAndHigh` (all open statuses from the server, then `priority in {critical, high}` on the device by `SavedFilter.matches`). Search sends `q` and keeps the saved filter. The list uses `view=compact` and pages by `limit`/`offset`; the gateway takes an `IssueQuery` so Phase 1b builds the URL.
**Consequences.** `CriticalAndHigh` can show fewer than a page; the holder keeps loading while a page was full. The fake applies the same rules as the server.
**Note.** In code the type is `IssueFilter` (not `SavedFilter`) and the on-device rule is `IssueFilter.matches`.

## ADR-023: Interactions are a sealed domain type with five supported kinds; anything else is `Unsupported` and is never sent

**Context.** Research W7/W8: six server kinds with large payloads; `connection_intent` has other endpoints; the resolver rule for a board user is addressee plus creator plus viewer role.
**Decision.** `Interaction` is a sealed interface: `SuggestTasks`, `AskUserQuestions`, `RequestConfirmation`, `RequestCheckboxConfirmation`, `RequestItemVerdicts` (only the fields a form renders) and `Unsupported(kind)` (also for `connection_intent`). `Interaction.answerFor` builds a typed `InteractionAnswer` that validates required answers, reasons and selection counts on the device; `RespondToInteraction` maps an answer to exactly one gateway call and never takes an `Unsupported`. `resolverAudience` reuses `ResolverAudience.allowsUser`; an interaction that is not for the user, not pending, or has an `acceptanceBlocker` is read only.
**Consequences.** The mobile app cannot send an invalid payload; the server's 422 remains the backstop.
**Note.** In code the unsupported case is the class `UnsupportedInteraction`, and the device-side answer check is the extension `Interaction.validate(answer)`, which returns an `AnswerIssue` or null; there is no `Interaction.answerFor`.

## ADR-024: Names are joined on the device, "current issue" comes from live runs, and the tail is by byte offset and sequence

**Context.** Research W2/A1/R1: the issue, comment and agent rows carry ids, not names; there is no current-issue field; logs are read by byte offset and events by sequence number.
**Decision.** Gateways return domain objects with the ids; `LoadIssue` and `LoadAgent` join assignee and author names from `AgentGateway.list`. An agent's current issue is the `issueId` of its active live run. `TailRunLog` takes a `LogCursor(offset)` and returns the new text and the next cursor; `RunGateway.events(afterSeq)` the same by sequence. The Run viewer keeps `autoScroll = true` until the user scrolls up (`UserScrolled` intent) and turns it back on at `JumpToLatest`.
**Consequences.** The polling policy of slice 1a-4 only has to call the holders' `Refresh` intent at the right interval.
**Note.** In code there is no `LogCursor`: `RunGateway.log(scope, id, offset)` returns a `LogChunk(content, nextOffset)`, `TailRunLog` takes and returns a bounded `LogTail`, and `RunGateway.events(scope, id, afterSeq)` reads events by sequence number.

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

