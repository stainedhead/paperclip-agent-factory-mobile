# Tasks: Phase 1a review fixes

**Date:** 2026-11-01 · **Status:** Planning

## Progress summary

0 / 51 tasks complete.

## Task format

`T-<n>` · requirement · dependencies · size (S under half a day, M a day) · acceptance criteria (the PRD requirement's list).

## Tasks (in priority order; Group order is in plan.md)

- **T-1** FR-R01 (P0) A board key can be sent to a different host if the address is edited while pairing or manu · deps: host-recording fake · M · acceptance: see PRD FR-R01
- **T-2** FR-R02 (P0) Sign out and sign out everywhere can leave credentials behind when cancelled or when one f · deps: host-recording fake · M · acceptance: see PRD FR-R02
- **T-3** FR-R03 (P0) A late probe result is applied to a different address, so an unprobed (possibly `local_tru · deps: host-recording fake · M · acceptance: see PRD FR-R03
- **T-4** FR-R04 (P1) A 401 on any company-scoped read or mutation does not mark the factory Signed out (PRD FR- · deps: none · S · acceptance: see PRD FR-R04
- **T-5** FR-R05 (P1) `AddInstance` trusts a caller-supplied `HealthReport` that is not bound to the address · deps: none · S · acceptance: see PRD FR-R05
- **T-6** FR-R06 (P1) Failed enrollment and cancellation leave key, status and config inconsistent · deps: none · S · acceptance: see PRD FR-R06
- **T-7** FR-R07 (P1) Reconciled results for heartbeat and comments do not say whether the mutation took effect · deps: none · S · acceptance: see PRD FR-R07
- **T-8** FR-R08 (P1) Stale-snapshot writes and unvalidated caller-supplied entities in use cases · deps: none · S · acceptance: see PRD FR-R08
- **T-9** FR-R09 (P1) Decision screen can run two execution pollers at once; `polling` flag is shared · deps: none · S · acceptance: see PRD FR-R09
- **T-0** FR-R10 (P1) Sign-in outcomes map to wrong or inconsistent error keys · deps: none · S · acceptance: see PRD FR-R10
- **T-1** FR-R11 (P1) Inbox inline Approve/Reject do not check the card type or the inline-action policy · deps: none · S · acceptance: see PRD FR-R11
- **T-2** FR-R12 (P1) Holders run on `Dispatchers.Default` but guards and fields are plain check-then-set · deps: none · S · acceptance: see PRD FR-R12
- **T-3** FR-R13 (P1) Approval comment append uses a stale snapshot; a refresh can overwrite a just-finished act · deps: none · S · acceptance: see PRD FR-R13
- **T-4** FR-R14 (P1) Screens can get stuck with no refresh or lose a final outcome · deps: none · S · acceptance: see PRD FR-R14
- **T-5** FR-R15 (P1) Decision contract suite asserts a "conflict" the pinned server does not return · deps: none · S · acceptance: see PRD FR-R15
- **T-6** FR-R16 (P1) Run viewer never tests Cancel run being disabled offline after the run loaded (acceptance  · deps: none · S · acceptance: see PRD FR-R16
- **T-7** FR-R17 (P1) Role and timeout matrix is narrower than its names and acceptance 8 and 10 claim · deps: none · S · acceptance: see PRD FR-R17
- **T-8** FR-R18 (P1) Platform "contract" suites are tautological (acceptance 4 "Met" overstated) · deps: none · S · acceptance: see PRD FR-R18
- **T-9** FR-R19 (P1) Requirement traceability can be satisfied by a comment, and some tags are stamps · deps: none · S · acceptance: see PRD FR-R19
- **T-0** FR-R20 (P1) `contract.yml` is green and tests nothing; the nightly drift job cannot detect drift · deps: none · S · acceptance: see PRD FR-R20
- **T-1** FR-R21 (P1) "No credential to the wrong host" is not testable with the fakes; only the address-edit pa · deps: none · S · acceptance: see PRD FR-R21
- **T-2** FR-R22 (P1) technical-details.md states AGENTS.md is stale in ways it is not (false "known limitation" · deps: none · S · acceptance: see PRD FR-R22
- **T-3** FR-R23 (P1) Spec 5a "403 after a role change refreshes the role" is not implemented; role is cached fo · deps: none · S · acceptance: see PRD FR-R23
- **T-4** FR-R24 (P1) Spec and status files are stale against what was built (progress table, criterion 14 text, · deps: none · S · acceptance: see PRD FR-R24
- **T-5** FR-R25 (P1) PRD still contradicts the pinned server and the built behaviour in three places · deps: none · S · acceptance: see PRD FR-R25
- **T-6** FR-R26 (P1) Requirements-traceability rule is satisfied by a comment mention, so "every FR is named by · deps: none · S · acceptance: see PRD FR-R26
- **T-7** FR-R27 (P2) `BaseUri` does not validate the path and accepts odd hosts · deps: none · S · acceptance: see PRD FR-R27
- **T-8** FR-R28 (P2) Retries stack up and `retryingRead` retries unreachable and certificate failures · deps: none · S · acceptance: see PRD FR-R28
- **T-9** FR-R29 (P2) Dead parameters and unused domain functions · deps: none · S · acceptance: see PRD FR-R29
- **T-0** FR-R30 (P1) `PairInstance` polling is unbounded by server-supplied values and ignores rate limiting · deps: none · S · acceptance: see PRD FR-R30
- **T-1** FR-R31 (P2) `Secret.startsWith` copies the whole key into an unwipeable String · deps: none · S · acceptance: see PRD FR-R31
- **T-2** FR-R32 (P2) `createGraph` and the demo graph hide real behaviour behind fakes with a frozen clock · deps: none · S · acceptance: see PRD FR-R32
- **T-3** FR-R33 (P2) UI mapping by enum ordinal or `valueOf` with no compile-time exhaustiveness · deps: none · S · acceptance: see PRD FR-R33
- **T-4** FR-R34 (P2) App lock and refresh lifecycle need two separate platform calls · deps: none · S · acceptance: see PRD FR-R34
- **T-5** FR-R35 (P2) Swift-facing and wording notes · deps: none · S · acceptance: see PRD FR-R35
- **T-6** FR-R36 (P2) Gating is hard-coded to `Role.Viewer` or a single `Action` in several places instead of us · deps: none · S · acceptance: see PRD FR-R36
- **T-7** FR-R37 (P2) Paired-devices loads are not ordered or cancelled · deps: none · S · acceptance: see PRD FR-R37
- **T-8** FR-R38 (P2) Real-time and unbounded waiting in umbrella tests (flake and vacuous-pass risk) · deps: none · S · acceptance: see PRD FR-R38
- **T-9** FR-R39 (P2) TDD evidence is self-reported and the history cannot show red · deps: none · S · acceptance: see PRD FR-R39
- **T-0** FR-R40 (P2) `DeviceAuthGuardTest` partly restates the production policy · deps: none · S · acceptance: see PRD FR-R40
- **T-1** FR-R41 (P2) Config and hygiene observations · deps: none · S · acceptance: see PRD FR-R41
- **T-2** FR-R42 (P2) CI supply-chain pinning and unpinned tools · deps: none · S · acceptance: see PRD FR-R42
- **T-3** FR-R43 (P2) Konsist rules: contract-per-port and Secret.reveal checks are weaker than they read · deps: none · S · acceptance: see PRD FR-R43
- **T-4** FR-R44 (P2) "Continue to pairing" is an enabled button with an empty action (honestly documented; acce · deps: none · S · acceptance: see PRD FR-R44
- **T-5** FR-R45 (P2) Framework build and consumption failure modes (debug vs release, destructive copy, no pref · deps: none · S · acceptance: see PRD FR-R45
- **T-6** FR-R46 (P2) Shipping framework contains demo graph, fakes and a fake-key demo set · deps: none · S · acceptance: see PRD FR-R46
- **T-7** FR-R47 (P2) Lint configs are lenient (swift-format enables only two rules; SwiftLint is near default) · deps: none · S · acceptance: see PRD FR-R47
- **T-8** FR-R48 (P2) ADR bodies still describe designs that do not exist; the correction is an appended "Note" · deps: none · S · acceptance: see PRD FR-R48
- **T-9** FR-R49 (P2) Infrastructure Konsist rule is vacuous today and will still miss platform source sets late · deps: none · S · acceptance: see PRD FR-R49
- **T-0** FR-R50 (P2) Swift holder lifecycle: FirstRunModel never closes its holder, and holders are created ins · deps: none · S · acceptance: see PRD FR-R50
- **T-1** FR-R51 (P2) Several Swift interop tests assert nothing about the intents they send, and close holders  · deps: none · S · acceptance: see PRD FR-R51

## Stub example

- **T-00** example: `KeyEnrollment` address check · deps: host-recording `FakeAuthGateway` · M · `pairing_then_address_edit_never_sends_key_to_new_host` fails before the fix and passes after.
