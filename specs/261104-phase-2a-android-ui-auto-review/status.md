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
| Phase 4 Implementation (Groups 1 to 5) | In progress (Group 1) |
| Phase 5 Completion and archive | Not Started |

Counts: 40 tasks · P0 0 · P1 9 · P2 31. Run assumptions DEC-1 (R-004) and DEC-2 (R-012) are in force; owner may overturn.

## Phase 0 checklist

- [x] Spec directory created; `spec.md` populated from the review PRD
- [x] Research questions identified (`research.md`)
- [x] Phase files initialized

## Fix checklist (tick when merged to the branch with its regression test; note commit hashes in implementation-notes.md)

- [x] T-001 R-001 (P1) Protected Keystore keys are not bound to the prompt; ADR-123's 5 second window claim is false (tests d1343a2, fc95a15; fix c445d44)
- [x] T-002 R-002 (P1) A biometric prompt can hang forever with the store mutex held; the lock is held across the user's prompt (tests 5c35532, cdce680; seam 9af6488; fix b6a2a25)
- [ ] T-004 R-004 (P1) `SetAgentBudget` raises money with no device-owner prompt, contradicting AGENTS.md
- [ ] T-007 R-007 (P1) An in-flight inline approve/reject is cancelled when the Inbox holder closes (ADR-117 not applied)
- [ ] T-024 R-024 (P2) The raise amount can be hidden from the field but still sent
- [ ] T-019 R-019 (P2) Business rules live in androidApp (principle 5, "build once")
- [ ] T-010 R-010 (P2) `AndroidKeystoreBackend.open()` does not wrap `getKey`
- [ ] T-011 R-011 (P2) The protected path and the real prompt path have no automated proof
- [ ] T-012 R-012 (P2) Connectivity counts "online" only when the network is VALIDATED
- [ ] T-013 R-013 (P2) `RecordFiles` robustness
- [ ] T-003 R-003 (P1) Typed text is lost on state change and on rotation: approval comment, reject and revision notes, budget amount
- [ ] T-023 R-023 (P2) `FormField` is single-line and strips newlines (iPhone parity)
- [ ] T-025 R-025 (P2) `FormField` TalkBack semantics hide typed text and the validation issue
- [ ] T-026 R-026 (P2) Nothing that appears asynchronously is announced to TalkBack
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

- 2026-11-04: spec created from the review PRD (`/dev-flow:create-spec`).
