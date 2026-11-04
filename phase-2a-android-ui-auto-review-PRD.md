# PRD: Phase 2a Android UI code review fixes (auto-review)

**Created:** 2026-11-04
**Status:** Reviewed (dev-flow Step 6), ready to implement. Both owner decisions are recorded below as run assumptions that the owner may overturn.
**Owner:** J
**Source spec:** [specs/archive/261104-phase-2a-android-ui](./specs/archive/261104-phase-2a-android-ui/spec.md) and [the Android PRD](./specs/archive/261104-phase-2a-android-ui/paperclip-agent-factory-android-PRD.md)
**Related:** [AGENTS.md](./AGENTS.md), [INTENT.md](./INTENT.md), [status.md](./specs/archive/261104-phase-2a-android-ui/status.md), the prior pattern [phase-1a auto-review PRD](./specs/archive/261101-phase-1a-shared-contracts-auto-review/phase-1a-shared-contracts-auto-review-PRD.md)

---

## Executive summary

Four independent read-only reviewers audited the Phase 2a Android branch: (A) the new shared Android services (Keystore secret store, BiometricPrompt bridge, connectivity), (B) the `androidApp` architecture, lifecycle, security shell, build and CI, (C) the attention and factory screens, (D) the operations and work screens, tests, docs and open items. Nobody ran Gradle or a device; every finding comes from reading code, tests and docs, and each cited `file:line` was re-opened while writing this PRD (see Method).

Overall assessment: **Approve with required follow-ups.** The layering and the security shell are strong. Release builds cannot reach fakes; the manifest is minimal (INTERNET, ACCESS_NETWORK_STATE, USE_BIOMETRIC, `allowBackup=false`, only `MainActivity` exported); secrets reach only the `SecretStore` (AES-256-GCM, path as AAD, no plaintext fallback, no logging); no Compose code calls a biometric prompt, and every destructive path except one goes through a shared use case behind `Biometrics`; the Konsist guards have planted-violation tests; CI actions are SHA-pinned; 538 unit tests, 73 Roborazzi baselines and 102 Konsist tests are green. There is **no P0**. The weaknesses are specific:

- the Keystore "5 second window" is system-wide, so a protected read can succeed with no prompt, and ADR-123 claims the opposite (R-001);
- a prompt can hang forever with the store mutex held (R-002);
- typed text is destroyed in several places, including a comment draft on a failed post (R-003);
- one shared use case (`SetAgentBudget`) raises money with no device-owner check, contradicting AGENTS.md (R-004; fixed by adding the gate, run assumption DEC-1);
- the Run viewer log may stop following (R-005, verify first), pushed detail routes survive a factory switch (R-006), an in-flight Inbox approve can be cancelled (R-007), and the Inbox fling misses the 5% jank budget (R-008);
- a large part of the "proven" surface (live pairing, real key invalidation, system prompt, TalkBack, StrongBox, API 28/33, Pixel, hosted CI) has never run (R-009);
- some tests, docs and `status.md` claim more than they prove.

| Priority | Count | Meaning |
|---|---|---|
| P0 | 0 | Reproducing security defect or data loss that must block the PR. None found. |
| P1 | 9 | Security-policy gaps, user-visible defects and unproven acceptance evidence that should be fixed before this phase is archived |
| P2 | 31 | Hardening, maintainability, accessibility, test honesty and doc accuracy; fix if cheap, otherwise schedule |

**Why P0 is empty (reviewed and confirmed in Step 6).** A P0 blocks the PR; it is reserved for a true blocker, not a preference or a hardening wish. A P0 here would be a credential exposure, a destructive action with no device-owner check that anyone can trigger remotely, or data corruption on the default path. The Keystore key still enforces its window, so bypassing the prompt callback gains nothing without a recent system authentication (a weakening, not a break). `SetAgentBudget` is a documented decision (ADR-013), not an accident. Every other P1 is a bounded, recoverable defect or missing evidence.

Two items were tested against this bar on purpose:

- **R-001 (Keystore window) stays P1.** The Keystore key itself enforces the 5 second authentication window, so no secret is readable without a recent system authentication. The weakness is that the window is system-wide: an unlock or another app's biometric check in the last 5 seconds satisfies a protected read without our prompt. That needs physical possession of an unlocked phone inside a 5 second window, exposes nothing at rest, and nothing ships from this branch (no release, no store listing). It is a real weakening of FR-011 that must be fixed before archive, but it is not an exposure that justifies blocking the PR. If the owner decides the phase will be installed on a shared or untrusted device before R-001 lands, promote it to P0.
- **R-004 (no prompt on a budget raise) stays P1.** The behaviour is a documented, deliberate decision (ADR-013, FR-014), the attacker needs an unlocked phone and the owner role, and the result is a changed spend ceiling that the owner can see and reverse (the server enforces the budget, the app never spends). It conflicts with the AGENTS.md wording, which is why it is fixed here (DEC-1), but a documented policy that is arguably too lax is a policy defect, not a P0 data-loss or credential-exposure defect.

Counter-test applied to every P1: "if this merged tomorrow with the fix scheduled, would a secret leak, money move without the owner, or data be destroyed on the default path?" R-003 loses typed drafts (recoverable), R-005 to R-008 are UX and reliability defects with a user workaround, and R-009 is missing evidence. The answer is no for all nine, so P0 stays empty.

## Goals

- G1 Fix every P1 with a failing regression test written first, and resolve the two owner decisions below.
- G2 Make test, doc and status evidence honest: no "proven" claim without a test or recorded run that would fail under the obvious mutation.
- G3 Move app-side business rules into shared presentation so iOS and Android cannot disagree (INTENT principle 5).
- G4 Keep every gate in AGENTS.md green and coverage at or above current levels (core 98.03%, presentation 98.10%).

## Non-Goals

- New features. No QR scan, no deep links, no markdown rendering on Android (see Out of scope).
- Reworking module layering or the screen set.
- Changing ADR decisions beyond the two named (ADR-123 correction, ADR-013/FR-014 amendment) and only with owner approval for the latter.

## Method

- **Four reviewers, read-only, no builds, no devices.** Each read AGENTS.md, INTENT.md, the spec and architecture, and the changed code (`git diff origin/main...HEAD`), plus the matching iPhone code where parity mattered.
- **Verified by reading:** the call chains behind each finding, test bodies (to judge whether an assertion can fail), manifests, build files, workflows, scripts, and documentation counts (538 tests in 80 classes, 73 PNG baselines, 14 connected tests, 102 Konsist tests, ADR numbering, image files; all sampled and correct).
- **Verified here (author of this PRD):** each cited path and line was opened or grepped on this branch; the confirmed ones are cited unchanged. Lines the reviewers quoted that were not individually re-opened are marked "(reviewer-cited)". Items I could not confirm are listed in "Findings dropped or not confirmed" at the end.
- **Not verified, by design:** behaviour that needs a runtime. Compose and `MutatorMutex` cancellation semantics (R-005), androidx navigation `ViewModelStore` lifetime on lock (R-021), TalkBack output (R-025, R-026), the library behaviour of biometric-1.1.0 beyond a string check in the cached AAR (R-002), Android network validation on a specific VPN (R-012), jank causes (R-008, not profiled), and the Roborazzi images themselves (only two viewed). Such findings say "verify first" and carry a demotion rule.

## Guidance for implementing this PRD

