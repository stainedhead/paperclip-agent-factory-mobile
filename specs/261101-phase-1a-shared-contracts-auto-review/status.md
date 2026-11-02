# Status: Phase 1a review fixes

**Created:** 2026-11-01 · **Spec dir:** `specs/261101-phase-1a-shared-contracts-auto-review/`

Update this file after every task and phase. It is the single source of progress.

## Overall progress

| Phase | State |
|---|---|
| Phase 0 Specification | In Progress |
| Phase 1 Research and data model | Not Started |
| Phase 2 Architecture and plan | Not Started |
| Phase 3 Task breakdown | Not Started |
| Phase 4 Implementation (Groups 1 to 4) | Not Started |
| Phase 5 Completion and archive | Not Started |

## Phase 0 checklist

- [x] Spec directory created; `spec.md` populated from the review PRD
- [x] Research questions identified (`research.md`)
- [x] Phase files initialized

## Fix checklist (tick when merged to the branch with its regression test; note commit hashes in implementation-notes.md)

- [x] FR-R01 (P0) F-A-1: A board key can be sent to a different host if the address is edited while pairing or manual entry i
- [ ] FR-R02 (P0) F-A-2: Sign out and sign out everywhere can leave credentials behind when cancelled or when one factory thr
- [x] FR-R03 (P0) F-B-1: A late probe result is applied to a different address, so an unprobed (possibly `local_trusted`) add
- [ ] FR-R04 (P1) F-A-3: A 401 on any company-scoped read or mutation does not mark the factory Signed out (PRD FR-023)
- [x] FR-R05 (P1) F-A-4: `AddInstance` trusts a caller-supplied `HealthReport` that is not bound to the address
- [x] FR-R06 (P1) F-A-5: Failed enrollment and cancellation leave key, status and config inconsistent
- [ ] FR-R07 (P1) F-A-6: Reconciled results for heartbeat and comments do not say whether the mutation took effect
- [ ] FR-R08 (P1) F-A-8: Stale-snapshot writes and unvalidated caller-supplied entities in use cases
- [ ] FR-R09 (P1) F-B-2: Decision screen can run two execution pollers at once; `polling` flag is shared
- [ ] FR-R10 (P1) F-B-3: Sign-in outcomes map to wrong or inconsistent error keys
- [ ] FR-R11 (P1) F-B-4: Inbox inline Approve/Reject do not check the card type or the inline-action policy
- [ ] FR-R12 (P1) F-B-6: Holders run on `Dispatchers.Default` but guards and fields are plain check-then-set
- [ ] FR-R13 (P1) F-B-7: Approval comment append uses a stale snapshot; a refresh can overwrite a just-finished action
- [ ] FR-R14 (P1) F-B-9: Screens can get stuck with no refresh or lose a final outcome
- [ ] FR-R15 (P1) F-C-1: Decision contract suite asserts a "conflict" the pinned server does not return
- [ ] FR-R16 (P1) F-C-2: Run viewer never tests Cancel run being disabled offline after the run loaded (acceptance 11 overcla
- [ ] FR-R17 (P1) F-C-3: Role and timeout matrix is narrower than its names and acceptance 8 and 10 claim
- [ ] FR-R18 (P1) F-C-4: Platform "contract" suites are tautological (acceptance 4 "Met" overstated)
- [ ] FR-R19 (P1) F-C-5: Requirement traceability can be satisfied by a comment, and some tags are stamps
- [ ] FR-R20 (P1) F-C-6: `contract.yml` is green and tests nothing; the nightly drift job cannot detect drift
- [ ] FR-R21 (P1) F-C-8: "No credential to the wrong host" is not testable with the fakes; only the address-edit path is cove
- [ ] FR-R22 (P1) F-D-1: technical-details.md states AGENTS.md is stale in ways it is not (false "known limitation")
- [ ] FR-R23 (P1) F-D-2: Spec 5a "403 after a role change refreshes the role" is not implemented; role is cached for a holder
- [ ] FR-R24 (P1) F-D-3: Spec and status files are stale against what was built (progress table, criterion 14 text, FR-096 wo
- [ ] FR-R25 (P1) F-D-4: PRD still contradicts the pinned server and the built behaviour in three places
- [ ] FR-R26 (P1) F-D-5: Requirements-traceability rule is satisfied by a comment mention, so "every FR is named by a test" i
- [ ] FR-R27 (P2) F-A-10: `BaseUri` does not validate the path and accepts odd hosts
- [ ] FR-R28 (P2) F-A-11: Retries stack up and `retryingRead` retries unreachable and certificate failures
- [ ] FR-R29 (P2) F-A-12: Dead parameters and unused domain functions
- [ ] FR-R30 (P1) F-A-7: `PairInstance` polling is unbounded by server-supplied values and ignores rate limiting
- [ ] FR-R31 (P2) F-A-9: `Secret.startsWith` copies the whole key into an unwipeable String
- [ ] FR-R32 (P2) F-B-10: `createGraph` and the demo graph hide real behaviour behind fakes with a frozen clock
- [ ] FR-R33 (P2) F-B-11: UI mapping by enum ordinal or `valueOf` with no compile-time exhaustiveness
- [ ] FR-R34 (P2) F-B-12: App lock and refresh lifecycle need two separate platform calls
- [ ] FR-R35 (P2) F-B-13: Swift-facing and wording notes
- [ ] FR-R36 (P2) F-B-5: Gating is hard-coded to `Role.Viewer` or a single `Action` in several places instead of using `Permi
- [ ] FR-R37 (P2) F-B-8: Paired-devices loads are not ordered or cancelled
- [ ] FR-R38 (P2) F-C-10: Real-time and unbounded waiting in umbrella tests (flake and vacuous-pass risk)
- [ ] FR-R39 (P2) F-C-11: TDD evidence is self-reported and the history cannot show red
- [ ] FR-R40 (P2) F-C-12: `DeviceAuthGuardTest` partly restates the production policy
- [ ] FR-R41 (P2) F-C-13: Config and hygiene observations
- [ ] FR-R42 (P2) F-C-7: CI supply-chain pinning and unpinned tools
- [ ] FR-R43 (P2) F-C-9: Konsist rules: contract-per-port and Secret.reveal checks are weaker than they read
- [ ] FR-R44 (P2) F-D-10: "Continue to pairing" is an enabled button with an empty action (honestly documented; acceptable for
- [ ] FR-R45 (P2) F-D-11: Framework build and consumption failure modes (debug vs release, destructive copy, no preflight)
- [ ] FR-R46 (P2) F-D-12: Shipping framework contains demo graph, fakes and a fake-key demo set
- [ ] FR-R47 (P2) F-D-13: Lint configs are lenient (swift-format enables only two rules; SwiftLint is near default)
- [ ] FR-R48 (P2) F-D-6: ADR bodies still describe designs that do not exist; the correction is an appended "Note"
- [ ] FR-R49 (P2) F-D-7: Infrastructure Konsist rule is vacuous today and will still miss platform source sets later
- [ ] FR-R50 (P2) F-D-8: Swift holder lifecycle: FirstRunModel never closes its holder, and holders are created inside view/s
- [ ] FR-R51 (P2) F-D-9: Several Swift interop tests assert nothing about the intents they send, and close holders mid-flight

## Blockers

(none)

## Recent activity

- 2026-11-01: spec created from the review PRD.
- 2026-11-01: FR-R01 fixed (F-A-1 key host binding).
- 2026-11-01: FR-R03 fixed (F-B-1 probe result bound to the probed address).
- 2026-11-01: FR-R05 fixed (F-A-4 AddInstance requires a probe bound to the address).
- 2026-11-01: FR-R06 fixed (F-A-5 enrollment failure and cancellation consistency).
