# Dev-Flow Process Analysis

> This file replaces the Phase 1a analysis. The previous content is available from git history (`git log -- dev-flow-analysis.md`; the Phase 1a version is the one before this commit).

**Feature:** Phase 2a Android app (Jetpack Compose UI, Keystore, BiometricPrompt, connectivity, benchmarks, CI) on the shared Kotlin core, plus the review-fix cycle that followed
**Spec directory:** `specs/archive/261104-phase-2a-android-ui/` (fix spec: `specs/archive/261104-phase-2a-android-ui-auto-review/`)
**Report generated:** 2026-11-05. Times are UTC (git author timestamps are UTC-5 and are converted here). Git is authoritative over `DEV-FLOW-STATUS.md`.

---

## 1. Executive Summary

The Android app for Paperclip Agent Factory Mobile was built on the shared core in milestones 2a-0 to 2a-9: first run, factories, Inbox, decisions, approvals, budgets, agents, run viewer, work and issues, an R8 release, a benchmark module and CI. A review then produced 40 findings (0 P0, 9 P1, 31 P2), and 39 of them were worked in one fix cycle.

**Total runtime:** dashboard 2026-11-04 15:09Z to 2026-11-05 03:46Z, 12 h 37 min. The run's git commits span 14:59Z (the PRD commit, before the run started) to 02:59Z: 198 commits in 12 h. The last 47 minutes of the dashboard (Steps 10 and 11) left no commit after the archive commit at 02:59Z.
**Overall assessment:** a large, mostly green result, and the review was worth its cost: it found real defects (R-005 and R-021 confirmed by failing tests). It also exposed process weaknesses (section 6). Three things are open, not done: R-009 (hardware evidence), R-008 (jank target missed) and live pairing.

---

## 2. Step-by-Step Timing

Dashboard times (orchestrator) with git commit counts per window (UTC). Git has no step markers, so the dashboard boundaries are used to slice the git log.

| Step | Name | Start | End | Min | Commits in window | Key outputs |
|---|---|---|---|---|---|---|
| 1 | Create spec | 15:09 | 15:14 | 5 | 2 (steps 1 and 2) | 57 tasks |
| 2 | Review spec | 15:14 | 15:18 | 4 | (above) | spec review fixes |
| 3 | Implement | 15:18 | 21:28 | 370 | 64 | slices 2a-0 to 2a-8 |
| 4 | Docs and user docs | 21:28 | 21:39 | 11 | 6 | docs, screenshots, milestone 2a-9 |
| 5 | Code and design review | 21:39 | 21:55 | 16 | 1 | 40 findings |
| 6 | Prepare review PRD | 21:55 | 21:58 | 4 | 3 (steps 6 to 8) | review PRD |
| 7 | Archive original spec | 21:58 | 22:01 | 3 | (above) | archived |
| 8 | Spec review fixes | 21:58 | 22:01 | 3 | (above) | DEC-1, DEC-2 recorded |
| 9 | Implement review fixes | 22:01 | 02:58 | 297 | 119 | 39 of 40 tasks |
| 10 | Archive fixes spec | 02:58 | 03:46 | 48 (shared with 11) | 1 | d43a3c8 |
| 11 | Final quality pass | 02:58 | 03:46 | 48 | 0 | gates only |
| 12 | This report | 03:46 | | | | |

**Notable observations:**
- Steps 3 and 9 are 667 of about 760 minutes (88%).
- Steps 7 and 8 ran in one agent, and so did 10 and 11, so their per-step times are shared, not separate.
- The dashboard shows Steps 10 and 11 running to 03:46Z, but the only commit in that window is at 02:59Z. Step 11 was verification only, so no commit is expected, but git cannot confirm the 47 minutes.
- Step 5 took 16 minutes and produced the findings that drove a 297-minute fix cycle.

---

## 3. Commit and Push Summary

**Total commits in the run:** 198 (a0748d3 at 14:59Z to d43a3c8 at 02:59Z). The repository has 640 commits in all.

| Type | Count |
|---|---|
| test | 59 |
| fix | 48 |
| feat | 41 |
| docs | 29 |
| refactor | 10 |
| chore | 6 |
| style | 4 |
| ci | 1 |

By step: Step 3 had 40 feat, 11 test, 5 fix, 4 docs, 2 chore, 1 ci, 1 style. Step 9 had 48 test, 43 fix, 13 docs, 10 refactor, 3 style, 1 feat, 1 chore.

Key commits: a0748d3 (PRD and plan), da990ec (spec review), 0490ccb (review PRD, DEC-1 and DEC-2), 380eac4 (archive original spec), 21131c3 (archived status mapped to named tests), d43a3c8 (archive the fix spec). Full list: `git log a0748d3^..d43a3c8`. No PR yet (Step 14).

---

## 4. Spec vs. Implementation Comparison

