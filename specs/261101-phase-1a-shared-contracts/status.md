# Status: Phase 1a shared contracts

**Created:** 2026-11-01 · **Spec dir:** `specs/261101-phase-1a-shared-contracts/`

Update this file after every task and phase. It is the single source of progress.

## Overall progress

| Phase | State |
|---|---|
| Phase 0 Specification | In Progress |
| Phase 1 Research and data model | Not Started |
| Phase 2 Architecture and plan | Not Started |
| Phase 3 Task breakdown | Not Started |
| Phase 4 Implementation (slices 1a-1 to 1a-4) | In Progress (1a-1 to 1a-3 built, awaiting PRs) |
| Phase 5 Completion and archive | Not Started |

## Phase 0 checklist

- [x] Spec directory created and `spec.md` populated from the PRD and the server types
- [x] Research questions identified (`research.md`)
- [x] Phase files initialized
- [x] `/dev-flow:review-spec` run 2026-11-01: verdict Needs revision; edge cases, acceptance criteria 8 to 14, task count, decision inputs and open-question paths fixed. Remaining warnings: use case list and sequence diagrams (due before slice 1a-2).
- [x] PRD updated for the badge decision (FR-030, FR-032, FR-082, design conventions); [ ] other server differences in spec section 5 (source kinds, sixth interaction kind, `deploymentExposure`)

## Slice 1a-1 (instances, secrets, settings, auth, roles; screens 15, 8, 16, 9, 13)

- [x] Research Q6/Q9, health, challenge create/poll/approve answered in `research.md` (ADR-011, ADR-012)
- [x] Domain: value objects, `Secret`, `DomainError`, `Role`, `Action`, `Permissions`, `DeviceAuthPolicy`, `AppLockPolicy`, entities, ports
- [x] Testing: fakes for all ten ports, `TestData`, contract suites for `InstanceRepository`, `SecretStore`, `AppSettingsStore`
- [x] Application: probe, add, update, reorder, remove, observe, switch, pair, manual key, sign out (+ everywhere), key list and revoke, expiry status, credential check, settings, deep links
- [x] Presentation: `FirstRun`, `Factories`, `ManageFactories`, `EditInstance`, `Settings`, `App` state holders
- [x] Umbrella wiring on fakes; iOS app and tests build and pass (`GraphInteropTests` added)
- [x] Konsist: fake per port, `Secret.reveal()` callers, no `Result` or declared generics in the presentation surface
- [x] `./gradlew spotlessApply && ./gradlew check lint -q` green; iOS `build-kit`, SwiftLint, swift-format and `ios-test.sh` green
- [ ] Open the slice 1a-1 pull request (not opened by the implementing agent)

## Blockers

- Phase 0 pull requests #1 to #3 are not merged yet. Phase 1a branches stack on `phase-0/contract-server`.
- None for the badge: resolved 2026-11-01 (match the web app, `deskBadgeCount`).

## Recent activity

- 2026-11-01: slice 1a-3 implemented test first (see the TDD log in implementation-notes.md): research W1 to D1 answered with file and line, ADR-021 to ADR-024, domain for issues, interactions, agents, runs and dashboard, five ports with fakes, 21 use cases, six state holders, `graph.work`, `graph.agents`, `graph.runs`, `graph.factory`, iOS interop tests.
- 2026-11-01: slice 1a-2 implemented test first (see the TDD log in implementation-notes.md): 24 use cases, four state holders, five fakes, six ports, ADR-015 to ADR-020, Konsist use-case boundary rules.
- 2026-11-01: slice 1a-1 implemented on `feat/phase-1a-shared-contracts` (domain, fakes, 30 use cases, six state holders, Koin wiring, Konsist rules, iOS interop test).
- 2026-11-01: spec created from the PRD, the iPhone plan and the pinned server's shared types.

## Slice 1a-2 (attention, decisions, approvals, budget incidents; screens 1, 2, 3, 12)

- [x] Research Q2, Q3, Q4, Q5, Q7, Q8 answered with file and line in `research.md`; four sequence diagrams in `architecture.md`; ADR-015 to ADR-020
- [x] Domain: Severity, Money, DecideBy, AttentionRules (UTC table tests), InboxGrouping, attention model, InlineActionPolicy, Decision, Approval, BudgetIncident, SnoozeTimes, ports (strict TDD, red seen first)
- [x] Testing: five gateway/cache fakes + time zone fake, fixtures, AttentionCache contract suite
- [x] Application: 24 use cases (attention, decision, approval, budget, company context)
- [x] Presentation: Inbox, Decision, Approval and Budget incident state holders
- [x] Umbrella wiring, Konsist use-case boundary rules
- [x] `./gradlew spotlessApply && ./gradlew check lint -q` green; Kover (reportCore.xml, domain+application) 98.94% lines (744 of 752), (reportPresentation.xml) 96.45% (1115 of 1156)
- [x] iOS: `build-kit.sh debug`, SwiftLint, swift-format and `ios-test.sh` green (`GraphInteropTests` extended; slice 1a-2 screens are reached through `graph.attention`)
- [ ] Open the slice 1a-2 pull request (not opened by the implementing agent)

## Slice 1a-3 (work, interactions, agents, runs, factory home; screens 4, 10, 5, 6, 11, 7)

- [x] Research W1 to W8, A1, A2, R1, D1 answered with file and line in `research.md`; ADR-021 to ADR-024
- [x] Domain: `Issue`, `IssueFilter`, `IssueQuery`, `Comment`, `PlanDocument`, `Attachment`, sealed `Interaction` with `InteractionAnswer` and `validate`, `Agent` (grouping and action rules), `HeartbeatRun`, `RunEvents`, `LogTail`, `DashboardSummary`, `SpendWindows`; ports `IssueGateway`, `InteractionGateway`, `AgentGateway`, `RunGateway`, `DashboardGateway` (strict TDD, red seen first)
- [x] Testing: five gateway fakes with call counters and scripted failures, `WorkFixtures`, `AgentFixtures`, `WorkFakesBehaviourTest`
- [x] Application: 21 use cases in `issue`, `interaction`, `agent`, `run`, `dashboard`; role x action matrix and single-attempt tests (acceptance 8 and 10); device authentication before the gateway for pause agent and cancel run (acceptance 9)
- [x] Presentation: Work list, Issue (with interaction forms), Factory home, Agents, Agent detail and Run viewer state holders (offline acceptance 11 in each)
- [x] Umbrella wiring as `graph.work`, `graph.agents`, `graph.runs`, `graph.factory`; Konsist: mutation rule covers the new packages, `DeviceAuthRulesTest`
- [x] `./gradlew spotlessApply && ./gradlew check lint -q` green; Kover (fresh `reportCore.xml`, domain+application) 97.42% lines (2191 of 2249); (fresh `reportPresentation.xml`) 97.87% (3822 of 3905)
- [x] iOS: `build-kit.sh debug`, SwiftLint, swift-format and `ios-test.sh` green (`GraphInteropTests` has a case per new group)
- [ ] Open the slice 1a-3 pull request (not opened by the implementing agent)
