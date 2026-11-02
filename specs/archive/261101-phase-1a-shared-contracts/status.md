# Status: Phase 1a shared contracts

> **Corrections after review (2026-11-01).** Review finding F-D-3: the progress table already matched the slices; changed here are the unticked PRD item (done in F-D-4), the criterion 7 row (a pull request and green workflows exist), criterion 14 (the wording the code implements), contract suite and test counts, and coverage after the review fixes. Numbers in the slice sections are as of their slice.


**Created:** 2026-11-01 · **Spec dir:** `specs/archive/261101-phase-1a-shared-contracts/`

Update this file after every task and phase. It is the single source of progress.

## Overall progress

| Phase | State |
|---|---|
| Phase 0 Specification | Complete (reviewed 2026-11-01) |
| Phase 1 Research and data model | Complete (research answers with file:line in `research.md`; data dictionary) |
| Phase 2 Architecture and plan | Complete (sequence diagrams in `architecture.md`; ADR-010 to ADR-029) |
| Phase 3 Task breakdown | Complete at slice level (`tasks.md`) |
| Phase 4 Implementation (slices 1a-1 to 1a-4) | Complete on `feat/phase-1a-shared-contracts`; acceptance criteria 1 to 6 and 8 to 14 met, criterion 7 (CI) tracked on draft PR #5 |
| Phase 5 Completion and archive | Archived 2026-11-01 as the first step of the review-fix cycle. Code review found 51 issues (3 P0, 24 P1, 24 P2) that are tracked and fixed in `specs/archive/261101-phase-1a-shared-contracts-auto-review/` (created next) and the review PRD |

## Phase 0 checklist

- [x] Spec directory created and `spec.md` populated from the PRD and the server types
- [x] Research questions identified (`research.md`)
- [x] Phase files initialized
- [x] `/dev-flow:review-spec` run 2026-11-01: verdict Needs revision; edge cases, acceptance criteria 8 to 14, task count, decision inputs and open-question paths fixed. Remaining warnings: use case list and sequence diagrams (due before slice 1a-2).
- [x] PRD updated for the badge decision (FR-030, FR-032, FR-082, design conventions); [x] other server differences in spec section 5 (source kinds, sixth interaction kind, `deploymentExposure`, no pre-pairing version) done in review fix F-D-4

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

- 2026-11-01: slice 1a-4 implemented test first (TDD log in implementation-notes.md): demo data set and `createDemoGraph()`, contract suites for every port, `RefreshVisibleScreen` with `LiveEvents` and `InvalidationRules`, Decision Snooze, requirement traceability rule, Swift interop review and tests, ADR-025 to ADR-029.
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
- [x] `./gradlew spotlessApply && ./gradlew check lint -q` green; Kover (fresh `reportCore.xml`, domain+application) 97.42% lines (2192 of 2250); (fresh `reportPresentation.xml`) 97.88% (3826 of 3909)
- [x] iOS: `build-kit.sh debug`, SwiftLint, swift-format and `ios-test.sh` green (`GraphInteropTests` has a case per new group)
- [ ] Open the slice 1a-3 pull request (not opened by the implementing agent)

## Slice 1a-4 (demo data, contract suites, polling policy, interop review)

