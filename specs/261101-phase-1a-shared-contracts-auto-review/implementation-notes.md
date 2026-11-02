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

### 2026-11-01 F-A-4 AddInstance requires a probe bound to the address (FR-R05)

Red evidence: add_with_report_probed_for_another_address -> 'expected:<ProbeRequired> but was:<Added(instance=...never-probed.example.com...)>' and the clean-report/local-trusted variant 'expected:<ProbeRequired> but was:<Added(... local.example.com ...)>' (red was taken with the new signature stubbed to the old behaviour, since the old signature cannot express binding).
Fix: AddInstance takes ProbeResult.Accepted and answers ProbeRequired when probed.baseUri differs from the validated address; local_trusted still refused in the use case. Duplicate-address policy: allowed, recorded in ADR-030; a test pins it (passes, no production change needed). Holders pass the bound probe.

### 2026-11-01 F-A-5 enrollment failure and cancellation consistency (FR-R06)

Red evidence (against the F-A-1 code): 'cancel between put and update leaves no orphan secret' -> 'expected:<false> but was:<true>' (secret stored while status stayed SignedOut); 'a failing update after put removes the new key and marks the factory signed out' -> 'expected:<[]> but was:<[instance/inst-1/boardToken]>'. Part 1 (failed validation keeps the previous key and status) was fixed as a side effect of F-A-1 (validation now precedes put); its test and the storage-failure and successful re-pair tests pass and are kept as regressions. Parts 2-3 verified red here.
Fix: KeyEnrollment.store runs put, re-read and update inside withContext(NonCancellable); unless the status was committed it deletes the new key and marks a still-signed-in factory Signed out (compensation in finally, no catch). A repository exception still propagates after the compensation.
Part 4 (re-pairing does not revoke the old key on the server): deliberately not changed. Revoking needs the old token before it is overwritten and is a server mutation the person did not ask for; the old key stays listed under Paired devices (FR-025) where it can be revoked. Recorded as accepted behaviour.

### 2026-11-01 F-A-2 (cancellation half, P0) sign out is cancellation-safe (FR-R02)

Red evidence: 'sign out cancelled during the revoke still deletes the secrets...' -> 'expected:<[]> but was:<[instance/inst-1/boardToken]>'.
Fix: SignOutInstance revokes in try and always runs the local deletion and the status update in finally via the shared internal helper deleteCredentialsLocally (NonCancellable), which RemoveInstance now uses too (research Q3 answered: yes, shared helper; the fake exposes onCall so a test can suspend inside the revoke). The status update is written onto a freshly read instance. The SignOutEverywhere half follows in its own commit.

### 2026-11-01 F-A-2 SignOutEverywhere continues past a failing factory (P1 half)

