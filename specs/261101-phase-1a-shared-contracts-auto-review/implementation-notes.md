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

### 2026-11-01 F-B-2 one execution poller per Decision screen (Group 2)

Red evidence: refreshDuringExecutionPollDoesNotStartSecondPoll 'expected:<4> but was:<8>' (a Refresh during the poll started a second poller; pollingFlagStaysTrue... passed on the old code at 1.5 s, kept as a regression).
Fix: show() no longer suspends; startPoll() claims the polling flag atomically (new claim() helper in Guards.kt, compareAndSet loop) and launches the single poller as its own job, so the load that triggered it no longer holds 'loading' for the whole poll. Only the poller clears the flag, in finally.

### 2026-11-01 F-B-3 sign-in outcomes map to one consistent set of keys (Group 2)

Red evidence: 'key: expected:<InvalidKey> but was:<Malformed>' (first run, pasted bad key) and 'expected:<StorageFailed> but was:<NotFound>' (edit re-pair with a failing secure store). First run showed a failed secure store as Malformed.
Fix: ErrorKey.InvalidKey and ErrorKey.StorageFailed; one SignInResult.toFailure() (SignInFailure.kt, no else branch) used by both holders; a pasted bad key now sets pairing Failed in first run like every other failure (it stayed Idle). Existing FR-021 test asserted Malformed for the bad key and was changed to InvalidKey.
Swift: ErrorKey gained two cases; no Swift source switches over it (checked iosApp/Sources and Tests).

### 2026-11-01 F-B-4 inline Approve/Reject check the card and the policy (Group 2)

Red evidence: approveTappedOnDecisionCardIsIgnored 'expected:<0> but was:<1>' (a decision id sent to the approvals endpoint), approveTappedOnNonInlineApprovalIsIgnored 'expected:<0> but was:<1>', approveWithoutSubjectStatusIsIgnored 'expected:<0> but was:<1>', approveTappedForViewerRole... 'Expected null but actual was FeedbackUi(kind=NotAllowed...)' (the viewer tap reached the use case).
Fix: InboxApprovalActions honours approve, reject and keepPaused only if InlineActionPolicy.actionsFor(item, role) lists that action, and ignores a card without subject.status instead of assuming pending. AttentionFixtures.approvalItem now carries status pending (the real feed does, as the demo data already did).

### 2026-11-01 F-B-5 role gating uses Permissions everywhere (Group 2)

Red evidence: 'Expected OwnerRole but actual was null' for the Operator on the budget incident (raiseDisabledReason) and on agent detail (budgetDisabledReason). The wrong-flag half did not reproduce (role != Viewer equals Permissions today), so RoleGatingTest (Role x Action x online over disabledReason and mayAct) is a regression guard, not a red.
Fix: RoleGating.kt (disabledReason, mayAct); ActionsDisabledReason.OwnerRole; BudgetIncidentState.raiseDisabledReason and AgentDetailState.budgetDisabledReason; every renderer and holder flag and reason goes through it (Decision confirm, dismiss and snooze now use their own action; Run cancel; Agents Resume and RunNow use their own action); rejectNeedsDeviceAuth follows DeviceAuthPolicy; interactions use RespondToInteraction.

### 2026-11-01 F-B-6 guards and shared fields are atomic (Group 2)

Red evidence (AtomicGuardsTest, JVM only, 60 iterations x 4 concurrent callers on Dispatchers.Default, run against the pre-fix holders): UnlockRequested prompted the device more than once in 31 of 120 iterations, Refresh loaded more than once in 76, ConfirmTapped sent the decision more than once or not at all in 113, DismissTapped dismissed more than once in 89 (counts from the first 120-iteration version; the final version uses 60 iterations and fails the same way).
Fix: Guards.kt claim() (compareAndSet loop) replaces every 'if (flag) return; update { flag = true }' in the Decision, Approval, Budget, Agents, AgentDetail, RunViewer, WorkList, Issue, FactoryHome, Settings and Inbox holders and sessions; AppStateHolder keeps settings, backgroundedAt and unlocking in one MutableStateFlow cell and clears unlocking in finally; ContextSource.cached, Factories, ManageFactories and SettingsConfirmations.instances are MutableStateFlow cells. Run 5 times after the fix: 4 tests, 0 failures, 8.7 s each run.
Not changed: the first-run and edit probe/pair job fields (probeJob, pairJob, session.probed) are still plain; they are written from intents on the UI thread and read from coroutines they launch, and a lost write there is bounded by the address binding of F-B-1 (a stale probe is dropped by address). Recorded as a known limitation.

### 2026-11-01 F-B-7 a late read never overwrites a finished action (Group 2)

Red evidence: staleLoadDoesNotOverwriteAfterApprove 'expected:<Approved> but was:<Pending>'; AgentDetail 'expected:<Paused> but was:<Running>'; Decision stale load re-opened a decided decision (assertion false). The comment lost-update half was appended inside a single update in the F-A-6 commit (ApprovalModel.withComment), so it needs no separate test.
Fix: a version counter in the Approval, Decision, Budget, AgentDetail, Agents, RunViewer and Issue models. An action bumps it in the same update that clears its in-flight flag; a read records the version when it starts and drops its result if the version moved. Test support: afterRead hooks in FakeApprovalGateway, FakeDecisionGateway, FakeAgentGateway (the answer is read first, then delayed, so it really is older than what follows).

