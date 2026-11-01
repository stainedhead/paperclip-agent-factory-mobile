# PRD: Phase 1a code review fixes (auto-review)

**Created:** 2026-11-01
**Status:** Draft, from the dev-flow Step 4 review of `feat/phase-1a-shared-contracts`
**Source spec:** [specs/261101-phase-1a-shared-contracts](specs/261101-phase-1a-shared-contracts/spec.md)
**Related:** [AGENTS.md](./AGENTS.md), [INTENT.md](./INTENT.md), draft PR #5

---

## Executive summary

Four read-only reviewers audited the Phase 1a branch (domain and application, presentation and umbrella, tests and build and CI, iOS and docs and spec fidelity). A fifth stage reproduced the serious findings with throwaway failing tests, so every finding below is marked with how it was verified. Overall assessment: **Request changes.** The architecture is sound and the gates are green (core coverage 97.58%, presentation 97.96%, 2,189 tests with zero failures), and the reviewers confirmed that role mapping fails closed, device authentication runs before the first gateway call in every destructive use case, no coroutine cancellation is swallowed, and the UTC rules match the server. But three security-relevant defects reproduce, and several tests and documents claim more assurance than they give.

| Priority | Count | Meaning |
|---|---|---|
| P0 | 3 | Security defects that reproduce; must be fixed before merge |
| P1 | 23 | Reproduced bugs and false assurance (tests, contracts, CI, docs) that should be fixed in this change |
| P2 | 25 | Maintainability, hardening and observations; fix if cheap, otherwise schedule |

Rating corrections from verification: F-A-2's "one factory throws" half is a Warning (real adapters return `DomainResult`), though the cancellation half stays P0; F-B-5 did not reproduce as a wrong flag and is P2; F-B-7's comment-append half is Info, the stale-load half is P1; F-B-6's double-submit risk is low in production (one UI thread) but the load/refresh overlap and unguarded `AppStateHolder` fields are real.

## Goals

- G1 Remove every P0 and P1 defect, each with a failing regression test written first.
- G2 Make test, contract and traceability evidence honest: no "Met" claim without a test that would fail under the obvious mutation.
- G3 Make docs, ADRs, the PRD and the spec agree with the code and the pinned server.
- G4 Keep all existing gates green and coverage at or above current levels.

## Non-Goals

- New features, new screens, or Phase 1b adapters.
- Reworking the layering or the module structure.
- Changing ADR decisions (clarify wording only).

## Guidance for implementing this PRD

- **TDD is mandatory.** For each requirement write the failing test first, run it, see it fail for the stated reason, then the minimum fix. Reproducing tests for P0 and several P1 findings already exist as the verification agent's throwaway tests (see Verification notes); recreate them as permanent tests.
- **One commit per finding** (AGENTS.md: every review finding has its own commit before the step is marked complete). Conventional Commits, mentioning the spec directory.
- **Review each fix** briefly before moving on (re-read the diff for new violations of the layering and Swift-facing rules).
- **Parallelism.** P0 and P1 groups touch mostly disjoint files (application security; presentation holders; tests/CI; docs). Use agent teammates with git worktrees per group, but only one Gradle build can run at a time on the dev machine, so teammates must serialise builds (or run builds in CI).
- Never lower a gate, never suppress a rule inline. After each group run `./gradlew spotlessApply && ./gradlew check lint`, and for Swift-facing changes `./scripts/build-kit.sh debug && ./scripts/ios-test.sh`.
- Regenerate coverage reports before quoting numbers (`rm -rf build/reports/kover && ./gradlew koverXmlReportCore koverXmlReportPresentation`).

## Functional requirements (one per finding)


### FR-R01 (P0): A board key can be sent to a different host if the address is edited while pairing or manual entry is in flight  [F-A-1]

