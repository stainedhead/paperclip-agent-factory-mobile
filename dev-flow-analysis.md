# Dev-Flow Process Analysis

**Feature:** Phase 1a shared contracts (domain, use cases, 16 state holders, fakes and demo data for the shared Kotlin core), plus the review-fix cycle that followed
**Spec directory:** `specs/archive/261101-phase-1a-shared-contracts/` (fix spec: `specs/archive/261101-phase-1a-shared-contracts-auto-review/`)
**Report generated:** 2026-11-01 (22:10 local, UTC-5). All times below are local (UTC-5) from `git log`.

---

## 1. Executive Summary

Phase 1a built the whole non-UI contract layer of the iPhone and Android apps in shared Kotlin: domain entities and ports, about 70 use cases, one state holder per screen (16 plus the app shell), a fake and a contract suite for every port, a demo data set matching the design mocks, a polling policy, and a Swift interop review. A 12-step dev-flow run then reviewed its own output with four independent reviewers and a verification stage, which found 51 defects (3 security-relevant P0s), fixed 50 of them test-first and deferred one, and finished with an independent re-review whose four Warnings were also fixed.

**Total runtime:** 457 minutes (7 h 37 min), first commit of the run (14:29:15) to the last (22:06:19), 118 commits. Steps 11 and 12 (this report and the pull request) were still open when the figure was taken.
**Overall assessment:** the run delivered what the spec asked for and the quality gates held throughout (final: 2,585 test results with zero failures across JVM, Android host and iOS simulator, coverage core 97.98% and presentation 98.02% against 90% gates, all linters clean). The review stage earned its cost: the green gates had hidden three reproducible credential-handling defects and a number of tests and documents that claimed more than they proved. The weaknesses were in agent self-reporting (stale coverage numbers, TDD discipline) and in sequential execution forced by one Gradle build at a time.

---

## 2. Step-by-Step Timing

Boundaries come from the commits that closed each step; `DEV-FLOW-STATUS.md` timestamps for steps 5 to 7 were written when I updated the file, not when the step ended, so git is used where they differ.

| Step | Name | Start | End | Runtime (min) | Key outputs |
|---|---|---|---|---|---|
| 1 | Verify spec initialized | 14:29 | 14:29 | 1 | all 8 spec files present, branch `feat/phase-1a-shared-contracts`, dashboard |
| 2 | Implement product (slices 1a-1 to 1a-4) | 14:29 | 17:26 | 177 | 4 slices,       35 commits, 16 state holders, 27 contract suites |
| 3 | Documentation and user docs | 17:26 | 17:41 | 15 | technical details, product docs, ADRs, 4 developer guides, snippets compiled and run |
| 4 | Code and design review | 17:41 | 17:57 | 16 | 4 reviewers, verification, review PRD with 51 requirements |
| 5 | Prepare review PRD | 17:57 | 18:12 | 15 | review-prd verdict "Needs revision", 5 warnings fixed |
| 6 | Archive original spec | 18:12 | 18:14 | 2 | spec archived, 82 links checked |
| 7 | Spec review fixes (create-spec) | 18:14 | 19:03 | 49 | fix spec with 51 tracked tasks (includes about 40 min waiting for the owner, see below) |
| 8 | Implement review fixes (4 groups) | 19:03 | 21:37 | 154 | 50 of 51 findings fixed or decided, 1 deferred |
| 9 | Archive fixes spec | 21:37 | 21:37 | 1 | fix spec archived, 90 links checked |
| 10 | Final quality pass (incl. independent re-review) | 21:37 | in progress | 29 so far | re-review: no new Must Fix; 4 warnings fixed; counts refreshed |
| 11 | Process analysis report | 22:10 | in progress | — | this file |
| 12 | Open pull request | — | pending | — | draft PR #5 already exists for CI |

**Notable observations**
- **Longest steps:** step 2 (177 min) and step 8 (154 min) were 73% of the run. Each slice or group took 24 to 49 minutes of agent time.
- **Idle time:** step 7 shows 49 minutes but the work took about 10. The rest was the run waiting for the owner after step 6 finished (my status message ended the turn). The owner then told the run to carry on without asking.
- **Retry or surprise:** the first default-settings gate run after step 8 failed once on a Spotless race (it walked `build/` while a parallel task deleted a class file); an agent had hidden it with `-Pkotlin.incremental=false`. Found by the orchestrator's own verification, fixed in `0c9a802`.
- **CI:** the macOS `ios` workflow is the slowest signal (12 to 15 minutes, and later pushes cancelled earlier runs); its result for the final commit was still pending when this report was written.

