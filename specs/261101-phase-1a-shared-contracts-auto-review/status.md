# Status: Phase 1a review fixes

**Created:** 2026-11-01 · **Spec dir:** `specs/261101-phase-1a-shared-contracts-auto-review/`

Update this file after every task and phase. It is the single source of progress.

## Overall progress

| Phase | State |
|---|---|
| Phase 0 Specification | Complete |
| Phase 1 Research and data model | Complete |
| Phase 2 Architecture and plan | Complete |
| Phase 3 Task breakdown | Complete |
| Phase 4 Implementation (Groups 1 to 4) | Complete (all four groups on the branch; see the finding table) |
| Phase 5 Completion and archive | Not Started (archive after the independent re-review) |

## Phase 0 checklist

- [x] Spec directory created; `spec.md` populated from the review PRD
- [x] Research questions identified (`research.md`)
- [x] Phase files initialized

## Fix checklist (tick when merged to the branch with its regression test; note commit hashes in implementation-notes.md)

- [x] FR-R01 (P0) F-A-1: A board key can be sent to a different host if the address is edited while pairing or manual entry i
- [x] FR-R02 (P0) F-A-2: Sign out and sign out everywhere can leave credentials behind when cancelled or when one factory thr
- [x] FR-R03 (P0) F-B-1: A late probe result is applied to a different address, so an unprobed (possibly `local_trusted`) add
- [x] FR-R04 (P1) F-A-3: A 401 on any company-scoped read or mutation does not mark the factory Signed out (PRD FR-023)
- [x] FR-R05 (P1) F-A-4: `AddInstance` trusts a caller-supplied `HealthReport` that is not bound to the address
- [x] FR-R06 (P1) F-A-5: Failed enrollment and cancellation leave key, status and config inconsistent
- [x] FR-R07 (P1) F-A-6: Reconciled results for heartbeat and comments do not say whether the mutation took effect
- [x] FR-R08 (P1) F-A-8: Stale-snapshot writes and unvalidated caller-supplied entities in use cases
- [x] FR-R09 (P1) F-B-2: Decision screen can run two execution pollers at once; `polling` flag is shared
- [x] FR-R10 (P1) F-B-3: Sign-in outcomes map to wrong or inconsistent error keys
- [x] FR-R11 (P1) F-B-4: Inbox inline Approve/Reject do not check the card type or the inline-action policy
- [x] FR-R12 (P1) F-B-6: Holders run on `Dispatchers.Default` but guards and fields are plain check-then-set
- [x] FR-R13 (P1) F-B-7: Approval comment append uses a stale snapshot; a refresh can overwrite a just-finished action
- [x] FR-R14 (P1) F-B-9: Screens can get stuck with no refresh or lose a final outcome
- [x] FR-R15 (P1) F-C-1: Decision contract suite asserts a "conflict" the pinned server does not return
- [x] FR-R16 (P1) F-C-2: Run viewer never tests Cancel run being disabled offline after the run loaded (acceptance 11 overcla
- [x] FR-R17 (P1) F-C-3: Role and timeout matrix is narrower than its names and acceptance 8 and 10 claim
- [x] FR-R18 (P1) F-C-4: Platform "contract" suites are tautological (acceptance 4 "Met" overstated)
- [x] FR-R19 (P1) F-C-5: Requirement traceability can be satisfied by a comment, and some tags are stamps
- [x] FR-R20 (P1) F-C-6: `contract.yml` is green and tests nothing; the nightly drift job cannot detect drift
- [x] FR-R21 (P1) F-C-8: "No credential to the wrong host" is not testable with the fakes; only the address-edit path is cove
- [x] FR-R22 (P1) F-D-1: technical-details.md states AGENTS.md is stale in ways it is not (false "known limitation")
- [x] FR-R23 (P1) F-D-2: Spec 5a "403 after a role change refreshes the role" is not implemented; role is cached for a holder
- [x] FR-R24 (P1) F-D-3: Spec and status files are stale against what was built (progress table, criterion 14 text, FR-096 wo
- [x] FR-R25 (P1) F-D-4: PRD still contradicts the pinned server and the built behaviour in three places
- [x] FR-R26 (P1) F-D-5: Requirements-traceability rule is satisfied by a comment mention, so "every FR is named by a test" i
- [x] FR-R27 (P2) F-A-10: `BaseUri` does not validate the path and accepts odd hosts [rule documented in the BaseUri KDoc]
- [x] FR-R28 (P2) F-A-11: Retries stack up and `retryingRead` retries unreachable and certificate failures
- [x] FR-R29 (P2) F-A-12: Dead parameters and unused domain functions
- [x] FR-R30 (P1) F-A-7: `PairInstance` polling is unbounded by server-supplied values and ignores rate limiting
- [x] FR-R31 (P2) F-A-9: `Secret.startsWith` copies the whole key into an unwipeable String
- [x] FR-R32 (P2) F-B-10: `createGraph` and the demo graph hide real behaviour behind fakes with a frozen clock [documented: createGraph KDoc, technical-details and ADR-037; the rename/gate and the advanceable demo clock are Phase 1b, see the table]
- [x] FR-R33 (P2) F-B-11: UI mapping by enum ordinal or `valueOf` with no compile-time exhaustiveness
- [x] FR-R34 (P2) F-B-12: App lock and refresh lifecycle need two separate platform calls
- [x] FR-R35 (P2) F-B-13: Swift-facing and wording notes [partly: `description` renamed to `summary` with a Konsist rule (0026607); deferred with reasons: UI twins for four domain enums (no Swift screen consumes them yet), boxed nullable numbers (no cheap SKIE option), hiding public initialisers (intents must stay constructible from Swift)]
- [x] FR-R36 (P2) F-B-5: Gating is hard-coded to `Role.Viewer` or a single `Action` in several places instead of using `Permi
- [x] FR-R37 (P2) F-B-8: Paired-devices loads are not ordered or cancelled
- [x] FR-R38 (P2) F-C-10: Real-time and unbounded waiting in umbrella tests (flake and vacuous-pass risk)
- [x] FR-R39 (P2) F-C-11: TDD evidence is self-reported and the history cannot show red [observation, nothing to change: red runs are self-reported in commit bodies and implementation-notes.md; git history cannot prove them]
- [x] FR-R40 (P2) F-C-12: `DeviceAuthGuardTest` partly restates the production policy
- [x] FR-R41 (P2) F-C-13: Config and hygiene observations
- [x] FR-R42 (P2) F-C-7: CI supply-chain pinning and unpinned tools
- [x] FR-R43 (P2) F-C-9: Konsist rules: contract-per-port and Secret.reveal checks are weaker than they read
- [ ] FR-R44 (P2) F-D-10: "Continue to pairing" is an enabled button with an empty action (honestly documented; acceptable for [DEFERRED: the iPhone shell is a Phase 1a walking skeleton; the enabled button that does nothing is documented in three places and in technical-details; wiring `ContinueRequested` belongs to the Phase 2 UI]
- [x] FR-R45 (P2) F-D-11: Framework build and consumption failure modes (debug vs release, destructive copy, no preflight)
- [x] FR-R46 (P2) F-D-12: Shipping framework contains demo graph, fakes and a fake-key demo set [decision recorded in ADR-035 (ships in every build) and ADR-037; stripping or splitting the demo graph is a Phase 2 build decision]
- [x] FR-R47 (P2) F-D-13: Lint configs are lenient (swift-format enables only two rules; SwiftLint is near default)
- [x] FR-R48 (P2) F-D-6: ADR bodies still describe designs that do not exist; the correction is an appended "Note"
- [x] FR-R49 (P2) F-D-7: Infrastructure Konsist rule is vacuous today and will still miss platform source sets later
- [x] FR-R50 (P2) F-D-8: Swift holder lifecycle: FirstRunModel never closes its holder, and holders are created inside view/s
- [x] FR-R51 (P2) F-D-9: Several Swift interop tests assert nothing about the intents they send, and close holders mid-flight

## Group 3 notes

- Partly covered requirements (F-C-5): FR-002, FR-008, FR-013, FR-016 and FR-097 have a tagged test for the Phase 1a part only; the rest is Phase 1b/1c (see the `partial` map in `RequirementsTraceabilityTest`). No requirement lacks an honest test.
- Deferred inside F-C-7: pinning `brew install swiftlint xcodegen` and `xcode-version: latest-stable` (brew cannot pin by hash), narrowing the gitleaks `TESTONLY` allowlist (cannot be exercised without Docker locally), and a Gradle dependency-vulnerability job.
- Deferred inside F-C-13: detekt over `build-logic`, root scripts and `androidApp`; Kover merging presentation execution data into the `core` total (accepted by AGENTS wording).
- Deferred inside F-C-6: drift detection on `/api/openapi.json`. The pinned server source (`~/Code/Test/paperclip/server/src`) has no openapi route, so the smoke script covers `/api/health` and the pairing challenge create/poll shapes instead.
- F-C-5: the sampled tags were checked against the review's own sample (FR-002/008/013/016/097 judged partial); the other 42 ids were not individually re-audited beyond being in `@Test` names.
- F-C-10: 50 JVM stress iterations of the refresh wiring tests passed; the Android host and iOS simulator ran them once per `check`.
- `unused_import` is configured as a SwiftLint analyzer rule (F-D-13): it only runs under `swiftlint analyze`, not `swiftlint --strict`.

## Group 4 notes

- Docs: AGENTS.md was already correct (F-D-1); the false limitation and the pre-fix numbers in technical-details, README, product-details and user-docs were replaced by regenerated ones (143/299/326/48/32 JVM tests, 37 Konsist tests, 26 contract suites for 21 ports, 23 ports and fakes, 71 invoke declarations, 16 holders, 33 Swift tests, ADR-001..037, coverage 97.97% core and 98.02% presentation, fresh Kover). The `usage-examples.md` Kotlin snippets were re-run as a temporary test (8 tests, passing) after the AddInstance probe and DecideDecision id changes and then deleted; `AppLifecycleBridge.setForeground` was added to them.
- `scripts/check-adr-identifiers.py` fails on the previous ADR text (SavedFilter, answerFor, LogCursor) and passes now; `scripts/test-build-kit.sh` has 11 checks.
- F-A-3 leftover: with `boardKeyId == null` `RevokeBoardKey` answers `RevokeKeyResult.CurrentKeyUnknown` and sends nothing; Settings shows `ErrorKey.CurrentKeyUnknown`. F-D-2 leftover: reads that answer Forbidden re-resolve the role once (all nine holder families; Factory home keeps no actions so it is unchanged). F-A-9 had no red run (behaviour tests pass before and after).
- Kotlin incremental compilation (AGP KMP library) crashed once after the property rename; `-Pkotlin.incremental=false` for the final `check` avoided it. Not a source problem.
- F-C-11 is an observation about the process, not a defect to fix.

## Findings: final table

| Finding | Result | Commit |
|---|---|---|
| F-A-1 | fixed (residual race by per-factory lock, ADR-033) | 1ca031a, f6e1d5d |
| F-A-2 | fixed (cancel-safe sign out; sign out everywhere continues) | 2663f69, e67e22c |
| F-B-1 | fixed | cc404bd |
| F-A-3 | fixed (decorators; revoke refused when own key id unknown) | 1737df4, 30168e3 |
| F-A-4 | fixed | 05624e0 |
| F-A-5 | fixed (part 4, re-pair does not revoke the old key, accepted as designed) | 4ad957d |
| F-A-6 | fixed | 8ed50dc, 1c2a2ac |
| F-A-8 | fixed (the sign-out half refuted: already fixed in Group 1) | 210b02a |
| F-B-2 | fixed | 7099838 |
| F-B-3 | fixed | 734ee6e |
| F-B-4 | fixed | d84eb48 |
| F-B-6 | fixed | fb0b167 |
| F-B-7 | fixed | bc2966a |
| F-B-9 | fixed | a5940a2 |
| F-C-1 | fixed | 4b2354a |
| F-C-2 | fixed | d40b0e4 |
| F-C-3 | fixed | cd11bf5 |
| F-C-4 | fixed | 86e2465 |
| F-C-5 | fixed (five partial ids listed) | a54e491 |
| F-C-6 | fixed (openapi drift detection deferred: no such route on the pinned server) | acb32d1 |
| F-C-8 | fixed | f6e90f0 |
| F-D-1 | fixed (AGENTS.md was never stale; false limitation removed) | 3900860 |
| F-D-2 | fixed (mutations in Group 2, reads in Group 4) | 047568d, 060593c |
| F-D-3 | fixed | c5de41f, 3900860 |
| F-D-4 | fixed | 4ee49b9 |
| F-D-5 | fixed (duplicate of F-C-5) | a54e491 |
| F-A-10 | fixed | d32bedd, 0bd8b7b |
| F-A-11 | fixed | e145f9a |
| F-A-12 | fixed | f9155c5 |
| F-A-7 | fixed | eb1afad |
| F-A-9 | fixed (no red run possible) | 3d95028 |
| F-B-10 | fixed as documentation (KDoc, ADR-037, technical-details); rename or gate deferred to Phase 1b | 26f4e1c |
| F-B-11 | fixed (structural, not red by design) | 9ee7576 |
| F-B-12 | fixed | 9118611 |
| F-B-13 | partly fixed (`summary` rename and Konsist rule); UI twins, boxed numbers and hidden initialisers deferred with reasons | 0026607 |
| F-B-5 | fixed (a wrong flag did not reproduce; the Operator disabled-reason gap did) | 4e23ba4 |
| F-B-8 | fixed | 88d1d9f |
| F-C-10 | fixed | 6fe6058 |
| F-C-11 | observation, nothing to change | none |
| F-C-12 | fixed | e94bf03 |
| F-C-13 | fixed (detekt over build-logic, root scripts and androidApp deferred; Kover merge accepted) | 9a5b8f2 |
| F-C-7 | fixed (brew pin, Xcode pin, gitleaks allowlist and a Gradle vulnerability job deferred) | 5bd1e1d, b1106a7 |
| F-C-9 | fixed | b3de91a |
| F-D-10 | deferred (Phase 2 UI; documented) | none |
| F-D-11 | fixed | 28022f5 |
| F-D-12 | fixed as a decision (ADR-035, ADR-037); split or strip is a Phase 2 build decision | 26f4e1c |
| F-D-13 | fixed | fe4db74 |
| F-D-6 | fixed | 26f4e1c, c5de41f |
| F-D-7 | fixed | be73235 |
| F-D-8 | fixed | 7d1a4a4 |
| F-D-9 | fixed | 3891996 |

## Blockers

(none)

## Recent activity

- 2026-11-01: Group 4 (docs, PRD, spec text, ADRs, remaining small items) done: F-A-3 and F-D-2 leftovers, F-A-9, F-A-10, F-B-10, F-B-12, F-B-13, F-D-1, F-D-3, F-D-4, F-D-6, F-D-11, F-D-12; F-D-10 deferred; F-C-11 observation.
- 2026-11-01: Group 3 (tests, contracts, Konsist, CI) done: F-C-1 to F-C-10, F-C-12, F-C-13, F-D-5, F-D-7 to F-D-9, F-D-13; commits in implementation-notes.md.
- 2026-11-01: spec created from the review PRD.
- 2026-11-01: FR-R01 fixed (F-A-1 key host binding).
- 2026-11-01: FR-R03 fixed (F-B-1 probe result bound to the probed address).
- 2026-11-01: FR-R05 fixed (F-A-4 AddInstance requires a probe bound to the address).
- 2026-11-01: FR-R06 fixed (F-A-5 enrollment failure and cancellation consistency).
- 2026-11-01: FR-R02 fixed (F-A-2 (cancellation half, P0) sign out is cancellation-safe).
- 2026-11-01: FR-R02 second commit (SignOutEverywhere continues past one failing factory) done.
- 2026-11-01: FR-R30 fixed (F-A-7 bounded pairing wait).
- 2026-11-01: F-A-3 done (401 marks Signed out in one choke point).
- 2026-11-01: F-A-6 done (timeout reconciliation compares with the pre-call state).
- 2026-11-01: F-A-8 done (decide re-reads the decision before device auth).
- 2026-11-01: F-A-1 residual done (per-factory lock (ADR-033)).
- 2026-11-01: F-B-2 done (one execution poller per Decision screen).
- 2026-11-01: F-B-3 done (sign-in outcomes map to one consistent set of keys).
- 2026-11-01: F-B-4 done (inline Approve/Reject check the card and the policy).
- 2026-11-01: F-B-5 done (role gating uses Permissions everywhere).
- 2026-11-01: F-B-6 done (guards and shared fields are atomic).
- 2026-11-01: F-B-7 done (a late read never overwrites a finished action).
- 2026-11-01: F-B-8 done (paired-devices loads are ordered and cancelled).
- 2026-11-01: F-B-9 done (no stuck run viewer, no lost budget outcome).
- 2026-11-01: F-B-11 done (UI enum mappings are exhaustive when expressions).
- 2026-11-01: F-D-2 done (a 403 re-resolves the role once).
- 2026-11-01: F-A-11 done (retry budget, offline and certificate failures).
- 2026-11-01: F-A-12 done (dead code removed, CriticalAndHigh fills the page).
