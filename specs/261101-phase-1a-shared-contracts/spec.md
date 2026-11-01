# Specification: Phase 1a shared contracts

**Feature:** domain model, ports, use cases, presentation state holders, fakes and demo data for the shared Kotlin core
**Created:** 2026-11-01 · **Status:** Draft · **Spec dir:** `specs/261101-phase-1a-shared-contracts/`
**Parent plan:** [../261101-iphone-app/plan.md](../261101-iphone-app/plan.md) section 4, slices 1a-1 to 1a-4

## 1. Executive summary

Phase 1a builds everything the apps need that is not an adapter and not a pixel: the domain model and ports, one use case per user action, one state holder per screen, and a complete set of fakes with a demo data set. When it is done, the iPhone app (and later Android) can run every screen on fakes with no server, and Phase 1b only has to supply real adapters behind the same ports. This is the milestone that unblocks both UIs.

## 2. Problem statement

The PRD's rules (what is enabled, what needs confirmation, what a role may do, how an error reads) must live once in shared Kotlin with tests, not in SwiftUI or Compose. Today the shared core holds one walking-skeleton slice (address validation). Nothing models instances, secrets, the Attention feed, decisions, approvals, issues, agents or runs, so no screen can be built.

## 3. Goals and non-goals

**Goals**
- G1 Model every PRD entity and value object in `shared/domain`, with the ports infrastructure will implement.
- G2 One use case per user action in `shared/application` (one public `operator fun invoke` each).
- G3 One state holder per screen (all 16 in the PRD inventory) in `shared/presentation`, exposing `StateFlow<ScreenState>` and accepting intents, holding every UI rule.
- G4 A fake for every port in `shared/testing` plus one demo data set that matches the design mocks, so the apps run in demo mode.
- G5 Shared contract-test suites that real adapters must later pass against the same fakes.
- G6 Coverage at or above 90% on domain + application (aggregate) and on presentation (separate gate); architecture rules stay green.

**Non-goals (later phases)**
- Ktor client, DTOs, SQLDelight, Keychain/Keystore, LocalAuthentication, WebSocket adapters (Phase 1b).
- Any SwiftUI or Compose screen beyond what Phase 0 shipped (Phase 2i, 2a).
- Push relay and notification settings behavior (Phase 3), except the domain types the settings screen needs to display.
- Anything in the PRD's Non-Goals.

## 4. User requirements

Functional requirements are the PRD's, scoped to what Phase 1a delivers. IDs are the PRD's.

| PRD FR | Phase 1a delivers |
|---|---|
| FR-001 to FR-008 | `Instance`, `InstanceConfig`, `DeploymentMode`, `Exposure`; use cases to add, probe, edit, rename, reorder, remove, switch instance and company, export as deep link; state holders for screens 15, 8, 9, 16 |
| FR-010 to FR-016 | `Secret`, `SecretStore` port (`put/get/delete/deleteNamespace`), `Biometrics` port, `DeviceAuthPolicy` (which actions require device authentication), pin value type; FR-011 and FR-012 implementations are Phase 1b |
| FR-020 to FR-025 | `PairingChallenge`, `BoardKey`, `AuthGateway` port; use cases pair, manual key, sign out, list/revoke keys, expiry warning rules; screen 13 and 9 state |
| FR-030 to FR-035 | `AttentionItem`, severity, source kinds, grouping into Decide now / This week / Whenever, badge count, inline-action eligibility by role, snooze and dismiss with undo; screen 1 |
| FR-040 to FR-044 | `Decision`, `DecisionOption`, effects, inputs, execution status; effect summaries as UI model keys; destructive detection; screen 2 |
| FR-050 to FR-052 | `Approval` by type, actions and their preconditions, budget incident resolution rules; screens 3 and 12 |
| FR-060 to FR-063 | `Issue`, filters, comments, status and assignee changes, interaction kinds and responses; screens 10 and 4 |
| FR-070 to FR-072, FR-075, FR-078 | `Agent`, `HeartbeatRun`, dashboard and spend window; screens 6, 11, 7, 5 |
| FR-090, FR-091 | `RefreshVisibleScreen` polling policy (15 s, 5 s on a running run, stop in background) and cache invalidation rules, on a fake clock |
| FR-095 to FR-097 | Cache-first read model with "Updated" timestamp, offline action gating, `DomainError` taxonomy and error-to-message keys |
| FR-080 to FR-086 | Out of scope except notification rule value types for screen 14 display |

**Role gating (PRD Users and Roles)** is a domain rule: `Role` (owner, operator, viewer) and a single `Permissions` function decides what each state holder enables.

## 5. Differences found between the PRD and the pinned server (Paperclip 2026.1023.0)

