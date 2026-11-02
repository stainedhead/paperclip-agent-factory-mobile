# Implementation notes: Phase 1a review fixes

**Date:** 2026-11-01

Record decisions, edge cases, deviations and lessons as fixes land, with commit hashes. Update after every task.

## Technical decisions

### 2026-11-01 F-A-1 key host binding (FR-R01)

Commit: see the hash table at the end of this file (F-A-1).
Red evidence: EnrollmentAddressChangeTest failed before the fix, e.g. 'Unexpected elements ... AuthenticatedCall(name=me, host=https://evil-b.example.com, token=pcp_board_TESTONLY...)' and 'expected:<Renamed> but was:<Home factory>'.
Fix: AuthGateway.listBoardKeysWithKey(baseUri, key) added (fake and contract case); KeyEnrollment validates and lists keys with the key against the snapshot host BEFORE storing, then re-reads the instance, deletes the key and returns Failed(Conflict) if baseUri changed, and writes only onto the fresh instance. UpdateInstance re-reads right before writing. Existing test 'a failure to read the key after approval rolls the stored token back' encoded the store-then-delete dance; it now asserts nothing is stored (putCalls 0).
Residual: no per-instance mutex between UpdateInstance and KeyEnrollment; the check-then-write window is two suspend calls and a lost race ends with the key deleted by UpdateInstance (address change always deletes the namespace).

### 2026-11-01 F-B-1 probe result bound to the probed address (FR-R03)

Red evidence (all five failed before the fix): FirstRun 'expected:<Idle> but was:<Accepted>' and late refusal 'expected:<Idle> but was:<Refused>'; EditInstance 'canSave expected:<false> but was:<true>' (the verifier had not run the Edit variant: it reproduces) and 'expected:<Idle> but was:<Refused>'; UpdateInstance 'Updated should not be of type Updated'.
Fix: ProbeResult variants carry baseUri (decision: bound result, research Q1 answered: ProbeResult.Accepted(baseUri, report), holders stay thin). Holders keep the probe Job, cancel it in addressChanged/back, drop a result whose baseUri is not the current address, and keep session.probed (the Accepted result) instead of a bare HealthReport; save/add requires probed.baseUri == the current address. UpdateInstance now needs InstanceEdit.probed for exactly the new address (accepted, not refused) or answers ProbeRequired and changes nothing (rule in the application layer, not only the canSave flag). The ADR-012 UpdateInstance test now passes an accepted probe for the new address because the address change is gated by the probe.
Test support: FakeHealthGateway.onProbe gate.

## Edge cases and solutions

(none yet)

## Deviations from plan

(none yet)

## Lessons learned

(none yet)