- [x] Demo data (`shared/testing/demo`) and `PaperclipCore.createDemoGraph()`; `DemoGraphTest` (14) and `DemoGraphDeviceTest` (5) drive every screen through the graph; ADR-026
- [x] Contract suites for the 21 ports a real adapter could break (26 subclasses; Connectivity and AppLifecycle are exempt), Konsist rule that every port is named by a suite; ADR-028
- [x] `RefreshVisibleScreen`, `LiveEvents`, `AppLifecycle`, `InvalidationRules`, `refreshTarget` on ten holders, `graph.refresh`, `graph.lifecycle`; ADR-027
- [x] Carry-overs: Decision Snooze from an Inbox card (ADR-025); budget resolved versus dismissed documented as a known limitation (ADR-029); Swift loading-phase races removed
- [x] Requirement traceability (`RequirementsTraceabilityTest`): all 47 Phase 1a FR ids are named by a `commonTest` test; no exemptions
- [x] Swift interop review recorded in implementation-notes.md; `DemoGraphInteropTests` (14); `ios-test.sh`, SwiftLint and swift-format green, zero Swift warnings
- [x] `./gradlew spotlessApply && ./gradlew check lint -q` green. Kover, measured fresh with `rm -rf build/reports/kover && ./gradlew koverXmlReportCore koverXmlReportPresentation -q`: `reportCore.xml` (domain+application) LINE 97.58% (2256 covered, 56 missed); `reportPresentation.xml` LINE 97.96% (3947 covered, 82 missed); gates stay at 90%. After the review fixes (fresh run 2026-11-01): core 97.97% (2558 covered, 53 missed), presentation 98.02% (4217 covered, 85 missed)
- [ ] Open the slice 1a-4 pull request (not opened by the implementing agent)

