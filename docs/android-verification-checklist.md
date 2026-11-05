# Android verification checklist (evidence nobody has produced yet)

Everything below is **not proven**. It needs hardware, a Docker daemon, an enrolled fingerprint, a screen reader or GitHub-hosted CI, none of which the sessions that wrote the app had. This is finding R-009 of `specs/archive/261104-phase-2a-android-ui-auto-review`. Run each item, write the result in its **Result** line (date, device and API level, pass or fail, one line of output; no tokens, no real addresses, no key ids, no emails), and copy that line into the "R-009 evidence record" table of [the archived Android spec's status.md](../specs/archive/261104-phase-2a-android-ui/status.md). A failing item is a new finding and blocks archiving the spec. When an item passes, edit the "Not proven" lists in [technical-details.md](./technical-details.md#known-limitations-and-gaps), [product-details.md](./product-details.md) and the [README](../README.md) in the same change.

Rules for every run: one Gradle build at a time; never paste a board key, a pairing token or a real server address into a result; use the demo graph for any screenshot you keep (AGENTS.md).

Devices and setup used below:

```bash
adb devices                                  # note the serial; ANDROID_SERIAL=<serial> picks one when several are connected
adb shell locksettings set-pin 1234          # an emulator needs a screen lock for the live graph (remove: locksettings clear --old 1234)
./gradlew :androidApp:tasks --all | grep -i connected      # confirms the task names used here: connectedDebugAndroidTest, connectedAndroidDeviceTest per module
```

## 1. Live pairing against the pinned server

Needs Docker running, `adb`, `python3`, one unlocked device with a screen lock.

```bash
ANDROID_SERIAL=<serial> ./scripts/android-live-pairing.sh     # starts the pinned server, routes the device with adb reverse, waits for you to pair in the app
./scripts/android-live-smoke.sh                               # then: the checks that need no server
```

In the app: add the factory `http://localhost:3199`, Check, Continue, Pair, give the screen lock when asked, then press Enter in the terminal. The script approves the pairing challenge as the board user (the browser's part) and then checks the Keystore record, the app's files, the log and `bmgr`.

Expected: the factory shows **Connected** and its Inbox loads; the script prints `OK 1` (the key is only in the Keystore-wrapped record, no file and no log line holds the `pcp_board_` prefix) and `OK 2` with `bmgr` answering "Backup is not allowed"; `android-live-smoke.sh` ends without `FAIL`. Also pair once over Tailscale if you have it (R-012: an unvalidated network must still be tried, not shown as offline).

The script has never run. The sessions that wrote it read it again in Group 5 (`bash -n` passes; `shellcheck` was not installed) and found nothing they could prove wrong; the first run is also the check of the script. If a step fails, fix the script in its own commit and record what you changed.

Result: _not run_

## 2. Real key invalidation after fingerprint enrolment

A protected secret needs app lock **on**, so first pair a factory (item 1), then Settings, turn on **Lock app**, and pair again (or sign out and back in) so the board key is stored protected. Then add a fingerprint: on a phone, Settings, Security, Fingerprint, add one; on an emulator, Settings, Security, Fingerprint, then `adb -e emu finger touch 1` for each touch.

Expected: opening the app and reading the factory says the key was invalidated and offers **Pair again** (`Invalidated`); no crash, no "denied" loop, no unprotected read. After pairing again the factory works. Also expected on the emulator: removing the screen lock deletes the key and the factory reads as needing re-pairing (observed in ADR-123, finding 7a).

Also run the two lock-screen phase tests, which skip unless told which phase they are in:

```bash
adb shell locksettings set-pin 1234
./gradlew :shared:infrastructure:securestore:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.invalidationPhase=1
adb shell locksettings clear --old 1234
./gradlew :shared:infrastructure:securestore:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.invalidationPhase=2
```

Expected: both phase tests pass (not skipped).

Result: _not run_

## 3. The system BiometricPrompt on a destructive action, and the "another app authenticated a moment ago" check

On a device with an enrolled fingerprint, with a paired factory (item 1), do each of these and look at the screen and at the server:

| Action | Where | Expected |
|---|---|---|
| Pause an agent | Agent detail | the app's confirmation sheet (lock glyph), then the system prompt; cancel the prompt and the agent is still running on the server |
| Cancel a run | Run viewer | same; cancel the prompt and the run is still running |
| Reject an approval | Approval | same; cancel and the approval is still pending |
| A destructive decision option | Decision | same |
| Raise a budget | Agent detail, Set monthly budget (above the current amount, or zero) | the Save button shows the lock glyph and its spoken name says it asks the device owner; the system prompt appears once; cancel and the budget is unchanged. A **decrease** shows no lock and no prompt (DEC-1, confirmed by the owner 2026-11-05, ADR-141). |
| Raise or resume from the budget incident | Budget incident sheet | same |
| Remove a factory | Manage factories | confirmation sheet, then the system prompt |

Each time the system prompt must appear. On the iPhone, the budget raise must show Face ID or the passcode too (ADR-141 changed the iPhone as well).

The "another app authenticated a moment ago" check (R-001 acceptance 3): with app lock **on** and the board key protected, unlock the phone with your fingerprint, immediately open the app and trigger an action that reads the protected key (pull to refresh). Expected: the app's own system prompt still appears; the unlock a second earlier does not satisfy it. Repeat after authenticating in another app that uses `BiometricPrompt` (for example a banking app) within five seconds.

Also check, once, that a prompt stays open for a long time (a minute) while another part of the app works: Settings and the lock still respond (ADR-140), and that backgrounding the app while the prompt is up never leaves a spinner stuck forever.

Result: _not run_

## 4. Prompts at unlock and per refresh with the session cache (a device check of ADR-145)

With app lock **on** and a factory paired **after** it was turned on, the board token is a protected secret. Since ADR-145 the app keeps it in memory from the first protected read until the app goes to the background, a 401 or a re-pair, so one refresh no longer asks once per gateway call. The device store itself is still per-use (ADR-139). Count the prompts the person sees for each step and record them:

1. Cold start, then unlock: expected two prompts, the lock prompt and then one prompt for the first read (the unlock prompt cannot read the key yet; see ADR-145, "Unlock").
2. On the Inbox: one pull to refresh, one automatic 15 second refresh, opening an Agent detail, opening an Issue: expected **no further prompt** while the app stays in the foreground.
3. Press Home, return inside the "Lock after" time: expected one prompt on the first call (the background empties the cache), none for the lock itself.
4. Switch to a second factory that also has a protected key: one prompt for that factory.
5. Pause an agent (or any destructive action) with the cache warm: the confirmation sheet and the system prompt appear every time.
6. Re-pair a factory, then refresh: the next call uses the new key and the factory stays Signed in.

A screen recording helps (`adb shell screenrecord /sdcard/prompts.mp4`, then `adb pull`; delete it afterwards, it may show real data).

Expected: the counts above. Any extra prompt per refresh is a bug in the cache; an extra prompt at unlock is the known two (ADR-145). Not observed on a device yet.

Result: _not run_ (counts: unlock _, Inbox pull _, auto refresh _, Agent detail _, Issue _, return from background _, second factory _, destructive action _)

## 5. TalkBack

```bash
adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure accessibility_enabled 1
```

Walk with swipes and double taps: Inbox, Decision, Approval, Budget incident, Add and Edit factory, Pairing (the first-run Pair step), Lock. Check specifically:

- every **text field** (address, name, device name, a note, the Issue comment bar, the Work search, a budget amount, a free-text answer) announces its label, then what has been typed, and the validation problem when there is one (R-025, `FormField`);
- the **banners** announce themselves without a touch: a feedback banner after an action, the Inbox Undo after a dismiss or snooze, an error banner, "Copied" in Diagnostics, the issue line in a note sheet (R-026, Polite live regions);
- every control with a lock glyph announces that it asks the device owner (`Modifier.asksDeviceOwner`: Pause on an Agent row and on the detail, Reject, Save budget on a raise, Submit on the budget incident), and the confirmation sheets of Cancel run, a destructive decision option and Remove say what follows (R-036);
- a card, a row and a pill read as one sentence in a sensible order; status is never only a colour;
- Reorder in Manage has the "Move up" and "Move down" actions.

Turn TalkBack off afterwards: `adb shell settings put secure enabled_accessibility_services ""`.

Result: _not run_ (the semantics tree is audited under Robolectric, `AccessibilityTest`, ADR-134; the framework's own checks and the spoken output are not)

## 6. StrongBox

```bash
adb shell pm list features | grep -i strongbox      # android.hardware.strongbox_keystore: present only on devices that have it
```

No shipped test reports whether a key actually lives in StrongBox (found while writing this checklist; the store asks for StrongBox first and falls back to the TEE, `AndroidKeystoreBackend`). On a device that lists the feature, add this to a scratch instrumented test (do not commit it), pair a factory, and read the key's `KeyInfo`: `SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo` and check `securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX` (API 31 and later; `isInStrongBox` below that). Expected: StrongBox on a device that has it, or the recorded fact that the TEE fallback was used and why. If you want it kept, turn the scratch test into a real device test in its own test-first commit.

Result: _not run_

## 7. API 28 and API 33

Create the images once (arm64 on Apple silicon, x86_64 elsewhere):

```bash
sdkmanager "system-images;android-28;google_apis;arm64-v8a" "system-images;android-33;google_apis;arm64-v8a"
avdmanager create avd -n Pixel_API28 -k "system-images;android-28;google_apis;arm64-v8a" -d pixel_6
avdmanager create avd -n Pixel_API33 -k "system-images;android-33;google_apis;arm64-v8a" -d pixel_6
emulator -avd Pixel_API28 -no-window -no-audio &     # then wait for adb, set a PIN, and run the commands below
```

On each: `adb shell locksettings set-pin 1234`, then

```bash
./gradlew :androidApp:connectedDebugAndroidTest
./gradlew :shared:infrastructure:securestore:connectedAndroidDeviceTest :shared:infrastructure:platform:connectedAndroidDeviceTest :shared:infrastructure:persistence:connectedAndroidDeviceTest :shared:umbrella:connectedAndroidDeviceTest
./scripts/android-release-smoke.sh live
```

Expected on **API 28**: the app launches, adds a factory in demo and live mode; a **protected** secret (app lock on) is **refused**, never stored unprotected (per-use keys need API 30, a protected write fails; ADR-139): note exactly what the person sees, because no test shows the wording; the prompt takes weak biometrics or the screen lock (ADR-123); record which Keystore device tests fail or skip. Expected on **API 33**: everything passes; **predictive back**: the manifest sets `android:enableOnBackInvokedCallback="true"` (`AndroidManifestRulesTest`; on the built APK `aapt2 dump xmltree --file AndroidManifest.xml <apk> | grep -i BackInvoked`), and on API 33 the developer option "Predictive back animations" (or `adb shell settings put global enable_back_animation 1`) shows the back preview on a pushed screen and a sheet; notification and permission behaviour: the app asks for no permission, so none should appear. Record the actual test counts from `androidApp/build/outputs/androidTest-results` (JUnit XML).

Result: API 28 _not run_; API 33 _not run_

## 8. A Pixel 6 and a Samsung phone, with the benchmark numbers

On a Pixel 6 and on one Samsung phone (screen lock set, USB debugging on): run the connected tests and the demo flows as in item 7, then the benchmark on the **Pixel 6** only (it is the target device):

```bash
PAFM_BENCHMARK_ALLOW_EMULATOR=0 ./scripts/run-benchmarks.sh
```

Expected: cold start to the Inbox with content under 1.5 s median of 10, and at most 5% of frames over their deadline in the 200-card Inbox fling (R-008). For comparison the Pixel_9_Pro emulator (API 37, arm64, host GPU, `run-benchmarks.sh`, 5 flings in two directions, about 2530 frames) gave 40.7% of frames over their deadline (CPU P50 7.8 ms, P90 25.9 ms) after the R-008 fixes and 52.4% before them, and a cold start of 315.4 ms (median of 10). If the Pixel 6 meets the budget the finding closes; if it does not, R-008 stays open and the next profile starts from the Pixel 6 trace (not the emulator's).

On the Samsung phone also check: the system prompt (item 3), Recents shows a blank thumbnail while locked (`FLAG_SECURE`), dark theme, font scale 2.0 and the back gesture.

Result: Pixel 6 _not run_ (cold start _ ms; frames over deadline _ %); Samsung _not run_

## 9. The first hosted `android.yml` run

Open the pull request and let the workflow run on GitHub.

```bash
gh run list --workflow android.yml
gh run view <id> --log-failed
```

Expected: every job green: `verify` (lint, unit tests, Roborazzi verify, `assembleRelease`), `emulator` (the device tests on API 34 x86_64), the benchmark gate and the release smoke. The workflow sets `PAFM_ALLOW_DEBUG_SIGNED_RELEASE=1` because the release job is a smoke build signed with the debug key; `verifyReleaseSigning` (R-015) would otherwise fail a CI release with no upload key, and that is intended. The first run is also the check of the workflow file itself (its pinned actions, KVM step and emulator image have never run). If it fails, fix the workflow in its own commit and record what you changed. Note the hosted emulator is API 34 x86_64, not the local API 37 arm64.

Result: _not run_ (run id _, jobs _)

## 10. The iPhone UI-test caveat ("iPhone Duo")

`./scripts/ios-test.sh` picks the first available iPhone simulator. On the machine that wrote this, that is a custom device type "iPhone Duo" (iOS 27.2) on which `AgentsUITests` fails 3 of 3 (the tab bar never appears within 30 s), at the commit before the review fixes as well as after. The same class passes on iPhone 17. The cause is not established. Run the iPhone gates on a standard device:

```bash
xcrun simctl list devices available | grep iPhone
IOS_SIMULATOR_ID=<udid of an iPhone 17> ./scripts/ios-test.sh
```

Expected: 378 Swift Testing unit tests pass and all XCUITests pass except the non-blocking `testAccessibilityAudit*` set (16 failed on iPhone 17 at the last run, identical before and after the review fixes; ADR-094). Record the counts you see.

Result: _not run_

## Where the open items are decided

| Open item | Where | Owner decision needed |
|---|---|---|
| Prompts at unlock and per refresh with the cache | item 4, [ADR-145](./architectural-decision-record.md#adr-145-the-board-token-is-held-in-memory-per-factory-for-the-foreground-unlock-session-resolves-adr-144-option-c) | decided (option C, 2026-11-05); only the device counts and the one-prompt-at-unlock follow-up are open |
| A budget raise asks the device owner | item 3, ADR-141 (DEC-1) | decided: kept, confirmed by the owner 2026-11-05 |
| An unvalidated network counts as online | item 1 (Tailscale), ADR-142 (DEC-2) | decided: kept, confirmed by the owner 2026-11-05 |