Read from the server's shared type definitions. The PRD should be updated as part of this phase (task P0.2).

1. **No `decideNowCount`; decided: match the web app.** The attention feed returns `deskBadgeCount` (items new today or with a `decideBy` due or overdue), `totalCount` and `countsBySourceKind`. The badge shows `deskBadgeCount`. "Decide now" is `decideBy` due today or past only (no severity rule), mirroring the server's `isDecideNow`, with `isNewToday` and UTC day boundaries mirrored in domain tests. FR-030 and FR-032 are corrected in the PRD (done), and `decideBy` is a string: `today`, `this_week`, `whenever`, a `YYYY-MM-DD` date, or null.
2. **Eleven attention source kinds**, not the short list the PRD implies: `approval`, `decision`, `issue_thread_interaction`, `join_request`, `recovery_action`, `productivity_review`, `blocker_attention`, `review`, `failed_run`, `budget_alert`, `agent_error_alert`. Kinds the app cannot act on open "Open in web app".
3. **Six interaction kinds**, including `connection_intent`: `suggest_tasks`, `ask_user_questions`, `request_confirmation`, `request_checkbox_confirmation`, `request_item_verdicts`, `connection_intent`. The sixth is unsupported in v1.
4. **Health exposure field** is `deploymentExposure`, not `exposure`.
5. **Resolver audience.** Interaction attention items carry `resolverAudience` (who may resolve). It must be shown and respected.
6. Confirmed matches: six decision effect types, option styles `default | primary | destructive`, approval types and statuses (`pending`, `revision_requested`, `approved`, `rejected`, `cancelled`), agent statuses, issue statuses and priorities, run statuses.

## 5a. Edge cases and error handling

Each item below becomes a named test in the slice that owns it.

