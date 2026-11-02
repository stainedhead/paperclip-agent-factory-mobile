# Specification: Phase 1a review fixes

**Feature:** fix the defects, false assurance and stale documentation found by the Step 4 code review of Phase 1a
**Created:** 2026-11-01 · **Status:** Implemented (Groups 1 to 4); see status.md · **Spec dir:** `specs/261101-phase-1a-shared-contracts-auto-review/`
**Source PRD:** [phase-1a-shared-contracts-auto-review-PRD.md](./phase-1a-shared-contracts-auto-review-PRD.md) (full text of every finding, location, fix and acceptance criteria)
**Parent spec:** [../archive/261101-phase-1a-shared-contracts/spec.md](../archive/261101-phase-1a-shared-contracts/spec.md)

## 1. Executive summary

Four reviewers and a verification stage audited the Phase 1a branch (`feat/phase-1a-shared-contracts`). The architecture and gates are sound, but three security defects reproduce (a board key can reach a different host when the address is edited during pairing; a cancelled sign-out leaves the secret stored; a late health probe lets an unprobed, possibly `local_trusted`, address be added), 24 further bugs and overclaims need fixing, and 24 observations remain. This spec turns the PRD into a trackable fix plan: 3 P0, 24 P1, 24 P2 requirements.

## 2. Problem statement

The merged-to-be Phase 1a code must not ship with reproducible credential-handling defects, with tests or contracts that pass while the behavior they name is wrong, or with documents that contradict the code and the pinned Paperclip server.

## 3. Goals and non-goals

**Goals:** (G1) remove every P0 and P1 defect with a failing regression test first; (G2) make test, contract and traceability evidence honest; (G3) make docs, ADRs, PRD and spec agree with the code and the pinned server; (G4) keep all gates green and coverage at or above current levels (core 97.58%, presentation 97.96%).
**Non-goals:** new features or screens, Phase 1b adapters, layering rework, changing ADR decisions (wording clarifications only).

## 4. User requirements: functional requirements

One requirement per finding; the PRD holds the full text. IDs are the PRD's (FR-R01 to FR-R51).

