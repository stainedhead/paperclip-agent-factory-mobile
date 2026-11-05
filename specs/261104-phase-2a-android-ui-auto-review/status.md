# Status: Phase 2a Android review fixes

**Created:** 2026-11-04 · **Spec dir:** `specs/261104-phase-2a-android-ui-auto-review/`

Update this file after every task and phase. It is the single source of progress.

## Overall progress

| Phase | State |
|---|---|
| Phase 0 Specification | Complete |
| Phase 1 Research and data model | Complete |
| Phase 2 Architecture and plan | Complete |
| Phase 3 Task breakdown | Complete |
| Phase 4 Implementation (Groups 1 to 5) | Group 1 done (shared core); Groups 2 to 5 not started |
| Phase 5 Completion and archive | Not Started |

Counts: 40 tasks · P0 0 · P1 9 · P2 31. Run assumptions DEC-1 (R-004) and DEC-2 (R-012) are in force; owner may overturn.

## Phase 0 checklist

- [x] Spec directory created; `spec.md` populated from the review PRD
- [x] Research questions identified (`research.md`)
- [x] Phase files initialized

## Fix checklist (tick when merged to the branch with its regression test; note commit hashes in implementation-notes.md)

- [x] T-001 R-001 (P1) Protected Keystore keys are not bound to the prompt; ADR-123's 5 second window claim is false (tests d1343a2, fc95a15; fix c445d44)
- [x] T-002 R-002 (P1) A biometric prompt can hang forever with the store mutex held; the lock is held across the user's prompt (tests 5c35532, cdce680; seam 9af6488; fix b6a2a25; follow-up: canShowNow without a lifecycle import, to keep the Konsist platform import allow-list)
- [x] T-004 R-004 (P1) `SetAgentBudget` raises money with no device-owner prompt, contradicting AGENTS.md (prep 3efcda7, bd83ffa; tests a085268; fix 1d7ed1a; iPhone fc19899, 9d0ad58; Android lock glyph and AgentDetailScreenTest stay with Group 2)
- [x] T-007 R-007 (P1) An in-flight inline approve/reject is cancelled when the Inbox holder closes (ADR-117 not applied) (SHARED HALF ONLY: test 9d53913, fix 7feba41; GraphSession deferral and idle-waiter cancel stay with Group 3)
- [x] T-024 R-024 (P2) The raise amount can be hidden from the field but still sent (SHARED HALF ONLY: test 8fe9d06, fix acd6326, iPhone d8f8dab; BudgetSheet field text stays with Group 2; Group 2 should verify the app half first: the Android AmountField is shown only while Raise is selected and the holder now clears the amount, so the hidden-amount symptom may already be gone). GROUP 2 APP HALF DONE: verified first, the symptom is gone (the holder clears the amount and the field leaves composition); regression test fails never on this code, passes at once, no app change; demoted with that evidence
- [x] T-019 R-019 (P2) Business rules live in androidApp (principle 5, "build once") (SHARED HALF, rules 1 to 4 moved: tests c071287, acb5059, 463f18f, 8fc8fa4; fixes df54fb8, 662fa1e, 65effb1, e76b252; iPhone switch 603677f, e258bbb, 27f3348, d7ac4ae. Not moved: InboxCardLink.of, cardContext, AgentFilter.includes, formatting helpers; androidApp copies and the Konsist rule forbidding them stay with Groups 2 to 4)
- [x] T-010 R-010 (P2) `AndroidKeystoreBackend.open()` does not wrap `getKey` (test 9c6582b, fix af8c55a)
- [x] T-011 R-011 (P2) The protected path and the real prompt path have no automated proof (launcher tests 9af6488, cdce680; contract cases 5fdd23c; the cases pass at once on the fake because R-001 landed first)
- [x] T-012 R-012 (P2) Connectivity counts "online" only when the network is VALIDATED (test 2b2d0a3, fix 04119ab)
- [x] T-013 R-013 (P2) `RecordFiles` robustness (test 22505a3, fix 0abf91a)
- [x] T-003 R-003 (P1) Typed text is lost on state change and on rotation: approval comment, reject and revision notes, budget amount (Group 2: test d30d934, fix c7aa63a; comment draft hoisted in Discussion with the seen pattern, note and amount rememberSaveable, restored amount re-sent once to a fresh holder)
- [x] T-023 R-023 (P2) `FormField` is single-line and strips newlines (iPhone parity) (Group 2: test + seam, then fix; FormField maxLines, notes, comment and decision text take up to four lines, address, name and amount stay single-line)
- [x] T-025 R-025 (P2) `FormField` TalkBack semantics hide typed text and the validation issue (Group 2: test (red) then fix; FormField uses a Material label, supportingText and error semantics, no contentDescription; the audit flags description-only fields (planted test); the Issue comment bar, Work search and interaction draft fields lose their redundant descriptions; Roborazzi baselines for the label look re-recorded at the end of Group 2)
- [x] T-026 R-026 (P2) Nothing that appears asynchronously is announced to TalkBack (Group 2: test 'asynchronous messages are live regions' (red), fix: Polite live region on feedback-banner, inbox-undo, error-banner, note-issue and diagnostics-copied; spoken output is for R-009 item 4)
- [ ] T-027 R-027 (P2) System Back on the first-run Pair step does not cancel pairing
- [ ] T-028 R-028 (P2) Manage factories reorder can show an order the store never accepted
- [ ] T-029 R-029 (P2) Locale glue in strings built in code
- [ ] T-033 R-033 (P2) Typing after Send is wiped and the failure banner can be off screen (Issue comment)
- [ ] T-034 R-034 (P2) Interaction drafts can disagree with the holder after process death
- [ ] T-036 R-036 (P2) Lock glyph has no description and row buttons have no disabled state
- [ ] T-037 R-037 (P2) Work search keeps unsent text; blank-title buttons enabled
- [ ] T-005 R-005 (P1) The Run viewer log tail may silently stop following
- [ ] T-006 R-006 (P1) A pushed detail screen survives a factory or company switch
- [ ] T-008 R-008 (P1) The Inbox fling misses the PRD jank budget (67% janky frames vs 5%)
- [ ] T-018 R-018 (P2) Graph opening runs on the main thread in `onCreate`
- [ ] T-021 R-021 (P2) Locking probably disposes the tab shell without clearing back-stack entry ViewModels
- [ ] T-022 R-022 (P2) Inbox "Open in web" door bypasses `WebOpener`, can crash with no browser, and can be an inert button
- [ ] T-032 R-032 (P2) The Agents and Work lists are not lazy
- [ ] T-014 R-014 (P2) The Konsist merged-manifest rules can pass stale locally on a dependency bump
- [ ] T-015 R-015 (P2) A release can silently ship debug-signed
- [ ] T-016 R-016 (P2) CI concurrency group lets the nightly benchmark and `push` runs cancel each other
- [ ] T-017 R-017 (P2) Konsist guards are weaker than they read
- [ ] T-020 R-020 (P2) A mutable-state rule is checked by tests that cannot fail
- [ ] T-030 R-030 (P2) Test gaps against the Android PRD acceptance criteria
- [ ] T-031 R-031 (P2) Roborazzi uses the default pixel-change threshold
- [ ] T-035 R-035 (P2) TDD evidence for security code is weaker than the notes imply
- [ ] T-039 R-039 (P2) Documentation claims that are false or stale
- [ ] T-040 R-040 (P2) `status.md` is stale and the acceptance mapping is `[TBD]`
- [ ] T-038 R-038 (P2) Attachments are not openable on either platform
- [ ] T-009 R-009 (P1) Acceptance evidence that has never been produced (needs the owner's hardware and CI)

## Blockers

None.

## Recent activity

- 2026-11-04: Group 1 (shared core) implemented: T-001, T-002, T-004, T-007 (shared half), T-010 to T-013, T-019 (shared half), T-024 (shared half). Open for the Group 2 and 3 agents: the androidApp halves named on the lines above, plus switching androidApp to the shared members (`RunStatusUi.isLive`, `AgentRowUi.visibleActions` and `offersViewRun`, `ToneUi` on the status enums, `FactoryStatusDecision`, `ExpiryDecision`, `DecideByUi.hintText`, `InboxState.isWorking`, `AgentDetailState.saveBudgetNeedsDeviceAuth`), the Roborazzi baselines for the lock glyph, `AgentDetailScreenTest` (a raise now prompts once) and an `AuthHost` that returns the prompt activity only while started.
- 2026-11-04: spec created from the review PRD (`/dev-flow:create-spec`).
