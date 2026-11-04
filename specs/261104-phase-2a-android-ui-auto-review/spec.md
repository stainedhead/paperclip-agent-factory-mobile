# Spec: Phase 2a Android review fixes

**Created:** 2026-11-04 · **Status:** Draft · **Spec dir:** `specs/261104-phase-2a-android-ui-auto-review/`
**Source PRD:** [phase-2a-android-ui-auto-review-PRD.md](./phase-2a-android-ui-auto-review-PRD.md) (requirements R-001 to R-040, one per finding)
**Reviews:** the Android spec [specs/archive/261104-phase-2a-android-ui](../archive/261104-phase-2a-android-ui/spec.md); pattern [phase-1a auto-review](../archive/261101-phase-1a-shared-contracts-auto-review/spec.md)

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

## Run assumptions (owner may overturn)

- **DEC-1 (R-004):** a raise of an agent's budget asks for the device owner (`SetAgentBudget` through `DeviceAuthGuard` when the new monthly amount exceeds the current one; a decrease needs no prompt). Changes iPhone behaviour. If overturned: amend AGENTS.md to "budget incident raise" only; R-004 closes with that ADR text.
- **DEC-2 (R-012):** connectivity is online whenever a default network declares `NET_CAPABILITY_INTERNET`, validated or not. No shared API change. If overturned, only R-012, its ADR text and tests change.

## Functional requirements

One per finding, in the PRD under the same id. Priority P0 none, P1 R-001 to R-009, P2 R-010 to R-040.

| Id | Priority | Requirement |
|---|---|---|
| R-001 | P1 | Protected Keystore keys are not bound to the prompt; ADR-123's 5 second window claim is false |
| R-002 | P1 | A biometric prompt can hang forever with the store mutex held; the lock is held across the user's prompt |
| R-003 | P1 | Typed text is lost on state change and on rotation: approval comment, reject and revision notes, budget amount |
| R-004 | P1 | `SetAgentBudget` raises money with no device-owner prompt, contradicting AGENTS.md (DEC-1 run assumption, owner may overturn) |
| R-005 | P1 | The Run viewer log tail may silently stop following (verify first) |
| R-006 | P1 | A pushed detail screen survives a factory or company switch |
| R-007 | P1 | An in-flight inline approve/reject is cancelled when the Inbox holder closes (ADR-117 not applied) |
| R-008 | P1 | The Inbox fling misses the PRD jank budget (67% janky frames vs 5%) |
| R-009 | P1 | Acceptance evidence that has never been produced (needs the owner's hardware and CI) |
| R-010 | P2 | `AndroidKeystoreBackend.open()` does not wrap `getKey` |
| R-011 | P2 | The protected path and the real prompt path have no automated proof |
| R-012 | P2 | Connectivity counts "online" only when the network is VALIDATED |
| R-013 | P2 | `RecordFiles` robustness |
| R-014 | P2 | The Konsist merged-manifest rules can pass stale locally on a dependency bump |
| R-015 | P2 | A release can silently ship debug-signed |
| R-016 | P2 | CI concurrency group lets the nightly benchmark and `push` runs cancel each other |
| R-017 | P2 | Konsist guards are weaker than they read |
| R-018 | P2 | Graph opening runs on the main thread in `onCreate` |
| R-019 | P2 | Business rules live in androidApp (principle 5, "build once") |
| R-020 | P2 | A mutable-state rule is checked by tests that cannot fail |
| R-021 | P2 | Locking probably disposes the tab shell without clearing back-stack entry ViewModels (verify first) |
| R-022 | P2 | Inbox "Open in web" door bypasses `WebOpener`, can crash with no browser, and can be an inert button |
| R-023 | P2 | `FormField` is single-line and strips newlines (iPhone parity) |
| R-024 | P2 | The raise amount can be hidden from the field but still sent (decided: stays P2; it concerns money) |
| R-025 | P2 | `FormField` TalkBack semantics hide typed text and the validation issue |
| R-026 | P2 | Nothing that appears asynchronously is announced to TalkBack |
| R-027 | P2 | System Back on the first-run Pair step does not cancel pairing |
| R-028 | P2 | Manage factories reorder can show an order the store never accepted |
| R-029 | P2 | Locale glue in strings built in code |
| R-030 | P2 | Test gaps against the Android PRD acceptance criteria |
| R-031 | P2 | Roborazzi uses the default pixel-change threshold |
| R-032 | P2 | The Agents and Work lists are not lazy |
| R-033 | P2 | Typing after Send is wiped and the failure banner can be off screen (Issue comment) |
| R-034 | P2 | Interaction drafts can disagree with the holder after process death |
| R-035 | P2 | TDD evidence for security code is weaker than the notes imply |
| R-036 | P2 | Lock glyph has no description and row buttons have no disabled state (a11y, reviewer P3) |
| R-037 | P2 | Work search keeps unsent text; blank-title buttons enabled (reviewer P3) |
| R-038 | P2 | Attachments are not openable on either platform (design only in this phase) |
| R-039 | P2 | Documentation claims that are false or stale |
| R-040 | P2 | `status.md` is stale and the acceptance mapping is `[TBD]` |

## Non-functional requirements

TDD red-first for every fix; one commit per finding; all AGENTS.md gates green after each commit that can affect them; coverage not reduced (core 98.03%, presentation 98.10%, gates 90%); no secret logged or stored outside `SecretStore`; mutation order and no-auto-retry never relaxed; no new unbounded wait; Roborazzi baselines change only when intended, with matching `docs/images/` and `user-docs/images/`; docs updated in the same PR. Full gate list and build-lock rules: PRD sections "Guidance" and "Non-functional requirements and quality gates".

## System architecture

No structural change. Layers: `shared/infrastructure/securestore` and `platform` (androidMain), `shared/domain` and `shared/application` (R-004), `shared/presentation` (R-007, R-019, R-024), `androidApp`, `shared/testing`, Konsist, CI and docs. See `architecture.md`.

## Scope of changes

Files per group are in `plan.md`. Out of scope: QR scan, deep links, Android markdown, agent configuration, a Perfetto rewrite (PRD "Out of scope").

## Breaking changes

R-004 changes the exported shared surface (`Action.RaiseAgentBudget`, `SetAgentBudget` signature) and the iPhone's behaviour. R-007 adds `InboxState.isWorking`. R-001 changes the protected-key format: records written by old keys read as Missing or re-pair cleanly (R-001 criterion 6).

## Success and acceptance criteria

1. Every requirement R-001..R-040 closed with its own commit and a test that failed first (or an evidence note where the PRD says "verify first"/"design only").
2. P0 none; all nine P1 closed; R-009 evidence recorded dated in the archived Android spec `status.md`.
3. All AGENTS.md gates green; iOS gates green after every commit that changes exported shared surface or shared behaviour.
4. Coverage at or above the current numbers.

## Risks and mitigation

Hardware-only evidence (R-009) cannot be produced by an agent: owner track. "Verify first" items (R-005, R-021, R-008, R-025/26) may demote to notes. Single Gradle build at a time: lock-ordered runs.

## Timeline and milestones

Group 1 (shared) then Groups 2 and 3 then Group 4 then Group 5; owner-hardware track in parallel.

## References

- Source PRD: `specs/261104-phase-2a-android-ui-auto-review/phase-2a-android-ui-auto-review-PRD.md`
- [../../AGENTS.md](../../AGENTS.md), [../../INTENT.md](../../INTENT.md)
