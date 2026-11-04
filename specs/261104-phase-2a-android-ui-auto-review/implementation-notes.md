# Implementation notes: Phase 2a Android review fixes

**Date:** 2026-11-04

Purpose: record decisions, edge cases and deviations while implementing. Update after every task; note commit hashes for each finding.

## Technical decisions

DEC-1 (R-004) and DEC-2 (R-012) are run assumptions the owner may overturn.

## Edge cases and solutions

- R-001: a protected key is per use; `Cipher.init` succeeds without authentication, so seal and open return the unused cipher and do no crypto until `finishSeal` or `finishOpen`. A key from the 5 second design reads as `Invalidated` (the factory pairs again) and a protected write replaces it. The Keystore authorisation of the real prompt (an enrolled fingerprint, another app authenticating a moment before) is not provable on the emulator: only the key parameters (`KeyInfo` validity 0) and the old-key refusal are device tests.
- R-002: the prompt runs with the store lock free. A read re-reads the record after the prompt and answers Denied when the bytes changed; a write that overlapped a delete (a counter under the lock) is Failed. One prompt at a time through a second mutex.
- R-004: zero is raise-class; a decrease and the same amount ask nothing. The Android lock glyph, `AgentDetailScreenTest` (now one prompt per raise) and the Roborazzi baselines are Group 2.
- R-012: the Tailscale-only case is for the R-009 evidence.
- R-019: the iPhone now uses the shared rules for the four moved rules. `InboxCardLink.of` and `cardContext` were not moved.

## Deviations from plan

- The PRD text for R-002 re-checks `host.current() === activity` in the posted lambda; `AuthHost.current()` is suspending, so the check is `canShowNow()` (activity started, state not saved) behind the `PromptScreen` seam.
- Most red tests are runtime red; where the fix adds new API (R-004 constructor and flag, R-007 `isWorking`, R-019 members) a small `refactor:` commit adds the seam first or the red run is a compile error, stated in the commit message.
- R-011 protected contract cases pass at once on the fake (R-001 had already made it per use); the red evidence for R-001 is the single-use fake test and the device KeyInfo test.
- The DEC-1 and DEC-2 ADR entries (ADR-141, ADR-142) and the ADR-123 correction (ADR-139) were written with their fixes instead of in Group 5; Group 5 only needs to check them.
- The full `./scripts/ios-test.sh` run on the default simulator (iPhone Duo) failed every UI test at launch; the iPhone 17 simulator passes. iOS verification used the unit tests (378) and the Agents, AgentDetail and Budget UI test classes on iPhone 17.

## Lessons learned

[TBD]