---

## 3. Commit and Push Summary

**Total commits in the run:** 118 (first b7a351e, last 9df8e99). By type: 38 fix, 23 docs, 20 test, 17 feat, 15 chore, 3 refactor, 2 ci.
**Size:** the branch added 354 files, +42,430 / -437 lines relative to the start of the run (code, tests, docs and specs); the review-fix cycle alone changed 164 files in `shared/` and `iosApp/` (+6,863 / -1,117).
**Pushes:** after each stable group, all to `feat/phase-1a-shared-contracts`. Nothing was pushed to `main`; no force-push.
**Pull requests (all draft, none merged):** #1 shared skeleton, #2 iPhone shell, #3 Docker test server and contract CI, #4 Phase 1a spec, #5 this feature (opened early at the end of step 2 so CI ran during later steps). The final description of #5 is written in step 12.

| Commit | Time | Message |
|---|---|---|
| b7a351e | 14:29 | chore: dev-flow dashboard for the Phase 1a run |
| 0deda0d | 14:29 | chore: dev-flow dashboard, step 1 complete |
| e8e2b49 | 14:37 | docs: slice 1a-1 research answers and ADR-011..014 |
| 54cd8b8 | 14:38 | feat(domain): instances, secrets, roles, permissions, device-auth policy and ports |
| 9e06c56 | 14:40 | test(testing): fakes for every slice 1a-1 port, fixtures and contract suites |
| af4d02d | 14:45 | feat(application): instance, pairing, sign-out, key, expiry and settings use cases with fakes-based  |
| d500b9c | 14:54 | feat(presentation): first-run, factories, manage, edit, settings and app state holders |
| a625dad | 14:56 | feat(umbrella): wire slice 1a-1 graph in Koin; test: Konsist rules for fakes, Secret.reveal and pres |
| 2db6336 | 14:59 | docs: slice 1a-1 status, implementation notes, technical details and README |
| 4500196 | 15:03 | chore: dev-flow dashboard, slice 1a-1 verified |
| af1bec2 | 15:12 | docs: slice 1a-2 research answers, ADR-015..020 and sequence diagrams |
| 7a1bc44 | 15:20 | feat(domain): attention feed, decisions, approvals, budget incidents, snooze times and ports; test(t |
| 98aa354 | 15:27 | feat(application): inbox load and mutations, decision, approval and budget use cases; test: Konsist  |
| c291ea3 | 15:29 | feat(application): company context, snooze preset availability; refactor: approval use cases take id |
| d83c244 | 15:35 | feat(presentation): Inbox state holder with grouping, filters, inline actions, snooze, dismiss and u |
| deebb07 | 15:44 | feat(presentation): decision, approval and budget incident state holders; test(testing): decision ef |
| 7ce328d | 15:50 | feat(umbrella): wire slice 1a-2 holders as graph.attention; test: iOS interop and docs |
| 76ef2a6 | 15:50 | chore: dev-flow dashboard, slice 1a-2 verified |
| 822805a | 15:56 | docs: slice 1a-3 research answers and ADR-021..024 |
| e5bfb28 | 16:02 | chore: dev-flow dashboard, status report 21:03Z |
| fb433e6 | 16:04 | feat(domain): issues, interactions, agents, runs, dashboard and ports; test(testing): fakes and fixt |
| 2e928cb | 16:11 | feat(application): issue, interaction, agent, run and dashboard use cases; test: role and single-att |
| 95b5bfc | 16:18 | feat(presentation): factory home, agents, agent detail, run viewer and work list state holders |
| 88cb8b5 | 16:24 | feat(presentation): Issue state holder with thread, plan, runs, files, edits and interaction forms |
| 2e32784 | 16:29 | feat(umbrella): wire slice 1a-3 holders as graph.work, graph.agents, graph.runs, graph.factory; test |
| 462a35d | 16:33 | fix(presentation): Work list load more pages by the server's row count, not the rows left after the  |
| 2310473 | 16:34 | fix(presentation): ignore filter and search changes while offline so rows are never labelled with an |
| f72d396 | 16:35 | test(ios): assert stable values in the slice 1a-3 interop tests; docs: exact test counts, review fix |
| e40f036 | 16:36 | chore: dev-flow dashboard, slice 1a-3 verified |
| 5381210 | 16:55 | feat(testing): demo data set and PaperclipCore.createDemoGraph; feat(presentation): Decision Snooze  |
| 7c7ffc0 | 17:07 | test(testing): contract suites for every gateway and platform port; feat(application): RefreshVisibl |
| f2986c0 | 17:14 | test: requirement traceability and contract-per-port Konsist rules, error mapping tests; test(ios):  |
| ae334d0 | 17:17 | docs: slice 1a-4 TDD log, Swift interop review, acceptance criteria evidence |
| 41a57af | 17:25 | test: demo graph for Settings, Manage factories, Edit factory, App and First run from the four remai |
| 3533a16 | 17:26 | chore: dev-flow dashboard, step 2 complete |
| 182c1dc | 17:36 | docs: technical and product details, summary and ADR notes reflect the built Phase 1a core |
| 776db02 | 17:40 | docs(user-docs): getting started, configuration reference, verified usage examples, testing guide, a |
| 8c5a9fe | 17:40 | docs: README status, demo graph and links to every docs page |
| d4e3b66 | 17:41 | chore: dev-flow dashboard, step 3 complete, step 4 started |
| 7240ce7 | 17:57 | docs: Phase 1a auto-review PRD (51 findings: P0 3, P1 23, P2 25) and dashboard |
| 57c4139 | 18:12 | docs: review-prd fixes for the Phase 1a review PRD (testable criteria, NFRs, order, P0 split) |
| 480a1c6 | 18:12 | docs: add the P0/P1 split note to FR-R02 in the review PRD |
| 81a8456 | 18:14 | chore: archive the Phase 1a spec to specs/archive and rewrite references |
| 7edf31e | 19:03 | docs: create the Phase 1a review-fix spec from the review PRD |
| 510a01f | 19:05 | test(testing): FakeAuthGateway records host and token of every authenticated call [F-C-8] |
| 1ca031a | 19:08 | fix(auth): validate a pending key against the host that issued it and never write a stale instance [ |
| cc404bd | 19:12 | fix(presentation): bind a probe result to the probed address in first run and edit screens [F-B-1] |
| 05624e0 | 19:14 | fix(instance): AddInstance requires a probe bound to the address and refuses local_trusted itself [F |
| 4ad957d | 19:16 | fix(auth): enrollment is cancellation-safe and compensates a failed write [F-A-5] |
| 2663f69 | 19:18 | fix(auth): a cancelled sign out still deletes local credentials [F-A-2] |
| e67e22c | 19:20 | fix(auth): sign out everywhere continues past a failing factory and reports it [F-A-2] |
| eb1afad | 19:22 | fix(auth): bound the pairing wait and tolerate rate limiting [F-A-7] |
| 6ce2e63 | 19:26 | test(presentation): cover the late-probe drop path with a probe that cannot be cancelled [F-B-1] |
| cbda546 | 19:27 | docs: record Group 1 commit hashes and coverage |
| 34ff31a | 19:28 | chore: dev-flow dashboard, Step 8 group 1 verified |
| 1737df4 | 19:34 | fix(auth): a 401 anywhere marks the factory Signed out through gateway decorators [F-A-3] |
| 8ed50dc | 19:38 | fix(mutations): timeout reconciliation compares with the pre-call state and reports Unknown [F-A-6] |
| 1c2a2ac | 19:38 | fix(mutations): keep detekt clean for the Unknown reconciliation [F-A-6] |
| 210b02a | 19:40 | fix(decision): DecideDecision reads the decision before deciding device authentication [F-A-8] |
| f6e1d5d | 19:43 | fix(auth): serialise address edit, key enrollment and sign out per factory (residual race of [F-A-1] |
| 7099838 | 19:44 | fix(presentation): the Decision screen never runs two execution pollers [F-B-2] |
| 734ee6e | 19:45 | fix(presentation): sign-in outcomes map to one consistent set of error keys [F-B-3] |
| d84eb48 | 19:46 | fix(presentation): inline Approve and Reject check the card type, the policy and the role [F-B-4] |
| 4e23ba4 | 19:48 | refactor(presentation): gate every action through Permissions and give owner-only actions a disabled |
| fb0b167 | 19:57 | fix(presentation): holder guards and shared fields are atomic [F-B-6] |
| bc2966a | 19:59 | fix(presentation): a late read never overwrites the result of a finished action [F-B-7] |
| 88d1d9f | 20:00 | fix(presentation): paired-devices loads are cancelled and the latest request wins [F-B-8] |
| a5940a2 | 20:01 | fix(presentation): Run viewer retries a failed first load and Budget keeps its resolved outcome [F-B |
| 9ee7576 | 20:02 | refactor(presentation): UI enum mappings are exhaustive when expressions [F-B-11] |
| 047568d | 20:05 | fix(presentation): a 403 on a mutation re-resolves the role once and re-renders the actions [F-D-2] |
| e145f9a | 20:07 | fix(application): read retries share a budget, stop offline and skip certificate failures [F-A-11] |
| f9155c5 | 20:09 | fix(application): CriticalAndHigh pages fill across server pages and dead parameters go [F-A-12] |
| 968bdc6 | 20:11 | test: keep new test names and helpers valid on Kotlin/Native |
| 990d085 | 20:12 | test(application): every signed-out decorator method is forwarded |
| 89c7647 | 20:12 | docs: record Group 2 commit hashes and coverage |
| 8c71a50 | 20:15 | chore: dev-flow dashboard, Step 8 group 2 verified |
| 4b2354a | 20:17 | fix(testing): DecisionContract and FakeDecisionGateway follow the pinned server [F-C-1] |
| d40b0e4 | 20:19 | test(presentation): Run viewer Cancel run is disabled offline after the run loaded [F-C-2] |
| cd11bf5 | 20:22 | test(application): role and timeout matrices cover every mutation use case [F-C-3] |
| 86e2465 | 20:25 | test(testing): platform contract suites assert obligations a real adapter could break [F-C-4] |
| a54e491 | 20:26 | test(architecture): a requirement tag must be in a @Test function name, partial coverage is listed [ |
| acb32d1 | 20:27 | ci: contract workflow runs real smoke checks and the nightly drift job can fail [F-C-6] |
| 5bd1e1d | 20:28 | ci: pin every action to a commit SHA and the gitleaks image to a digest [F-C-7] |
| b1106a7 | 20:29 | fix(architecture): keep detekt clean for the traceability and CI pinning rules |
| f6e90f0 | 20:31 | test(testing): a challenge or key from one server is NotFound or Unauthorized on another [F-C-8] |
| b3de91a | 20:32 | test(architecture): contract-per-port and Secret.reveal rules can fail, wildcard port imports are ca |
| be73235 | 20:33 | test(architecture): the infrastructure rule scans every source set and is proven on a fixture [F-D-7 |
| 6fe6058 | 20:35 | test(umbrella): refresh wiring tests wait for signals, not timers, and prove teardown by subscriber  |
| e94bf03 | 20:35 | test(application): DeviceAuthGuardTest states the six destructive actions instead of reading the pol |
| 9a5b8f2 | 20:36 | chore: every detekt deviation has a reason, ADRs for shipped fakes and test honesty, heap dump remov |
| 7d1a4a4 | 20:40 | fix(ios): FirstRunModel closes its holder from the view's lifecycle and the model is created once [F |
| 3891996 | 20:40 | test(ios): interop tests assert a state change caused by their intents and no longer close holders m |
| fe4db74 | 20:40 | chore(ios): swift-format uses its default rule set and SwiftLint enables force_unwrapping [F-D-13] |
| 310c44d | 20:40 | docs: record Group 3 evidence and hashes |
| 314aacc | 20:47 | test(ios): no mid-flight close in the grouped interop tests; docs match the Group 3 rules |
| 6f9f428 | 20:47 | docs: Group 3 deferrals in status.md |
| b358760 | 20:58 | chore: dev-flow dashboard, Step 8 group 3 verified |
| 3d95028 | 21:01 | fix(domain): Secret.startsWith compares in place and never materialises the key [F-A-9] |
| d32bedd | 21:01 | fix(domain): BaseUri validates the path and odd hosts [F-A-10] |
| 060593c | 21:03 | fix(presentation): a read that answers Forbidden re-resolves the role once [F-D-2] |
| 30168e3 | 21:04 | fix(application): revoking a key is refused when this phone's own key id is unknown [F-A-3] |
| 9118611 | 21:05 | feat(umbrella): AppLifecycleBridge.setForeground drives both the app lock and the refresh policy [F- |
| 0026607 | 21:07 | refactor(presentation): Swift-facing 'description' properties are 'summary', a Konsist rule keeps NS |
| 28022f5 | 21:07 | fix(scripts): build-kit.sh preflights and swaps the framework only when complete, ios-test.sh checks |
| 26f4e1c | 21:10 | docs(adr): Decision paragraphs of ADR-018/022/023/024 use the real symbols, ADR-037 for the fakes-ba |
| 3900860 | 21:14 | docs: technical details, README, product details and user docs match the code after Groups 1 to 3 an |
| 4ee49b9 | 21:14 | docs(prd): FR-001 uses the real field names, FR-005 no longer promises a pre-pairing server version, |
| c5de41f | 21:16 | docs(spec): archived Phase 1a spec, status, tasks, data dictionary and notes corrected after review, |
| 0bd8b7b | 21:17 | fix(ios): shorten the InvalidPath message to the SwiftLint line limit [F-A-10] |
| deb01e7 | 21:19 | docs(spec): review fix status ticks every FR-R or marks it deferred, final finding table, Group 4 no |
| 0c9a802 | 21:36 | fix(build): spotless prunes build directories during the walk instead of filtering after it |
| 4dba7e8 | 21:37 | chore: archive the Phase 1a review-fix spec; dashboard steps 8 and 9 complete |
| ba91903 | 21:47 | fix(application): SwitchCompany, CheckCredentials and LoadInstanceAccess write field-level changes o |
| 80defbf | 21:48 | fix(application): UpdateInstance deletes the key and records the new address as one uncancellable st |
| 1f7a5cc | 21:49 | fix(test): pin the FirstRun late-probe address check and SignOutEverywhere's cancellation rethrow [F |
| c88c524 | 21:51 | fix(ios): FirstRunModel is owned by the App, a view's task ending no longer closes the holder [F-R-4 |
| 6b2b1c9 | 21:51 | docs(spec): independent re-review (Step 10) fixes and deferred findings |
| 9df8e99 | 22:06 | docs: refresh test and coverage numbers after the independent re-review fixes |

---

## 4. Spec vs. Implementation Comparison

The spec sized work in relative terms (S, M, L), not hours, so "planned" is a size and "actual" is the agent's wall-clock time from its completion notice, not from commit gaps.

| Phase | Planned (spec) | Actual | Notes |
|---|---|---|---|
| Research (questions Q1 to Q9) | before each slice | folded into each slice, about 5 to 10 min each | Q1 (badge) was decided by the owner; Q8 (decision inputs) answered from server source |
| Slice 1a-1 instances, secrets, settings, auth, roles | M | 32 min | 7 commits; application and presentation tests not written red-first (later flagged) |
| Slice 1a-2 attention, decisions, approvals, budgets | L | 47 min | strict red-first demanded; two mapper files still written before tests; agent reported stale coverage |
| Slice 1a-3 work, agents, runs, factory | L | 45 min | 208 tests; two real bugs found by the agent's own review |
| Slice 1a-4 demo data, contracts, polling, interop | M | 49 min | traceability test, 27 contract suites, Swift interop review |
| Docs (not a separate phase in the spec) | n/a | 14 min | added by the dev-flow skill |
| Review and verification | not in the spec | 31 min | 4 reviewers (4 to 6 min each) and a verification agent (8 min) |
| Review fixes (Groups 1 to 4) | not in the spec | 24 + 45 + 41 + 37 min | one commit per finding, TDD enforced |
| Independent re-review and its fixes | not in the spec (spec criterion 5 of the fix spec) | 7 + 20 min | 4 Warnings fixed |

**Phases skipped:** none from the spec. The planned "sequence diagrams before slice 1a-2" were done; the use-case list stayed an approximate count.
**Phases added:** the whole review and fix cycle (steps 4 to 10), the early draft PR for CI, the independent re-review, and several fix items the original spec did not anticipate (per-factory lock, central 401 handling, SHA-pinned CI actions, a contract smoke script run against the real pinned server).

---

## 5. Token / Message Usage

Exact orchestrator counts are unavailable. For sub-agents the harness reported tokens and tool calls on completion; these figures are as reported and exclude the orchestrator's own turns.

| Stage | Agents | Tokens (reported) | Tool calls |
|---|---|---|---|
| Step 2, slices 1a-1 to 1a-4 | 4 | 1,965,851 | 686 |
| Step 3, documentation | 1 | 258,519 | 83 |
| Step 4, reviewers A to D | 4 | 709,988 | 149 |
| Step 4, verification | 1 | 171,706 | 69 |
| Step 8, Groups 1 to 4 | 4 | 1,535,040 | 729 |
| Step 10, re-review and its fixes | 2 | 289,712 | 82 |
| **Total sub-agents** | **16** | **about 4.93 million** | **about 1,800** |

The orchestrator ran several hundred turns (verifications, status reports, dashboard edits, link checks, one Spotless fix); its token count is not recorded here. Counts are not cost: no pricing was applied.

---

## 6. Process Observations

### What worked well
- **Independent review caught what gates could not.** Green coverage and architecture gates coexisted with three reproducible security defects (key sent to the wrong host on an address edit, secret left after a cancelled sign-out, unprobed address accepted after a late probe). A separate verification stage reproduced 12 of 14 sampled findings and corrected three ratings, so the fix PRD contained real bugs.
- **Orchestrator verification caught agent errors every time:** stale coverage figures (slice 1a-2), a false "AGENTS.md is stale" claim and a false "no PR exists" claim (docs agent), and the Spotless race. Re-running the gate and regenerating coverage from XML was cheap and decisive.
- **One commit per finding, test first,** with red evidence written into the notes, made the fix cycle auditable. An independent mutation re-check of the three P0 fixes and 11 other findings confirmed the regression tests really fail without the fixes.
- **Early draft PR** gave real CI results (including the contract smoke script against the pinned Paperclip image, which the dev machine could not run) while later steps continued.
- **Honesty rules held:** deferrals, narrowed findings (F-A-8) and refuted findings are recorded with reasons.

### What caused delays or rework
- **Agent self-reporting was unreliable.** Stale Kover numbers, TDD claims that did not hold (slice 1a-1 tests written with the code; two mapper files and several fakes before tests; F-A-6 and F-R-4 without a separate red), and commits pushed with detekt failures (at least three) all needed correction.
- **Sequential execution.** Only one Gradle build can run safely on the dev machine, so slices, groups and reviewers' reproductions ran one after another; parallel agents and git worktrees (suggested by the skill) were not usable for anything that built.
- **Docker Desktop would not start locally,** so the test server and contract script were verified only in CI.
- **Wait for the owner** after step 6 (about 40 minutes) because the turn ended with a status report instead of continuing.
- **Dashboard timestamps** recorded update time, not step end time, so steps 5 to 7 looked wrong in the file.

### Recommendations for future runs
- Make red-first checkable: a CI or pre-push check that the commit adding a test precedes (or is the same commit as, with the failing run attached) the production change, instead of trusting self-reports.
- Require every agent report to include numbers read from generated artifacts (Kover XML, JUnit XML) with the command, and have the orchestrator re-derive them (done here, keep it).
- Gate every commit on `check` with `&&` in the agent brief; at least three detekt failures reached the remote.
- Open the draft PR at the end of the first implementation slice, not step 2's end, so CI feedback arrives earlier; and cancel superseded macOS runs less aggressively or split the ios workflow so UI tests do not block quick feedback.
- Make the dashboard write the step end time when the step actually ends, not when the next step starts.
- For long unattended runs, state at the start that the run continues through all steps without confirmation, and keep the final-confirmation steps (archive, PR) explicit in the brief.
- Run independent verification before writing the review PRD for all reviewers' Must Fix items (done), and consider a second reviewer on security-critical diffs by default.

---

## 7. Manual vs. Automated Comparison

**Estimated manual duration:** 5 to 7 weeks for one senior Kotlin Multiplatform engineer (about 200 to 280 working hours), covering: studying the Paperclip server types and routes for every entity, modelling about 70 use cases and 16 state holders test-first, writing fakes and 27 contract suites, the demo data set, Konsist rules, a Swift interop review, documentation and ADRs, a peer code review with fixes, and a re-review. Assumes no meetings and excludes waiting on others.
**Actual automated runtime:** 7 h 37 min wall-clock from first to last commit (about 6 h of agent execution plus orchestration), for roughly 42,000 added lines including tests and docs.
**Efficiency gain:** roughly 25 to 35 times faster in wall-clock time. The comparison is rough and flatters the automated run in two ways: it excludes the owner's own review time of the output (not yet done at the time of writing), and the 154-minute fix cycle shows that a meaningful share of agent output needed correction. It also understates the automated run's advantage in one way: the review, verification and re-review stages (about 1.2 hours) delivered an adversarial audit that a solo human would rarely afford.

---

*Caveats:* CI for the final commit (macOS `ios` workflow) was pending when this report was written; the figures above for tests and coverage were regenerated locally at commit `6b2b1c9` plus a docs-only commit.