## Acceptance criteria 1 to 14 (spec section 10), checked 2026-11-01

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Every Phase 1a FR is named by a test on JVM, Android host and iOS simulator | Met | `RequirementsTraceabilityTest` (Konsist, JVM) requires each of the 47 ids in a `commonTest` source; `commonTest` runs in `jvmTest`, `testAndroidHostTest` and `iosSimulatorArm64Test` under `./gradlew check`. FR-002 is the persistence port contract plus `PairInstanceTest` (the token is only in the secret store, never in the configuration or settings); the SQLDelight adapter is Phase 1b; FR-013 and FR-097 gained tags and `ErrorMappingTest`. Review fix F-C-5: a tag now counts only in the name of a `@Test` function (a comment no longer satisfies the rule), and five requirements are covered for their Phase 1a part only: FR-002, FR-008 (deep link, not QR), FR-013, FR-016 (pin format, not pin enforcement) and FR-097 (error to error key, not JSON to error); they are listed in the `partial` map of the rule. Exemptions: none. |
| 2 | `koverVerifyCore` and `koverVerifyPresentation` at or above 90% | Met | 97.58% core, 97.96% presentation at archive time; 97.97% and 98.02% after the review fixes (fresh reports); gates unchanged, run by `./gradlew check`. |
| 3 | A state-holder test script per screen with error and role-gated variants | Met for the 15 Phase 1a screens plus the app shell (16 holders) | `FirstRunStateHolderTest`, `FactoriesAndManageTest`, `EditInstanceStateHolderTest`, `SettingsAndAppTest`, `InboxStateHolderTest`, `DecisionStateHolderTest`, `DecisionSnoozeTest`, `ApprovalStateHolderTest`, `BudgetIncidentStateHolderTest`, `WorkListStateHolderTest`, `IssueStateHolderTest`, `IssueInteractionsTest`, `AgentsStateHolderTest`, `AgentDetailStateHolderTest`, `RunViewerStateHolderTest`, `FactoryHomeStateHolderTest`. Screen 14 (notification settings, FR-083 and FR-086) is Phase 3 and out of Phase 1a scope (spec section 4). |
| 4 | Every port has a fake and the same contract suite runs against it | Partly: fake-only; real adapters in Phase 1b | 26 contract subclasses (one per suite; 25 when this row was written) run each suite against its fake only (no real adapter exists yet, so "the same suite runs against it" is not yet true). Review fix F-C-4: the platform suites now check real obligations (Biometrics never reuses an approval and reports each result unchanged; Clock moves on; PairingBrowser opens exactly the approval link; pairing secrets never print their value) and each was shown to fail when its fake rule is broken; the Connectivity and AppLifecycle suites were deleted because a `StateFlow` already guarantees what they checked (listed as exempt in `PortAndSecretRulesTest`). The Decision suite follows the pinned server (F-C-1). |
| 5 | The demo graph renders the mocks' data through the state holders | Met, with documented gaps | `DemoGraphFactoriesTest`, `DemoGraphInboxTest`, `DemoGraphDetailTest`, `DemoGraphWorkTest`, `DemoGraphAgentsTest`, `DemoGraphRunAndFactoryTest` (14 tests), `DemoGraphDeviceTest` (Settings, Manage factories, Edit factory, App, First run; 5 tests) and Swift `DemoGraphInteropTests`. Gaps and the severity-order deviation: implementation-notes.md "Deviations and gaps (slice 1a-4)". |
| 6 | Swift interop review recorded; no Swift 6 concurrency warnings | Met | implementation-notes.md "Swift interop review (slice 1a-4)"; `./scripts/ios-test.sh` green with no warnings; `swiftlint --strict iosApp` 0 violations; `swift-format lint --recursive --strict` clean. |
| 7 | `check lint`, `ios.yml`, `contract.yml`, `security.yml` green | Met on draft PR #5 | `./gradlew check lint -q` exit 0; `build-kit.sh debug`, SwiftLint, swift-format and `ios-test.sh` green locally. On GitHub the `shared`, `ios`, `contract` and `security` workflows passed on the pull request runs of 2026-11-01 and 2026-11-02 UTC (the contract job runs `scripts/contract-smoke.sh` against the pinned server, review fix F-C-6). Corrected after review: this row said no pull request existed. |
| 8 | (role, action) table; viewer never reaches a gateway | Met | `WorkRoleMatrixTest` ("acceptance 8": a viewer never reaches a gateway; owners and operators differ only on the agent budget), "acceptance 8" cases in the Agent detail, Issue, Run viewer and Work list holder tests, and `PermissionsTest` (domain). |
| 9 | Destructive actions need `Biometrics` first (cancel, failure, lockout) | Met | `DeviceAuthGuardTest`, `DeviceAuthRulesTest` (Konsist: exactly the destructive use cases depend on the guard), "acceptance 9" cases in the Agents, Agent detail and Run viewer holder tests, FR-014 cases in the Decision, Approval, Budget and Inbox tests, `RemoveInstanceTest`. |
| 10 | A timed-out mutation is attempted once, then the entity is re-read | Met | `WorkRoleMatrixTest` ("acceptance 10 a timeout on any mutation reaches the gateway exactly once") and "acceptance 10" cases in the Inbox, Decision, Approval, Budget incident, Work list, Issue, Agents, Agent detail and Run viewer holder tests and the Decision, Approval and Budget use case tests; `RemoveInstanceTest` (ADR-012). |
| 11 | Offline: cached data with timestamp, every mutation disabled with a reason | Met | "acceptance 11" cases in the Work list, Issue, Agents, Agent detail and Factory home holder tests; offline cases in the Inbox, Decision, Approval, Budget incident and Run viewer holder tests; `DecisionSnoozeTest` covers the new Snooze. Review fix F-C-2: the Run viewer had no offline-after-load case; `RunViewerCancelTest` now has three (`acceptance 11 offline after load disables Cancel run with the Offline reason and makes no call`, `... going offline after the confirmation was shown still sends nothing`, `... offline then online enables Cancel run again`), and deleting any of the three guards makes one of them fail. |
| 12 | A decision or approval resolved elsewhere shows its final state and no options | Met | "FR-044" tests in `DecisionStateHolderTest`, `ApprovalStateHolderTest`, `DecisionUseCasesTest`, `ApprovalUseCasesTest`. |
| 13 | `decideBy` table incl. the UTC midnight boundary | Met | `AttentionRulesTest`, `AttentionBasicsTest`, `InboxGroupingTest`. |
| 14 | Konsist rule restricts `Secret.reveal()` callers | Met, as reworded in spec section 10 | `PortAndSecretRulesTest` "Secret reveal is called only from infrastructure, the testing fakes and Secret itself". The original text named the API auth plugin; the built rule allows infrastructure, `shared/testing` and `Secret` itself. |

Open before the pull request: (the four workflows have run, criterion 7); decide whether to join the agent name in the Approval screen and to replace boxed nullable numbers on the Swift surface (both deferred, see the interop review).