| Phase | Planned | Actual | Notes |
|---|---|---|---|
| Spec and spec review | no estimate | 9 min | 57 tasks |
| Implementation | milestones 2a-0 to 2a-9 | 370 min + 11 min docs | all built; live pairing and jank target not met |
| Review | no estimate | 16 min | 40 findings |
| Fixes | 40 tasks | 297 min, 39 done | T-009 open |
| Final gates | | 48 min | see section 2 |

**Phases skipped:** extra PRD validation (owner decision); live pairing against the pinned server (Docker would not start); hardware evidence.
**Phases added:** the whole review and fix cycle (Steps 5 to 11), including shared and iPhone changes for DEC-1 and several Konsist input fixes.

Review findings: 40 total, 0 P0, 9 P1, 31 P2.
- Fixed, mostly with a red regression test first: 38 of the 40 (all P1 except R-009).
- R-005 (log tail stops following) and R-021 (lock leaks holders) were confirmed real: the verify-first test failed before the fix.
- R-024 was demoted: its test passed at once, so there was no bug left to fix.
- R-008 (Inbox fling jank) only partly met: 52% to 41% janky frames on the Pixel_9_Pro emulator against the 5% target. The criterion stays open for the Pixel 6.
- R-009 (acceptance evidence) left open for the owner: a checklist and an evidence table are packaged, every result is "Not run".
- R-038 (attachments) is design only (ADR-143), as the PRD said.
- R-029 and R-030 were partial: some pseudo-locale, large-font, error-state and Viewer-role items were replaced or demoted.

---

## 5. Token / Message Usage

Exact token counts are unavailable. Rough estimate: about one sub-agent per slice (nine in Step 3) and per group (five in Step 9), plus one for each other step. No numbers were measured.

---

## 6. Process Observations

### What worked well
- Verify-first tests found two real bugs (R-005, R-021) and one non-bug (R-024) before any code changed.
- Konsist rules with planted-violation tests held the architecture. The review judged layering and the security shell strong.
- Moving business rules into shared Kotlin (Group 1) gave the iPhone the same fix.
- A review PRD with explicit priorities and counter-tests kept the P0 call reasoned.

### What caused delays or rework
- **Test-first was not consistent.** Step 3 has 40 feat commits against 11 test commits. Group 1 and the 2a-5 and 2a-7 screens were sometimes written before their tests, and the tests for T1.3 and T1.4 were written after the code. Step 3 counts are not red-first evidence. Step 9 is better (48 test commits, 43 fix commits), but the pairing was not machine-checked. T-035 records the weak TDD evidence for security code as a known deviation.
- **Flaky `awaitDisplayed` assertions** were found late and cost reruns.
- **The stale-pass Konsist bug was found twice** (R-014, then again for docs inputs in T-039): a rule that can pass on stale inputs was written twice.
- **Product issues found late.** The Keystore 5 s window instead of per-use authorisation was flagged at slice 2a-1 but not decided until the review; the prompts-per-refresh question with app lock on was found only at Group 1 and is still an owner decision (ADR-144, not implemented).
- **Orchestration friction** from the worktree guard: commands had to be split into plain steps and sub-agents could not always write where the work was.
- **Acceptance evidence was left to the end.** Jank was measured only after the review (67% without the content wait, 52% with it, 41% after fixes) and never reached the target.

### Recommendations for future runs
- Commit the failing test before the fix as a separate commit, and check that in a script, so red-first is evidence and not a claim.
- Decide protocol-level questions (per-use authorisation, prompts per refresh) before the slice that implements them.
- Run the benchmark and the live pairing script early and record the result in the first status.
- Run the Konsist input check on every new rule on its first write.
- Do not mark a step complete on documents alone; say which evidence is local and which waits for hardware.

### Run assumptions the owner can overturn
- **DEC-1 (R-004):** a budget raise asks for the device owner; a decrease does not. It also changes iPhone behaviour. If overturned: amend AGENTS.md to "budget incident raise" only.
- **DEC-2 (R-012):** connectivity is online when a default network declares INTERNET, validated or not. If overturned, only R-012, its ADR text and tests change.

### Never proven in this run
- Live pairing against the pinned Paperclip server (Docker would not start; `scripts/android-live-pairing.sh` never run).
- TalkBack and spoken output.
- Real fingerprint flows: key invalidation after enrolment, the system prompt on a destructive action, StrongBox, adb backup.
- Hosted CI: `android.yml` has never run on GitHub.
- Pixel 6, Samsung, API 28 and API 33. Only the Pixel_9_Pro API 37 emulator was used.

---

## 7. Manual vs. Automated Comparison

**Estimated manual duration:** about 4 to 6 weeks for one senior Android developer who knows Compose and the shared core (app, shared-core changes, 700+ tests, docs, review and fixes). Excludes meetings and hardware testing.
**Actual automated runtime:** about 12.5 hours elapsed (dashboard 12 h 37 min; git 14:59Z to 02:59Z).
**Efficiency gain:** roughly 10 times on elapsed time, a rough guess. Part of the gain is only apparent: the hardware, live-server and TalkBack evidence that a person would have gathered along the way has not been gathered, and it will cost the owner time later.
