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

- Group 5 (docs): predictive back was fixed in code (the manifest attribute, a Konsist rule per variant, `tools:targetApi="33"` for lint) rather than by rewording. The deep-link claim was reworded: the scheme is not registered. `konsistTest` did not list the documents its documentation rules read as inputs, so a docs-only edit left it UP-TO-DATE (the R-014 bug class); fixed with the inputs and a red case in `scripts/test-konsist-inputs.sh`. ADR-143 (attachments, design only) and ADR-144 (prompts per refresh, an open OWNER decision, not implemented) are new. No hosted issues were filed for the R-038 follow-ups (an external action). T-009 is packaged, not produced.

## Round 2: Inbox scroll jank, 2026-11-06 (R-008 follow-up, branch perf/inbox-scroll-jank-round-2)

Emulator: Pixel_9_Pro AVD, API 37, arm64, host GPU, `benchmark` build type (R8 plus the baseline profile), `flingTwoHundredInboxCards` (209 cards, 5 flings down and 5 up, about 2520 frames per run). "Over deadline" is the share of frames with `frameOverrunMs` above 0 over the 5 iterations of a run (the measure the earlier 40.7% used). Numbers are an emulator regression guard; the owner measures on a Pixel (docs/android-verification-checklist.md, item 8). Traces: Perfetto traces the benchmark itself records, read with `trace_processor`.

**Where the frame time goes (measured on the unchanged main, 5 traces)**

| Evidence | Value |
|---|---|
| UI thread, `Choreographer#doFrame`, average | 3.0 to 4.6 ms |
| Compose `Recomposer:animation` + `AndroidOwner:measureAndLayout`, total over a 52 s run | about 150 ms |
| UI frames over 12 ms (58 of 509 in the sampled trace): share of their time in `postAndWait` (waiting for the RenderThread) | 1007 of 1012 ms |
| FrameTimeline jank type, share of frames | Buffer Stuffing about 92%; App Deadline Missed 2% to 3% |
| RenderThread `Drawing` average | 10.3 to 11.9 ms, of which flush commands about 5.0 ms and `eglSwapBuffers`/`dequeueBuffer` about 4 ms |
| Text atlas drawing | about 1.2 ms a frame (about 12 ops) |
| Offscreen layer flushes | about 110 frames, 0.6 to 0.7 s a run (look like the stretch overscroll at the list ends; not changed) |

So the frames are late because the RenderThread and the buffer queue behind it are slow on the emulator's host GPU, not because composition is slow. Recomposition during a fling is nil already (`InboxClockTickTest`; `InboxScrollCompositionTest`: a whole down-and-up scroll of 40 cards composes each card at most twice). What the app controls is the work per card that scrolls in.

**Fixes, with the per-card cost (composables one approval card composes on first composition, with the theme around it, counted by `InboxCardCostTest` with the `CompositionTracer`)**

| Step | Composables | LaunchedEffects | Roborazzi (73 baselines) |
|---|---|---|---|
| main | 210 | 9 | n/a |
| Web door only for cards that are one | 208 | 9 | unchanged |
| `AppButton` drawn directly (ADR-145), no Material Button/Surface | 133 | 3 | unchanged (the modifier order copies the Surface's; a first version that clipped first changed 41 baselines by at most 0.027% of pixels, corner anti-aliasing) |

| Frames over deadline, median of the 3 runs (min to max) | Run 1 | Run 2 | Run 3 | Median |
|---|---|---|---|---|
| Baseline, main | 38.3% | 47.6% | 43.2% | 43.2% (38.3 to 47.6) |
| After both fixes, profile not regenerated | 48.6% | 44.4% | 51.5% | 48.6% (44.4 to 51.5) |
| After both fixes, baseline profile regenerated (final) | 44.6% | 47.7% | 47.8% | 47.7% (44.6 to 47.8) |

The per-fling spread inside one run is 26% to 72%, much wider than any difference between the rows, so the per-fix frame change is below the noise floor and was not measured fix by fix; the two fixes are shown as the composable counts above. On the emulator the result is no improvement (a small regression cannot be ruled out), and the budget of 5% is not met (frameOverrunMs P95 about 22 ms to 24 ms). App Deadline Missed stayed at 8 to 15 frames of about 505 (2% to 3%). The final `run-benchmarks.sh` also gave a cold start to the Inbox with content of 350 ms median of 10 (338.8 ms without the profile; the earlier 315 ms is within the emulator's run to run spread, 261 to 477 ms). The CPU P90 of the frame is 33 ms on main today against the 25.9 ms recorded in round 1; this was not explained.

**Measured and not done**

- Hoisting the per-card `ActionRow`/`CardButtons` `buildList` lambdas and the `CustomAccessibilityAction` lists into `remember`: the accessibility list was already remembered; a card in a fling is composed once and not recomposed (test above), so a `remember` only adds a slot to the one composition it gets. The Compose compiler also remembers lambdas whose captures are stable.
- Stretch overscroll at the list ends (offscreen layers): a platform behaviour and a look; not changed.
- A Pixel profile: owner track (R-009). If the Pixel's UI thread is the limit, the next step is the card's remaining structure (`SwipeToDismissBox` anchors per card, eight `Text` nodes) from that trace.

## Lessons learned

- A change that makes a list lazy breaks every connected test that taps a row below the fold; Robolectric screens are tall enough to hide it. The connected flows are the only check, so run them after UI-structure changes (R-032 broke two of 14).