Red evidence: 'java.lang.IllegalStateException: secure store unavailable' propagated out of SignOutEverywhere and the second factory stayed SignedIn (red taken with the result type's new `failed` field stubbed to emptyList()).
Fix: SignOutEverywhere wraps each factory in runCatching, rethrows CancellationException, records failed ids in SignOutEverywhereResult.failed and continues; SettingsState.signOutFailures exposes the count (a plain Int, no prose). A cancelled sign out everywhere stops after the factory in progress has finished its own local deletion (shielded); remaining factories are not signed out, which is the cancel the person asked for.
Test support: FakeSecretStore.failNamespaceDeletes.

### 2026-11-01 F-A-7 bounded pairing wait (FR-R30)

Red evidence: huge interval 'expected:<Cancelled> but was:<Expired>' (waited 2 x 1 h); far expiry 'expected:<900000L> but was:<3600000L>' and a year 'expected:<900000L> but was:<31536000000L>'; rate limit 'Failed(error=RateLimited) is of type Failed but expected SignedIn' (red taken with DomainError.RateLimited and its ErrorKey stubbed, since 429 had no representation).
Fix: interval clamped 250 ms to 10 s; the wait is withTimeoutOrNull(min(expiresAt - now, 15 min)) around the polling only (approval is stored after the timed section so a timeout can never discard a key); DomainError.RateLimited (429) added with ErrorKey.RateLimited and tolerated while waiting. Existing test 'an expired challenge stops polling once the clock passes its expiry' expected 5 polls; the wait now ends at the expiry instant, so 4 polls happen (the 5th would only answer 'expired'); the test was changed with that comment, not weakened (it still asserts Expired, no secret, token wiped).
Phase 1b adapters must map HTTP 429 to DomainError.RateLimited.

### 2026-11-01 F-A-3 401 marks Signed out in one choke point (Group 2)

Red evidence: 'inbox refresh | expected:<SignedOut> but was:<SignedIn>' (all ten use-case cases) and 'expected:<1> but was:<0>' (exactly once), taken with the decorators absent.
Fix: ADR-031. SignedOutAware<X>Gateway decorators over the nine company-scoped gateways (research Q2 answered: decorator at the port, not 30 use cases and not retryingRead, which has no scope). The umbrella binds adapters under RawPort and hands use cases the decorated bindings; a graph test proves the wiring (SignedOutWiringTest). SignedOutMarker now writes only when the factory is not already Signed out. Phase 1b adapters must map 401 to Unauthorized.
Not done: the PRD fix also suggested making RevokeBoardKey refuse the revoke-other path when boardKeyId is null; left for Group 4 (needs a product decision on what the screen shows).

### 2026-11-01 F-A-6 timeout reconciliation compares with the pre-call state (Group 2)

Red evidence (against the old code, with MutationResult.Unknown only added): 'expected:<Unknown(error=Timeout)> but was:<Reconciled(error=Timeout, current=Started(run=...run-live...))>' (pre-existing live run reported as started), 'but was:<Reconciled(error=Timeout, current=Skipped)>' (no new run reported skipped), 'Failed(error=Timeout)' for the approval comment (no re-read at all), and for the issue comment 'but was:<Reconciled(... current=Comment(id=c-foreign ...' (a foreign newest comment reported as own).
Fix: ADR-032. MutationResult.Unknown(error); InvokeHeartbeat, CommentOnIssue, CommentOnApproval read before the call and compare after a timeout or conflict. Presentation: FeedbackKind.Unknown, issue and approval threads reload, inbox refreshes. Existing tests that encoded the guess were changed in the same commit: run now timeout (now Unknown, 2 reads), issue comment timeout (Unknown, 4 reads in the holder test), approval comment timeout (Unknown), WorkRoleMatrixTest acceptance 10 (accepts Unknown for the comment case). Presentation tests for the new feedback were written together with the code, so their red run was not taken separately.
Test support: FakeAgentGateway.onInvoke, FakeIssueGateway.storesCommentOnFailure, FakeApprovalGateway.storesCommentOnFailure.

### 2026-11-01 F-A-8 decide re-reads the decision before device auth (Group 2)

Red evidence: 'expected:<NotAuthorized(result=Cancelled)> but was:<Done(value=Decision(... status=Decided' (a caller copy without the cancel effect skipped biometrics; the store held the destructive one). The sign-out half (sign_out_keeps_rename_made_during_revoke) PASSES on the Group 1 code: refuted as already fixed; the test is kept.
Fix: ADR-032 (scope paragraph). DecideDecision takes DecisionId, reads the decision, derives the real action from the fresh option, answers Failed when the read fails, and returns Reconciled(Conflict, fresh) without a gateway call when the fresh copy is not open (acceptance 12 behaviour kept). Tests changed in the same commit: tests built around a caller copy now put the copy in the fake store; read counts rose by one (acceptance 10: 3 in the holder test); FR-044 not-open now expects Reconciled(Conflict) with zero decide calls instead of Invalid(WrongStatus), because the screen can then show the final state.
Not changed, with reason: PauseAgent, CancelRun, ResolveBudgetIncident, ApproveApproval keep entity parameters. Their device-auth requirement does not depend on the entity, the server repeats the eligibility checks, and the use case reconciles; re-reading would add a read per tap with no security gain (see ADR-032).

### 2026-11-01 F-A-1 residual per-factory lock (ADR-033) (Group 2)

Red evidence: 'expected:<false> but was:<true>' in InstanceLockingTest (a gated repository update: after the address edit the factory was SignedIn on the old address with no key stored).
Fix: InstanceLocks, ADR-033; UpdateInstance, KeyEnrollment, deleteCredentialsLocally (sign out, remove) and SignedOutMarker take it. Constructor changes touched every Env in tests.

## Edge cases and solutions

(none yet)

## Deviations from plan

(none yet)

## Group 1 commit hashes

| Finding | Commit |
|---|---|
| F-C-8 prerequisite (host and token recording fake) | 510a01f |
| F-A-1 | 1ca031a |
| F-B-1 | cc404bd (plus the follow-up test commit after eb1afad) |
| F-A-4 | 05624e0 |
| F-A-5 | 4ad957d |
| F-A-2 cancellation half | 2663f69 |
| F-A-2 SignOutEverywhere half | e67e22c |
| F-A-7 | eb1afad |

F-C-8 (FR-R21) stays open in status.md: the host-recording fake is in, but the contract case "a challenge created for server A polled against server B is NotFound" belongs to Group 3.
Group 1 coverage (fresh Kover run): core 97.67% (was 97.58%), presentation 97.70% (was 97.96%, -0.26): the drop is Kover counting the header lines of when-branches that stopped being last plus the unreachable-by-design ProbeRequired defence branches in four holder results.

## Lessons learned

(none yet)
