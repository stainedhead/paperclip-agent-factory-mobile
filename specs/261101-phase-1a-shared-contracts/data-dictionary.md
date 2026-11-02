# Data dictionary: Phase 1a shared contracts

**Purpose:** the shared domain types, ports and enumerations. Names follow the server's types where one exists (verified against `@paperclipai/shared` 2026.1023.0). Status: draft, refined in research and architecture phases.

## Value objects

| Type | Rules |
|---|---|
| `BaseUri` | exists (Phase 0): https only, localhost/10.0.2.2 http in debug, no user info, no query |
| `InstanceId`, `CompanyId`, `AgentId`, `IssueId`, `RunId`, `ApprovalId`, `DecisionId` | opaque string ids, non-blank |
| `Secret` | wraps a string; `toString()` prints `Secret(***)`; no accessor that returns text except `reveal()` used only by the API auth plugin |
| `Money` | integer cents plus currency; arithmetic never loses precision; formatting is a presentation concern |
| `Severity` | `critical`, `high`, `medium`, `low` (server order) |
| `Role` | `owner`, `operator`, `viewer` |
| `SpkiPin` | base64 SHA-256 of a public key; validated length |

## Entities

| Entity | Key fields |
|---|---|
| `Instance` | `id`, `displayName` (1 to 40, unique), `baseUri`, `deploymentMode`, `exposure`, `authMethod`, `boardKeyId`, `keyExpiresAt`, `defaultCompanyId`, `lastCompanyId`, `pins`, `createdAt`, `lastConnectedAt`, `sortOrder` |
| `Company` | `id`, `name`, `role` (from membership) |
| `AttentionItem` | server shape: `id`, `sourceKind`, `subject`, `whyNow`, `decisionVerbs`, `inlineResolvable`, `severity`, `decideBy`, `snoozedUntil`, `dismissalKey`, `activityAt`, `originAgentName`, `detail`, `resolverAudience` |
| `AttentionFeed` | `items`, `nextCursor`, `totalCount`, `deskBadgeCount`, `countsBySourceKind`, `generatedAt` |
| `Decision` | question, origin agent, target issue, decide-by, `options`, state (`open` or final) |
| `DecisionOption` | `id`, `label`, `description`, `style` (`default`, `primary`, `destructive`), `effects`. The PRD's `inputs[]` (FR-042) is not on the server's `DecisionOption` type; `DecisionInput` (`id`, `label`, `placeholder`, `required`, `maxLength`) exists but where it attaches is unverified (research Q8) |
| `DecisionEffect` | sealed: `CommentOnIssue`, `CreateIssue`, `UpdateIssueStatus`, `AssignIssue`, `CancelIssueTree`, `ResolveBlocker` (each with `staleness`) |
| `Approval` | `id`, `type`, `status`, `payload` by type, requester, linked issues, comments |
| `BudgetIncident` | scope, observed spend, limit, status |
| `Issue` | `id`, `identifier`, title, `status`, `priority`, assignee, project, parent, subtasks |
| `Interaction` | sealed by kind (see enumerations) plus `Unsupported(kind)` |
| `Agent` | `id`, name, role, `status`, `lifecycleState`, reports-to, current issue, month spend, budget |
| `HeartbeatRun` | `id`, `status`, started, duration, events, log tail |
| `DashboardSummary`, `SpendWindow` | tiles and rolling spend (5 h, 24 h, 7 d) |
| `AppSettings` | device name, app lock on/off, idle period, notification defaults |

## Ports (domain interfaces)

`InstanceRepository`, `SecretStore`, `AppSettingsStore`, `PaperclipGateway` (split by feature: `AttentionGateway`, `DecisionGateway`, `ApprovalGateway`, `IssueGateway`, `AgentGateway`, `RunGateway`, `DashboardGateway`, `AuthGateway`, `HealthGateway`), `LiveEvents`, `Clock`, `Biometrics`, `Connectivity`, `PairingBrowser`.

## Enumerations (from the server)

| Name | Values |
|---|---|
| `AttentionSourceKind` | approval, decision, issue_thread_interaction, join_request, recovery_action, productivity_review, blocker_attention, review, failed_run, budget_alert, agent_error_alert |
| `AttentionSubjectKind` | approval, decision, issue, interaction, join_request, recovery_action, run, budget_incident, agent |
| `ApprovalType` | hire_agent, approve_ceo_strategy, budget_override_required, request_board_approval |
| `ApprovalStatus` | pending, revision_requested, approved, rejected, cancelled |
| `InteractionKind` | suggest_tasks, ask_user_questions, request_confirmation, request_checkbox_confirmation, request_item_verdicts, connection_intent |
| `AgentStatus` | active, paused, idle, running, error, pending_approval, terminated |
| `IssueStatus` | backlog, todo, in_progress, in_review, done, blocked, cancelled |
| `IssuePriority` | critical, high, medium, low |
| `RunStatus` | queued, scheduled_retry, running, succeeded, interrupted, failed, cancelled, timed_out |
| `DeploymentMode` | local_trusted (refused), authenticated |

## API request and response types

[TBD in Phase 1b: DTOs live in `infrastructure/api`; domain types never carry JSON annotations.]