| ID | Priority | Finding | Summary |
|---|---|---|---|
| FR-R01 | P0 | F-A-1 | A board key can be sent to a different host if the address is edited while pairing or manual entry is in fligh |
| FR-R02 | P0 | F-A-2 | Sign out and sign out everywhere can leave credentials behind when cancelled or when one factory throws |
| FR-R03 | P0 | F-B-1 | A late probe result is applied to a different address, so an unprobed (possibly `local_trusted`) address can b |
| FR-R04 | P1 | F-A-3 | A 401 on any company-scoped read or mutation does not mark the factory Signed out (PRD FR-023) |
| FR-R05 | P1 | F-A-4 | `AddInstance` trusts a caller-supplied `HealthReport` that is not bound to the address |
| FR-R06 | P1 | F-A-5 | Failed enrollment and cancellation leave key, status and config inconsistent |
| FR-R07 | P1 | F-A-6 | Reconciled results for heartbeat and comments do not say whether the mutation took effect |
| FR-R08 | P1 | F-A-8 | Stale-snapshot writes and unvalidated caller-supplied entities in use cases |
| FR-R09 | P1 | F-B-2 | Decision screen can run two execution pollers at once; `polling` flag is shared |
| FR-R10 | P1 | F-B-3 | Sign-in outcomes map to wrong or inconsistent error keys |
| FR-R11 | P1 | F-B-4 | Inbox inline Approve/Reject do not check the card type or the inline-action policy |
| FR-R12 | P1 | F-B-6 | Holders run on `Dispatchers.Default` but guards and fields are plain check-then-set |
| FR-R13 | P1 | F-B-7 | Approval comment append uses a stale snapshot; a refresh can overwrite a just-finished action |
| FR-R14 | P1 | F-B-9 | Screens can get stuck with no refresh or lose a final outcome |
| FR-R15 | P1 | F-C-1 | Decision contract suite asserts a "conflict" the pinned server does not return |
| FR-R16 | P1 | F-C-2 | Run viewer never tests Cancel run being disabled offline after the run loaded (acceptance 11 overclaimed) |
| FR-R17 | P1 | F-C-3 | Role and timeout matrix is narrower than its names and acceptance 8 and 10 claim |
| FR-R18 | P1 | F-C-4 | Platform "contract" suites are tautological (acceptance 4 "Met" overstated) |
| FR-R19 | P1 | F-C-5 | Requirement traceability can be satisfied by a comment, and some tags are stamps |
| FR-R20 | P1 | F-C-6 | `contract.yml` is green and tests nothing; the nightly drift job cannot detect drift |
| FR-R21 | P1 | F-C-8 | "No credential to the wrong host" is not testable with the fakes; only the address-edit path is covered |
| FR-R22 | P1 | F-D-1 | technical-details.md states AGENTS.md is stale in ways it is not (false "known limitation") |
| FR-R23 | P1 | F-D-2 | Spec 5a "403 after a role change refreshes the role" is not implemented; role is cached for a holder's life |
| FR-R24 | P1 | F-D-3 | Spec and status files are stale against what was built (progress table, criterion 14 text, FR-096 wording) |
| FR-R25 | P1 | F-D-4 | PRD still contradicts the pinned server and the built behaviour in three places |
| FR-R26 | P1 | F-D-5 | Requirements-traceability rule is satisfied by a comment mention, so "every FR is named by a test" is weaker t |
| FR-R27 | P2 | F-A-10 | `BaseUri` does not validate the path and accepts odd hosts |
| FR-R28 | P2 | F-A-11 | Retries stack up and `retryingRead` retries unreachable and certificate failures |
| FR-R29 | P2 | F-A-12 | Dead parameters and unused domain functions |
| FR-R30 | P1 | F-A-7 | `PairInstance` polling is unbounded by server-supplied values and ignores rate limiting |
| FR-R31 | P2 | F-A-9 | `Secret.startsWith` copies the whole key into an unwipeable String |
| FR-R32 | P2 | F-B-10 | `createGraph` and the demo graph hide real behaviour behind fakes with a frozen clock |
| FR-R33 | P2 | F-B-11 | UI mapping by enum ordinal or `valueOf` with no compile-time exhaustiveness |
| FR-R34 | P2 | F-B-12 | App lock and refresh lifecycle need two separate platform calls |
| FR-R35 | P2 | F-B-13 | Swift-facing and wording notes |
| FR-R36 | P2 | F-B-5 | Gating is hard-coded to `Role.Viewer` or a single `Action` in several places instead of using `Permissions` |
| FR-R37 | P2 | F-B-8 | Paired-devices loads are not ordered or cancelled |
| FR-R38 | P2 | F-C-10 | Real-time and unbounded waiting in umbrella tests (flake and vacuous-pass risk) |
| FR-R39 | P2 | F-C-11 | TDD evidence is self-reported and the history cannot show red |
| FR-R40 | P2 | F-C-12 | `DeviceAuthGuardTest` partly restates the production policy |
| FR-R41 | P2 | F-C-13 | Config and hygiene observations |
| FR-R42 | P2 | F-C-7 | CI supply-chain pinning and unpinned tools |
| FR-R43 | P2 | F-C-9 | Konsist rules: contract-per-port and Secret.reveal checks are weaker than they read |
| FR-R44 | P2 | F-D-10 | "Continue to pairing" is an enabled button with an empty action (honestly documented; acceptable for this phas |
| FR-R45 | P2 | F-D-11 | Framework build and consumption failure modes (debug vs release, destructive copy, no preflight) |
| FR-R46 | P2 | F-D-12 | Shipping framework contains demo graph, fakes and a fake-key demo set |
| FR-R47 | P2 | F-D-13 | Lint configs are lenient (swift-format enables only two rules; SwiftLint is near default) |
| FR-R48 | P2 | F-D-6 | ADR bodies still describe designs that do not exist; the correction is an appended "Note" |
| FR-R49 | P2 | F-D-7 | Infrastructure Konsist rule is vacuous today and will still miss platform source sets later |
| FR-R50 | P2 | F-D-8 | Swift holder lifecycle: FirstRunModel never closes its holder, and holders are created inside view/scene const |
| FR-R51 | P2 | F-D-9 | Several Swift interop tests assert nothing about the intents they send, and close holders mid-flight |

## 5. Non-functional requirements

No regression (2,189 tests stay green on JVM, Android host and iOS simulator; coverage never below current or the 90% gates); security (no secret in strings, logs or outside `SecretStore`; mutation order and the no-auto-retry rule never relaxed; no authenticated call to a host that did not issue the key); reliability (no new unbounded waits or loops; cancellation never swallowed); CI wall time grows at most 10%; no new third-party dependency; Swift-facing API stays free of generics, default arguments, `Result` and Objective-C reserved prefixes.

## 6. System architecture

Affected layers: `shared/application` (auth, instance, decision, agent, issue, approval use cases), `shared/presentation` (first run, edit instance, decision, approval, inbox, work, run viewer, app state holders), `shared/domain` (probe result type, outcome types), `shared/testing` (contracts, fakes), `shared/umbrella` (Konsist rules), `.github/workflows`, docs and ADRs. No new modules.

## 7. Scope of changes

Modify existing files named in each requirement's "Where" line (see PRD). Create regression tests next to each fix. New ADR(s) for decisions made while fixing (probe result bound to an address; central 401 handling; demo-in-release). Update the PRD (FR-005 version-before-pairing; items from the doc review), the parent spec status and the docs.

## 8. Breaking changes

Possible internal API changes: `AddInstance` takes a probe result bound to an address; approval, decision and agent use cases may change signatures when they re-read before acting; `InvokeOutcome` gains an `Unknown` case. Swift tests are updated in the same commits. No user-visible or server-facing change.

## 9. Success criteria and acceptance criteria

1. Every P0 and P1 requirement has a regression test that fails on the pre-fix code and passes after (commit order shows test before fix).
2. `./gradlew check lint`, Kover gates (90%), Swift lint, format and Xcode tests pass; `ios.yml`, `shared.yml`, `contract.yml`, `security.yml` are green on the pull request.
3. No document, ADR, spec file or status table contradicts the code or the pinned server (re-run the doc-claim sample).
4. P2 items are fixed or listed as deferred in `status.md` with the reason.
5. The reviewer-found security defects are re-checked by an independent re-review of the changed code (no new Must Fix).

## 10. Risks and mitigation

| Risk | Mitigation |
|---|---|
| Fixing the address-change flow regresses pairing | Group 1 goes first and sequentially; existing pairing tests stay; add the host-recording fake first (FR-R for F-C-8) |
| Use-case signature changes ripple into presentation and Swift | Fix in one group; update iOS tests in the same commit; run `./scripts/ios-test.sh` after each group |
| Findings that do not reproduce waste effort | Policy in the PRD: write the failing test first; if it passes, record the finding as refuted and keep the test |
| One Gradle build at a time slows parallel work | Serialise builds; use git worktrees for editing, CI for the rest |

## 11. Timeline and milestones

Group 1 credentials and address changes (sequential) -> Group 2 state holders and Group 3 tests/contracts/CI (parallel) -> Group 4 docs and spec. Sizes: G1 M, G2 L, G3 M, G4 S.

## 12. References

- PRD (moved here by create-spec): [phase-1a-shared-contracts-auto-review-PRD.md](./phase-1a-shared-contracts-auto-review-PRD.md)
- [AGENTS.md](../../AGENTS.md), [INTENT.md](../../INTENT.md), [parent spec](../archive/261101-phase-1a-shared-contracts/spec.md)