**External API failures (FR-095 to FR-097).** `DomainError` is a sealed taxonomy: `Unreachable`, `Timeout`, `Unauthorized` (401, instance shown as "Signed out", config kept), `Forbidden` (403, "no permission in <company>"), `NotFound`, `Conflict` (stale or already resolved), `Rejected(reason)` (422, with the server's reason, for example budget incidents), `ServerError`, and `Malformed` (unparseable body). Reads retry with exponential backoff, at most 3 times. A mutation is never retried; after a timeout the use case re-reads the entity and the screen shows its true state.
**Empty and null.** Empty attention feed; null `decideBy`, `subject.title`, `detail`, `snoozedUntil`; a company with no agents or issues; an account with no companies; zero configured factories (first run); offline with an empty cache (explain, do not show a blank screen); unknown enum values from a newer server map to `Unsupported(kind)`, never a crash.
**Races and concurrent changes.** A decision or approval resolved elsewhere between load and confirm shows its final state with no options (FR-044); a decision effect that is stale is reported per effect as `skipped` or `failed`, not as overall failure; a second tap on a confirming button is ignored while a mutation is in flight; a dismissal undo racing a refresh restores the item exactly once; two screens showing the same entity stay consistent through the cache.
**Permission boundaries.** Viewer: no mutation use case calls the gateway. Operator: cannot raise a budget or set an agent budget (owner only). An interaction whose `resolverAudience` excludes the user is shown read-only. A 403 after a role change mid-session returns `Forbidden`, refreshes the role and does not retry.
**Device authentication.** If `Biometrics` is cancelled, fails or is locked out, the action does not run and no gateway call is made. An invalidated key moves the instance to "needs re-pairing" and never falls back to unprotected storage.
**Time.** Day boundaries use UTC to match the server (`isNewToday`, `isDecideNow`); the midnight edge is tested; "Updated <time>" uses the feed's `generatedAt` when present, otherwise the `Clock` port.

## 6. Non-functional requirements

- Domain imports only the Kotlin standard library, kotlinx-coroutines and kotlinx-datetime. Application imports domain only. Presentation imports application and domain only. Enforced by Konsist.
- No secret value ever appears in `toString()`, logs, fixtures or demo data; demo tokens are `pcp_board_TESTONLY…`.
- All user-facing text is a key, not prose; the shared layer never builds localized strings.
- Public surface used from Swift avoids generics, default arguments and `Result`, and avoids `new/alloc/copy/init` prefixes (existing Konsist rules).
- A mutation is never retried automatically; a use case that times out re-reads the entity (FR-096).
- Time comes from the `Clock` port only (deterministic tests).

## 7. System architecture

Layers and modules are unchanged from Phase 0. New code lands in:

| Module | New contents |
|---|---|
| `shared/domain` | entities, value objects, ports, `DomainError`, `Role`, `Permissions`, `DeviceAuthPolicy` |
| `shared/application` | use cases (about 45), grouped by feature package |
| `shared/presentation` | 16 state holders, state and intent types, UI models, error-key mapping |
| `shared/testing` | one fake per port, fixtures, `DemoData`, contract-test base classes |
| `shared/umbrella` | Koin modules wiring everything; a `DemoGraph` that builds the graph on fakes |

See [architecture.md](./architecture.md) and [data-dictionary.md](./data-dictionary.md).

## 8. Scope of changes

Create: the packages above under `shared/*/src/commonMain` and `commonTest`; Konsist rules for the new conventions (fake per port, no `Result` in presentation public API). Modify: `PaperclipCore` to build either the demo or the (Phase 1b) real graph; the PRD (section 5 deltas). No new third-party dependencies are expected; kotlinx-datetime joins domain's dependencies and the catalog.

## 9. Breaking changes

None to users. `PaperclipCore.createGraph(allowInsecureLocal:)` gains a mode parameter (demo or live); the Phase 0 iOS shell is updated in the same pull request that changes it.

## 10. Success criteria and acceptance criteria

1. Every PRD FR listed in section 4 has at least one test that names it, run on JVM, Android host and iOS simulator.
2. `koverVerifyCore` and `koverVerifyPresentation` are at or above 90% and not lowered.
3. For each of the 16 screens, a state-holder test script walks the screen's main flow on fakes and its error and role-gated variants.
4. Every port has a fake, and the same contract-test suite runs against it (real adapters join in Phase 1b).
5. The demo graph renders the data in the design mocks (Acme Labs, PAP-1182, Engineer 1) through the state holders.
6. A Swift-interop review of the exported surface is recorded in `implementation-notes.md`, with no Swift 6 concurrency warnings from the existing app.
7. `./gradlew check lint`, `ios.yml`, `contract.yml` and `security.yml` are green.
8. A table-driven test covers every (role, action) pair: a viewer's mutation use cases never call the gateway fake; operator and owner differences match the PRD.
9. For every destructive action (pause agent, cancel run, reject approval, destructive decision option, budget raise, remove factory), the gateway fake is not called unless the `Biometrics` fake succeeds; cancel, failure and lockout paths are tested.
10. A mutation that times out is attempted exactly once (call count 1) and the entity is then re-read.
11. With the `Connectivity` fake offline, reads show cached data with its timestamp and every mutation intent is disabled with an explanation key.
12. A decision or approval that was resolved elsewhere shows its final state and no options.
13. A table of `decideBy` cases (`today`, `this_week`, `whenever`, past date, today's date, null) and `createdAt` values, including the UTC midnight boundary, gives the same grouping and badge inputs as the server rules (research Q1).
14. A Konsist rule restricts callers of `Secret.reveal()` to the API auth plugin.

## 11. Risks and mitigation

| Risk | Mitigation |
|---|---|
| Domain model drifts from the real server types | Model from the server's shared types (section 5); Phase 1b contract tests per DTO |
| 16 state holders are a lot of surface to export to Swift | Review the SKIE output per slice; keep UI models plain data classes |
| Sealed hierarchies explode (attention details, interactions, effects) | Model only what screens render; keep an `Unsupported(kind)` branch |
| Fakes drift from real adapters | Shared contract suites; fakes extend them |
| Large phase delays the UI | Slices 1a-1 to 1a-4 each merge on their own; UI streams start per slice |
| Domain mirrors of `isDecideNow` and `isNewToday` drift from the server | Table-driven tests copied from the web app's cases; Phase 1b contract test against the pinned server |

## 12. Timeline and milestones

Relative sizes only. Each slice is its own pull request (shared first, then the UI that uses it).

| Milestone | Size | Contents |
|---|---|---|
| 1a-1 | M | instances, secrets, settings, auth, roles; screens 15, 8, 16, 9, 13 |
| 1a-2 | L | attention, decision, approval, budget incident; screens 1, 2, 3, 12 |
| 1a-3 | L | work, agents, runs, factory; screens 4, 10, 5, 6, 11, 7 |
| 1a-4 | M | demo data completeness, contract suites, polling policy, interop review |

## 13. References

- PRD: [../../paperclip-agent-factory-mobile-PRD.md](../../paperclip-agent-factory-mobile-PRD.md) (kept at the repository root: it is the product-level document several specs share, and `README.md`, `AGENTS.md` and `CLAUDE.md` link to it)
- Design mocks: [../../Paperclip-Remote-mobile-UI-design.html](../../Paperclip-Remote-mobile-UI-design.html)
- [INTENT.md](../../INTENT.md), [AGENTS.md](../../AGENTS.md), [parent plan](../261101-iphone-app/plan.md)
- Server types read: `@paperclipai/shared` 2026.1023.0 (`types/attention.d.ts`, `types/decision.d.ts`, `constants.d.ts`)