- **Reviewer severity:** Must Fix  ·  **Verification:** REPRODUCED. `TmpVerifyF_A_1`: PairInstance, address edited via UpdateInstance while polling, then Approved. Authenticated calls to the NEW host: `me -> https://evil-b.example.com with token pcp_board_TE...` and `listBoardKeys -> https://evil-b...`. Result `SignedIn`; final baseUri reverted to `home.example.com` (user's edit lost). The `SaveManualBoardKey` path validates with `meWithKey(snapshot host)` first, so only the `listBoardKeys` call in `complete` is exposed there (tiny window).
- **Corrected severity (verification):** Must Fix stands (pairing path; manual entry is Warning-level)
- **Where:** shared/application/src/commonMain/kotlin/com/stainedhead/paperclipmobile/application/auth/KeyEnrollment.kt:50-91; auth/PairInstance.kt:38-99; auth/SaveManualBoardKey.kt:25-35; instance/UpdateInstance.kt:75-90
- **Issue:** `PairInstance` and `SaveManualBoardKey` capture an `Instance` snapshot (old `baseUri`) and pass it to `KeyEnrollment.complete`. `complete` first writes the new key to the `SecretStore` under `instance.id`, then calls `auth.me(instance.id)` and `auth.listBoardKeys(instance.id)`. These port methods resolve the host from the repository's CURRENT `baseUri`, not from the snapshot. Pairing can sit in the browser for minutes (the `delay` loop in `waitForApproval`). Scenario: user starts pairing against host A, opens Edit factory and changes the address to host B. `UpdateInstance` runs `deleteNamespace` and saves the new URI. The user approves on host A, so `Approved` arrives. `complete` stores A's key under the instance id, then `auth.me(instance.id)` sends A's bearer token to host B. This breaks ADR-012 ("a key must never be sent to a host other than the one that issued it") and AGENTS.md security rules. After that, `repository.update(instance.copy(...))` writes the STALE snapshot back, reverting the address to A and discarding the user's edit, so the user's edit is silently lost.
- **Why it matters:** Credential disclosure to an unintended host, which INTENT.md names as the zero-tolerance outcome. A stale write-back of the whole `Instance` also reverts rename, default company and lastCompanyId changes.
- **Fix:** (a) In `complete`, re-read the instance by id and abort (and delete nothing of the newer state) if `baseUri` differs from the snapshot. (b) Validate the key against the snapshot host before storing: use `auth.meWithKey(instance.baseUri, token)` for pairing as well, which also removes the store-then-delete dance. (c) Do not use `listBoardKeys(instance.id)` before the baseUri check. (d) Update only the credential fields on a freshly read instance rather than `instance.copy` of the old snapshot. Consider serializing instance edits with enrollment per `InstanceId` (a Mutex), or have the edit screen refuse while pairing runs.
- **Acceptance criteria:**
  - `pairing_then_address_edit_never_sends_key_to_new_host`: with a recording `FakeAuthGateway`, start pairing, call `UpdateInstance` with a new address, approve; assert no authenticated call carries the pending token to the new host, the result is not `SignedIn`, and the stored address equals the edited one. `enrollment_does_not_overwrite_concurrent_rename`: rename during pairing, finish pairing, assert the new name survives.


### FR-R02 (P0): Sign out and sign out everywhere can leave credentials behind when cancelled or when one factory throws  [F-A-2]

- **Reviewer severity:** Must Fix  ·  **Verification:** REPRODUCED (both). `sign_out_cancelled_during_revoke_still_deletes_secrets`: cancel while revoke suspended gives `secrets=[instance/inst-1/boardToken] status=SignedIn`. `sign_out_everywhere_continues_after_one_factory_throws`: first factory throws, second stays `SignedIn` and the exception propagates.
- **Corrected severity (verification):** Cancellation half: Must Fix stands. Throw half: Warning (real adapters return DomainResult, so a throw needs a secure-store or programming error)
- **Where:** auth/SignOut.kt:24-55 (compare instance/RemoveInstance.kt:45-52, which does it right)
- **Issue:** `SignOutInstance` awaits `revoker.revoke(id)` (a network call, no timeout in this layer) and only then runs `secrets.deleteNamespace` and `repository.update`. None of this is in `NonCancellable`. If the caller's scope is cancelled while the revoke is in flight (screen dismissed, app backgrounded, state-holder scope cleared), the `CancellationException` skips the deletion. The user tapped "Sign out", but the key stays in the Keychain/Keystore and the instance stays `SignedIn`. The KDoc promises "Credentials are deleted locally even when the server cannot be reached". `RemoveInstance` already uses `try/finally + withContext(NonCancellable)` for exactly this case, and `RemoveInstanceTest`/`PairInstanceTest` cover cancellation; there is no equivalent for sign-out. `SignOutEverywhere` runs the factories sequentially with `map`, so one thrown exception (secure store IO error) aborts the rest, and nothing is reported for the ones already done. The remaining factories keep valid keys with no indication.
- **Why it matters:** Sign-out is the user's emergency control for a lost or shared phone. Silent partial failure is a credential-exposure risk.
- **Fix:** Wrap the local deletion in `try { revoke } finally { withContext(NonCancellable) { deleteNamespace; update } }`, or delete locally first then best-effort revoke with a timeout (the revoke needs the key, so read-then-delete or revoke under `withTimeout`). In `SignOutEverywhere`, catch non-cancellation failures per factory, continue, and return `failed` ids in the result. Do the status update on a freshly read instance (see F-A-1).
- **Acceptance criteria:**
  - `sign_out_cancelled_during_revoke_still_deletes_secrets` (cancel the job while the fake revoke is suspended; assert `secrets.pathsFor(id)` is empty and status `SignedOut`). `sign_out_everywhere_continues_after_one_factory_fails_and_reports_it`.


### FR-R03 (P0): A late probe result is applied to a different address, so an unprobed (possibly `local_trusted`) address can be saved  [F-B-1]

- **Reviewer severity:** Must Fix  ·  **Verification:** REPRODUCED. `TmpVerifyF_B_1` (FirstRunStateHolder, gated health gateway): probe(a) in flight, edit to b, release a. Result: `probeStatus after late result=Accepted; address=b; instances saved after Continue=[https://b.example.com]`; `probed=[a]` only. b was configured as `local_trusted` and was never probed, yet it was added. EditInstanceStateHolder has the same code shape (same `probe()` write-back) but I did not run it.
- **Corrected severity (verification):** Must Fix stands (security rule bypass, reachable by typing on a slow link)
- **Where:** presentation/.../FirstRunStateHolder.kt:213-215 (`addressChanged`), 274-283 (`probe`), 313-326 (`save`); presentation/.../EditInstanceStateHolder.kt:254-268 (`addressChanged`), 381-388 (`probe`), 400-421 (`add`), 441-460 (`update`); application AddInstance.kt (`invoke` takes `probed` but never compares it with `address`)
- **Issue:** `probe()` launches `useCases.probe(uri)` and, when it returns, unconditionally writes `session.report = <report>` and `state.update { withProbe(result) }`. Nothing cancels the probe job or checks the address when the address is edited. `addressChanged` resets `probe` to `Idle`, but the in-flight probe then flips it back to `Accepted`.
  - Scenario: type `https://a.example`, tap Probe (probe in flight), edit the field to `https://b.example` (state goes to `Idle`, `canContinue=true`), the late `Accepted` for `a` arrives, so `probe=Accepted` and `session.report` is a's report. Tap Continue. `save()` calls `useCases.add(name, "https://b.example", reportOfA)`. `AddInstance` only checks `report.refusal()`, so b is added without ever being probed. If b reports `local_trusted`, the "refuse `local_trusted`" rule is bypassed.
  - The reverse also happens: a late `Refused` for a labels the new address b as refused.
  - In Edit mode it is worse. `EditInstanceSaving.update` passes no report at all, and `UpdateInstance` cannot enforce the probe rule. The only guard is the UI flag `canSave` (`probe == Accepted`), and the same race sets that flag for an address that was never probed.
- **Why it matters:** This breaks AGENTS.md Security Rule "Refuse instances whose `/api/health` reports `local_trusted`" (INTENT principle 1). It is reachable through ordinary typing on a slow link, not just an attacker.
- **Fix:** (a) Keep the probe `Job` in the session and cancel it in `addressChanged` and `back()`. (b) Tag each probe with the normalized address it ran for and drop the result unless it still equals the current normalized address. (c) In `save()`/`add()`, require `session.reportAddress == normalizedAddress`. (d) Make `UpdateInstance` take the `HealthReport` when the address changes, as `AddInstance` does, so the rule lives in the application layer and not only in a UI flag.
- **Acceptance criteria:**
- `FirstRunStateHolderTest.probeResultForEditedAddressIsIgnored`: probe a (gated fake), edit to b, release a. Assert `probe == Idle`, `canContinue` unchanged, and `ContinueRequested` does not call `add`.
  - `FirstRunStateHolderTest.lateRefusalDoesNotLabelNewAddress`.
  - `EditInstanceStateHolderTest.canSaveStaysFalseAfterAddressEditedDuringProbe`.
  - `UpdateInstanceTest.addressChangeWithoutAcceptedReportIsRejected`.


### FR-R04 (P1): A 401 on any company-scoped read or mutation does not mark the factory Signed out (PRD FR-023)  [F-A-3]

- **Reviewer severity:** Warning  ·  **Verification:** REPRODUCED at use-case layer. `inbox_refresh_401_marks_instance_signed_out`: RefreshInbox with `feed` -> Unauthorized leaves status `SignedIn`. `dismiss_401...`: same. `SignedOutMarker` is only referenced from BoardKeys.kt and LoadInstanceAccess.kt (grep). Caveat: Phase 1b gateway adapters could mark centrally, so this is a gap in the contract rather than a visible bug today.
- **Corrected severity (verification):** Warning stands (FR-023)
- **Where:** auth/SignedOutMarker.kt; only callers are auth/BoardKeys.kt:20,67 and auth/LoadInstanceAccess.kt:31,34. Not called from attention/Inbox.kt, approval, decision, budget, agent, run, issue, dashboard, interaction use cases. The only other `Unauthorized` handling is the presentation error text (`presentation/Common.kt:63`).
- **Issue:** FR-023 says a `401` marks the instance "Signed out" without deleting its configuration. In this branch that only happens in the access/key-list use cases. A revoked or expired key hit while refreshing the Inbox, deciding or pausing returns `Failed(Unauthorized)` and shows a "signed out" message, but `Instance.status` stays `SignedIn`, so Factories still shows it as signed in and no re-pair prompt appears. Related case: `RevokeBoardKey` revokes this phone's own key through the "other key" path when `instance.boardKeyId` is null (so `isCurrentDevice` is false for all rows), and the device is then dead but still `SignedIn`.
- **Why it matters:** Correctness over speed: the app shows stale credentials state, and key-expiry recovery (one-tap re-pair) never triggers on the main flows.
- **Fix:** Apply the marker centrally (a decorator over the company-scoped gateways, or inside `retryingRead` and `failed` via a small `AuthAwareCall` helper) so every `Unauthorized` marks `SignedOut` once. Make `boardKeyId == null` refuse the revoke-other path or treat it as unknown.
- **Acceptance criteria:**
  - `inbox_refresh_401_marks_instance_signed_out`, `approve_401_marks_instance_signed_out_and_keeps_config` (instance row still has name/uri/default company).


### FR-R05 (P1): `AddInstance` trusts a caller-supplied `HealthReport` that is not bound to the address  [F-A-4]

- **Reviewer severity:** Warning  ·  **Verification:** REPRODUCED at API level, practically reachable only via F-B-1. `add_with_report_probed_for_another_address_is_refused`: `AddInstance("Other","https://never-probed...", healthy())` returns `Added`, `health.probeCalls=0`. Sole production callers are FirstRunSaving.add and EditInstanceSaving.add, which take the report from the probe session; so exploitation needs F-B-1 (or another UI bug). It is a design weakness (rule enforced by convention), independent as an API but not independently exploitable today.
- **Corrected severity (verification):** Warning stands; effectively the same fix as F-B-1(c)
- **Where:** instance/AddInstance.kt:38-60; domain/Instance.kt `HealthReport` (no `BaseUri`)
- **Issue:** The security rule "refuse `local_trusted`" is enforced through `probed.refusal()`, but `probed` is any `HealthReport` the caller passes. Nothing ties it to `address`. A UI that probes `https://a.example`, lets the user edit the field to `https://b.example`, then calls `AddInstance` with the old report adds an unprobed (possibly `local_trusted`) server. The stored `deploymentMode` and `exposure` also come from that report.
- **Why it matters:** The refusal rule is enforced by convention in the caller, not by the use case, which contradicts "UI holds no rules".
- **Fix:** Have `AddInstance` call `ProbeInstance` itself on the validated `BaseUri`, or accept a `ProbeResult.Accepted` that carries the probed `BaseUri` and check equality. Consider rejecting a duplicate `baseUri` (names are unique, addresses are not).
- **Acceptance criteria:**
  - `add_with_report_probed_for_another_address_is_refused`; `add_local_trusted_address_never_persists_even_with_clean_stale_report`.


### FR-R06 (P1): Failed enrollment and cancellation leave key, status and config inconsistent  [F-A-5]

- **Reviewer severity:** Warning  ·  **Verification:** REPRODUCED (part 1). `failed_validation_of_new_key_keeps_previous_key_and_status`: signed-in factory with a stored key, `complete()` with a new token and `me` -> Timeout: result `Failed(Timeout)`, secret store `Missing` (old key deleted), instance still `status=SignedIn boardKeyId=key-current`. Applies to pairing re-pair only (manual entry validates first). Parts 2-4 (cancel between put and update, update throwing, no revoke of old key) not tested; same shape as F-A-2.
- **Corrected severity (verification):** Warning stands
- **Where:** auth/KeyEnrollment.kt:49-91
- **Issue:** (1) `secrets.put` overwrites any existing key before `auth.me` validates the new one. If `me` fails, `secrets.delete(key)` removes the old, previously working key too, but the instance row still says `SignedIn` with the old `boardKeyId`. (2) `complete` is not shielded from cancellation: cancelled between `put` and `repository.update`, a key is stored while the status is still `SignedOut`/old. (3) A failure of `repository.update` or `secrets.delete` after `put` throws and leaves the secret. (4) Re-pairing an already signed-in factory never revokes the old key on the server (orphan active key).
- **Why it matters:** The visible state and the secret store disagree. A signed-in-looking factory without a key, or a stored key with "signed out" status.
- **Fix:** Validate with `meWithKey` first (F-A-1), then `put`, then `update`, with the last two in `NonCancellable` and a compensating delete if `update` throws. On any failure after `put`, set the instance to `SignedOut`.
- **Acceptance criteria:**
  - `failed_validation_of_new_key_keeps_previous_key_and_status`; `cancel_between_put_and_update_leaves_no_orphan_secret`.


### FR-R07 (P1): Reconciled results for heartbeat and comments do not say whether the mutation took effect  [F-A-6]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** agent/AgentMutations.kt:73-98 (`InvokeHeartbeat`); issue/IssueMutations.kt:44-72 (`CommentOnIssue`); approval/ApprovalUseCases.kt:100-113 (`CommentOnApproval`)
- **Issue:** After a timeout, `InvokeHeartbeat` re-reads the agent's runs and returns `Started(liveRun)` if ANY live run exists (a run already in progress before the tap qualifies) and `Skipped` otherwise (a run that has not been queued yet, or a lagging list, is reported as "Skipped", a server verdict the app never received). `CommentOnIssue` returns the newest comment of the thread as `current`, which can be another user's or an agent's comment, so the screen cannot tell if its own comment was posted. `CommentOnApproval` has no re-read at all: a `Timeout` returns `Failed`, and re-sending duplicates the comment.
- **Why it matters:** INTENT principle 2, "never guess what an action will do". The Reconciled contract (FR-096) is "`current` is the truth", but here it is a guess.
- **Fix:** Compare against the pre-call state: record the set of live run ids (or newest comment id) before the call, and report `Started` only for a new run, otherwise `Unknown`/`Failed(Timeout)` with no `Skipped`. For comments, match on body and author id, or return the thread and let the screen decide. Add an unknown outcome to `InvokeOutcome` rather than reusing `Skipped`.
- **Acceptance criteria:**
  - `heartbeat_timeout_with_preexisting_live_run_is_not_reported_started`; `heartbeat_timeout_without_new_run_is_not_reported_skipped`; `comment_timeout_with_foreign_newest_comment_is_not_reported_as_own`.


### FR-R08 (P1): Stale-snapshot writes and unvalidated caller-supplied entities in use cases  [F-A-8]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** auth/SignOut.kt:33-39, auth/KeyEnrollment.kt:81-90 (`instance.copy(...)` after a long suspension); instance/UpdateInstance.kt; mutation use cases that take `decision`, `incident`, `agent`, `run`, `status` from the caller (`DecideDecision`, `ResolveBudgetIncident`, `PauseAgent`, `CancelRun`, `ApproveApproval`)
- **Issue:** `SignOutInstance` reads the instance, awaits a network revoke, then writes the old snapshot back, discarding edits made meanwhile. The mutation use cases make their state decisions (`isDestructive`, `isOpen`, `canPause`, `isResolvable`, budget amount check) on an entity the caller loaded earlier. That is acceptable only because the server is the authority. But `DecideDecision` derives the device-auth requirement from the caller's copy of `option.isDestructive`. A stale or caller-forged `Decision` whose option lacks the destructive effect skips biometrics for what the server now treats as `cancel_issue_tree`.
- **Why it matters:** The device-auth rule can be bypassed by an out-of-date entity; lost updates on instance config.
- **Fix:** For the destructive-option check, re-read the decision (one read) before authenticating, or take only `DecisionId` and load it. Write config changes as field-level updates on a fresh read.
- **Acceptance criteria:**
  - `decide_uses_server_side_option_destructiveness_for_device_auth`; `sign_out_keeps_rename_made_during_revoke`.


### FR-R09 (P1): Decision screen can run two execution pollers at once; `polling` flag is shared  [F-B-2]

- **Reviewer severity:** Warning  ·  **Verification:** REPRODUCED. `refreshDuringExecutionPollDoesNotStartSecondPoll`: runningReads=100, Confirm, then Refresh while polling. `get` calls in the next 3 s of virtual time = 6 (one poller gives 3). `isPolling` was true before refresh.
- **Corrected severity (verification):** Warning stands
- **Where:** presentation/.../DecisionStateHolder.kt:113-129 (`load`), 173-187 (`act`), 189-203 (`show`/`poll`)
- **Issue:** `show()` ends in `poll()` when `isExecutionPending`. `load()` is guarded by `loading`, but `act()`'s `show()` (and its `poll()`) runs with `loading=false`.
  - Scenario: tap Confirm, the decision returns with execution pending, and `act` starts `poll()`. Then the 15 s timer or an Invalidation calls `Refresh`. `load()` passes its guard, reads a still-pending decision, and `show()` starts a second `poll()`. Both write `polling=false` as soon as either finishes, so the spinner turns off while the other still polls. Both can also write older results over newer ones.
  - Conversely, `load()` holds `loading=true` for the whole poll, so Refresh is silently dropped meanwhile.
- **Why it matters:** Violates the in-flight-guard rule and shows stale or flickering execution state on an irreversible action.
- **Fix:** Keep one `pollJob: Job?`. `poll()` cancels or ignores a running one, sets `polling` only for the job that owns it, and is never started from inside `load`'s `loading` window. Alternatively make `load()` skip while `polling`.
- **Acceptance criteria:**
  - `DecisionStateHolderTest.refreshDuringExecutionPollDoesNotStartSecondPoll` (count `PollDecisionExecution` calls == 1) and `pollingFlagStaysTrueUntilTheOnlyPollerFinishes`.


### FR-R10 (P1): Sign-in outcomes map to wrong or inconsistent error keys  [F-B-3]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** presentation/.../FirstRunStateHolder.kt:478-495 (`InvalidKey`, `StorageFailed` -> `ErrorKey.Malformed`); presentation/.../EditInstanceStateHolder.kt:557-563 (`else` -> `ErrorKey.NotFound`, which covers `InvalidKey`, `StorageFailed` and `NotFound`); presentation/.../Common.kt:40-50 (`ErrorKey` has no storage or invalid-key entry)
- **Issue:** Failing to write the secret to the Keychain/Keystore (`StorageFailed`) is shown as "unexpected response" (`Malformed`) in first run and as "not found" in Edit. A pasted key the server rejects (`InvalidKey`) is `Malformed` in first run, `NotFound` in Edit, and leaves `pairing` Idle in first run but `Failed` in Edit. The same input gives different keys per screen.
- **Why it matters:** FR-097 messages must tell the person what to do. A secure-store failure is a security-relevant event and reads as a server problem. The `else ->` branch in Edit also hides future `SignInResult` cases.
- **Fix:** Add `ErrorKey.StorageFailed` (and use `SignedOut`/`Rejected` or a new `InvalidKey` for a bad key). Put one `SignInResult.toErrorUi()` in Common.kt used by both holders. Make the `when` exhaustive with no `else`.
- **Acceptance criteria:**
  - `SignInResultMappingTest` asserts every `SignInResult` maps to the same `ErrorKey` in `FirstRunStateHolder` and `EditInstanceStateHolder`. `storageFailureIsNotReportedAsMalformed`.


### FR-R11 (P1): Inbox inline Approve/Reject do not check the card type or the inline-action policy  [F-B-4]

- **Reviewer severity:** Warning  ·  **Verification:** REPRODUCED. `approveTappedOnDecisionCardIsIgnored`: `ApproveTapped("decision:dec-1")` calls approvals `approve` once (calls={approve=1}). `approveTappedOnNonInlineApprovalIsIgnored` (inlineResolvable=false): also 1 call. Needs a stale or forged intent (UI does not render the button), server rejects an unknown id.
- **Corrected severity (verification):** Warning, could be rated Info-Warning (defence in depth; no UI path today)
- **Where:** presentation/.../InboxActions.kt:25-31 (`approve`), 33-50 (`askForRejectNote`/`reject`), 66 (`statusOf` defaults to `"pending"`)
- **Issue:** `approve(itemId)` uses `item.subject.id` as an `ApprovalId` for any item with that id. It never checks `InlineActionPolicy.actionsFor(item, role)` or `item.sourceKind == Approval`, while `keepPaused` does check (`budgetIncident()`). A stale or forged `ApproveTapped` for a Decision or Unsupported card sends the decision id to the approvals endpoint. `statusOf` replaces a missing `subject.status` with `"pending"`, which makes the use case's own `WrongStatus` guard pass on unknown state. Role, device auth and online are enforced downstream, so the damage is limited to a wrong-entity call, but "UI rules live in presentation" is not met here.
- **Why it matters:** INTENT principle 3 and AGENTS "presentation holds every UI rule". The button list is computed in the renderer, but the intent handler does not use the same list.
- **Fix:** In `approve`/`askForRejectNote`/`reject`/`keepPaused` require `InlineAction.Approve` (or `Reject`/`KeepPaused`) in `InlineActionPolicy.actionsFor(item, model.context.role)`. If `subject.status` is null, return without calling.
- **Acceptance criteria:**
  - `InboxStateHolderTest.approveTappedOnDecisionCardIsIgnored`, `approveTappedForViewerRoleMakesNoGatewayCall`, `approveWithoutSubjectStatusIsIgnored`.


### FR-R12 (P1): Holders run on `Dispatchers.Default` but guards and fields are plain check-then-set  [F-B-6]

- **Reviewer severity:** Warning  ·  **Verification:** REPRODUCED (with concurrent callers). JVM stress, DecisionStateHolder on `Dispatchers.Default`, 4 concurrent `ConfirmTapped` released from a gate, 3000 iterations. Variant 1: iterations where the gateway `decide` was reached more than once = 617/3000 (worst 4 calls). Variant 2 (decide blocked until all taps returned, so no post-completion gap): 545/3000 iterations entered the gateway more than once (worst 4). So the pure check-then-set race is real. Realism: in production `onIntent` is called from one UI thread and `act` sets `submitting=true` synchronously inside `onIntent`, so serial taps are safe; the hazard needs a second thread calling intents (refresh-policy `refreshNow`, collectors, AppStateHolder plain vars).
- **Corrected severity (verification):** Warning stands for load/refresh and AppStateHolder; for double-submit of mutations downgrade to Warning-low (needs multi-thread callers)
- **Where:** umbrella/.../PaperclipCore.kt:72 (`newScreenScope()` = `Dispatchers.Default`); presentation/.../AppStateHolder.kt:76-79, 148-173 (plain `var settings`, `backgroundedAt`, `unlocking` written from the collector and from `onIntent`); InboxSession.kt:112-114 (`if (model.value.mutating ...) return` then `update`); the `loading` / `submitting` / `busy` / `refreshing` guards in Approval, Decision, Budget, Agents, AgentDetail, RunViewer, WorkList, Issue and FactoryHome holders; Common.kt `ContextSource.cached`; SettingsConfirmations.instances; FactoriesStateHolder.instances
- **Issue:** `onIntent` runs on the UI thread, while `refreshNow()` from the refresh policy and the collectors run on Default threads. Every guard is `if (state.flag) return; state.update { flag = true }`, which is not atomic. Two callers can both pass the guard (double load, double mutation if two threads submit). The plain vars in `AppStateHolder` have no happens-before, so `relock()` can read a stale `settings`, and a lock decision can use an older value.
- **Why it matters:** "Never double-submit a mutation" is a stated rule, and the app-lock decision is security relevant.
- **Fix:** Make guards atomic: `if (!flag.tryClaim())` implemented as `model.updateAndGet`/`compareAndSet` returning whether this call won. Hold shared vars in the `MutableStateFlow` or mark them `@Volatile`/atomic. Or confine every holder to one serial dispatcher (`limitedParallelism(1)` / Main) and say so in the graph.
- **Acceptance criteria:**
  - `AtomicGuardsTest` fires two `Refresh` intents and two `DismissTapped` intents concurrently on a multi-thread test dispatcher and asserts one use-case call each. `AppStateHolder` lock test with intents on a different dispatcher from the settings flow.


### FR-R13 (P1): Approval comment append uses a stale snapshot; a refresh can overwrite a just-finished action  [F-B-7]

- **Reviewer severity:** Warning  ·  **Verification:** PARTLY. The stale-load half is REPRODUCED: `staleLoadDoesNotOverwriteAfterApprove`: Refresh response (pending) delayed, person approves, then the old response arrives: status goes `Approved` -> `Pending` with `actions=[Approve, Reject, RequestRevision, Comment]` again. The comment lost-update half is NOT reproducible deterministically: the read and the `model.update` are in the same non-suspending callback, so it only occurs with multi-thread callers (same class as F-B-6).
- **Corrected severity (verification):** Warning stands for the stale load (INTENT principle 2); comment lost update is Info
- **Where:** presentation/.../ApprovalStateHolder.kt:181-188 (`comment`), 113-130 (`load`), 191-201 (`act`)
- **Issue:** In the `comment` result callback, `val detail = model.value.detail` is read outside `update`, then `model.update { it.copy(detail = detail?.copy(comments = detail.comments + ...)) }` writes it back. A `load()` that completes between the two lines is discarded (lost update). Separately, `load()` is not blocked while `submitting`; a read issued before an approve/reject (for example during the biometric prompt) and answered after the mutation result replaces `detail` with the older status.
- **Why it matters:** INTENT principle 2 (never show stale data as current): the screen could briefly show an approved approval as pending with Approve enabled.
- **Fix:** Do the append inside `update { it.detail?.let { d -> it.copy(detail = d.copy(comments = d.comments + new)) } ?: it }`. Make `load()` drop its result if a mutation finished after the load started (a version counter in the model), or skip `load()` while `submitting`.
- **Acceptance criteria:**
  - `ApprovalStateHolderTest.commentAppendKeepsDetailRefreshedMeanwhile`; `staleLoadDoesNotOverwriteAfterApprove`.


### FR-R14 (P1): Screens can get stuck with no refresh or lose a final outcome  [F-B-9]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed (interval); suspected (Budget overwrite, depends on server emitting an event).
- **Where:** presentation/.../RunViewerStateHolder.kt:192 (`refreshIntervalSeconds = if (run?.isLive == true) 5 else 0`); BudgetIncidentStateHolder.kt:86-100 (`load`), 105-111 (`noLongerActiveOrError`), 191-199 (`show`); BudgetIncidentRenderer.kt:64
- **Issue:** (1) If the first Run viewer load fails or the screen opens offline, `run == null`, so the interval is 0 and the policy never retries; the person sees a Failed phase until they pull to refresh. Approval, Decision and Budget use 15 s in the same state. (2) After a successful budget resolve, `show()` sets `outcome=Raised/KeptPaused`. Any later `Refresh` (an Attention invalidation calls `refreshNow()` regardless of interval) reloads the incident; the server's active list no longer holds it, so `NotFound` overwrites the outcome with `NoLongerActive`. The screen forgets what the person just did. On success `load` also sets `outcome = null` without calling `outcomeOf(status)`.
- **Why it matters:** FR-044 final-state handling and "show the server's truth": the final state is lost or never recovered.
- **Fix:** Use the default interval until a run is loaded. In Budget, keep a resolved outcome (`outcome != null` and not `NoLongerActive`) when a refresh returns `NotFound`, and map status with `outcomeOf` on load success.
- **Acceptance criteria:**
  - `RunViewerStateHolderTest.failedFirstLoadKeepsRefreshInterval`; `BudgetIncidentStateHolderTest.refreshAfterResolveKeepsRaisedOutcome`.


### FR-R15 (P1): Decision contract suite asserts a "conflict" the pinned server does not return  [F-C-1]

- **Reviewer severity:** Warning  ·  **Verification:** REPRODUCED (reviewer right). `~/Code/Test/paperclip/server/src/services/decisions.ts:686-689`: `if (current.status === "decided" && current.chosenOptionId === input.optionId && sameInputValues(...)) { if (decidedByUserId !== caller) throw forbidden; return resumeDecision(...) }` - a success, whatever the idempotency key. Conflict (`decision_already_resolved`) comes only at line 691 for a different option or inputs. The contract (`DecisionContract.kt:50-62`) re-decides the SAME option with key-2 and requires Conflict; the fake replays only on a key match. Extra: `dismissingEndsAnOpenDecisionOnlyOnce` is also server-dependent: `dismiss` (decisions.ts:735-739) delegates to `decide` with the effect-less option when one exists, so a second dismiss replays as success; only a decision with no effect-less option (direct update path, line 743) gives Conflict.
- **Corrected severity (verification):** Warning stands; it affects the Phase 1b oracle
- **Where:** shared/testing/src/commonMain/kotlin/com/stainedhead/paperclipmobile/testing/contract/DecisionContract.kt:50-62 (`aSecondDecisionIsAConflictButTheSameKeyReplays`); shared/testing/src/commonMain/kotlin/com/stainedhead/paperclipmobile/testing/fakes/FakeDecisionGateway.kt:`decide` and `problem`; server ~/Code/Test/paperclip/server/src/services/decisions.ts:683-711
- **Issue:** The contract decides decision #1 with option X and key-1, then decides it again with the SAME option X, the same (empty) inputs and a different key-2, and requires `DomainError.Conflict`. The server's `decide` first checks `current.status === "decided" && current.chosenOptionId === input.optionId && sameInputValues(...)` and, for the same user, returns `resumeDecision` (a success). It throws `decision_already_resolved` only when the option or inputs differ, or the decision was decided by someone else. The fake replays only when the idempotency key matches (`decidedKeys[id] == key`), so the suite encodes the fake's rule, not the server's. The suite cites `svc/decisions.ts:690-711` as its source, so the citation is misleading.
- **Why it matters:** Acceptance 4 and ADR-028 present these suites as the oracle that Phase 1b's real adapter must satisfy. Run against the pinned server, this one would fail; the likely "fix" is to bend the adapter to the fake. Presentation tests built on the fake (a repeat tap of the same option is "resolved elsewhere") also describe behaviour the server does not have.
- **Fix:** Change the suite to the server's rules: same user + same option + same inputs after the decision is decided is a success whatever the key; a different option, or different inputs, is `Conflict`. Change `FakeDecisionGateway.decide` the same way. Add a case for a different option with a new key.
- **Acceptance criteria:**
  - `DecisionContract` test "deciding again with the same option and inputs but a new key succeeds with the stored decision" and "deciding again with another option is a Conflict"; both pass for the fake and match `services/decisions.ts:683-711`.


### FR-R16 (P1): Run viewer never tests Cancel run being disabled offline after the run loaded (acceptance 11 overclaimed)  [F-C-2]

- **Reviewer severity:** Warning  ·  **Verification:** REPRODUCED (mutation survives). Removed all three guards in RunViewerStateHolder.kt (`connectivity.isOnline.value &&` in canCancelNow, `online &&` in renderRun mayCancel, `!online -> Offline` reason). `jvmTest` for the whole presentation and umbrella modules (including RunViewerCancelTest, RunViewerLoadTest, 12 Run viewer tests) stayed GREEN. File restored with `git checkout`.
- **Corrected severity (verification):** Warning stands (destructive action, acceptance 11 overclaimed)
- **Where:** shared/presentation/src/commonMain/kotlin/com/stainedhead/paperclipmobile/presentation/RunViewerStateHolder.kt:140,165,187; shared/presentation/src/commonTest/kotlin/com/stainedhead/paperclipmobile/presentation/RunViewerStateHolderTest.kt:119-134; specs/261101-phase-1a-shared-contracts/status.md (criterion 11)
- **Issue:** The only offline Run viewer test starts the holder already offline with nothing loaded (phase Failed, `get` count 0). No test loads a live run and then calls `connectivity.setOnline(false)`. The three guards `connectivity.isOnline.value && ...` (line 140, the cancel intent guard), `online && ...` (line 165, `mayCancel`) and `!online -> ActionsDisabledReason.Offline` (line 187) can each be deleted with every test still green, so Cancel run would be sent while offline. Status criterion 11 lists the Run viewer as covered and says "Met".
- **Why it matters:** Cancel run is a destructive action. Criterion 11 requires every mutation intent to be disabled offline with a reason key, and this screen has no evidence for it.
- **Fix:** Add the test below and correct the criterion 11 evidence. Audit the other holders in the same way: Inbox, Decision, Approval, Budget, Agents, Agent detail, Work list and Issue do have offline-after-load cases.
- **Acceptance criteria:**
  - `RunViewerStateHolderTest."acceptance 11 offline after load disables Cancel run with the Offline reason and a cancel intent makes no gateway or biometric call"`. It loads a live run, calls `setOnline(false)`, then asserts `canCancel == false`, `actionsDisabledReason == Offline`, `runs.count("cancel") == 0` and `biometrics.calls == 0`. Removing any of the three guards fails it.


### FR-R17 (P1): Role and timeout matrix is narrower than its names and acceptance 8 and 10 claim  [F-C-3]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** shared/application/src/commonTest/kotlin/com/stainedhead/paperclipmobile/application/WorkRoleMatrixTest.kt:19-135; status.md criteria 8 and 10
- **Issue:** (a) `acceptance 10 a timeout on any mutation reaches the gateway exactly once` scripts timeouts for 6 of the 10 cases (comment, status, assign, respond, pause, cancel run). `create`, `resume`, `run now` and `set budget` are not covered; `Env.gatewayCalls()` also ignores the attention, decision, approval and budget fakes. (b) The "acceptance 8" viewer test therefore proves "a viewer never reaches a gateway" for the Work area only. Decision, approval, budget and Inbox mutations (snooze, dismiss, undo, set decide-by) are checked in separate hand-written tests, so there is no table over `Action.entries` x `Role` at the use-case level. The spec asks for a table-driven test that covers every (role, action) pair. (c) The owner/operator test only asserts `result !is Denied`. An `Invalid` result counts as "reached", so a wrongly granted action that fails validation would still pass.
- **Why it matters:** The test name and the status table say more than the test proves. A new mutation use case can be added without a role or timeout row, and nothing fails.
- **Fix:** Build one registry of every mutation use case (it can be asserted against the Konsist list of mutation classes), run viewer, operator and owner through it, and assert `Denied` with zero gateway and biometric calls for viewers. Add timeout rows for the missing cases. Tighten the owner/operator assertion to "the gateway was called, or the result is Done, Failed or Reconciled". Or rename the tests to "work mutations" and word the criteria honestly.
- **Acceptance criteria:**
  - A Konsist test fails when a class in a mutation package is missing from the matrix registry. `viewer never reaches a gateway` covers decision, approval, budget and inbox use cases. The timeout test has a row per use case whose gateway call can time out, each asserting call count 1.


### FR-R18 (P1): Platform "contract" suites are tautological (acceptance 4 "Met" overstated)  [F-C-4]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** shared/testing/src/commonMain/kotlin/com/stainedhead/paperclipmobile/testing/contract/PlatformContracts.kt:23-39 (Biometrics), 41-55 (Clock), 57-70 (Connectivity), 105-116 (PairingBrowser), 118-134 (AppLifecycle)
- **Issue:** Each suite scripts the fake and reads back what it just scripted. `BiometricsContract` calls `willAnswer(result)` and asserts `authenticate` returns the same result. `Connectivity` and `AppLifecycle` set a flow and read it. `ClockContract` asserts `second >= first` after an `advance`. `PairingBrowserContract` calls `browser.open(link)` and then asserts that `Secret.toString()` is redacted, which does not exercise the browser at all. `AuthPairingContract` asserts `!challenge.toString().contains("pcp_")`, which is true whether or not the redaction works, because the fake token has no `pcp_` prefix. The mutation check in the TDD log was done for four gateway suites only.
- **Why it matters:** "27 contract classes" reads as strong evidence, but about a third of them cannot fail for any plausible bug. For the real platform adapters (Phase 1b) they say nothing about the properties that matter: a biometric lockout is reported as `LockedOut` and not `Failed`; `open` does not log the URL.
- **Fix:** Keep those suites only where there is a real invariant. Delete or rewrite the rest. For the pairing browser, record the opened URL in the fake and assert it equals the approval URL and is never printed. For the Secret redaction, assert on the actual `toString()` of a token with a known `pcp_board_TESTONLY` prefix. Change the status wording to "fake-only; real adapters in Phase 1b".
- **Acceptance criteria:**
  - Mutation check recorded: break each fake's rule and each platform suite fails. `PairingBrowserContract` fails if `open` is a no-op. `AuthPairingContract` fails if `PairingChallenge.toString()` prints the token.


### FR-R19 (P1): Requirement traceability can be satisfied by a comment, and some tags are stamps  [F-C-5]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** shared/umbrella/src/konsistTest/kotlin/com/stainedhead/paperclipmobile/architecture/RequirementsTraceabilityTest.kt:23-33; tags FR-002, FR-008, FR-013, FR-016, FR-097
- **Issue:** The rule regex-searches every `commonTest` file for `FR-\d{3}`. A KDoc line or `//` comment suffices; `@Test` is not required. The sample of 18 tags checked was mostly honest (FR-014, 030, 041, 043, 051, 071, 090, 091, 095, 096 and others are tagged on tests that exercise the requirement). These are partial or stamped:
  - FR-016 ("a pin mismatch blocks all requests"): the only tagged test checks that a pin parses as a base64 SHA-256 string.
  - FR-097 (an `{error, details}` body is mapped to a typed error): `ErrorMappingTest` maps `DomainError` to `ErrorKey`. The JSON-to-`DomainError` mapping is Phase 1b. The tag was added late to satisfy the rule (status: "FR-013 and FR-097 gained tags").
  - FR-013: a path-naming test.
  - FR-002 (persisted in SQLDelight, never in backups): a fake contract plus a fake secret-store check.
  - FR-008 (QR code or deep link): only the deep link.
  - FR-030 also appears in a comment in `DemoGraphTest.kt:132`, which would count if the real tests were removed.
- **Why it matters:** Criterion 1 reads as "every FR has a test"; for five FRs the honest statement is "the Phase 1a part of the FR has a test". The rule is a presence check that cannot say which part.
- **Fix:** Count only `@Test` function names, or require the tag in a `@Requirement`-style annotation or a name; stop counting comments. Add a table of FR to the part delivered in 1a. Put FR-002, FR-008, FR-013, FR-016 and FR-097 in `exempt` with the reason (or a "partial" map), which the rule already supports and which the spec's criterion 1 allows.
- **Acceptance criteria:**
  - `RequirementsTraceabilityTest` fails when an FR id appears only in a comment. A partial map is checked and listed in status.md. Criterion 1 evidence names the five partial FRs.


### FR-R20 (P1): `contract.yml` is green and tests nothing; the nightly drift job cannot detect drift  [F-C-6]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** .github/workflows/contract.yml:25-34, 36-48; status.md criterion 7
- **Issue:** The "Contract tests" step is `echo "No contract tests yet..."`. The `latest` job only starts the container (the script checks `deploymentMode == authenticated`) and sets `continue-on-error: true`, so a failing run never turns red. It will not detect a changed API shape. Status criterion 7 is worded honestly ("CI not run"), but once the PR opens, a green "contract" check will read as assurance about the API that the project does not have. No commonTest suite is run against a recorded server fixture either.
- **Why it matters:** Branch protection that requires "contract" gives a false pass. The pinned server version is the only link between the fakes and server truth (see F-C-1), and nothing verifies it.
- **Fix:** Until Phase 1b, make the step fail (`exit 1`) behind a clearly named `if: false`/skipped job, or do not make the check required. In the meantime, add a script that fetches `/api/openapi.json` from the container and diffs the paths used by the fakes' research citations. Remove `continue-on-error` and open an issue instead, or post the drift as an artifact.
- **Acceptance criteria:**
  - The contract job reports "skipped" (not "success") while no test runs. The nightly `latest` job fails when `/api/health` or `openapi.json` drifts from the committed snapshot.


### FR-R21 (P1): "No credential to the wrong host" is not testable with the fakes; only the address-edit path is covered  [F-C-8]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** shared/testing/src/commonMain/kotlin/com/stainedhead/paperclipmobile/testing/fakes/FakeAuthGateway.kt:`createChallenge`, `pollChallenge`, `meWithKey`; shared/testing/src/commonMain/kotlin/com/stainedhead/paperclipmobile/testing/contract/AuthContracts.kt:13-80; application/auth/KeyEnrollment.kt and SaveManualBoardKey.kt
- **Issue:** The fake records the call name but not the `baseUri` it received, and the pairing contract uses one `server`. A bug that sends the challenge token, the pending board token or the pasted key to another factory's address (wrong `instance.baseUri`, a stale copy after an edit, a deep-link host) fails no test. The one real test, `ADR-012 a new address deletes the credentials...` (InstanceUseCasesTest.kt:130), is a good mutation-resistant test but covers only the edit path. `me(instanceId)` binds to the instance id, so a host mix-up there is the adapter's job.
- **Why it matters:** INTENT principle 1 and the zero-credential-exposure outcome. This is the one security property in the brief that the application tests cannot observe.
- **Fix:** Make `FakeAuthGateway` record `(call, baseUri)`. Add application tests: pairing and manual-key entry send every call (create, poll, `meWithKey`) to the instance's own `baseUri`, and never to another instance's. Add a contract case: a challenge created for server A polled against server B is `NotFound`.
- **Acceptance criteria:**
  - `PairInstanceTest."every pairing call goes to the factory's own address"` and `SaveManualBoardKeyTest."the pasted key is validated against the entered address only"`. Both fail if `baseUri` is taken from another instance.


### FR-R22 (P1): technical-details.md states AGENTS.md is stale in ways it is not (false "known limitation")  [F-D-1]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed (I traced the code path).
- **Where:** docs/technical-details.md "CI" section ("AGENTS.md names them `pafm-*.yml`; see 'Known limitations'") and "Known limitations", Process bullet 3; compare AGENTS.md lines 42, 133, 147-155; DEV-FLOW-STATUS.md 2026-11-01T22:41 entry ("AGENTS.md claim of staleness checked and found false")
- **Issue:** The doc says AGENTS.md "still names `androidUnitTest`, lists `koverVerify`, the workflow names `pafm-*.yml`, and describes a monorepo-root `.github`". The AGENTS.md in this tree already says `androidHostTest`, `koverVerifyCore koverVerifyPresentation`, `.github/workflows/{shared,android,ios,contract,security}.yml` and "its own repository" (commit 4e0300a). DEV-FLOW-STATUS.md records that the same claim was checked and found false, yet the text stayed in the shipped doc. The CI section also points the reader to a limitation that does not exist. The one true residue: AGENTS.md lists `android.yml`, but no such workflow exists (there is no androidApp yet).
- **Why it matters:** Readers are told to distrust the rules file that governs the repo. Docs that contradict each other erode the "docs match code" gate in AGENTS.md.
- **Fix:** Delete the bullet and the "(AGENTS.md names them ...)" parenthesis. Replace with one accurate line: AGENTS.md lists `android.yml`, which does not exist until the Android app does.
- **Acceptance criteria:**
  - `grep -n "pafm-\|androidUnitTest" docs/technical-details.md` returns nothing; every workflow file name in technical-details.md exists under `.github/workflows/` (a doc-link check script or reviewer checklist item).


### FR-R23 (P1): Spec 5a "403 after a role change refreshes the role" is not implemented; role is cached for a holder's life  [F-D-2]

- **Reviewer severity:** Warning  ·  **Verification:** REPRODUCED. `forbiddenMutationReResolvesTheRole` (ApprovalStateHolder): load as Operator, server role becomes Viewer, approve returns Forbidden. After the tap `role=Operator`, `actions=[Approve, Reject, RequestRevision, Comment]`, `auth.me calls=1` (never re-resolved).
- **Corrected severity (verification):** Warning stands (fails safe, contradicts spec 5a)
- **Where:** specs/261101-phase-1a-shared-contracts/spec.md section 5a "Permission boundaries"; shared/presentation/.../ApprovalStateHolder.kt:129-139 (`ensureContext`: `model.value.context ?: resolveContext(...)`); same pattern in the other mutating holders; shared/presentation/.../Common.kt:64 (Forbidden -> `ErrorKey.NoPermission` only)
- **Issue:** The spec says a 403 mid-session "returns `Forbidden`, refreshes the role and does not retry". The code resolves the `CompanyContext` once and never clears it (no `context = null` anywhere in presentation). A mutation that returns `Forbidden` shows `NoPermission` and keeps the stale role: an operator demoted to viewer still sees enabled actions on that screen, each tap hits the server and fails again, until the screen is reopened. No test asserts the refresh (the Forbidden cases in the holder tests only check the error key). Status.md marks the edge-case work as done without listing this.
- **Why it matters:** UI rules are meant to live in shared Kotlin (INTENT principle 5). It fails safe (server is the backstop) but contradicts a stated requirement, and the wasted taps on a viewer are the "guess what an action will do" case.
- **Fix:** On `MutationResult.Failed(Forbidden)` (and read `Forbidden`) clear the cached context in the shared `act` path and re-run `resolveContext`; or amend the spec to say "shown as no permission; role re-resolved when the screen is reopened" and record it in implementation-notes.
- **Acceptance criteria:**
  - Test `forbiddenMutationReResolvesTheRole` in each holder family: fake auth returns role Operator, then Viewer after the first 403; after the failed tap the state shows actions disabled with `ViewerRole` and `resolveContext` was called twice, gateway mutation count is 1.


### FR-R24 (P1): Spec and status files are stale against what was built (progress table, criterion 14 text, FR-096 wording)  [F-D-3]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed (I traced the code path).
- **Where:** specs/.../status.md "Overall progress" (Phase 0 "In Progress", Phases 1 to 3 "Not Started") and Phase 0 checklist item "[ ] other server differences in spec section 5"; spec.md line 4 ("Status: Draft"); tasks.md (P1.1 to P1.3 and P1.5 not marked done, only P1.4); implementation-notes.md "Lessons learned: (none yet)"; spec.md section 10 criterion 14 ("restricts callers of `Secret.reveal()` to the API auth plugin"), section 6 ("Time comes from the `Clock` port only"), section 7 ("use cases (about 45)"), section 9 ("`createGraph` gains a mode parameter")
- **Issue:** AGENTS.md says status.md is "the single source of truth for progress". Its top table says implementation phases are not started while the same file says all four slices are built. Criterion 14 is marked "Met", but the rule built is "infrastructure, testing fakes and `Secret` itself", not "the API auth plugin"; the spec text was never amended. Section 6's "Clock port only" is contradicted by ADR-027 (timers use `delay`). Use cases number 71 invoke declarations, not about 45. Section 9's mode parameter was dropped (implementation-notes records it, the spec does not).
- **Why it matters:** The acceptance table is the evidence a reviewer relies on; "Met" against criteria whose wording was silently changed is false assurance. A later reader cannot tell what was agreed.
- **Fix:** Update the progress table, tick tasks, add a "Spec amendments" list to spec.md (criterion 14, Clock wording, createGraph, use-case count) and fill "Lessons learned" (at minimum the red-first deviation and the stale-coverage episode).
- **Acceptance criteria:**
  - status.md progress table matches the slice checklists; spec.md criterion 14 text equals the Konsist rule name in `PortAndSecretRulesTest`; no "(none yet)" in implementation-notes.md.


### FR-R25 (P1): PRD still contradicts the pinned server and the built behaviour in three places  [F-D-4]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed (I traced the code path).
- **Where:** paperclip-agent-factory-mobile-PRD.md FR-001 (`exposure`), FR-005 ("shows the reported deployment mode, exposure and server version"), FR-031 and FR-033 (no source-kind list); status.md Phase 0 checklist (unchecked item); server: ~/Code/Test/paperclip/server/src/routes/health.ts:405-415 (anonymous response has `deploymentMode`, `deploymentExposure`, no `version`)
- **Issue:** Spec section 5 promised the PRD would be corrected (task P0.2). Only FR-030/FR-032/FR-082 and the Snooze note were. FR-005 still promises showing the server version before pairing, but the anonymous health response omits `version` (docs/technical-details.md says so too, so docs and PRD disagree). The field name `deploymentExposure` and the 11 attention source kinds are not in the PRD. The `FR-063` kinds list (five plus unsupported) is consistent with the code.
- **Why it matters:** Phase 1b adapter authors build from the PRD; FR-005 as written cannot be met.
- **Fix:** Edit FR-005 ("server version when the server reveals it, which it does only to signed-in callers"), FR-001 (`deploymentExposure`), add the source-kind list to FR-031, tick the status item. Also correct technical-details "version is only in the response to signed-in callers": the 503 unhealthy branch (health.ts:326) includes `version` for anyone.
- **Acceptance criteria:**
  - PRD FR-005 no longer promises a pre-pairing version; `grep deploymentExposure` hits the PRD; status.md Phase 0 box ticked.


### FR-R26 (P1): Requirements-traceability rule is satisfied by a comment mention, so "every FR is named by a test" is weaker than documented  [F-D-5]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed (I traced the code path).
- **Note:** duplicate of F-C-5; fix once.
- **Where:** shared/umbrella/src/konsistTest/.../architecture/RequirementsTraceabilityTest.kt:24-36; claims in docs/technical-details.md, ADR-028, status.md criterion 1 ("Met")
- **Issue:** The rule regex-matches `FR-\d{3}` anywhere in any `commonTest` file text, including KDoc, comments and fixtures. It does not check that the id appears in a `@Test` name or that the test asserts anything. A one-line comment satisfies it. The "named by a test" and "Met" wording overstates it. (All 47 ids are in fact present; I did not audit whether each has a real assertion.) It also does not verify the ids on the three PRD ranges that are Phase 1b (FR-011, FR-012) are excluded for the right reason.
- **Why it matters:** Criterion 1 is the spec's coverage-of-requirements evidence; a mention-only check gives false assurance as the code evolves and tests are deleted but comments remain.
- **Fix:** Require the id in a `@Test` function name or in the KDoc/annotation attached to a `@Test` function (Konsist `functions().withAnnotationOf(Test::class)`), and soften the docs to "tagged in a test".
- **Acceptance criteria:**
  - Test `traceabilityIgnoresIdsOutsideTestFunctions` (Konsist rule applied to a temp file with the id only in a top-level comment must fail).


### FR-R27 (P2): `BaseUri` does not validate the path and accepts odd hosts  [F-A-10]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: suspected.
- **Where:** domain/BaseUri.kt:59-117 (`HOST_NAME`, `normalise`)
- **Issue:** The path is kept verbatim: spaces, control characters, `..` segments and `//` pass (`https://h/a b`, `https://h/%0d`). The host regex allows `a..b` and `a-.b`. Query, fragment and userinfo are correctly rejected; `localhost.evil.com` is correctly not treated as local. `http` allowlist is exact.
- **Why it matters:** The value is concatenated into `"$value/api"`; downstream request building must then be trusted to encode it.
- **Fix:** Reject whitespace/control chars and `..` in the path; tighten the host regex to labels.
- **Acceptance criteria:**
  - `BaseUriTest.rejects_whitespace_and_dot_segments_in_path`, `rejects_empty_host_labels`.


### FR-R28 (P2): Retries stack up and `retryingRead` retries unreachable and certificate failures  [F-A-11]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** RetryingRead.kt; auth/LoadInstanceAccess.kt:32-35; approval/ApprovalUseCases.kt:34-50; issue/IssueReads.kt:74-93
- **Issue:** Multi-read use cases (`LoadApproval`, `LoadIssue`, `LoadInstanceAccess`) each retry independently (3.5 s of backoff per read). Offline, a screen waits roughly 3.5 s per read in sequence before the failure shows. `DomainError.Unreachable` also covers "certificate or name could not be verified" (DomainError doc), which is retried three times though it can never succeed.
- **Why it matters:** 
- **Fix:** Skip retries when `Connectivity.isOnline` is false; add an overall budget per use case; split cert failure from `Unreachable`.
- **Acceptance criteria:**
  - `load_approval_offline_fails_within_one_backoff_budget`.


### FR-R29 (P2): Dead parameters and unused domain functions  [F-A-12]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** issue/IssueReads.kt:34-50 (`userId` unused in `ListIssues` and `SearchIssues`); domain/WorkEnums/Issue.kt `IssueFilter.matches` (used by fakes and tests only); domain/SnoozeTimes.kt imports `DayOfWeek`, `atStartOfDayIn` unused
- **Issue:** `ListIssues.invoke(..., userId)` never reads `userId` (AssignedToMe is server-side), so callers pass a value that does nothing. `CriticalAndHigh` is filtered after paging, so a page can be empty with `hasMore = true` (disclosed in `IssuePage` KDoc, but the UI must keep fetching).
- **Why it matters:** 
- **Fix:** Drop the parameter; for CriticalAndHigh loop until the page fills or the server runs out.
- **Acceptance criteria:**
  - `critical_and_high_page_fills_across_server_pages`.


### FR-R30 (P2): `PairInstance` polling is unbounded by server-supplied values and ignores rate limiting  [F-A-7]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** auth/PairInstance.kt:72-84, 112-113
- **Issue:** The poll interval is only floored (`coerceAtLeast(250)`), never capped, and `expiresAt` is trusted. A server (or typo/garbage value) with a huge `suggestedPollIntervalMs` or far-future `expiresAt` makes the screen wait indefinitely; the loop checks the clock only before a `delay`, so one `delay(interval)` can run far past expiry. The KDoc on `isKeepWaiting` says "or a rate limit" must not abort pairing, but only `Unreachable`, `Timeout` and `ServerError` are tolerated; `DomainError` has no rate-limit variant, so how a 429 maps is unclear and it may abort the pairing as `Failed(...)`. Poll failures such as `Unauthorized`/`NotFound` end the pairing correctly, but no maximum wait or failure count exists for a persistent `Unreachable` (keeps polling until expiry).
- **Why it matters:** A hung "waiting for approval" screen the user cannot trust; a documented behavior (rate limit) that is not implemented.
- **Fix:** Clamp the interval to a range (for example 250 ms to 10 s) and the wait to `min(expiresAt, now + 15 min)`; use `withTimeoutOrNull` on the whole wait; map 429 to an explicit tolerated error or document and test it.
- **Acceptance criteria:**
  - `huge_poll_interval_is_clamped`; `far_future_expiry_is_capped`; `rate_limited_poll_keeps_waiting`.


### FR-R31 (P2): `Secret.startsWith` copies the whole key into an unwipeable String  [F-A-9]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** domain/Secret.kt:23-24 (used by auth/SaveManualBoardKey.kt:30)
- **Issue:** `startsWith` calls `chars.concatToString()`, creating an immutable copy of the full secret that `wipe()` cannot clear, just for a prefix check. The KDoc says it exists so callers can check shape "without reading it".
- **Why it matters:** Key material lifetime (the class exists to avoid exactly this). Low impact.
- **Fix:** Compare the first N chars of the `CharArray` directly with no String allocation.
- **Acceptance criteria:**
  - `startsWith_does_not_allocate_full_value` (cannot be asserted directly; assert behavior on empty, shorter-than-prefix and wiped secrets).


### FR-R32 (P2): `createGraph` and the demo graph hide real behaviour behind fakes with a frozen clock  [F-B-10]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** umbrella/.../Modules.kt:~97-116 (`portsModule`), DemoModules.kt:~60-110 (`demoPortsModule`), PaperclipCore.kt:79-92 (`createGraph`); testing FakeClock (only moves via `advance`), FakeBiometrics (always `Approved`)
- **Issue:** `PaperclipCore.createGraph(allowInsecureLocal)` is documented as the live graph but wires every port (`SecretStore`, `Biometrics`, `Clock`, gateways) to in-memory fakes. A platform that calls it ships auto-approving biometrics and non-persistent secrets with nothing to say so. There is also no seam for the platform to supply real adapters. In both graphs the clock never advances, so the idle-lock timeout, the 5 s undo window, key expiry, "Decide now" grouping and snooze presets cannot be exercised in demo mode, and the holder-level `Updated` stamps never move. Biometric failure paths (Cancelled/LockedOut) cannot be triggered from the graph.
- **Why it matters:** Demo and live graph differ in exactly the time- and device-auth-dependent rules the product depends on, and a release build can reach fake security ports.
- **Fix:** Rename or gate `createGraph` (for example `createFakeBackedGraph`, internal until Phase 1b) or accept a `PlatformPorts` parameter. Add a Konsist/Gradle check that release variants do not link `createGraph` with fakes. Drive the demo `FakeClock` from a real or accelerated source, and expose `FakeBiometrics`/connectivity toggles for demo mode.
- **Acceptance criteria:**
  - `PaperclipCoreTest.releaseGraphDoesNotUseFakeBiometrics` (or an architecture test that `createGraph` requires injected `Biometrics`/`SecretStore`); `DemoGraphTest.idleLockEngagesAfterConfiguredPeriod` using an advanceable clock.


### FR-R33 (P2): UI mapping by enum ordinal or `valueOf` with no compile-time exhaustiveness  [F-B-11]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** about 14 sites, for example ApprovalRenderer.kt `ApprovalActionUi.entries[it.ordinal]` and `ApprovalStatus.toUi()`; DecisionRenderer.kt `ExecutionStatusUi.entries[...]`, `EffectStatusUi.entries[ordinal]`; BudgetIncidentRenderer.kt `BudgetThresholdUi.entries[it.ordinal]`; AttentionUi.kt (`Severity`, `InboxGroup`, `InlineAction`, `Destination.kind`, `ResolverPolicy`), AttentionSupportUi.kt (`SnoozePreset`); RunViewerState.kt `RunStreamUi.valueOf(it.name)`
- **Issue:** Today the orders match (checked `ApprovalStatus`, `ApprovalAction`, `ExecutionStatus`, `EffectStatus`, `BudgetThreshold`). Adding or reordering a domain value silently mis-maps (ordinal) or crashes with `IllegalArgumentException` (`valueOf`), and the compiler does not warn.
- **Why it matters:** 
- **Fix:** Use exhaustive `when` expressions, as the `AttentionSourceKind`, `ErrorKey` and `DomainError` mappers already do. Or add one test per enum asserting `entries.map(name)` equality.
- **Acceptance criteria:**
  - `UiEnumMappingTest` asserting each domain enum's names map one-to-one to its Ui twin.


### FR-R34 (P2): App lock and refresh lifecycle need two separate platform calls  [F-B-12]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** presentation/.../AppStateHolder.kt:92-102 (`WentToBackground`/`CameToForeground` intents); umbrella/.../RefreshScreens.kt:24-37 (`AppLifecycleBridge.enterBackground/enterForeground`)
- **Issue:** The doc comment asks the platform to call both "next to" each other. Forgetting one means the app keeps polling in the background, or never relocks. `unlock()` also leaves `unlocking=true` if `useCases.unlock()` throws.
- **Why it matters:** 
- **Fix:** Let `AppStateHolder` call the bridge (or take `AppLifecycle` as an input), so there is one entry point per scene change. Wrap the unlock in `try/finally`.
- **Acceptance criteria:**
  - `AppStateHolderTest.backgroundIntentAlsoStopsRefreshPolicy`.


### FR-R35 (P2): Swift-facing and wording notes  [F-B-13]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** `val description: String?` in DecisionState.kt:42 (`OptionUi`), IssueState.kt:27, InteractionUi.kt:58 and 76; `ErrorUi.detail` (Common.kt:52-55); domain/application types in public state and intents (`IdlePeriod`, `Exposure`, `ProbeRefusal`, `InstanceNameIssue`, `ServerKeyOutcome` mapped but `SettingsIntent.IdlePeriodChosen(IdlePeriod)`)
- **Issue:** (a) `description` collides with `NSObject.description`; the Obj-C export renames it to `description_`, so Swift code reads an odd name. (b) `ErrorUi(Rejected).detail` carries the server's own reason string into state; this is documented, and it is not raw JSON, but it is server prose and cannot be localised. (c) A few public state types expose domain enums, which ties the Swift surface to domain changes.
- **Why it matters:** 
- **Fix:** Rename to `summary`/`details`. Keep (b) but state it in the ADR. Introduce UI twins for (c) as the other enums already have.
- **Acceptance criteria:**
  - `PresentationSurfaceRulesTest` fails on public properties named `description`/`hash`; public presentation types reference only `presentation` types plus kotlin stdlib.


### FR-R36 (P2): Gating is hard-coded to `Role.Viewer` or a single `Action` in several places instead of using `Permissions`  [F-B-5]

- **Reviewer severity:** Warning  ·  **Verification:** NOT REPRODUCED as a wrong flag (maintainability only). `Permissions` has exactly two owner-only actions (RaiseBudgetAndResume, SetAgentBudget); every other mutating action is allowed to Operator, so `role != Viewer` equals `Permissions.allows` for Decision, RunViewer, Agents, Inbox. Budget raise and AgentDetail set-budget already use `Permissions`. The only concrete defect found (by code reading, BudgetIncidentRenderer.kt:115 and AgentDetailStateHolder.kt:211): an Operator sees raise and set-budget disabled with `actionsDisabledReason = null` (no reason key), a UX gap, not a wrong enabled flag. It would become a bug only if the matrix changes. `rejectNeedsDeviceAuth = true` is a literal but matches `DeviceAuthPolicy` today.
- **Corrected severity (verification):** Downgrade to Info (maintainability); the missing disabled-reason for Operator is a small Warning-level UX gap
- **Where:** presentation/.../DecisionRenderer.kt:81 and 94 (`allowed = ... role != Role.Viewer`, `canDismiss = open != null && allowed`), 52-57; presentation/.../RunViewerStateHolder.kt:141, 165 (`it != Role.Viewer` for cancel); `disabledReason(...)` copies in Inbox, Approval, Decision, Budget, Agents, AgentDetail, RunViewer, WorkList and Issue renderers (all test `role == Role.Viewer`); presentation/.../AgentsState.kt:~107-121 (`actionsFor` checks `Action.PauseAgent` for Resume and RunNow too); ApprovalRenderer.kt:75 (`rejectNeedsDeviceAuth = true` constant)
- **Issue:** The matrix is `Permissions.allows`. Where it is bypassed, the screen and the use case can disagree if the matrix changes. Example: `canDismiss` is true for Operators from `allowed`, while the holder's `dismiss()` later asks `mayAct(Action.DismissDecision)`. Cancel uses `Viewer`, while the use case checks `Action.CancelRun`. `rejectNeedsDeviceAuth` is a literal, so it cannot follow `DeviceAuthPolicy`. The disabled-reason is "Viewer" even if a non-viewer role lacks the action (e.g. Operator on owner-only budget raise shows no reason).
- **Why it matters:** Role and offline gating is the main safety net in the UI. Duplicated rules drift silently.
- **Fix:** Use `Permissions.allows(role, Action.X)` and `DeviceAuthPolicy.requires(Action.X)` in every `can*`/`needsDeviceAuth` flag. Move one shared `disabledReason(online, role, action)` to Common.kt. Use the per-action permission in `actionsFor`.
- **Acceptance criteria:**
  - A parameterized `RoleGatingTest` over (Role x screen action x online) asserting each `can*` flag equals `online && Permissions.allows(role, action)`. `rejectNeedsDeviceAuthFollowsPolicy`.


### FR-R37 (P2): Paired-devices loads are not ordered or cancelled  [F-B-8]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** presentation/.../SettingsStateHolder.kt:246-261 (`SettingsDevices.load`), 318-324 (`revoke` -> `devices.load`)
- **Issue:** Each `load(instanceId)` launches an independent coroutine and no job is kept. Request devices for factory A, then B: if A's response comes last it overwrites `pairedDevices` (and profile name/email) with A's keys while the user is looking at B. The `error` also comes from whichever finished last. A revoke triggers another overlapping load.
- **Why it matters:** The list drives the revoke confirmation; showing keys for the wrong factory next to a revoke button is a correctness and safety issue.
- **Fix:** Keep `loadJob`, cancel it when a new load starts, and apply the result only if `state.pairedDevices?.instanceId` still equals the requested id.
- **Acceptance criteria:**
  - `SettingsStateHolderTest.slowLoadForPreviousFactoryDoesNotOverwriteCurrent`.


### FR-R38 (P2): Real-time and unbounded waiting in umbrella tests (flake and vacuous-pass risk)  [F-C-10]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: suspected (needs a stress run on the Android host and the iOS simulator).
- **Where:** shared/umbrella/src/commonTest/kotlin/com/stainedhead/paperclipmobile/umbrella/DemoRefreshWiringTest.kt:28-80; DemoGraphTest.kt:45; DemoGraphDeviceTest.kt:30; PaperclipCoreTest.kt:79-128
- **Issue:** These tests run on `Dispatchers.Default` inside `withContext` with real `delay` and 10 s `withTimeout`. Implementation-notes already records one Android-host flake (the event is emitted before the refresh session subscribes; events are not replayed), now handled by re-sending every 100 ms. The negative test `a stopped session no longer refreshes on live events` emits once, waits 300 ms of real time and asserts the list-call count did not change. It passes as well when nothing ever subscribed or when a refresh is merely slower than 300 ms, so it cannot detect a `stop()` that leaves the subscription alive. Every test shares one shared demo graph per instance, and `Dispatchers.Default` interleaving makes the call-count read racy.
- **Why it matters:** CI runs iOS Kotlin/Native and Android host tests on slower runners; a timing-based negative assertion gives false confidence about teardown, a resource-leak risk in a polling app.
- **Fix:** Drive the refresh session with a test scheduler and the injected `Clock`/dispatcher (the same approach as `RefreshVisibleScreenTest`) and prove teardown by `FakeLiveEvents` subscriber count going to 0. Expose `subscriberCount` in `FakeLiveEvents`.
- **Acceptance criteria:**
  - `FakeLiveEvents.subscriberCount == 0` after `session.stop()`, asserted with virtual time and no real delay; the stress run of the umbrella suite (50 iterations) has no failure.


### FR-R39 (P2): TDD evidence is self-reported and the history cannot show red  [F-C-11]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** git history of phase-1a/spec..HEAD; implementation-notes.md TDD logs (slice 1a-1 line 36, slice 1a-2 line 66, slice 1a-4 line 138)
- **Issue:** Every layer commit contains production and test files together (for example 54cd8b8, af4d02d, d500b9c, 7a1bc44, 98aa354, 2e928cb), so the history cannot show test-before-code. The logs state "red seen first" with error counts, and they admit the deviations: slice 1a-1 application and presentation written in the same pass, two mappers written before their tests, contract-suite fakes upgraded with the suites. Two logs claim mutation spot checks that were reverted (RemoveInstance device auth; CancelRun permission). I reproduced the quality of such checks by reading: device-auth-before-gateway, viewer-never-reaches-gateway (Work area), one-attempt-on-timeout (six use cases), secrets-removed on `RemoveInstance` and "address edit clears credentials" would each fail under the obvious mutation (move the call before the guard, drop the permission line, add a retry loop, skip `deleteNamespace`).
- **Why it matters:** AGENTS makes TDD non-negotiable. The process claims are credible and honestly flagged, but a reviewer cannot verify them from history.
- **Fix:** For later slices commit the red test first (one `test:` commit per behaviour group) so the history shows it. For this branch, keep the disclosure in the PR description.
- **Acceptance criteria:**
  - From slice 1b onward, a CI script checks that each `feat:` PR has at least one earlier `test:` commit touching the same module.


### FR-R40 (P2): `DeviceAuthGuardTest` partly restates the production policy  [F-C-12]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** shared/application/src/commonTest/kotlin/com/stainedhead/paperclipmobile/application/DeviceAuthGuardTest.kt:11-27
- **Issue:** The first test computes its expected count with `DeviceAuthPolicy.requires(action)`, the code under test's own dependency, so it proves "the guard obeys the policy" but not which actions are destructive. The destructive list is pinned independently in `PermissionsTest` (`FR-014 exactly the listed actions need device authentication`) and `DeviceAuthRulesTest`, so overall coverage is fine. The test is only weak on its own.
- **Why it matters:** Minor: a reader may take this test as the FR-014 evidence.
- **Fix:** Use the literal set of six actions in this test, or drop the policy call from the expectation.
- **Acceptance criteria:**
  - The test fails if `DeviceAuthGuard` always prompts or never prompts, with no reference to `DeviceAuthPolicy` in the expectation.


### FR-R41 (P2): Config and hygiene observations  [F-C-13]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** config/detekt/detekt.yml; build.gradle.kts:22-35; shared/umbrella/build.gradle.kts:56; status.md header; gradle.properties
- **Issue:** - Detekt: `ReturnCount: 4` (default 2) and the `MagicNumber` ignores are loosened without a stated reason, although the file header says deviations are listed "with the reason". The detekt `source` is `shared/**/src/**` only; `build-logic`, root scripts and `androidApp` are not analysed. There are no inline `@Suppress`, `@Ignore` or `@Disabled` in the branch's Kotlin (checked).
  - Kover: the `core` and `presentation` variants include only the three package prefixes, and `shared/testing`, the umbrella and infrastructure are excluded, as AGENTS requires. The numbers in status.md (97.58% and 97.96%) come from the local run and I could not reproduce them (no Gradle runs allowed). The `core` total also counts lines hit by presentation tests, because the aggregate merges every project's execution data. That is acceptable under the AGENTS wording but means the application gate is not "application tests only".
  - `shared/umbrella` (a production source set) now depends on `:shared:testing`, so fakes and contract suites ship in the XCFramework and the Android library. This is intended for demo mode, but it is a release-size and surface decision that deserves an ADR line.
  - status.md's "Overall progress" table still says Phase 0 "In Progress" and Phases 1 to 3 "Not Started", contradicting the body.
  - A 424 MB `java_pid79587.hprof` heap dump sits in the project root (git-ignored). A build JVM ran out of memory on this machine; with `-Xmx4g` and iOS plus Kover tasks, expect CI memory pressure.
- **Why it matters:** Small, but status.md is "the single source of truth" under AGENTS.
- **Fix:** Add one-line reasons for the detekt deviations. Fix the status table. Delete the heap dump. Add an ADR sentence for fakes in the shipped artifact.
- **Acceptance criteria:**
  - Status table matches the body; the detekt file carries a reason per deviation.


### FR-R42 (P2): CI supply-chain pinning and unpinned tools  [F-C-7]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** .github/workflows/shared.yml, ios.yml, contract.yml, security.yml (all `uses:` lines); .github/workflows/security.yml:20-24; .gitleaks.toml:6-8
- **Issue:** All third-party actions are pinned by a moving tag (`actions/checkout@v7`, `setup-java@v6`, `gradle/actions/setup-gradle@v6`, `actions/cache@v6`, `upload-artifact@v7`, `maxim-lobanov/setup-xcode@v1`), not by commit SHA. The secret scan runs `ghcr.io/gitleaks/gitleaks:latest`. `brew install swiftlint xcodegen` and `xcode-version: latest-stable` are unpinned. The gitleaks allowlist regex `TESTONLY` is applied per match line, so any real-looking token on a line that contains TESTONLY is hidden. The `pcp_board_TESTONLY` entry is redundant.
- **Why it matters:** A compromised or changed action or scanner image runs with the repo checkout. Dependabot is configured for `github-actions`, which only helps with SHA pins. AGENTS asks for secret scanning plus a dependency vulnerability scan; only the first exists, and no Gradle dependency vulnerability check runs.
- **Fix:** Pin actions to full commit SHAs (Dependabot keeps them current). Pin the gitleaks image digest. Narrow the allowlist to `paths` of fixture files or a stricter regex (`pcp_board_TESTONLY[A-Za-z0-9]*`). Add a dependency-vulnerability job (OWASP dependency-check, or Dependabot security updates stated in AGENTS).
- **Acceptance criteria:**
  - No `uses:` without a 40-character SHA (a small grep test in CI). Gitleaks flags `pcp_board_REALKEY... TESTONLY` placed on one line.


### FR-R43 (P2): Konsist rules: contract-per-port and Secret.reveal checks are weaker than they read  [F-C-9]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed.
- **Where:** shared/umbrella/src/konsistTest/kotlin/com/stainedhead/paperclipmobile/architecture/PortAndSecretRulesTest.kt:23-35, 37-52; DependencyRulesTest.kt:48-58; UseCaseBoundaryRulesTest.kt:23-27
- **Issue:** (a) "every port has a contract suite" is satisfied by the string `[PortName]` anywhere in any file in a `contract` directory, so a KDoc cross-reference passes it. Today every port is genuinely named by a suite and a subclass exists (checked: 22 ports, 27 subclasses), so the rule passes honestly. It would still pass if the suite were deleted but another suite's KDoc mentioned the port. (b) The `.reveal()` rule is a text search over `*Main` source sets under `shared/` only: `with(secret) { reveal() }`, a call through a different name, and any Kotlin under `androidApp/` are not seen. (c) The gateway-import rule matches full imports only. A wildcard import `domain.port.*` in presentation would bypass it; today there is none (presentation imports only Clock, Connectivity, BiometricResult from `domain.port`). (d) Infrastructure modules without sources are skipped (known).
- **Why it matters:** These are architectural guards cited as evidence for criteria 4 and 14. The weaknesses are narrow but cheap to close.
- **Fix:** For (a) require a concrete subclass: a Konsist class whose parent is the contract and whose fake is the port's (`Fake<Port>`), or check that `FakeGatewayContractTest`/`FakeContractTest` references `<Port>Contract`. For (b) also scan `androidApp`, and add a rule for `::reveal` and receiver forms. For (c) fail on any wildcard import in presentation.
- **Acceptance criteria:**
  - A negative Konsist fixture or sampled mutation: delete `AppLifecycleContract`'s subclass and `PortAndSecretRulesTest` fails; add `import ...domain.port.*` to a holder and `UseCaseBoundaryRulesTest` fails.


### FR-R44 (P2): "Continue to pairing" is an enabled button with an empty action (honestly documented; acceptable for this phase)  [F-D-10]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed (I traced the code path).
- **Where:** iosApp/Sources/App/FirstRunView.swift:26; docs/product-details.md, user-docs/first-factory.md, docs/technical-details.md
- **Issue:** The button is enabled for a valid address and does nothing; `FirstRunIntent.ContinueRequested` exists but is not sent. This is stated plainly in three documents and the README, so it is not misleading to a reader of the docs. In the running app, a user sees an enabled control that silently does nothing.
- **Why it matters:** INTENT principle 3 and 2 ("never guess what an action will do"). Acceptable for a Phase 0/1a developer-facing shell.
- **Fix:** Either wire `ContinueRequested` and render the next step as "coming soon" text, or keep it disabled until Phase 2 UI exists.
- **Acceptance criteria:**
  - UI test `continueButtonIsNotEnabledUntilItDoesSomething` or a test that tapping it advances `state.step`.


### FR-R45 (P2): Framework build and consumption failure modes (debug vs release, destructive copy, no preflight)  [F-D-11]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed (I traced the code path).
- **Where:** scripts/build-kit.sh:18-23; scripts/ios-test.sh; iosApp/Packages/PaperclipKit/Package.swift
- **Issue:** The package binary target path (`../../Frameworks/PaperclipKit.xcframework`) resolves correctly to `iosApp/Frameworks/`. But the debug and release builds are copied to the same path with no marker, so a Release Xcode build can silently link a debug XCFramework. `build-kit.sh` does `rm -rf "$destination"` before checking `$source` exists, so a wrong source path leaves no framework. `ios-test.sh` does not check the framework exists first, so a missing one surfaces as an opaque package-resolution error (documented only in a comment). project.yml has no signing, no ATS keys and no cleartext keys (good), and `CODE_SIGNING_ALLOWED=NO` is only in the test script.
- **Why it matters:** Convenience only, but a debug framework in a release build is the kind of mistake that is invisible until later.
- **Fix:** Check `[ -d "$source" ]` before `rm -rf`; write a `.configuration` file next to the framework and have `ios-test.sh` verify it; add a preflight message.
- **Acceptance criteria:**
  - running `ios-test.sh` with no framework exits 1 with "run scripts/build-kit.sh"; `build-kit.sh` with a bad source path leaves the old framework in place.


### FR-R46 (P2): Shipping framework contains demo graph, fakes and a fake-key demo set  [F-D-12]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed (I traced the code path).
- **Where:** shared/umbrella/.../PaperclipCore.kt:68-74 (`createDemoGraph` public); docs/technical-details.md ("Ships inside the XCFramework")
- **Issue:** `createDemoGraph()` and the `pcp_board_TESTONLY...` fake key are in every build of the app framework, including release. Gitleaks accepts the obviously fake token. There is no flag to compile demo out of release.
- **Why it matters:** Surface area and binary size; not a secret exposure.
- **Fix:** Decide in Phase 2 whether demo mode is a release feature (PRD FR-009 "demo factory" says yes) and record it in an ADR.
- **Acceptance criteria:**
  - ADR records the decision.


### FR-R47 (P2): Lint configs are lenient (swift-format enables only two rules; SwiftLint is near default)  [F-D-13]

- **Reviewer severity:** Info  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed (I traced the code path).
- **Where:** .swift-format; .swiftlint.yml
- **Issue:** `.swift-format` sets only `AlwaysUseLowerCamelCase` and `OrderedImports` plus `respectsExistingLineBreaks: true`; SwiftLint adds four opt-in rules and disables `todo`. Nothing is hidden dishonestly (the file says "rules stay close to defaults"), and the code is tiny. No `@unchecked Sendable`, `nonisolated(unsafe)` or `@preconcurrency` exist in the Swift sources (grep). Complete strict concurrency is on in project.yml.
- **Why it matters:** Low now; when the Swift code grows the gates will be weaker than AGENTS.md's "zero issues" implies.
- **Fix:** Enable `force_unwrapping`, `force_cast`, `unused_import` and swift-format's default rule set in a later PR.
- **Acceptance criteria:**
  - n/a, observation.


### FR-R48 (P2): ADR bodies still describe designs that do not exist; the correction is an appended "Note"  [F-D-6]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed (I traced the code path).
- **Where:** docs/architectural-decision-record.md ADR-018 (polling "on the `Clock`"), ADR-022 (`SavedFilter`, `SavedFilter.matches`), ADR-023 (`Interaction.answerFor`, `Unsupported(kind)`), ADR-024 (`LogCursor(offset)`), each followed by a "Note" correcting it; code: `DecisionUseCases.kt:101` (`delay(INTERVAL_MS)`), `IssueFilter`, `UnsupportedInteraction`, `Interaction.validate`, `LogChunk`/`LogTail`
- **Issue:** I verified all four disclosed discrepancies are real and are honestly flagged by a Note, so no one is deceived if they read to the end. But the Decision text, which readers quote, still names symbols that do not exist, and data-dictionary.md (not touched on this branch) still lists `Unsupported(kind)`. ADR-018 also contradicts ADR-027 in its own text until the Note.
- **Why it matters:** Future Phase 1b authors grep for `SavedFilter`/`LogCursor` and find nothing; the ADR log is meant to be the reliable record.
- **Fix:** Rewrite the four Decision paragraphs with the real names (keep a one-line "originally proposed as ..." history), and update data-dictionary.md.
- **Acceptance criteria:**
  - every backticked identifier in the ADR Decision paragraphs of ADR-018, 022, 023, 024 resolves via `grep -r` in `shared/`.


### FR-R49 (P2): Infrastructure Konsist rule is vacuous today and will still miss platform source sets later  [F-D-7]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed (I traced the code path).
- **Where:** shared/umbrella/src/konsistTest/.../DependencyRulesTest.kt:46-58; docs/technical-details.md "Layering rules" (discloses the skip)
- **Issue:** Disclosed: the test passes with zero checks because the five infra modules have no `src/commonMain`. Additionally the scan target is only `src/commonMain`, but the securestore and platform modules (Keychain, Keystore, biometrics) will live in `androidMain`/`iosMain` per AGENTS.md. In Phase 1b the rule will start checking only part of the code and still report green. The test also has no "sees code" assertion (unlike the others the docs say "every rule" has; user-docs/testing-guide.md line 82 says "Every rule ... also has a 'can see the code it guards' assertion", which is false for this one).
- **Why it matters:** The infrastructure boundary is where secrets and DTOs live; a silently partial rule is the likeliest place for a violation to enter.
- **Fix:** Scan all `src/*Main` directories of each module; once any infra module has sources, fail if the scan finds none; fix the testing-guide sentence now.
- **Acceptance criteria:**
  - `infrastructureRuleScansPlatformSourceSets` (fixture module with only `iosMain` importing application fails the rule); testing-guide wording matches reality.


### FR-R50 (P2): Swift holder lifecycle: FirstRunModel never closes its holder, and holders are created inside view/scene construction  [F-D-8]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: suspected (needs a reproducing test).
- **Where:** iosApp/Sources/App/FirstRunModel.swift:5-28, iosApp/Sources/App/PaperclipApp.swift:17-21, iosApp/Sources/App/FirstRunView.swift:5; tests iosApp/Tests/iosAppTests/FirstRunModelTests.swift
- **Issue:** `FirstRunModel.deinit` cancels the observation task but never calls `holder.close()`, although technical-details.md ("the screen owns it and calls `close()`") and the Swift rules say the screen must. The model is built inline in `WindowGroup { FirstRunView(model: FirstRunModel(holder: graph.createFirstRunStateHolder())) }`; `@State var model` takes the initial value on first use only, but the closure (and therefore a new holder and scope on `Dispatchers.Default` with a `SupervisorJob`) runs every time SwiftUI re-evaluates the scene content. Each leaked holder keeps its scope alive. `FirstRunView` also keeps a separate `@State address` that duplicates `state.address` (two sources of truth, no sync back if the holder normalizes or resets it).
- **Why it matters:** Today's impact is small (one screen, fakes), but this is the template every later screen copies; leaking scopes with in-flight pairing polls would matter in Phase 1b.
- **Fix:** Own the model in a `@State`/`@StateObject`-style container created once, call `holder.close()` from an explicit `close()` invoked in `.onDisappear`/model deinit, and bind the text field to `model.state.address` through an intent.
- **Acceptance criteria:**
  - Swift Testing `closingTheModelClosesTheHolder` (a fake or counting wrapper, or assert the state flow stops updating after `model.close()`); UI test still passes.


### FR-R51 (P2): Several Swift interop tests assert nothing about the intents they send, and close holders mid-flight  [F-D-9]

- **Reviewer severity:** Warning  ·  **Verification:** Not separately reproduced; reviewer confidence: confirmed (I traced the code path).
- **Where:** iosApp/Tests/iosAppTests/GraphInteropTests.swift:`detailHoldersAcceptTypedIntents`, `workScreensAcceptTypedIntents`, `agentScreensAcceptTypedIntents`, `runViewerAndFactoryHomeAcceptTypedIntents`, `inboxAcceptsTypedIntentsOnceItHasSettled`; DemoGraphInteropTests.swift `aRefreshSessionStartsAndStopsFromAHoldersTarget`, `theLifecycleBridgeFollowsTheShell`
- **Issue:** These call `onIntent(...)` with a long list of intents and then `close()` immediately, with no assertion on the result (the holders load on `Dispatchers.Default`, so the effect has not happened when `close()` runs). They prove the symbols compile and do not crash, nothing more. `aRefreshSessionStartsAndStopsFromAHoldersTarget` never checks that a refresh happened. `FirstRunModelTests` do not close holders. The tests are not racy in a failing sense, but "28 Swift tests green" overstates behavioural coverage; the real behaviour is covered in Kotlin.
- **Why it matters:** The Swift tests exist to guard the interop surface (SKIE names, boxing, main-actor use). Rename-breaking is caught by compilation; semantic breakage (an intent mapped to the wrong case) would not be.
- **Fix:** Add at least one observed effect per group (e.g. after `AgentsIntentPauseTapped` wait with `eventually` for `confirmPause == true`; after `WorkListIntentFilterSelected(.blocked)` wait for `filter == .blocked`), and label the rest as compile-surface smoke tests in the docs.
- **Acceptance criteria:**
  - each listed test contains an `await eventually { ... }` assertion on a state change caused by one of its intents.


## Positive observations (keep doing this)

**Area A**

- Device authentication is before the first gateway call in every destructive use case (`PauseAgent`, `CancelRun`, `RejectApproval`, `DecideDecision` for destructive options, `ResolveBudgetIncident` for a raise, `RemoveInstance`), and it comes after validation so invalid input does not prompt. A viewer returns `Denied` before anything else.
- Mutations are never retried; the single re-read is limited to `Timeout`/`Conflict` (plus a 422 for approvals, which matches the server).
- `retryingRead` is used only for reads; there is no `runCatching` around suspend code, so `CancellationException` is not swallowed anywhere in application or domain.
- `RemoveInstance` correctly shields the secret and row deletion with `NonCancellable`; `PairInstance` wipes all three secrets in `finally`, and `Secret` redacts `toString`, is not a data class and is `@HiddenFromObjC` for `reveal()`.
- `Role.fromServer`/`Permissions` fail closed (unknown role becomes Viewer); local actions open to all roles is a documented, consistent decision.
- `UpdateInstance` deletes the credentials before saving a changed address, so existing stored keys cannot follow an address change (the in-flight enrollment hole is F-A-1).
- Domain layer is dependency-clean (stdlib, coroutines, datetime only); application depends on domain only.

**Area B**

- Every holder owns a `childScope(...)` and `close()` cancels only that child; no holder launches into the caller's scope. No unbounded loops: refresh is driven from the application policy through `ScreenRefreshTarget`, and holders only react.
- `DomainError.toErrorUi()` is an exhaustive `when` (no `else`), and the UI models carry keys (`ErrorKey`, `InvalidKey`, `DeviceAuthIssue`, `ActionsDisabledReason`) and epoch millis rather than prose. The only prose is the documented `ErrorUi.detail`.
- Mutation handling is consistent: one in-flight flag per screen, ignored while offline, `Reconciled` results are shown as the entity's true state, and the Inbox re-reads after `Done`, `Reconciled` and `Timeout`. Mutations are never auto-retried.
- Device auth is not decided in presentation: the confirm flows (reject, destructive decision option, raise budget, pause, cancel, remove factory) all call use cases that go through `DeviceAuthGuard`, and `NotAuthorized` surfaces as `FeedbackKind.DeviceAuth` with an issue key.
- Decision Snooze is shown only when `dismissalKey` is present (renderer `canSnooze` and holder `snooze()` both check). Unsupported attention kinds expose `rawKind` and `webUrl` from the item's `href`. The Inbox badge is the server's `deskBadgeCount`, not computed locally.
- Koin: use cases are `single`, state holders are `factory`, each screen gets its own scope, and the graph uses an isolated `koinApplication`, so graphs do not share state.

**Area C**

- The device-authentication tests are mutation-resistant. `AgentUseCasesTest`, `RunUseCasesTest`, `DecisionUseCasesTest`, `ApprovalUseCasesTest`, `BudgetUseCasesTest` and `RemoveInstanceTest` each loop over cancelled, failed, locked-out and unavailable results and assert the gateway count is 0 and the result is `NotAuthorized`. Viewer cases also assert that biometrics was never asked.
- `WorkRoleMatrixTest` viewer rows assert `Denied` exactly, with zero gateway and zero prompt counts per use case, so removing a `Permissions` line fails it.
- The single-attempt rule is asserted by counting the mutating call (`count("pause") == 1`) and requires `Reconciled`, so a retry loop would fail it.
- `RemoveInstanceTest` and the address-edit test check secret removal through the fake store's namespace paths, including that another factory's secret survives, and test the revoke-timeout path.
- The Konsist rules mostly guard against vacuous passes (non-empty scope assertions), and `DeviceAuthRulesTest` is an exact set equality.
- Fakes carry the server's approval and budget rules; I checked the approval state machine (`approval-records.ts`, `approvals.ts`) against `ApprovalContract` and it matches. The Kover filters correctly exclude `shared/testing`, and the root `check` depends on the Kover verify tasks and `konsistTest`.
- No `@Suppress`, `@Ignore` or `@Disabled` anywhere in the branch.

**Area D**

- Disclosed gaps (ADR-018/022/023/024 notes, unwired Continue button, vacuous infra rule, 21 boxed numbers, demo fakes ignoring company id) are stated plainly in technical-details.md and the spec notes; I found no hidden contradictions beyond F-D-1/F-D-4.
- Doc numbers (ports, use cases, holders, tests, Konsist rules, coverage inputs, versions) match the source exactly; links and image files exist.
- INTENT principles hold in the sampled code: `retryingRead` is used only for reads; mutations are single-call with fixed order; `DeviceAuthRulesTest` pins the exact set of device-auth use cases; no ATS or cleartext keys in project.yml; `createGraph` takes `allowInsecureLocal` from `#if DEBUG`.
- Swift 6 with complete strict concurrency and no unsafe escape hatches; the interop test helper `eventually` avoids asserting transient loading phases.
- ADR numbering is contiguous and the README/ADR index agree.


## Global acceptance criteria

1. Every P0 and P1 requirement above has a regression test that fails on the pre-fix code and passes after.
2. `./gradlew check lint` passes; `koverVerifyCore` and `koverVerifyPresentation` stay at or above 90% and are not lowered; Swift lint, format and Xcode tests pass when Swift-facing code changes.
3. `ios.yml`, `shared.yml`, `contract.yml` and `security.yml` are green on the pull request.
4. No document, ADR, spec file or status table states something the code or the pinned server contradicts (re-run the doc-claim sample from FR-R for docs findings).
5. Findings deferred (P2 only) are listed in the final status with the reason.

## Verification notes

Reproducing tests were temporary and were deleted; the evidence (test names and assertion output) is in the verification table of the Step 4 run. P0 reproductions: F-A-1 (me and listBoardKeys sent to the new host with the stored token), F-A-2 (cancel during revoke leaves the secret in the store), F-B-1 (a late probe result leaves `probe=Accepted` and the new address is saved unprobed).

## Dependencies and risks

- Fixes to address-change and pairing flows touch `KeyEnrollment`, `PairInstance`, `UpdateInstance` and two holders; sequence them so the shared helper is fixed once (FR for F-A-1 first, then F-B-1, F-A-4, F-A-5).
- A central 401 handler (F-A-3) is best placed in the Phase 1b API adapter; here, apply the marker in the use cases and record the adapter requirement.
- The contract suites are the specification for Phase 1b; correcting them (F-C-1, F-C-4) before 1b starts avoids encoding wrong behavior in real adapters.

## Open questions

1. Should `createGraph` keep wiring fakes, or take a mode now (F-B-10)? Recommendation: add the seam in Phase 1b, document it now.
2. Is an enabled "Continue to pairing" button with an empty action acceptable in the Phase 1a app? Reviewer D says yes, documented in four places.

## Appendix: acceptance criteria 1 to 14, audited by reviewer C

| # | Verdict | Note |
|---|---|---|
| 1 | Met in form, partly in substance | See F-C-5 (comment-satisfiable rule; FR-002, 008, 013, 016, 097 are partial). iOS-simulator execution of commonTest has not run on this branch. |
| 2 | Plausible | Gate configuration is correct (domain+application aggregate and presentation separate, line unit, 90, wired into `check`). Numbers not re-measured. |
| 3 | Met | 16 holder test files exist. Run viewer lacks the offline-after-load variant (F-C-2). |
| 4 | Overstated | 27 contract subclasses exist and run against the fake only. Several are tautological (F-C-4) and one contradicts the server (F-C-1). Real adapters are Phase 1b, so "same suite runs against it" is vacuous today. |
| 5 | Met with disclosed gaps | The gaps list is honest. The assertions compare to values copied from the mock into the demo data, which is reasonable but is not a check against the mock itself. |
| 6 | Not verified here (Swift area) | Recorded in implementation-notes.md. |
| 7 | Not met (honestly stated) | Workflows never ran; `contract.yml` runs nothing real (F-C-6). |
| 8 | Partly | Work area table only (F-C-3); other areas by separate tests; domain `PermissionsTest` covers the table. |
| 9 | Met | Cancel, failure, lockout and unavailable are tested with gateway count 0 for pause, cancel run, reject approval, destructive decision, budget raise and remove factory; `DeviceAuthRulesTest` is an equality of two sets and cannot pass vacuously. |
| 10 | Partly | Six of ten use cases in the matrix; others in their own tests (F-C-3). |
| 11 | Partly | Run viewer gap (F-C-2). Only the Inbox has a persisted cache; other screens keep the last in-memory load. |
| 12 | Met | The FR-044 tests exist at use case and holder level. |
| 13 | Met | `AttentionRulesTest` and friends use UTC boundary tables. |
| 14 | Met as worded | Rule limits `.reveal()` to infrastructure, testing and Secret.kt; see F-C-9 for bypasses. |
