# Implementation notes: Phase 2a Android review fixes

**Date:** 2026-11-04

Purpose: record decisions, edge cases and deviations while implementing. Update after every task; note commit hashes for each finding.

## Technical decisions

DEC-1 (R-004) and DEC-2 (R-012) are run assumptions the owner may overturn.

## Edge cases and solutions

- R-001: a protected key is per use; `Cipher.init` succeeds without authentication, so seal and open return the unused cipher and do no crypto until `finishSeal` or `finishOpen`. A key from the 5 second design reads as `Invalidated` (the factory pairs again) and a protected write replaces it. The Keystore authorisation of the real prompt (an enrolled fingerprint, another app authenticating a moment before) is not provable on the emulator: only the key parameters (`KeyInfo` validity 0) and the old-key refusal are device tests.
- R-002: the prompt runs with the store lock free. A read re-reads the record after the prompt and answers Denied when the bytes changed; a write that overlapped a delete (a counter under the lock) is Failed. One prompt at a time through a second mutex.
- R-004: zero is raise-class; a decrease and the same amount ask nothing. The Android lock glyph, `AgentDetailScreenTest` (now one prompt per raise) and the Roborazzi baselines are Group 2.
- R-009 manual evidence added by Group 1 (device with a secure lock screen, app lock ON, enrolled fingerprint): (a) a protected read prompts every time and an unlock or another app's authentication a moment earlier does not satisfy it (R-001 acceptance 3); (b) the authorised-cipher round trip (`finishOpen` with the cipher the prompt returns) works with a real fingerprint; (c) count the prompts during one refresh with app lock on: `StoredCredentials.call` reads the protected board token for every gateway call, so each call is a prompt now (ADR-139 consequences, an owner decision); (d) a budget raise shows one prompt and a cancel changes nothing (R-004); (e) pairing and browsing over a network the system cannot validate (R-012).
- R-012: the Tailscale-only case is for the R-009 evidence.
- R-019: the iPhone now uses the shared rules for the four moved rules. `InboxCardLink.of` and `cardContext` were not moved.

## Deviations from plan

- The PRD text for R-002 re-checks `host.current() === activity` in the posted lambda; `AuthHost.current()` is suspending, so the check is `canShowNow()` (activity started, state not saved) behind the `PromptScreen` seam.
- Most red tests are runtime red; where the fix adds new API (R-004 constructor and flag, R-007 `isWorking`, R-019 members) a small `refactor:` commit adds the seam first or the red run is a compile error, stated in the commit message.
- R-011 protected contract cases pass at once on the fake (R-001 had already made it per use); the red evidence for R-001 is the single-use fake test and the device KeyInfo test.
- The DEC-1 and DEC-2 ADR entries (ADR-141, ADR-142) and the ADR-123 correction (ADR-139) were written with their fixes instead of in Group 5; Group 5 only needs to check them.
- `./scripts/ios-test.sh` is NOT green on its default device: it picks the first iPhone, which here is "iPhone Duo", and on it AgentsUITests fails (3 of 3, "Waiting 30s for TabBar" then an assertion; re-run at HEAD) while the same class passes on iPhone 17. The cause is not established (a device-specific layout is a guess) and was not checked against the code before this group. On iPhone 17 (`IOS_SIMULATOR_ID`) the full script at HEAD gave: 378 Swift Testing unit tests passed, 116 XCUITests passed, 16 failed, all 16 named `testAccessibilityAudit*` (the non-blocking audit set of ADR-094, "Contrast failed for SwiftUI.AccessibilityNode", including ShellUITests whose screen this group did not touch); every functional UI test passed.
- iPhone UI-test failures (Group 4, Scope B): NOT regressions. Baseline c151237 (the commit before Group 1) on iPhone 17: 132 XCUITests, 116 passed, 16 failed, all `testAccessibilityAudit*`: identical in count and class to HEAD (116 and 16). On "iPhone Duo" `AgentsUITests` fails 3 of 3 at the baseline too (about 35 s each, the tab bar never appears). "iPhone Duo" is a custom device type (`iPhone19,4`, runtime iOS 27.2) that only exists on this machine and `ios-test.sh` picks it first because it sorts first; it is environment-specific. No iosApp change was made. Recorded in `docs/technical-details.md`.
- R-035 (D-11) is recorded as a known deviation: the first Android Keystore, `RecordFiles`, `BiometricPromptBiometrics` and `AuthHost` code landed with its tests in one `feat` commit, and the 2a-5 screens were written before their tests, so red-first cannot be proven from that history. From R-001 on, security code is committed test-first: R-001 (`d1343a2`, `fc95a15` before `c445d44`), R-002 (`5c35532`, `cdce680` before `b6a2a25`) and R-004 (`a085268` before `1d7ed1a`) each show a `test:` commit that fails before the `fix:` commit. Nothing is enforceable in Konsist or CI for this: a history rule would only be a check of commit messages, so it stays a process rule in AGENTS.md.
- Group 4 red evidence: R-016, R-017, R-015 and R-031's glyph rule were red in one forced `konsistTest` run (6 failed of 117) before their fixes; R-014 is red in `scripts/test-konsist-inputs.sh` (the task stayed UP-TO-DATE after a catalog-only change) and green after (catalog, proguard and app build file each re-run it); R-020 is a compile-red (the seam) then green, and fails when `run.isLive -> RUNNING_REFRESH_SECONDS` is changed to `0`; R-030's zero-size and sheet audits were red first; the Undo "second dismiss" presentation test fails only when both the timer cancel and the itemId guard are removed (each alone is covered by the other, so one mutation survives by design); swipe tests fail when a swipe direction is enabled or disabled wrongly; R-031 a one-label colour change failed 12+ baselines with threshold 0.
- R-015: the PRD's guard is "opt-in or CI unset"; a local build (CI unset) is allowed and warns, CI needs `PAFM_ALLOW_DEBUG_SIGNED_RELEASE=1` (set in `android.yml`). The check is a task wired to `packageRelease`, so lint and debug builds never need it. A half-set upload key fails everywhere.
- R-030: the audit exempts only Material's `ModalBottomSheet` scrim ("Close sheet") and drag handle ("Drag handle"), which have a name and a click but no role and no hook to add one. Demoted, not done: a large-font audit, an error-state audit, a pseudo-locale test (only an RTL Inbox audit was added), a Viewer-role swipe test (the gating is tested through offline, which sets the same `actionsDisabledReason`), and the Run viewer swipe test (the Run viewer has no swipe).

## Lessons learned

[TBD]