### 2026-11-01 F-B-8 paired-devices loads are ordered and cancelled (Group 2)

Red evidence: slowLoadForPreviousFactoryDoesNotOverwriteCurrent 'expected:<cloud> but was:<home>' (the slow answer for the first factory overwrote the second).
Fix: SettingsDevices keeps loadJob, cancels it when a new load starts, and applies a result only while pairedDevices still names the requested factory.

### 2026-11-01 F-B-9 no stuck run viewer, no lost budget outcome (Group 2)

Red evidence: failedFirstLoadKeepsRefreshInterval 'expected:<15> but was:<0>'; refreshAfterResolveKeepsRaisedOutcome 'expected:<Raised> but was:<NoLongerActive>'.
Fix: the Run viewer keeps the default 15 s interval until a run has loaded; the Budget holder keeps a resolved outcome when a refresh answers NotFound and maps the status with outcomeOf when a load succeeds.

### 2026-11-01 F-B-11 UI enum mappings are exhaustive when expressions (Group 2)

Not red by design: the orders and names match today (the finding said so), so UiEnumMappingTest passes before and after; the fix is structural. It checks that every domain enum maps to the twin of the same name and that the round trips return the original.
Fix: the 14 ordinal/valueOf one-liners became exhaustive 'when (this)' without else, and the 7 inline entries[ordinal]/valueOf sites use new toUi functions in EnumMappings.kt, so a new or reordered domain value now fails to compile (checked: no .ordinal/valueOf/entries[ left in presentation except sorting chips by source kind).

### 2026-11-01 F-D-2 a 403 re-resolves the role once (Group 2)

Red evidence: forbiddenMutationReResolvesTheRole failed in all 8 holder families (Approval, Decision, Budget, Agent detail, Agents, Run viewer, Issue, Work list) with 'expected:<2> but was:<1>' (auth.me was called once; the role stayed Operator and actions stayed enabled); the Inbox variant was added after the fix and passes.
Fix: a Forbidden mutation result triggers ONE resolveContext call (ContextSource.reResolve for the work screens, refreshContext for the screens that keep the context in their model); the mutation is not repeated, the old role is kept if the read fails, and the holder re-renders with the new role (ActionResult.role carries it). Reads that answer Forbidden are not handled; the finding only asked for mutations to refresh the role.

### 2026-11-01 F-A-11 retry budget, offline and certificate failures (Group 2)

Red evidence: load_approval_offline_fails_without_backoff 'expected:<0L> but was:<3500L>'; multi_read_use_case_total_wait_is_bounded 'expected:<true> but was:<false>' (a read that recovers after 1.5 s plus a read that never answers waited 5 s). certificate_failure_is_not_retried passes once DomainError.CertificateInvalid exists: with the case split, isTransientFailure() already excludes it, so that test guards the split rather than turning red.
Fix: ADR-034. RetryBudget (3.5 s per use case), connectivity check, DomainError.CertificateInvalid with ErrorKey.CertificateInvalid, LoadApproval/LoadIssue/LoadInstanceAccess take Connectivity. Phase 1b adapters must map TLS failures to CertificateInvalid.

### 2026-11-01 F-A-12 dead code removed, CriticalAndHigh fills the page (Group 2)

Red evidence: critical_and_high_page_fills_across_server_pages 'Missing elements from index 1: expected [i01, i11, i12, i13, i14] but was [i01]' and the exhausted-server case 'expected:<false> but was:<true>'.
Fix: ListIssues reads server pages (up to 10) until the page holds the limit of urgent rows or the server runs out; serverRowCount is the number of server rows used so the next page starts right after the last row shown; a later page that fails returns the rows already found with hasMore. Removed the unused userId parameter of ListIssues and SearchIssues (and their callers), the unused DayOfWeek and atStartOfDayIn imports of SnoozeTimes, and the domain function Interaction.canBeResolvedBy with its test (production uses InteractionRenderer.readOnlyReason, which has its own tests). Kept on purpose: DecideBy.toServer (the Phase 1b request mapping), IssueQuery.nextPage (used by the Issue contract), IssueFilter.matches and matchesSearch (the rule the fake shares with tests).

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

## Group 2 commit hashes

| Finding | Commit |
|---|---|
| F-A-3 | 1737df4 |
| F-A-6 | 8ed50dc (+ 1c2a2ac detekt follow-up) |
| F-A-8 | 210b02a |
| F-A-1 residual (per-factory lock) | f6e1d5d |
| F-B-2 | 7099838 |
| F-B-3 | 734ee6e |
| F-B-4 | d84eb48 |
| F-B-5 | 4e23ba4 |
| F-B-6 | fb0b167 |
| F-B-7 | bc2966a |
| F-B-8 | 88d1d9f |
| F-B-9 | a5940a2 |
| F-B-11 | 9ee7576 |
| F-D-2 | 047568d |
| F-A-11 | e145f9a |
| F-A-12 | f9155c5 |

Group 2 coverage (fresh Kover run): core 97.89%, presentation 98.01%.
Process note: the first full `check` failed only on Kotlin/Native (a test name containing a comma, String.format in common code); fixed in 968bdc6. The commit for F-A-6 was pushed with a detekt failure and fixed in 1c2a2ac.

## Lessons learned

(none yet)
