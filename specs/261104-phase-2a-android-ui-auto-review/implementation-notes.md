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

## Lessons learned

[TBD]