- **TDD is mandatory for every fix (red, green, refactor).** Write the failing test named in each requirement, run it, see it fail for the stated reason, write the minimum fix, then refactor with everything green. Bug fixes start with a failing regression test (AGENTS.md). Where a finding says "verify first", the failing test is also the verification; if it cannot be made to fail, demote the finding to a note in `implementation-notes.md` and close it with that evidence instead of changing code.
- **One commit per finding** (merged findings are one commit per merged requirement). Conventional Commits, mention `specs/archive/261104-phase-2a-android-ui`. Every finding has its own commit before the step is marked complete; P0 would block the PR (none).
- **Brief code and design review per fix before moving on.** After green, re-read your own diff against AGENTS.md (layering, Security Rules, Swift-facing rules, no business rules in a `@Composable`), run the relevant gate, and note any follow-up in `implementation-notes.md`. A second teammate reviews security-sensitive fixes (R-001, R-002, R-004).
- **Agent teammates (worker model `claude-sonnet-4-6`)** run the parallel fix workstreams below, each in its own git worktree. The lead (this session's model) reviews each diff, merges in the order given, and owns the two owner-decision items. Teammates get the requirement text, the evidence lines and the gate list, not the whole review.
- **Git worktrees.** One worktree and one branch per workstream (`git worktree add ../pafm-<group> -b fix/<group> worktree-android-prd`); a teammate works only in its own worktree and never edits files owned by another group. Partition by file ownership so merges are conflict-free (see the groups below). Worktrees are for editing and reviewing in parallel; they are not a licence to build in parallel.
- **One Gradle build at a time per machine.** The Gradle daemon, the Kotlin build cache and `build/` outputs are shared by the whole machine, and the Android SDK and emulator are single instances. Rules: (1) every Gradle, `xcodebuild` and emulator command is wrapped in a machine-wide lock, `flock /tmp/pafm-build.lock <command>` (macOS without `flock`: `lockf /tmp/pafm-build.lock <command>`, or a `mkdir /tmp/pafm-build.lock` spin loop with a trap); (2) a teammate does the edit-and-think work (test, fix, self-review) without holding the lock and takes it only for a targeted run (`./gradlew :shared:presentation:allTests --tests ...`, `:androidApp:testDebugUnitTest --tests ...`); (3) the full gate list runs once per merged group, by the lead, not once per teammate commit; (4) Roborazzi record/verify and connected tests are never run concurrently; (5) the iOS gates (`build-kit.sh`, `ios-test.sh`) take the same lock because they call Gradle and the simulator.
- Never lower a gate, never suppress a rule inline, never edit baselines to green a build without reading the diff. Regenerate coverage before quoting numbers (`rm -rf build/reports/kover && ./gradlew koverXmlReportCore koverXmlReportPresentation`).

## Non-functional requirements and quality gates

All of these stay green after every commit that can affect them, and before the final merge (AGENTS.md Toolchain):

```bash
./gradlew spotlessCheck                          # ktlint
./gradlew detekt                                 # zero issues
./gradlew lint                                   # Android Lint, zero new warnings
./gradlew allTests                               # jvm, androidHostTest, iosSimulatorArm64
./gradlew konsistTest                            # architecture rules (add rules, never relax)
./gradlew koverVerifyCore koverVerifyPresentation
./gradlew check
./gradlew :androidApp:lint :androidApp:testDebugUnitTest :androidApp:verifyRoborazziDebug :androidApp:assembleRelease   # android.yml
# only when exported shared surface or behaviour changes (R-004, R-007, R-019, R-024; see the iPhone impact table), under the build lock:
./scripts/build-kit.sh debug && swiftlint --strict && xcrun swift-format lint --recursive --strict iosApp/Sources iosApp/Tests iosApp/Packages/PaperclipKit/Package.swift && ./scripts/ios-test.sh
```

- **Coverage is not reduced:** core (domain + application) and presentation stay at or above 98.03% and 98.10% and never below the 90% gates; infrastructure and app modules are outside the gate, but new code there ships with tests anyway.
- **Security:** no fix may log, stringify or store a secret outside `SecretStore`; the mutation order (permission, validation, device authentication, one gateway call, one re-read) and the no-auto-retry rule are never relaxed. New Konsist rules carry a planted-violation test.
- **Reliability:** no new unbounded wait; every new suspension has a bound or a cancellation path; cancellation is never swallowed.
- **Roborazzi:** baselines change only when the screen change is intended; record with `./gradlew :androidApp:recordRoborazziDebug`, read the diff images before committing, update the matching `docs/images/` and `user-docs/images/` files in the same PR (AGENTS.md "stale image is a docs gap").
- **Docs in the same PR as behaviour:** `docs/technical-details.md`, `docs/product-details.md`, `docs/architectural-decision-record.md` (new ADR for R-001, R-004), `README.md`, `user-docs/`.
- **Compatibility:** the Swift-facing API stays free of generics, default arguments and `Result`; any public shared change updates the iOS tests in the same commit.

## Implementation order and parallel workstreams

The order is built so that the long single-lane work (shared code, with the iOS gates) goes first and alone, and only editing parallelises afterwards. With one Gradle build at a time, Groups 2 to 4 gain parallel authoring and review, not parallel builds; budget their build time as sequential, lock-ordered runs.

1. **Group 1, shared core (first, one teammate or the lead, sequential, owns every file under `shared/`):** R-001 then R-002 (same files; R-002 as its own commit after R-001's test commit), R-004 (DEC-1 is decided, see below), R-007 shared half, R-024 shared half (holder clears the amount), R-019 shared parts, R-010, R-011 (contract and launcher tests), R-012, R-013. Merge before anything else because `androidApp` and `iosApp` consume these APIs. After each commit that touches exported surface, run the iOS gates (table below) before starting the next one.
2. **Group 2, androidApp text and input fixes (starts after Group 1 is merged; one teammate; owns `ApprovalSections.kt`, `BudgetSections.kt`, `NoteSheet.kt`, `Components.kt`, `IssueCommentBar.kt`, `InteractionControls.kt`, `FirstRunScreen.kt`, `ReorderableColumn.kt`):** R-003, R-023, R-024 app half, R-025, R-026 (banners owned here), R-027, R-028, R-029, R-033, R-034, R-036, R-037.
3. **Group 3, androidApp navigation, lifecycle, Inbox and Run (authoring parallel with Group 2, builds serialised; one teammate; owns `env/*`, `shell/*`, `InboxScreen.kt`, `InboxCard.kt`, `InboxSwipe.kt`, `InboxSheets.kt`, `RunViewerLog.kt`, `UpdatedLabel.kt`, list screens):** R-005, R-006, R-007 app half, R-008, R-018, R-021, R-022, R-032. R-026's Undo snackbar change in `InboxSheets.kt` belongs to Group 3; the other R-026 elements belong to Group 2 (the teammates agree the semantics wording first).
4. **Group 4, tests, build and CI (authoring parallel; one teammate; owns test additions that no other group touches, `build.gradle.kts`, `.github/workflows/android.yml`, Konsist):** R-014, R-015, R-016, R-017, R-020, R-030, R-031, R-035. Roborazzi baseline re-records (R-031, and any visual change from Groups 2 and 3) happen once, last, by the lead, to avoid baseline merge conflicts on binary PNGs.
5. **Group 5, docs and status (last, describes the final code):** R-039, R-040, R-038 (design only, see its note), the ADR-123 correction and the DEC-1 and DEC-2 ADR entries, plus the evidence requirement R-009 recorded in `status.md`.
6. **Owner-hardware track (parallel, not a teammate task):** R-009 runs on the owner's devices and CI as the code lands.

Merge order: Group 1 (full gate list plus iOS gates), then Group 2, then Group 3 (disjoint files; full Android command after each), then Group 4 (re-record baselines once), then Group 5. After each merge run the Android CI command and `./gradlew check`.

### Shared changes: iPhone impact and iOS gates

Every shared change below names what it does to the iPhone. Whenever the exported surface (anything visible through `PaperclipKit`: public classes, enums, state data classes, factory functions in `shared/umbrella` or `shared/presentation`) or a user-visible behaviour of shared code changes, the same commit updates the iOS tests, and before the next commit this runs under the build lock: `./scripts/build-kit.sh debug && swiftlint --strict && xcrun swift-format lint --recursive --strict iosApp/Sources iosApp/Tests iosApp/Packages/PaperclipKit/Package.swift && ./scripts/ios-test.sh`. A change that only touches `infrastructure/*` androidMain or `androidApp` needs no iOS gate.

| Req | Shared change | iPhone impact | iOS tests and gates |
|---|---|---|---|
| R-001, R-002, R-010, R-013 | `securestore` and `platform` androidMain only (`KeystoreBackend`, `KeystoreSecretStore`, `PromptLauncher`, `RecordFiles`); `FakeKeystoreBackend` in `shared/testing` | None at runtime (iOS uses the Keychain actual). `SecretStoreContract` additions in `shared/testing` also run on the iOS simulator | `./gradlew allTests` (includes `iosSimulatorArm64`); no Swift change; run `./scripts/build-kit.sh debug` once after the contract change to confirm the framework still links |
| R-004 | New `Action.RaiseAgentBudget` in the domain, added to `DeviceAuthPolicy`; `SetAgentBudget` takes `DeviceAuthGuard` and returns `NotAuthorized` | **Behaviour change on iPhone**: raising a budget on Agent detail now shows the Face ID / passcode prompt; lock glyph on the Set budget button (`AgentDetailView.swift:206`) | Update the iPhone agent-detail and budget tests in `iosApp/Tests/iosAppTests` (a raise asks for the prompt, a cancel changes nothing); full iOS gates |
| R-007 | `isWorking` field on `InboxState` | New public field visible to Swift (additive) | Swift tests that construct or compare `InboxState` updated; full iOS gates |
| R-024 | `BudgetIncidentStateHolder` clears the amount when the choice leaves Raise | iPhone keeps `amountText` in the sheet (`BudgetIncidentSheet.swift:35,191-197`); it must stay consistent with the holder's cleared amount | `BudgetModelTests.swift` updated; full iOS gates |
| R-019 | Move `RunStatusUi.isLive`, visible agent actions, factory status decisions, tone enums, `DecideByUi.hintText` into `shared/presentation` | iPhone has its own copies today; it switches to the shared result in the same PR or a recorded follow-up. New public types are Swift-facing: no generics, no default arguments, no `Result` | Swift tests for each consumed rule; full iOS gates; one commit per moved rule so a failing gate points at one rule |
| R-012 | None to shared (the `Connectivity` port stays `StateFlow<Boolean>`); only `ConnectivityManagerConnectivity` (androidMain) | None | No iOS gate |
| R-011, R-020, R-030 | Test-only | None | `allTests` |

If an iOS gate fails because of a shared change, the commit is not done; fix forward in the same group before moving on.

## Decisions (run assumptions, owner may overturn)

No owner was available during the dev-flow run, so the two open owner questions are resolved by adopting this PRD's own recommendations. Each is marked **Run assumption, owner may overturn**. If the owner overturns one, only the named requirement, its ADR text and the listed tests change; nothing else in this PRD depends on the answer.

- **DEC-1 (R-004): a raise of an agent's budget asks for the device owner. Run assumption, owner may overturn.** `SetAgentBudget` goes through the device-owner check (`DeviceAuthGuard`, hence `Biometrics`) when the new monthly amount is greater than the current one. Reasons: AGENTS.md Security Rules already list "budget raise" as a destructive action, and INTENT principle 1 puts security over convenience; the text in ADR-013 and FR-014 that limits the prompt to budget incidents is the outlier and is amended. A decrease needs no prompt (it cannot let an agent spend more), with the exception recorded in R-004. The Set budget button gets the lock glyph on both platforms. This is a shared change and changes the iPhone's behaviour (see the table above). If the owner overturns it, the alternative is to amend AGENTS.md to say "budget incident raise" only, and R-004 closes with that ADR text and no code.
- **DEC-2 (R-012): connectivity counts as online whenever the system reports a default network that declares `NET_CAPABILITY_INTERNET`, validated or not. Run assumption, owner may overturn.** A network that has INTERNET but is not `VALIDATED` (home LAN, split-DNS, captive portal, Tailscale-only path, a VPN whose probe fails) is "online, unknown": the app attempts the request and lets the real success or typed failure surface (INTENT principle 7: home factories behind a VPN are the primary case; principle 2: show the server's truth). The app reads as offline only when there is no default network, or the network lacks INTERNET. Mutations are not blocked by an unvalidated network and are still never auto-retried. No shared API change: the `Connectivity` port stays a `StateFlow<Boolean>`.

## Out of scope / deferred

- QR scan to add a factory, the `paperclipmobile://add` deep link and export-as-QR (FR-004, FR-008): not built on either platform; one follow-up PR for both (PRD table line 181). The scheme is not registered in the manifest today (see R-039).
- Markdown rendering on Android: the iPhone renders it; Android shows plain text. Deferred by the Android PRD.
- Configuring agents, authoring documents, administering the instance (INTENT).
- A Perfetto-driven rewrite of the Macrobenchmark; only the targeted R-008 changes and a re-measure.

---

# Functional requirements (one per finding)

Reviewer ids are in brackets. P1 first, then P2.

## P1

### R-001 (P1): Protected Keystore keys are not bound to the prompt; ADR-123's 5 second window claim is false  [A-1]

- **Evidence:** `shared/infrastructure/securestore/src/androidMain/kotlin/com/stainedhead/paperclipmobile/infrastructure/securestore/AndroidKeystoreBackend.kt:26` (`AUTH_WINDOW_SECONDS = 5`) and `:155` (`setUserAuthenticationParameters(...)` with the window, STRONG or CREDENTIAL); `KeystoreSecretStore.kt:140-142` (`open()` first, prompt only on `NeedsAuthentication`); `FakeKeystoreBackend.kt:15` (sticky `authenticated`) hides it; `docs/architectural-decision-record.md` ADR-123 (line 935, item 3 says the window "opens only through a prompt the store itself asked for"); `docs/technical-details.md:319` and `:410` describe the window.
- **Failure scenario:** Keystore authentication tokens are system-wide. A lock-screen unlock, or a biometric or credential check in any other app, within the last 5 seconds opens the window for this app's key, so a protected `get()` returns `Found` with no prompt. A prompt approval also stays reusable for 5 seconds by anything that can call `open()` in the process. This violates "protected means the person is asked" (FR-011) and INTENT principle 1. The key still enforces the window, so this is a weakening, not a break.
- **Fix (per-use authorization):** generate protected keys with `setUserAuthenticationParameters(0, STRONG or CREDENTIAL)` (valid on API 30+, already required for protected keys). `KeystoreBackend.open` and `seal` return `NeedsAuthentication(cipher)` carrying an initialised, unused `Cipher`; `authenticate` becomes `suspend (reason, Cipher) -> Cipher?`; `ActivityPromptLauncher` passes `BiometricPrompt.CryptoObject(cipher)` and returns `result.cryptoObject.cipher`; the store runs `doFinal` with that cipher. `javax.crypto` stays in `securestore`; the activity still never enters it. Correct ADR-123 and `docs/technical-details.md:319,410` in the same PR and record the superseded 5 second design in an ADR consequence; until merged, record the lock-screen-unlock bypass as a known risk.
- **Failing test first:** make `FakeKeystoreBackend` authorization single-use (consume `authenticated` on every `open` and `seal`); add `two_consecutive_protected_reads_prompt_twice`. Add an `androidDeviceTest`: read, then read again within 5 seconds must prompt again.
- **Acceptance criteria:**
  1. `SecretStoreContract` (run on fake and real) has a protected case: two consecutive reads of a protected secret call `authenticate` twice.
  2. A protected key created by the new code has authentication validity 0 (asserted via `KeyInfo.getUserAuthenticationValidityDurationSeconds() == 0` in a device test).
  3. A prior authentication by another app inside 5 seconds does not satisfy a protected read (device test or documented manual step in R-009 evidence).
  4. ADR-123 and `docs/technical-details.md` no longer contain "5 second window" as the mechanism.
  5. `Denied` after a cancelled prompt and `Invalidated` after `KeyPermanentlyInvalidatedException` still map as in the existing read-mapping tests.
  6. Existing records written by the old keys read as `Missing` or re-pair cleanly (decide and test; no crash). Coverage and `konsistTest` unchanged or higher.

### R-002 (P1): A biometric prompt can hang forever with the store mutex held; the lock is held across the user's prompt  [A-2, A-4]

- **Evidence:** `shared/infrastructure/platform/src/androidMain/kotlin/com/stainedhead/paperclipmobile/infrastructure/platform/PromptLauncher.kt:39` (`host.current()` on the IO thread), `:42-43` (post to the main executor), `:66` (`prompt.authenticate(info)` with no re-check). `KeystoreSecretStore.kt:68` and `:73` (`lock.withLock` around `putNow` and `getNow`), and `:111`, `:140` (`authenticate` inside the lock). biometric-1.1.0 logs and returns without calling the callback when "Called after onSaveInstanceState()" or the fragment manager is null (confirmed with `grep -a` in the cached AAR).
- **Failure scenario:** the user backgrounds the app between `current()` and the main-thread post; the continuation is never resumed. `getNow` and `putNow` hold the store lock, so `namespaces()`, `delete`, `deleteNamespace` and the orphan sweep (`startupNoticeOf` uses `runBlocking`) block too; a destructive use case hangs on its spinner. An exception inside the main-thread lambda crashes the app and leaves the coroutine unresumed. Even when it works, a prompt left open blocks every other secret operation.
- **Fix:** inside the main-thread lambda, re-check `host.current() === activity`, `activity.lifecycle.currentState.isAtLeast(STARTED)` and `!activity.supportFragmentManager.isStateSaved`; if any fails resume with `PromptOutcome.Unavailable`; wrap `authenticate` in try/catch and resume `Unavailable` or `Error`. In the store, prompt outside the lock: attempt under the lock, on `NeedsAuthentication` release it, prompt, re-take it and retry once (falls out naturally from the R-001 `Cipher` design). Do R-002 in the same files as R-001 but as its own commit.
- **Failing test first:** a seam in `ActivityPromptLauncher` for "activity not resumed at post time"; the test asserts `Unavailable` returns within `withTimeout`. A store test with a never-completing `authenticate`: `namespaces()` must still complete while the prompt is pending.
- **Acceptance criteria:**
  1. With the activity stopped between `current()` and the post, `prompt()` returns `Unavailable` and does not hang (`withTimeout(1.seconds)`).
  2. A thrown exception inside the prompt lambda resumes the continuation and does not crash the test process.
  3. While a protected read's prompt is pending, `namespaces()`, `delete` and `deleteNamespace` complete.
  4. Cancelling the caller during a pending prompt calls `cancelAuthentication` and releases state (existing `invokeOnCancellation` path now covered by a test).

### R-003 (P1): Typed text is lost on state change and on rotation: approval comment, reject and revision notes, budget amount  [C-1, D-5, C-3]

- **Evidence:** `androidApp/src/main/kotlin/com/stainedhead/paperclipmobile/android/screens/ApprovalSections.kt:107` (`CommentBox` only when `ApprovalActionUi.Comment in state.actions`) and `:127` (`var text by remember { mutableStateOf("") }`); `NoteSheet.kt:88` (`var note by remember`); `BudgetSections.kt:243` (`var text by remember`). Shared `ApprovalRenderer` empties `actions` when `submitting` or offline (`actionable = online && !model.submitting && role != null`). The Issue comment bar already uses `rememberSaveable` (`IssueCommentBar.kt:77`) and the iPhone keeps `commentText` as view state (`ApprovalView.swift:49`, `160-166`).
- **Failure scenario:** the user types a comment and taps Post; `submitting` empties `actions`, the box leaves composition, and if the post fails (`Failed`, `Unknown`, `NotAllowed`) the box returns empty. The same happens when connectivity flickers or an Approve starts while a comment is half written. Rotating the phone (no `configChanges`; the activity is recreated) loses a reject note, a comment or an amount while the shared `rejectPrompt` is retained and the sheet reopens blank. The PRD rule "a failed comment returns text to the box" is proven only for Issue.
- **Fix:** hold each draft above the conditional and use `rememberSaveable`; hide or disable the box instead of removing it from composition; clear only when `commentPostedCount` changes, using the `seen` pattern from `IssueCommentBar.kt:80` so it does not clear on first composition. Apply `rememberSaveable` to `NoteSheet` and the budget amount (see R-024 for the hidden-amount half).
- **Failing test first:** `ApprovalScreenTest`: type a comment, `app.controls.setOnline(false)` then `true`, assert the text is unchanged. Stateless `ApprovalContent` test: flip `isSubmitting` true then false with `Failed` feedback and assert the text is still present. `StateRestorationTester` (or `scenario.recreate()`) tests for the comment, the reject note and the amount.
- **Acceptance criteria:**
  1. A failed comment post leaves the typed text in the box.
  2. Going offline and online, or an Approve/Reject starting, does not clear a half-written comment.
  3. After `recreate()`, the comment, the reject or revision note and the budget amount equal what was typed.
  4. A successful post (count increments) clears the box exactly once, and not on first composition.

### R-004 (P1, DEC-1 run assumption, owner may overturn): `SetAgentBudget` raises money with no device-owner prompt, contradicting AGENTS.md  [D-1]

- **Evidence:** `shared/application/src/commonMain/kotlin/com/stainedhead/paperclipmobile/application/agent/AgentMutations.kt:132-145` (`SetAgentBudget`: role and currency checks, then `agents.setBudget`, no `Biometrics`); `AGENTS.md` Security Rules ("budget raise" is a destructive action); `AgentDetailScreenTest.anOwnerSetsTheMonthlyBudget...` asserts `biometricPromptCount shouldBe 0`; `docs/architectural-decision-record.md` ADR-013 and the `SetAgentBudget` entry (line 128) record "no device authentication".
- **Failure scenario:** an unlocked phone in someone else's hands raises any agent's spend ceiling with one tap (setting it very low also stops the agent). **This is shared code: the fix and the policy change affect the iPhone too.**
- **Fix (DEC-1, run assumption, owner may overturn):** add `Action.RaiseAgentBudget` to the domain and to `DeviceAuthPolicy.destructive` (`shared/domain/.../Permissions.kt:23`); `SetAgentBudget` takes a `DeviceAuthGuard` (as `PauseAgent` at `AgentMutations.kt:36-48` does) and, when `monthly > agent.monthlyBudget`, requires `BiometricResult.Approved` before `agents.setBudget`, returning `MutationResult.NotAuthorized(result)` otherwise. The order stays: permission, currency check, device authentication, one gateway call, one re-read. A decrease needs no prompt, except that a new monthly amount of zero (which stops the agent in practice) is treated as a raise-class change and is gated too; this is a run assumption inside DEC-1. Add the lock glyph (with a content description that says it asks for the device owner) and the effect line to the Set budget button on Android and on the iPhone (`AgentDetailView.swift:206`). Amend ADR-013, the `SetAgentBudget` entry at ADR line 128, and FR-014 to say a raise asks for the device owner, labelled as a run assumption pending the owner; update `docs/product-details.md`. If the owner overturns DEC-1, amend AGENTS.md instead and close R-004 with the ADR text.
- **iPhone impact and gates:** behaviour change on the iPhone (new prompt); update its tests and run the full iOS gate set (see "Shared changes: iPhone impact and iOS gates").
- **Failing test first:** `SetAgentBudgetTest` with a fake `Biometrics` that cancels: a raise returns `NotAuthorized` (the existing device-auth result) and `setBudget` call count is 0; with approval it succeeds with exactly one prompt and one call; a decrease succeeds with zero prompts and one call; a set-to-zero asks for the prompt.
- **Acceptance criteria:**
  1. Raise with a cancelled or unavailable prompt: zero gateway calls, a typed `DomainError`/result, no state change.
  2. `AgentDetailScreenTest` is updated: prompt count is 1 after confirming a raise; a cancelled prompt leaves the budget unchanged.
  3. The lock glyph appears on Set budget for raises on Android (Roborazzi) and iPhone (XCUITest or snapshot), with a content description that says it asks for the device owner.
  4. A decrease (non-zero) makes zero prompts and one gateway call; a raise makes exactly one prompt.
  5. ADR-013/FR-014 and the ADR line-128 entry amended in the same PR, marked as a run assumption; the Konsist rule list unchanged; coverage unchanged or higher; the iPhone test for the prompt passes and `./scripts/ios-test.sh` is green.

### R-005 (P1, verify first): The Run viewer log tail may silently stop following  [D-2]

- **Evidence:** `androidApp/src/main/kotlin/com/stainedhead/paperclipmobile/android/screens/RunViewerLog.kt:134-136` (`LaunchedEffect(state.autoScroll) { if (state.autoScroll) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }`); the holder learns of a drag only after it ends (`:138-147`, `UserScrolled(atBottom)`).
- **Failure scenario:** while a finger is down and the log grows (5 s refresh), `scrollTo` at default priority collides with a user-input scroll; as the reviewer recalls the Compose source, `MutatorMutex` makes the lower-priority call throw `CancellationException`, ending the collecting coroutine silently. A drag that stays at the bottom keeps `autoScroll` true, so the effect key never changes and is not restarted: the tail never follows again until the person toggles auto-scroll. The content is also moved under the finger.
- **Fix:** key the effect on `(state.autoScroll, scroll.maxValue)` and skip while `scroll.isScrollInProgress`, or catch cancellation per emission; do not rely on collector survival.
- **Failing test first (this is the verification):** a Robolectric test that holds a drag with `touchDown`, appends log text, releases, appends again, and asserts `scroll.value == scroll.maxValue`. **If the test passes on the unfixed code, demote R-005 to a P2 note and close it with that evidence; do not change code.**
- **Acceptance criteria:**
  1. After a touch interrupts a live tail and the finger lifts at the bottom, the next appended line is followed (`scroll.value == maxValue`).
  2. While a drag is in progress the content does not jump.
  3. Toggling auto-scroll off stops following and on resumes it.

### R-006 (P1): A pushed detail screen survives a factory or company switch  [C-7, D-3]

- **Evidence:** `androidApp/src/main/kotlin/com/stainedhead/paperclipmobile/android/shell/AppRoute.kt` (`Decision/Approval/Issue/Agent/Run` routes carry ids only); `env/ScreenHolders.kt:84,100,156,172` (`session.selection()` re-read at composition for the holder key and calls); `shell/TabShell.kt:58-76` (one back stack per tab, nothing pops them on selection change); `env/GraphSession.kt` (`followSelection` handles only the Inbox holder). The iPhone has the same route design but calls `releaseScreenModels()` on `appStateChanged` (`AppEnvironment.swift:298-300`); Android has no equivalent.
- **Failure scenario:** open a Decision from the Inbox (factory A), switch to factory B in the Factories tab, return to the Inbox tab. The still-pushed Decision builds a holder for B with A's decision id and dismissal key, so it shows "not found" or a Snooze targets the wrong company.
- **Fix:** on a selection change, pop every tab's controller back to its root (observe the selection in `TabShell`), or carry instance and company in the route payload (the second is safer; do both if cheap).
- **Failing test first:** `TabShellTest`: push `Run(x)` in the Agents tab, change the selection through the app holder, assert the Agents controller is back at its root (or the screen's holder key is unchanged and still addresses factory A).
- **Acceptance criteria:**
  1. After a factory or company change, no tab shows a detail screen from the previous selection.
  2. A mutation started on a detail screen is never sent to a scope other than the one the screen was opened in (fake gateway records the scope).
  3. Rotation and process death still restore the stack when the selection did not change (`RestorationTest` green).

### R-007 (P1): An in-flight inline approve/reject is cancelled when the Inbox holder closes (ADR-117 not applied)  [B-3]

- **Evidence:** `androidApp/src/main/kotlin/com/stainedhead/paperclipmobile/android/env/GraphSession.kt:69,73,84,155` (`mutableInbox`, `followSelection` calls `stopInbox()` which closes the holder; `close()` also closes it); `env/ScreenHolder.kt:52,59-61` (`inFlight` defaults to `{ false }`, used by the Inbox holder); `shared/presentation/src/commonMain/kotlin/com/stainedhead/paperclipmobile/presentation/InboxStateHolder.kt:91-93` (`close()` is `scope.cancel()`). The model tracks `mutating` but it is not exposed as state.
- **Failure scenario:** the person taps Approve, biometrics pass, the POST is in flight; then the selection changes, the demo graph is swapped in, or the graph is retried. The coroutine is cancelled and the outcome and reconciling read are never recorded: the server may have approved while the UI shows nothing. Related leak: the `HolderViewModel.onCleared` idle waiter (`holder.state.first { !holder.isWorking(it) }`) never completes if the scope was cancelled while working, so `idleScope` is never cancelled and the holder and graph stay reachable.
- **Fix:** expose `isWorking` (from `model.mutating`) on `InboxState` (shared presentation, iPhone-visible, add to Swift tests); make `GraphSession.stopInbox` and `close` defer closing a working Inbox holder as ADR-117 does; cancel the idle waiter when the session or graph closes.
- **Failing test first:** a `GraphSession` test with a fake gateway that suspends on approve, a selection change mid-flight, and an assertion that the holder is not closed until the outcome is recorded. A second test: closing the graph while working cancels the idle waiter.
- **Acceptance criteria:**
  1. A selection change during an in-flight approve does not cancel it; the outcome and the reconciling read are recorded; then the holder closes.
  2. `GraphSession.close()` still releases everything once the work finishes, and is idempotent.
  3. No coroutine from the idle waiter survives graph close (leak test, `isActive == false`).

### R-008 (P1): The Inbox fling misses the PRD jank budget (67% janky frames vs 5%)  [D open item]

- **Evidence (ranked likely causes, not profiled; confirm each by measurement before fixing):**
  1. A time ticker per card: `androidApp/.../screens/InboxCard.kt:52` (`rememberNowMillis()`) and `designsystem/UpdatedLabel.kt:76-88` (`produceState` with `while (true)` at `:82`): one coroutine and state per composed card, each recomposing every 15 s. The KDoc at `UpdatedLabel.kt:74` ("no timer per label") is false. Same in `AgentCard.kt:155` and `AgentDetailScreen.kt:132`.
  2. Per-item swipe machinery: `screens/InboxSwipe.kt` (`rememberSwipeToDismissBoxState` and `SwipeToDismissBox` per item, `SwipeBackground` composed and drawn even when settled).
  3. Items cannot skip recomposition: `SwipeableCard(item, state, ...)` receives the whole `InboxState` (`InboxScreen.kt:231`) but reads two fields; types come from a non-Compose module and are unstable; the PRD (line 193) calls for a `stability_config.conf` that is not present (reviewer: no `stabilityConfigurationFiles` setting found).
  4. No `contentType` on `item(key=section-...)` and `items(key=id)` (`InboxScreen.kt:229-231`).
  5. Extra clip: `designsystem/KeyedComponents.kt` card stacks `.clip(shape).background().border()` (reviewer-cited 225-232; the stack is there, `background(color, shape)` already draws the shape).
  6. Per-item allocations in `CardButtons` (`buildList`, `CustomAccessibilityAction` lists).
  Also: the benchmark flings immediately after launch; wait for the content tag first.
- **Fix, in order of cost:** one `LocalNow` state at the screen root; pass two Booleans instead of `InboxState`; add `stability_config.conf` listing the shared presentation package; compose the swipe background only when `dismissDirection != Settled`; add `contentType`; drop the redundant clip; hoist allocations. Re-measure after each of the first three.
- **Failing test first:** a Compose test with a counting recomposition probe asserting that a clock tick recomposes no `InboxCard`; then the Macrobenchmark as the acceptance measurement.
- **Acceptance criteria:**
  1. A clock tick recomposes no `InboxCard` (test).
  2. `FrameTimingMetric` over the scripted 200-card fling shows at most 5% janky frames on the CI emulator image (`google_apis` API 34 x86_64) via `./scripts/run-benchmarks.sh`, and on a Pixel 6 when the owner runs it with `PAFM_BENCHMARK_ALLOW_EMULATOR=0` (R-009); numbers recorded in `status.md`. If the emulator passes but the Pixel fails, the finding stays open.
  3. The false KDoc at `UpdatedLabel.kt:74` is fixed or made true.

### R-009 (P1): Acceptance evidence that has never been produced (needs the owner's hardware and CI)  [A-5 evidence half, B, D, docs "Not proven"]

- **Evidence:** `docs/technical-details.md:13` says "Not proven: a pairing against the pinned server, the system prompt with an enrolled fingerprint, new-fingerprint key invalidation, StrongBox, TalkBack, a Pixel 6 or Samsung run, and the frame budget". `scripts/android-live-pairing.sh` header: "NOT RUN in the session that wrote it ... Treat the first run as the check of the script itself." `android.yml` has never run on a hosted runner.
- **Why P1:** each item is an acceptance criterion in the Android PRD (criteria 4 to 6 and the device matrix); shipping without them leaves the phase unproven. It is not P0 because nothing here is a known defect, only an unproven claim. A teammate cannot produce this evidence: it needs a device, Docker, an enrolled fingerprint or hosted CI.
- **Required evidence (record each result, with date, device and command output summary, in `specs/archive/261104-phase-2a-android-ui/status.md`; no secrets, no real addresses, per AGENTS.md):**
  1. **Live pairing against the pinned server.** `ANDROID_SERIAL=<serial> ./scripts/android-live-pairing.sh` (needs Docker, `adb`, `python3`, an unlocked device). The first run also validates the script. Pass criteria: the factory shows Signed in; the script's final check says the key is in the Keystore-wrapped record only; no key in logs, SQLDelight or backups. Then `./scripts/android-live-smoke.sh`.
  2. **Real key invalidation after fingerprint enrolment.** On a device or emulator with a protected secret stored (R-001 applied), add a fingerprint (Settings, or `adb -e emu finger touch <id>` after enrolling on an emulator), open the app, read the protected secret: expect `Invalidated` and the re-pair path, not a crash.
  3. **System BiometricPrompt on a destructive action.** On a device with an enrolled fingerprint: Pause agent, Cancel run, Reject approval, a budget raise (R-004): the system prompt appears each time; cancelling the prompt performs nothing (check the server state).
  4. **TalkBack.** Enable with `adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`; walk Inbox, Decision, Approval, Budget, Add/Edit factory, Pairing, Lock. Specifically check that text fields (those using `FormField`, R-025) announce what was typed, that banners and the Undo are announced (R-026), and every destructive control announces the lock.
  5. **StrongBox** on a device that has it: confirm the key reports `KeyInfo.isInStrongBox` or record that the TEE fallback was used.
  6. **API 28 and API 33** (emulator images): launch, add a factory, protected secret refused cleanly on 28 (documented fallback), predictive back (R-039), notification/permission behaviour. `./gradlew :androidApp:connectedDebugAndroidTest` on each (the connected task name is confirmed first with `./gradlew :androidApp:tasks --all`; record the name in `status.md`).
  7. **Pixel and Samsung pass.** Run the connected tests and the demo-flow tests on a Pixel 6 and one Samsung device; the Pixel 6 is the benchmark target: `PAFM_BENCHMARK_ALLOW_EMULATOR=0 ./scripts/run-benchmarks.sh` for cold start (under 1.5 s median of 10) and the frame metric (R-008).
  8. **`android.yml` first hosted run.** Open the PR, let the workflow run on GitHub, confirm every job (verify, emulator, benchmark gating, release smoke) is green or fix and record. `gh run list --workflow android.yml` and `gh run view <id> --log-failed` for the evidence. The first run is also the check of the workflow file itself.
- **Acceptance criteria:** each of items 1 to 8 has a dated line in `status.md` with pass, or a linked issue with a named blocker; items that fail create a new finding and block archive. `docs/technical-details.md:13` "Not proven" list is edited to match the recorded evidence in the same PR.

## P2

### R-010 (P2): `AndroidKeystoreBackend.open()` does not wrap `getKey`  [A-3]

- **Evidence:** `shared/infrastructure/securestore/src/androidMain/kotlin/com/stainedhead/paperclipmobile/infrastructure/securestore/AndroidKeystoreBackend.kt:67` (`keyStore().getKey(...)` outside the `try` that starts at `:68`); the write path at `:117` is in a guarded section. `KeyStore.getKey` can throw `KeyStoreException`, `UnrecoverableKeyException`, `NoSuchAlgorithmException`; some OEMs throw for an invalidated or damaged key.
- **Failure scenario:** `SecretStore.get` throws instead of returning a result, so the caller crashes or hits an unmapped path, breaking the read-mapping contract in ADR-123.
- **Fix:** move `getKey` into the `try`; map `UnrecoverableKeyException` to `Invalidated`, other `GeneralSecurityException` to `Unreadable`.
- **Failing test first:** a `throwOnGetKey` option on the backend seam; `get` returns `Denied` or `Invalidated`, not an exception.
- **Acceptance criteria:** `get` never throws for any of the three exceptions (three tests); the mapping table in ADR-123 includes the new rows.

### R-011 (P2): The protected path and the real prompt path have no automated proof  [A-5]

- **Evidence:** `shared/testing/.../contract/SecretStoreContract.kt:17` uses only `requireUserPresence = false`; `ActivityPromptLauncher.prompt` has no test beyond the null-host case (`BiometricPromptBiometricsTest.kt:115-123`); device tests cover seal and `NeedsAuthentication` only; `implementation-notes.md:73` admits the T1.3/T1.4 device tests were written after the code.
- **Failure scenario:** a regression in the protected branch or in success/error/cancel mapping of the prompt ships green.
- **Fix:** add protected, `Denied` and `Invalidated` cases to `SecretStoreContract` and run them against fake and real; add a Robolectric or fake-`FragmentActivity` test for `ActivityPromptLauncher` covering success, each error code, cancellation and `invokeOnCancellation` to `cancelAuthentication`.
- **Failing test first:** the new contract cases themselves; run on the fake first (expected to fail until the fake's single-use behaviour from R-001 lands).
- **Acceptance criteria:** the contract has at least four protected cases and passes on both implementations; `ActivityPromptLauncher` has tests for all outcomes; Konsist unchanged.

### R-012 (P2): Connectivity counts "online" only when the network is VALIDATED  [A-6, DEC-2 run assumption, owner may overturn]

- **Evidence:** `shared/infrastructure/platform/.../ConnectivityManagerConnectivity.kt:49` requires `NET_CAPABILITY_VALIDATED` (with INTERNET at `:46-48`).
- **Failure scenario:** a LAN-only Wi-Fi, split-DNS or captive network is not validated, so the app reports offline and blocks mutations although the home factory is reachable; this is INTENT principle 7's primary scenario. The reviewer did not verify validation behaviour on a specific VPN.
- **Fix (DEC-2):** remove the `NET_CAPABILITY_VALIDATED` requirement from `isUsable()` (`ConnectivityManagerConnectivity.kt:44-49`); a default network with `NET_CAPABILITY_INTERNET` is online, validated or not, so requests are attempted and the real failure surfaces. No default network, or no INTERNET capability, stays offline. Update the class KDoc (it says a network counts only when validated) and the "fail closed" wording; keep the fail-closed behaviour only for a callback the system refuses to register. No shared API change, no iOS impact.
- **Failing test first:** an INTERNET-but-not-VALIDATED network capability fake reads as online (fails today).
- **Acceptance criteria:** tests for validated (online), INTERNET-but-unvalidated (online), no-network (offline), a network without INTERNET (offline) and callback-removed; `docs/product-details.md` states the rule; the Tailscale-only case is part of R-009 item 1 (run pairing over Tailscale when available).

### R-013 (P2): `RecordFiles` robustness  [A-7, reviewer P3]

- **Evidence:** `shared/infrastructure/securestore/.../RecordFiles.kt:69` (`URLDecoder.decode` on every `*.rec` file name); no directory fsync after the atomic rename (`:50`); old-mode key deleted at `KeystoreSecretStore.kt:117`.
- **Failure scenario:** a stray file with a bad percent sequence throws `IllegalArgumentException` from `namespaces()` and `deleteNamespace`; power loss after the rename but before the directory entry is durable could revert to the old record whose key is gone, which reads as `Missing` (a sign-out). Rare.
- **Fix:** skip undecodable names (and sweep or ignore them); fsync the directory after rename where the platform allows.
- **Failing test first:** a record directory containing `bad%zz.rec`: `namespaces()` returns the valid names and does not throw.
- **Acceptance criteria:** the test passes; no change to the on-disk format; existing `RecordFiles` tests green.

### R-014 (P2): The Konsist merged-manifest rules can pass stale locally on a dependency bump  [B-1]

- **Evidence:** `shared/umbrella/build.gradle.kts:56-66` only `dependsOn(":androidApp:process<Variant>Manifest")` and passes the merged-manifest path as a `systemProperty`; declared inputs are only `shared/**/src/**/*.kt` and `androidApp/src/**/*.kt|xml`. Not inputs: the merged manifests, `androidApp/build.gradle.kts`, `proguard-rules.pro`, `gradle/libs.versions.toml`. `AndroidReleaseBuildRulesTest` reads the first two directly.
- **Failure scenario:** an androidx bump adds a permission or exported receiver; a local `konsistTest` is UP-TO-DATE and green; CI (clean) would catch it, so this is a local false green.
- **Fix:** register the five merged manifests (via a provider from the process task outputs, not a hard-coded `intermediates/` path), the two Android build files and the version catalog as `inputs.files`.
- **Failing test first:** a Gradle TestKit or CI check that changes only the catalog and asserts `konsistTest` re-executes.
- **Acceptance criteria:** touching only `gradle/libs.versions.toml` makes `konsistTest` re-run; no other task's cache behaviour regresses.

### R-015 (P2): A release can silently ship debug-signed  [B-2]

- **Evidence:** `androidApp/build.gradle.kts:134` (`signingConfig = if (upload.storeFile != null) upload else signingConfigs.getByName("debug")`); no warning, and a half-set environment (store file without passwords) fails late with an opaque error; `AndroidReleaseBuildRulesTest` checks only the env var name and absence of literal secrets. ADR-132 documents the fallback; CI has no publish step.
- **Failure scenario:** a future release workflow forgets the secrets and `assembleRelease` produces a debug-signed APK that looks normal.
- **Fix:** fall back to debug only with an explicit opt-in (`PAFM_ALLOW_DEBUG_SIGNED_RELEASE=1`) or when `CI` is unset; otherwise throw `GradleException`; add a clear message when a partial upload configuration is detected.
- **Failing test first:** a Konsist text rule asserting the fallback branch sits behind the guard (with a planted violation).
- **Acceptance criteria:** with `CI=true` and no upload key, `assembleRelease` fails with the message unless the opt-in is set; `android.yml`'s smoke build sets the opt-in explicitly; the rule has a planted-violation test.

### R-016 (P2): CI concurrency group lets the nightly benchmark and `push` runs cancel each other  [B-6]

- **Evidence:** `.github/workflows/android.yml:15` `group: android-${{ github.ref }}` with `cancel-in-progress: true`; scheduled and dispatch runs use `refs/heads/main`.
- **Failure scenario:** a push to main during the 03:17 benchmark cancels it, and the scheduled run can cancel an in-flight main verify.
- **Fix:** `group: android-${{ github.event_name == 'schedule' && 'nightly' || github.ref }}`.
- **Failing test first:** extend `CiPinningRulesTest` (or a sibling) with a rule that the group expression contains `github.event_name`.
- **Acceptance criteria:** the rule passes; two simultaneous events (push to main, schedule) show separate concurrency groups in the workflow run view.

### R-017 (P2): Konsist guards are weaker than they read  [B-7]

- **Evidence:** `shared/umbrella/src/konsistTest/kotlin/com/stainedhead/paperclipmobile/architecture/AndroidAppScanning.kt` `secretsInSavedState` is a same-line regex (a multi-line `rememberSaveable { ... token ... }` or `handle.set("token", ...)` slips through; the planted test shows `handle.set("token", token)` is not flagged); the "only `releaseDemo` turns the demo entry on" test does not check `benchmark` and `nonMinified`, which inherit `DEMO_ENTRY = true` via `initWith`; no signing-fallback check (R-015).
- **Failure scenario:** a future change stores a token in saved state across lines and passes the rule.
- **Fix:** scan the full call expression (multi-line) for `rememberSaveable`, `SavedStateHandle.set`, `Bundle.put*`; make the demo-entry test name match what it checks or extend it to the other two types.
- **Failing test first:** planted multi-line and `handle.set("token", token)` violations that the current rule misses.
- **Acceptance criteria:** both planted violations are flagged; the demo-entry test either covers or explicitly excludes `benchmark`/`nonMinified` by name; no existing production code newly flagged.

### R-018 (P2): Graph opening runs on the main thread in `onCreate`  [B-8]

- **Evidence:** `AppEnvironment` runs `open()` in its property initializer (`MutableStateFlow(open())`); `MainActivity.onCreate` calls `app.environment(intent)`; this opens the Keystore, the SQLDelight DB and creates the holders.
- **Failure scenario:** a slow Keystore or a locked database on cold start approaches ANR.
- **Fix:** open on `Dispatchers.Default` and expose Loading from the session; keep failures visible ("cannot start yet", never fake graph).
- **Failing test first:** a test with a graph provider that suspends: `environment()` returns immediately with a Loading destination and `StrictMode` (or a main-thread check) is not tripped.
- **Acceptance criteria:** `open()` is never called on the main thread (assertion in the test provider); cold-start benchmark median is not worse than before (R-009 item 7); Loading shows then resolves to the same destinations as before.

### R-019 (P2): Business rules live in androidApp (principle 5, "build once")  [B-5]

- **Evidence:** `androidApp/.../screens/OperationsPresentation.kt:57` (`RunStatusUi.isLive()`: Running, Queued, ScheduledRetry) and `:81` (`visibleActions()` drops Run now for a live run; `AgentCard.kt` adds View run and orders the groups, Pause last and locked); `FactoryPresentation.kt` (`FactoryStatusPresentation.of`: expired key outranks the status, `offersPairing = status != SignedIn || expired`, expiry tone); tone maps for run, agent, issue status and priority, approval status, effect status (`DecisionSections.kt:170`, reviewer-cited) and the budget threshold; `DecideByUi.hintText()` building `"today"`, `"this_week"` strings for the shared decision parser (a wire format built in the UI); `InboxCardLink.of` and `cardContext`; `AgentFilter.includes` (acceptable view-only filter). `AgentInitials.of`, `RunTime.elapsed` and `RunTime.offset` are formatting, arguably fine.
- **Failure scenario:** the iPhone silently differs (which buttons a row offers, which statuses are live, what colour a threshold is); the comment in `AgentsScreen` already says these are "the holder's".
- **Fix:** move `isLive`, `visibleActions`, the factory status decisions, the tone/severity enums and `DecideByUi.hintText` into `shared/presentation` with `commonTest`; Android and iOS render the result. Keep platform-only mappings (route destinations, colours) in the apps.
- **Failing test first:** a `commonTest` "a live run replaces Run now with View run"; one per moved rule.
- **Acceptance criteria:** each moved rule has a `commonTest`; androidApp and iosApp contain no copy of the rule (grep in the PR); a Konsist rule forbids `isLive`-style classification in `androidApp` (planted violation); presentation coverage not reduced; iOS uses the shared result (Swift tests updated).

### R-020 (P2): A mutable-state rule is checked by tests that cannot fail  [D-8]

- **Evidence:** `androidApp/src/test/kotlin/com/stainedhead/paperclipmobile/android/screens/RunRefreshTest.kt:138` (`(clock.testScheduler.currentTime - tapped) shouldBeLessThan LIVE_SECONDS * MILLIS_PER_SECOND`): no virtual time advances between `tapped` and the check, so the delta is always 0. The test name says "within one refresh cycle"; `docs/product-details.md:85` and `status.md` map main PRD criterion 8 to it; the notes admit "Cancelled before any virtual time passes".
- **Failure scenario:** a regression that makes the viewer ignore an external cancel for minutes passes.
- **Fix:** a fake status change in `FakeRunGateway` and an assertion that the state flips within 5 virtual seconds; otherwise rename the test and drop the criterion 8 claim.
- **Failing test first:** mutate production (stop the 5 s refresh) and confirm the new test fails.
- **Acceptance criteria:** the corrected test fails under that mutation; `docs/product-details.md:85` and the status mapping wording match what the test proves.

### R-021 (P2, verify first): Locking probably disposes the tab shell without clearing back-stack entry ViewModels  [B-4]

- **Evidence:** `shell/RootScreen.kt` `Destination` swaps `Tabs` for `Lock`; `shell/TabShell.kt:58-60` (`rememberNavController()` instances leave composition); `HolderViewModel.onCleared` is the only place pushed-screen holders are closed; no test covers lock with a pushed route (`RestorationTest`, `HolderViewModelTest`, `HolderViewModelRotationTest` cover rotation).
- **Failure scenario:** the app locks with a Decision or Approval open; those holders and their payloads stay in memory until the Activity is destroyed; each lock cycle adds more. The reviewer did not read androidx navigation internals.
- **Fix if confirmed:** pop each tab controller to root on lock, or scope holder ViewModels to a store owned by `GraphSession` that is cleared on lock.
- **Failing test first (also the verification):** a Robolectric test that pushes a Decision, flips app state to locked, and asserts `holder.isClosed`. If it already passes, close with that evidence.
- **Acceptance criteria:** after a lock, no pushed-screen holder is open (`isClosed == true`, test); unlock lands on the tab root; repeated lock cycles do not grow the count of live holders.

### R-022 (P2): Inbox "Open in web" door bypasses `WebOpener`, can crash with no browser, and can be an inert button  [B-9, D-4, C-8]

- **Evidence:** `androidApp/.../screens/InboxScreen.kt:108` calls `uriHandler.openUri(destination.url)` for a server-provided `webUrl` that only passes `startsWith("https://")` (`InboxPresentation.kt:17,82`); the Issue screen uses `WebOpener` (`WebOpener.kt:24`: `acceptablePage` host parsing, Custom Tab, browser, failure reporting). `InboxCard.kt:144-150` always draws the "Open in web" button for `InboxCardLink.OpenInWeb`, while `inboxDestination` returns `InboxDestination.None` for a missing or non-https URL (tap does nothing).
- **Failure scenario:** `AndroidUriHandler.openUri` throws when no activity can handle the link (a device with no browser): a crash with no message, and a second, weaker https door. A web-only item with a null or `http://` URL shows a button that does nothing.
- **Fix:** route the Inbox web door through `currentWebOpener()` and show an `issue_open_web_failed`-style message on failure; hide or disable the button when the destination is `None`.
- **Failing test first:** an Inbox test with `LocalWebOpener` set to a `RecordingLauncher`: a `WebOnly` card opens through it; a refusing launcher shows the error; a card with `webUrl = null` and one with `http://...` show no enabled web button.
- **Acceptance criteria:** the three tests pass; `WebOpenerTest` (https door) unchanged; no `LocalUriHandler.openUri` call remains in `androidApp/src/main` (Konsist rule with planted violation).

### R-023 (P2): `FormField` is single-line and strips newlines (iPhone parity)  [C-4]

- **Evidence:** `androidApp/.../designsystem/Components.kt:382` (`onValueChange(it.replace("\n", ""))`), `:392` (`singleLine = true`); iPhone uses `TextField(axis: .vertical).lineLimit(1...4)` (`DesignSystem/Components.swift:50-51`). `FormField` is used for the reject note, revision note, comment and decision text.
- **Failure scenario:** a pasted multi-line note is flattened; a long rejection reason scrolls horizontally so the user cannot review what will be sent.
- **Fix:** add `singleLine`/`maxLines` parameters; 1 to 4 lines for notes, comments and decision text; single-line for address, name and amount.
- **Failing test first:** type "a\nb" into `note-field` and assert the intent carries "a\nb".
- **Acceptance criteria:** the multi-line cases keep their newlines and show up to four lines; address, name and amount still reject newlines; Roborazzi baselines for affected sheets re-recorded and reviewed.

### R-024 (P2, decided: stays P2; it concerns money): The raise amount can be hidden from the field but still sent  [C-2]

- **Evidence:** `androidApp/.../screens/BudgetSections.kt:90` (`AmountField` only when `choice == RaiseAndResume`) and `:243` (`var text by remember`); the holder keeps the amount (`choose()` copies only `choice`; `AmountTyped` stores `amount`). iPhone keeps `amountText` at sheet level (`BudgetIncidentSheet.swift:35,191-197`). Decided P2 (see "Decisions" note below) because the budget Submit goes through the device-owner prompt (a tap and a prompt are both needed) and the amount is visible in the button label ("Raise to $250"); the reviewer showed the amount can be sent while the field is blank.
- **Failure scenario:** the user types 250, switches to "Keep paused", back to "Raise and resume": the field is blank, `amountMinorUnits` is still 25000, `canSubmit` is true, the button says "Raise to $250" and Submit sends it.
- **Fix:** hoist the text into `BudgetBody` as `rememberSaveable`, or send `AmountTyped("")` when leaving Raise (prefer the shared fix: the holder clears the amount on `choose` away from Raise, with a `commonTest`).
- **Failing test first:** `BudgetSheetTest`: type an amount, choose Keep paused, then Raise again; assert the field text equals the state's amount, or that Submit is disabled and not labelled with an amount. Add the shared `commonTest` for the holder.
- **Acceptance criteria:** the field text and the state's amount are always equal after any choice sequence; no hidden amount can be submitted; iPhone behaviour unchanged (parity test).

### R-025 (P2): `FormField` TalkBack semantics hide typed text and the validation issue  [C-5]

- **Evidence:** `Components.kt:389` (`.semantics { contentDescription = title }` on the `OutlinedTextField`); the `issue` and `help` texts are separate `Text` nodes (`:416-420`) with no `error()` semantics; `AccessibilityAudit.kt` `name()` counts any `contentDescription` as a pass, so the tests cannot catch this. The reviewer did not run TalkBack; this is a known Compose pitfall.
- **Failure scenario:** a `contentDescription` on an edit box can replace the entered text when TalkBack reads the field; a blind user may not hear what they typed; a validation issue is not tied to the field.
- **Fix:** use a Material3 `label`, drop the `contentDescription`, pass the issue as `supportingText` and add `error(issue)` semantics.
- **Failing test first:** assert the field node exposes `EditableText` and that the issue is part of its merged semantics.
- **Acceptance criteria:** the test passes; `AccessibilityAudit` distinguishes a text field with only a `contentDescription` (planted-violation test); verified with TalkBack in R-009 item 4.

### R-026 (P2): Nothing that appears asynchronously is announced to TalkBack  [C-6]

- **Evidence:** a grep for `liveRegion` in `androidApp/src/main` finds nothing. Affected: `FeedbackBanner` (`DetailSupport.kt:19`), `UndoSnackbar` (`InboxSheets.kt`, shown from `InboxScreen.kt:151`), `ErrorBanner`, the "Copied" text in `DiagnosticsScreen.kt`, and the `NoteSheet` device-auth issue (`note-issue`).
- **Failure scenario:** a declined biometric, a failed approve, or the 5 second Undo is never spoken; a TalkBack user cannot reach Undo in time and gets no feedback that the card disappeared.
- **Fix:** `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` on these; announce the Undo message.
- **Failing test first:** assert `SemanticsProperties.LiveRegion` on `feedback-banner`, `inbox-undo` and `note-issue`.
- **Acceptance criteria:** the assertions pass for all five elements; verified in R-009 item 4.

### R-027 (P2): System Back on the first-run Pair step does not cancel pairing  [C-10]

- **Evidence:** the only `BackHandler` in screens is `EditFactoryScreen.kt:73` (`enabled = state.hasUnsavedChanges`); `FirstRunScreen.kt` has none, though its Back button sends `FirstRunIntent.BackRequested` (cancels pairing, returns to Address).
- **Failure scenario:** Back on the Pair or Secure step leaves the task with pairing still waiting.
- **Fix:** `BackHandler(enabled = state.step == Pair)` (and Secure) sending `BackRequested`.
- **Failing test first:** a FirstRun test that presses system back on the Pair step and asserts `step == Address` and `pairing == Idle`.
- **Acceptance criteria:** the test passes for Pair and Secure; Back on Address still leaves the app.

### R-028 (P2): Manage factories reorder can show an order the store never accepted  [C-9]

- **Evidence:** `designsystem/ReorderableColumn.kt:121` (`remember(items) { ReorderController(...) }` keeps the controller while incoming rows are structurally equal); `shared/presentation/.../ManageFactoriesStateHolder.kt:132-136` sets only `reorderRejected = true` on `ReorderResult.Mismatch` without re-emitting rows.
- **Failure scenario:** the controller's local order stays reordered while the stored order is unchanged, with the "reorder rejected" note beside a list that looks reordered.
- **Fix:** key the controller on `reorderRejected` as well, or reset the local order when the flag rises.
- **Failing test first:** a stateless ManageFactories test: move a row, then set `reorderRejected = true` with the same rows, assert the original order.
- **Acceptance criteria:** the test passes; a successful reorder still keeps the new order.

### R-029 (P2): Locale glue in strings built in code  [C-11]

- **Evidence:** `BudgetSections.kt` (`"$title. $help"` as `contentDescription`), `DecisionSections.kt` (`"$selected: ${it.label}"`, the `"${confirmText(state)}. ..."` spoken string), the " · " joins in `InboxPresentation`.
- **Failure scenario:** punctuation and order are hard-wired English in a localised app (passes the Konsist "no literal Text" rule).
- **Fix:** format strings in resources.
- **Failing test first:** a pseudo-locale (`en-XA`) test asserting these strings come from resources.
- **Acceptance criteria:** no concatenated user-facing sentence remains in these three files; pseudo-locale test passes.

### R-030 (P2): Test gaps against the Android PRD acceptance criteria  [C test gaps, D-9]

- **Evidence:**
  - Criterion 8 (accessibility, localisation): `AccessibilityTest` audits only inbox, decision, approval, factoryHome, agents, agentDetail, runViewer, work, issue, factories, editFactory, settings and lock; not audited: Budget sheet, NoteSheet, Snooze sheet, Confirm sheets, First run, Manage factories, Paired devices, Diagnostics. Only `values` and `values-night` exist; no pseudo-locale or RTL test.
  - `AccessibilityAudit.kt:24` filters controls with `isPlaced()` (`:45` requires width and height above 0), so a zero-size control is skipped; the 2a-7 notes record a real zero-width "New issue" button bug that this audit would miss. Each screen is audited once at its ready-tag, in a 2000 dp window at default font scale; only the Thread tab of Issue is audited; no contrast or state-description check; `lock` uses `audited({ true })`.
  - Undo window: no test for the 5 second lapse or a second dismiss replacing the first (a presentation-level test, plus one UI test that the snackbar disappears when `undo` clears).
  - Swipe gestures: no real swipe test, only TalkBack custom actions; `confirmValueChange` mapping and `enabled` gating (`InboxSwipe.kt`) unproven; no Viewer test for the swipe gating.
  - Typed-text retention (R-003, R-024): none. Criteria 5 (secrets) and 6 (live graph) have no coverage in the screen area.
- **Failure scenario:** a regression in any of these passes CI; the audit gives a false green.
- **Fix:** report visible clickable nodes with zero width or height as findings; extend the audit to the listed sheets and screens, a large font scale and an error state; add the Undo lapse test in presentation; add a real swipe test per direction and a Viewer swipe test; add an RTL/pseudo-locale smoke test.
- **Failing test first:** the existing planted-violation pattern for the zero-size control.
- **Acceptance criteria:** the audit flags a planted zero-width button; every listed screen/sheet is audited; the Undo lapse and a real swipe each have a passing test; `status.md` criterion mapping updated (R-040).

### R-031 (P2): Roborazzi uses the default pixel-change threshold  [D-10]

- **Evidence:** `androidApp/build.gradle.kts` sets no `roborazzi { compare }` options and nothing in `src/test` sets `changeThreshold`. Two images viewed (`run-viewer-light.png`, `issue-light.png`) look right; the Issue status menu uses a clock glyph (`ic_snooze`, `IssueCommentBar.kt:152`) described as "Change status", which is misleading next to Snooze.
- **Failure scenario:** a changed label, a lost icon or a recoloured pill on a 411x2000 px baseline passes `verifyRoborazziDebug`.
- **Fix:** set `changeThreshold = 0f` for fixed demo data; use a status icon for the Issue status menu.
- **Failing test first:** change one label colour in a throwaway commit and confirm `verifyRoborazziDebug` fails.
- **Acceptance criteria:** the throwaway change fails the verify; baselines re-recorded once and reviewed; the Issue status icon no longer reuses the snooze glyph.

### R-032 (P2): The Agents and Work lists are not lazy  [D-12]

- **Evidence:** `AgentsScreen.kt` (about `:105`) uses `Column(verticalScroll)` over sections; `WorkListScreen.kt` does `for (row in state.rows) WorkRow(...)` with Load more; every row is composed at once. Inbox is a `LazyColumn`.
- **Failure scenario:** a company with hundreds of agents or issues janks and uses memory.
- **Fix:** `LazyColumn` with keys and `contentType`, like Inbox.
- **Failing test first:** a Compose test with 500 rows asserting that off-screen rows are not composed.
- **Acceptance criteria:** the test passes; scroll position, section headers and Load more behave as before (Roborazzi unchanged); a benchmark entry for Agents is optional.

### R-033 (P2): Typing after Send is wiped and the failure banner can be off screen (Issue comment)  [D-6]

- **Evidence:** `IssueCommentBar.kt:81-88` clears `comment` when `commentSentCount` changes, deleting anything typed between tapping Send and the response; Send is enabled while the field is blank (line 108 passes only `state.canComment`); a refusal shows in `FeedbackBanner` at the top of the scroll content (`IssueScreen.kt:130`) while the bar is fixed at the bottom. The busy guard (`IssueSession.change`, `claim`) prevents double posts.
- **Failure scenario:** text typed during the send is lost; a failure is invisible when the thread is scrolled.
- **Fix:** send a snapshot and clear only if `comment == sentText` at the increment; show feedback inside the bar or near the input; disable Send for blank text.
- **Failing test first:** a Compose test that appends text after the tap, bumps the count, expects only the sent text removed.
- **Acceptance criteria:** the test passes; a blank comment cannot be sent; a failure is visible without scrolling (test asserts the feedback node is inside the bar).

### R-034 (P2): Interaction drafts can disagree with the holder after process death  [D-7]

- **Evidence:** `InteractionControls.kt:69` (`rememberSaveable(tag) { mutableStateOf(initial) }`): after process death the text is restored while the new holder has an empty draft; no change intent is re-sent; choices are lost, only text is restored.
- **Failure scenario:** the field shows an answer the shared card does not have; Send looks ready or is disabled for no visible reason.
- **Fix:** do not save the draft locally (hold it only in the holder) or re-emit the restored text once on restore.
- **Failing test first:** a restoration test with a typed answer; assert the holder's draft equals the visible field.
- **Acceptance criteria:** after restore, the visible field text and the holder draft are equal; Send enablement matches the shared `canAnswer`.

### R-035 (P2): TDD evidence for security code is weaker than the notes imply  [D-11]

- **Evidence:** `588c75c` (`feat`) landed the real `AndroidKeystoreBackend` (186 lines), `KeystoreSecretStore` and `RecordFiles` together with host tests over the fake backend; the device tests that use the real Keystore arrived later in `af2f906` (`test:`) and `0288029`; T1.3 `BiometricPromptBiometrics` and T3.6 `AuthHost` were each one `feat` commit with tests; the 2a-5 notes admit Decision, Approval and Budget screens "were written before their tests". Red-first cannot be proven from history.
- **Failure scenario:** no code failure; the process claim in the notes overstates.
- **Fix (process):** record this as a known deviation in `implementation-notes.md`; from here, commit the failing test first for security code (this PRD's R-001, R-002, R-004 follow that).
- **Failing test first:** n/a (process); the evidence is the git history of the fixes.
- **Acceptance criteria:** the R-001, R-002 and R-004 histories each show a `test:` commit that fails before the `fix:` commit; the deviation is recorded.

### R-036 (P2, a11y, reviewer P3): Lock glyph has no description and row buttons have no disabled state  [D-13]

- **Evidence:** `AgentCard.kt:118` (the lock `Icon(... null ...)`): TalkBack does not say the Pause row button asks for the device owner; the Agent detail `AppButton` icon should be checked too; `RowButton` has no disabled state while an action is busy, and taps are silently ignored by the holder.
- **Failure scenario:** a TalkBack user is not told that Pause asks for the device owner; a tap during an in-flight action appears to do nothing.
- **Fix:** give the glyph a content description (or merge it into the button's description); disable the row buttons while busy.
- **Failing test first:** assert the Pause button's merged semantics mention the device-owner prompt; assert disabled while mutating.
- **Acceptance criteria:** both assertions pass for Pause and Cancel on rows and on the detail screen.

### R-037 (P2, reviewer P3): Work search keeps unsent text; blank-title buttons enabled  [D-14]

- **Evidence:** `WorkListParts.kt:80-87` (search text is local state while a filter chip applies the old search); Create issue and Save are enabled while the title is blank (the holder refuses).
- **Failure scenario:** the visible search text differs from the applied search; a tap does nothing.
- **Fix:** apply or clear the pending text when a chip is chosen; disable Create and Save for a blank title.
- **Failing test first:** a Work test: type a search, tap a chip, assert the field and the applied search agree; assert Create is disabled for blank.
- **Acceptance criteria:** both pass.

### R-038 (P2, design only in this phase): Attachments are not openable on either platform  [D attachments]

Scope for this PRD: write the ADR (decision on the port and the cache rules) and file the two follow-up PRs. The fix and criteria below describe those follow-up PRs, not work for this phase; this phase is complete for R-038 when the ADR exists, the follow-up issues are linked in `status.md`, and the "cannot open" limitation is stated in `docs/product-details.md` and `user-docs/`.

- **Evidence:** `androidApp/.../screens/IssueTabs.kt` `FilesTab` lists name, type and size plus a note only; ADR-064 makes attachment content an adapter-only ranged call; there is no port for attachment bytes and the endpoint needs the board key, so a URL cannot be handed to a browser. The iPhone has the same limitation.
- **Failure scenario:** the person sees a file they cannot open on the phone.
- **Fix:** add a domain `AttachmentGateway.open(id)` port (downloads with auth to the app cache, bytes plus content type), an application use case, a fake in `shared/testing` and the real adapter; Android shares the cached file through a `FileProvider` `ACTION_VIEW`, iOS uses QuickLook. Cache files are cleared on sign-out and lock.
- **Failing test first:** a gateway contract test (fake and real): the bytes return with the right content type and no token appears in the cache path or file name.
- **Acceptance criteria:** the contract passes on fake and real; a use case test covers denied, not found and too large; the cache is removed on sign-out; no secret in the cache path (test); the feature lands in two separate PRs, shared first, then each platform (AGENTS.md).

### R-039 (P2): Documentation claims that are false or stale  [D docs]

This requirement is the **documentation inaccuracy list**. Each false or stale claim:

1. `docs/product-details.md:101` and `:118` claim predictive back; `androidApp/src/main/AndroidManifest.xml` has no `android:enableOnBackInvokedCallback` and no test proves it (it works only by platform default at targetSdk 36 or higher). Add the attribute and a test, or reword.
2. The Android PRD AND-012 (`paperclip-agent-factory-android-PRD.md:143`) says the `paperclipmobile://` scheme "is reserved in the manifest"; the manifest's only `intent-filter` (line 31) is the launcher. Reword.
3. `docs/product-details.md:85` says `RunRefreshTest` covers "criterion 8 in virtual time"; false (R-020).
4. `RefreshWhileVisible` KDoc (`DetailSupport.kt:134`) says "15 seconds while the item can change"; the Run viewer is 5 seconds while live.
5. `UpdatedLabel.kt:74` KDoc says "no timer per label" (R-008).
6. `docs/technical-details.md:319` and `:410` and ADR-123 describe the 5 second window as a deliberate equivalent of per-use authorization (R-001).
7. README says ADRs 121 to 138 (line 32); `docs/technical-details.md:13` says 121 to 137. Make both 138.
8. `implementation-notes.md` historic notes say "55 baselines" against 72 plus smoke (73 files) now; add "at the time" or update.
9. `docs/technical-details.md:13` "Not proven" list must follow R-009 evidence.
- **Failing test first:** where a check exists, a Konsist or script check (for example `scripts/check-adr-identifiers.py` extended with the ADR range, a manifest rule for the back-callback attribute).
- **Acceptance criteria:** each of the nine items is corrected in one commit (docs only, except item 1's manifest attribute with its test); a reviewer grep for each false phrase returns nothing.

### R-040 (P2): `status.md` is stale and the acceptance mapping is `[TBD]`  [D status, T9.3]

- **Evidence:** `specs/archive/261104-phase-2a-android-ui/status.md` "Overall progress" table shows Phases 1 to 5 "Not Started" and the 2a-9 box open while the activity log says milestones are done (Phase 0 is the only Complete row); the acceptance mapping has `[TBD]` for criteria 1 to 4, 8, 9, 11 to 13 (line 108; T9.3 at `specs/archive/261104-phase-2a-android-ui/tasks.md:173` is open); criterion 7 is honestly "Android half"; criterion 8's proof is the weak test of R-020.
- **Failure scenario:** the single source of truth for progress is wrong; the archive step would copy false state.
- **Fix:** update the progress table; do T9.3: map criteria 1 to 9 and 11 to 13 to named Android tests, with an honest "not proven" where R-009 is open.
- **Failing test first:** n/a (docs); a small script or Konsist check that every test class named in `status.md` exists.
- **Acceptance criteria:** no `[TBD]` remains; every named test class exists (script passes); criteria whose evidence is R-009 say "pending R-009 item N".

## Merged findings

| This PRD | Merges | Why |
|---|---|---|
| R-002 | A-2 + A-4 | the prompt hang and the lock held across the prompt are one change in the same two files |
| R-003 | C-1 + D-5 + C-3 | one root cause: drafts held in plain `remember` or removed from composition; the hidden-amount half is R-024 |
| R-006 | C-7 + D-3 | same cause (routes carry ids only) found in two screens |
| R-022 | B-9 + D-4 + C-8 | the Inbox web door: bypass, crash and inert button |
| R-030 | C test gaps + D-9 | both are audit and test-coverage honesty |

## Findings dropped or not confirmed

- Nothing was dropped for lack of a file. All cited paths exist and the cited lines were confirmed as quoted, with these cautions. Lines marked "(reviewer-cited)" (for example the `KeyedComponents.kt` clip stack, `DecisionSections.kt:170`, `DiagnosticsScreen.kt` "Copied" and the `AgentsScreen.kt` `Column(verticalScroll)` line) were found at or near the cited place but not each re-read line by line.
- Not confirmable without running: the `MutatorMutex` cancellation behaviour (R-005), navigation `ViewModelStore` retention on lock (R-021), TalkBack output (R-025, R-026), jank cause ranking (R-008), network validation on a VPN (R-012), and the content of baseline images other than the two the reviewer viewed.
- Dropped as non-findings: reviewer observations that parity gaps (`isReadOnly`, `audience`, `profileEmail`, `role`, `isFromCache`, `ManualKeySubmitted`) are unused on both platforms; and "reviewed and clean" areas (release steering onto fakes, manifest, logging, privacy shield, destructive-action gating, CI pinning, scripts). These are listed in the reviewer reports as clean and need no work.

## Resolved questions

- **Should R-024 be promoted to P1? Decided: no, it stays P2 (run assumption, owner may overturn).** The amount that can be sent while the field looks blank is visible in the Submit button label ("Raise to $250") and the send goes through the device-owner prompt, so two deliberate actions stand in front of it. It is fixed in Groups 1 and 2 regardless of its label; the priority only affects ordering, not whether it ships.
- **DEC-1 and DEC-2:** resolved above as run assumptions.
- **Remaining unknowns are verifications, not decisions:** R-005, R-021 (verify first), R-008 (profile first), R-025/R-026 (TalkBack) and the R-009 hardware evidence. Each carries a demotion rule or a named owner-run check.
