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

Result (2026-11-06, Pixel_9_Pro emulator API 37 arm64, `main` 0693434; second run on `origin/worktree-adr144-unlock-session-token` 1bdb70c plus the script fix): **passed on the emulator, with one script bug and one app bug found.**

- Docker Desktop is installed but `open -a Docker` and `docker desktop start` never produced a daemon in this session, so the server ran under **podman 6.1.0** (`podman machine init` and `start`, about 2.8 GB of VM image; the pinned `ghcr.io/paperclipai/paperclip:2026.1023.0` image is 6.61 GB unpacked) with `DOCKER_HOST` set to the podman socket; `docker compose` and the script's `up`, `invite` and `down` worked unchanged. `bash -n` passes.
- `scripts/android-live-pairing.sh` ran end to end on both commits (bootstrap invite, first board user, build, install, `adb reverse`, the approver answered `200` for the app's challenge). In the app (driven with `adb input` and `uiautomator`): Check address showed "Server found / private network", Continue, Open approval page (Chrome's first-run had to be dismissed once), the approver approved it, the real system prompt "Protect the key for this factory" appeared and the fingerprint (`adb -e emu finger touch 1`) was accepted, and Factories showed the factory **Connected** with "Board key expires in 30 days".
- **Script bug (fixed, commit 305aedb):** the `run-as ... sh -c '...'` checks lost their quoting through `adb shell`, so `find` listed every file and `grep` ran with no arguments and found nothing: "OK 1" passed vacuously on the first run. The fix passes one string to `adb shell` and adds a positive control (a `grep` for the SQLite header must hit). `scripts/android-live-smoke.sh` had the same `grep`; fixed in the same commit. After the fix the run printed `OK 1` with exactly one record (`no_backup/secrets/instance...boardToken.rec`) and `OK 2` with `bmgr` answering "Backup is not allowed". `android-live-smoke.sh` printed `OK 1` to `OK 4` (no board key prefix in 13 app files or logcat, `allowBackup=false` and `bmgr` refused, the release manifest allows no cleartext; the stored-http refusal itself is `AndroidLiveGraphDeviceTest`, which passed).
- **App bug (finding F-1, fixed by the company gate, ADR-150; the original observation):** against a fresh server with no company, the Inbox and Factory tabs show "Loading..." forever and the app makes no request after the pairing calls (server log checked), while Factories says Connected. After creating a company on the server and pulling to refresh on Factories, the company appeared and the Inbox loaded ("Nothing needs you"). Same on `main` and on the ADR-144 branch. It needs an empty or error state and a refresh of the access list on start. Root cause not established.
- **Still the owner's:** pairing over Tailscale (R-012); a real browser sign-in instead of the script's approver. The contract smoke (`scripts/contract-smoke.sh`) was not run.

## 2. Key behaviour after fingerprint enrolment (a new fingerprint must NOT invalidate the key)

A protected secret needs app lock **on**, so first pair a factory (item 1), then Settings, turn on **Lock app**, and pair again (or sign out and back in) so the board key is stored protected. Then add a fingerprint: on a phone, Settings, Security, Fingerprint, add one; on an emulator, Settings, Security, Fingerprint, then `adb -e emu finger touch 1` for each touch.

Expected (decided 2026-11-06, F-2): on Android the key policy is `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` (PIN fallback), so enrolling a **new fingerprint does not invalidate the key** and the factory keeps working; this differs from the iPhone, where `.biometryCurrentSet` does invalidate. Check that: no crash, no "denied" loop, no unprotected read. What does invalidate the key is removing the screen lock. Also expected on the emulator: removing the screen lock deletes the key and the factory reads as needing re-pairing (observed in ADR-123, finding 7a).

Also run the two lock-screen phase tests, which skip unless told which phase they are in:

```bash
adb shell locksettings set-pin 1234
./gradlew :shared:infrastructure:securestore:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.invalidationPhase=1
adb shell locksettings clear --old 1234
./gradlew :shared:infrastructure:securestore:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.invalidationPhase=2
```

Expected: both phase tests pass (not skipped).

Result (2026-11-06, Pixel_9_Pro emulator API 37, `main` 0693434 plus this PR's test fix): **the expected outcome ("Invalidated after a new fingerprint") does not happen for the shipping key; this is a new finding (F-2).**

- Fingerprints enrol on the emulator: set the PIN, `am start -a android.settings.FINGERPRINT_ENROLL`, enter the PIN, tap MORE then I AGREE, then `adb -e emu finger touch N` about 12 times (a different N for each extra fingerprint; the screen is FLAG_SECURE so `screencap` is black, drive it with `uiautomator dump`). `dumpsys fingerprint` showed `count` 1, 2, 3.
- `connectedAndroidDeviceTest -P...invalidationPhase=1` passed (phase 1 not skipped), but **Gradle uninstalls the test APK afterwards, which deletes its Keystore keys, so a later phase 2 passes vacuously**. Run the phases by hand: `adb install -r -t shared/infrastructure/securestore/build/outputs/apk/androidTest/securestore-androidTest.apk`, then `adb shell am instrument -w -e invalidationPhase 1 -e class com.stainedhead.paperclipmobile.infrastructure.securestore.KeystoreInvalidationDeviceTest com.stainedhead.paperclipmobile.shared.infrastructure.securestore.test/androidx.test.runner.AndroidJUnitRunner`, change the lock or the fingerprints, then the same with `invalidationPhase 2`.
- After enrolling a **second** fingerprint, phase 2 **failed**: `got NeedsAuthentication`. The key was still usable (the test only accepts `KeyMissing` or `Invalidated`, which is what removing the PIN gives).
- A scratch instrumented test (not committed) made two per-use keys, `AUTH_BIOMETRIC_STRONG` only and `AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL` (the shipping `AndroidKeystoreBackend` setting), both with `setInvalidatedByBiometricEnrollment(true)`. After enrolling a **third** fingerprint the biometric-only key threw `KeyPermanentlyInvalidatedException` and the shipping combination was still `usable`. So on this emulator a key that allows the screen lock as an authenticator is not invalidated by a new fingerprint, while the emulator does invalidate biometric-only keys: it is the key configuration, not the emulator.
- Meaning: the `Invalidated` and "Pair again" path on enrolment is unreachable with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`; a person who knows the PIN can add a fingerprint and the app keeps working (they could already use the PIN). **Owner decision (2026-11-06): keep the PIN fallback.** A new fingerprint does not invalidate the Android key; FR-015, the ADR and the manual phase 2 test text are to be corrected to say so (ADR-149 records it; FR-015, the docs and the phase 2 test text now say so, and `KeyPolicyTest` pins the authenticators). No app change.
- Not re-run: removing the screen lock deleting the key (observed in ADR-123).
- **Still the owner's:** the same on a real Pixel, to confirm the platform behaves like the emulator.

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

Result (2026-11-06, API 37 emulator with an enrolled fingerprint; `main` 0693434 for the first launch and pairing, ADR-144 branch 1bdb70c for the rest): **partly observed.**

- The real system `BiometricPrompt` appears on the emulator and accepts `adb -e emu finger touch 1`. A demo launch never shows it: the demo graph uses scripted fakes (`demo_biometrics`), so the real prompt needs the live graph (the debug app plus the pairing server).
- **Pause an agent (Agent detail):** observed: the confirmation sheet ("Pause Bot?", "Confirm with your fingerprint, face or screen lock"), then the system prompt "Confirm this action". Cancelling it left the agent `idle` on the server (checked with `GET /api/agents/{id}`); a second try accepted with the fingerprint and the server showed `paused`. The Pause control's accessibility name reads "Pause. Asks for your fingerprint, face or screen lock".
- **"Another app authenticated a moment ago":** screen off and on, PIN entered on the lock screen, app force-stopped and launched about 2 s later: the app's own prompt still appeared. `KeystorePerUseDeviceTest` (validity 0 s) passes on API 33 and 37. **Not done:** authenticating in a second app's `BiometricPrompt`, and a fingerprint (not PIN) lock-screen unlock.
- **Not done on the emulator (need server data or a person):** Cancel run, Reject approval, destructive decision option, raise budget, budget incident, Remove factory (the test server had no approvals or runs), a prompt left open for a minute, and backgrounding while a prompt is up.
- **Wording bug (F-3, fixed, ADR-151):** the key prompt's subtitle read `call paperclip` (lower case, an internal string): `StoredCredentials.kt`, `REASON`. It now comes from `strings.xml` (`biometric_key_read_reason`, "Read the board key to talk to the factory"). The unlock prompt and the Factories refresh still pass their own internal strings (F-3b).
- **Still the owner's:** the rows above that were not run, on a Pixel and a Samsung phone with a real fingerprint sensor.

## 4. Prompts at unlock and per refresh with the session cache (a device check of ADR-145)

With app lock **on** and a factory paired **after** it was turned on, the board token is a protected secret. Since ADR-145 the app keeps it in memory from the first protected read until the app goes to the background, a 401 or a re-pair, so one refresh no longer asks once per gateway call. The device store itself is still per-use (ADR-139). Count the prompts the person sees for each step and record them:

1. Cold start, then unlock: expected **one** prompt (the key prompt is the unlock, ADR-146). Repeat with a factory paired before app lock was turned on: one plain unlock prompt, no key prompt. Cancel the key prompt: the app stays locked and Unlock asks again.
2. On the Inbox: one pull to refresh, one automatic 15 second refresh, opening an Agent detail, opening an Issue: expected **no further prompt** while the app stays in the foreground.
3. Press Home, return inside the "Lock after" time: expected one prompt on the first call (the background empties the cache), none for the lock itself.
4. Switch to a second factory that also has a protected key: one prompt for that factory.
5. Pause an agent (or any destructive action) with the cache warm: the confirmation sheet and the system prompt appear every time.
6. Re-pair a factory, then refresh: the next call uses the new key and the factory stays Signed in.
7. Unlock with the PIN or pattern instead of the fingerprint, then refresh twice: the second refresh must not prompt (a system sheet must not look like the app going to the background and empty the cache).

A screen recording helps (`adb shell screenrecord /sdcard/prompts.mp4`, then `adb pull`; delete it afterwards, it may show real data).

Expected: the counts above. Any extra prompt per refresh is a bug in the cache; an extra prompt at unlock is the known two (ADR-145).

Result (2026-11-06, API 37 emulator, tested on the PR #11 head **1bdb70c** with this PR's script fix cherry-picked; the merged `main` (dc4f18e) has no production-code difference from it (the diff of 1bdb70c to origin/main is empty under `shared/application`, `shared/infrastructure`, `shared/presentation`, `shared/umbrella/src/commonMain` and `androidApp/src/main`), so the counts describe the merged code; prompts counted from logcat lines `BiometricScheduler: [Finishing] ... FingerprintAuthenticationClient ... owner=com.stainedhead.paperclipmobile` and by answering each prompt through `uiautomator`): **the cache behaves as ADR-145 and ADR-146 describe.**

Counts with app lock on and a factory paired after app lock was on: cold start then unlock **1**; Inbox open **0**; Inbox pull **0**; Factory auto refresh (15 s) **0**; Agents list and Agent detail **0**; Issue open **0**; Home then return then first pull to refresh **1** (the background emptied the cache; no prompt for the lock itself); that prompt answered with the **PIN** ("Use PIN"), then two more refreshes **0**; Pause agent (sheet, then system prompt) **1 each time**, also with the cache warm. Pairing itself showed **2** prompts (protect the key, then one read). Cancelling the unlock prompt: a new prompt appeared at once (the UI is hidden under FLAG_SECURE, so whether the app showed its Unlock screen in between could not be seen). On `main` (0693434, no cache) the pairing and first start showed at least four consecutive prompts ("Unlock Paperclip", "Protect the key", then two "call paperclip" reads).

**Not run:** a second factory with its own protected key; a factory paired before app lock was turned on; the same on a real device. (The Inbox of a factory without companies hung, see item 1, F-1; the counts above were taken after the company existed.)

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

Result (2026-11-06): **partly; the spoken output was not verified.** TalkBack (`com.google.android.marvin.talkback`) is present on the API 33 and API 37 google_apis images and not on API 28. Enabling it with the two `settings put secure` commands worked: it announced itself, showed its notification-permission dialog, and its green focus ring moved with swipes (screenshots reviewed: it landed on the Inbox filter row and on the dialog's buttons). TalkBack only logs speech when its text-to-speech is not ready, so utterances cannot be read from logcat; its "Display speech output" overlay (TalkBack settings, Visual) exists but was not switched on. Only the accessibility tree was read (`uiautomator dump`): the Inbox cards expose one sentence ("Critical. Decision, PAP-1182. Ship the billing migration to production?. CTO proposes 3 options..."), and the Pause control exposes "Pause. Asks for your fingerprint, face or screen lock". Not walked: Decision, Approval, Budget incident, Add and Edit factory, Pairing, Lock, text-field label and error announcements, live-region banners, Reorder actions. Note: `adb shell input swipe` in the Inbox also swiped real cards away (the app's own dismiss), so scripted swipes are a poor TalkBack driver. **Still the owner's:** the whole list above, with sound, on a real phone.

## 6. StrongBox

```bash
adb shell pm list features | grep -i strongbox      # android.hardware.strongbox_keystore: present only on devices that have it
```

No shipped test reports whether a key actually lives in StrongBox (found while writing this checklist; the store asks for StrongBox first and falls back to the TEE, `AndroidKeystoreBackend`). On a device that lists the feature, add this to a scratch instrumented test (do not commit it), pair a factory, and read the key's `KeyInfo`: `SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo` and check `securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX` (API 31 and later; `isInStrongBox` below that). Expected: StrongBox on a device that has it, or the recorded fact that the TEE fallback was used and why. If you want it kept, turn the scratch test into a real device test in its own test-first commit.

Result (2026-11-06): `pm list features | grep -i strongbox` printed nothing on the API 37 and API 28 emulators (only `android.hardware.fingerprint`), so the emulator has **no StrongBox** and the store runs its fallback path; that is what every emulator run above exercised. The `KeyInfo.securityLevel` read was not done. **Still the owner's:** the scratch `KeyInfo` read on a Pixel (it has StrongBox).

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

Result (2026-11-06, Pixel_API28 and Pixel_API33 google_apis arm64 AVDs created for this; the system images unpack to 2.6 GB (API 28) and 8.2 GB (API 33); the `brew` `sdkmanager` installs under `/opt/homebrew/share/android-commandlinetools`, so the two `system-images/android-NN` directories were symlinked into `~/Library/Android/sdk`, and `avdmanager create avd` printed a devices.xml error yet created both AVDs). `main` 0693434 plus this PR's commits, PIN 1234 set on each.
- **API 28:** the first run of the four module device tests had `KeystorePerUseDeviceTest` failing 2 of 2 (a protected write is refused below API 30, as designed, and the second test calls an API 30 method: `NoSuchMethodError`). That is a test gap, fixed test-first in 4e5b03c (gate both on API 30, add a test that the protected write is refused and leaves no key). After the fix: securestore 18 tests, 0 failures, 5 skipped; platform 6; persistence 5; umbrella 9; `:androidApp:connectedDebugAndroidTest` 14; all 0 failures. The live release APK launches and shows "Unlock Paperclip / Enter your device PIN to continue" (the system credential screen). `android-release-smoke.sh live` **failed** first (an info-level `java.lang.ClassValue` miss in `MainActivity.<init>`, not a crash; then the expected-screen text), was fixed in d02e30b, then passed; it still passes on API 37 and 33. **Not observed:** the wording a person sees when app lock cannot protect a key on API 28 (only the device test: refused, nothing stored); the demo flows by hand (the 14 connected tests are the demo flows).
- **API 33:** securestore 18 tests, 0 failures, 4 skipped; platform 6; persistence 5; umbrella 9; androidApp 14, 0 failures; the release smoke passed. `enableOnBackInvokedCallback=true` is in the built release manifest. **Not observed:** the predictive back preview (needs eyes), the permission and notification behaviour.
- API 37 for comparison: securestore 17, platform 6, persistence 5, umbrella 9, androidApp 14, 0 failures.
- **Still the owner's:** the predictive back preview on an API 33 or newer phone; the API 28 app-lock wording on a real device.

## 8. A Pixel 6 and a Samsung phone, with the benchmark numbers

On a Pixel 6 and on one Samsung phone (screen lock set, USB debugging on): run the connected tests and the demo flows as in item 7, then the benchmark on the **Pixel 6** only (it is the target device):

```bash
PAFM_BENCHMARK_ALLOW_EMULATOR=0 ./scripts/run-benchmarks.sh
```

**Command** (only the Pixel connected, unlocked, screen on; do not change the script or its thresholds):

```bash
PAFM_BENCHMARK_ALLOW_EMULATOR=0 ./scripts/run-benchmarks.sh
```

**Pass rule.** Cold start to the Inbox with content (`coldStartToTheInbox`, `timeToFullDisplayMs` median) under 1500 ms; and for `flingTwoHundredInboxCards` the printed `frameOverrunMs` **P95 is 0 ms or less** (at most 5% of frames finish after their deadline; the script prints percentiles, not a percentage). R-008 closes if both hold.

**Send back**: the whole printed output of the script, and `androidApp/benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/` (the `*benchmarkData.json` and the `.perfetto-trace` files). If P95 is above 0, also run the same command once on the `main` branch and send both, so the effect of this branch's changes (a lighter `AppButton`, ADR-147) can be told from the phone's own noise. Run it 3 times and send all three; a single run on an emulator varied between 38% and 48%.

For comparison the Pixel_9_Pro emulator (API 37, arm64, host GPU, 5 flings in two directions, about 2520 frames) gave 43.2% of frames over their deadline on main (median of three runs, range 38.3% to 47.6%; `frameOverrunMs` P95 about 22 ms) and 47.7% after the round 2 changes (range 44.6% to 47.8%), with a cold start of 350 ms median of 10; the traces show the emulator's host GPU and display path as the limit, not the app (technical-details.md, known limitation 6). If the Pixel meets the pass rule the finding closes; if not, R-008 stays open and the next profile starts from the Pixel's trace (not the emulator's).

On the Samsung phone also check: the system prompt (item 3), Recents shows a blank thumbnail while locked (`FLAG_SECURE`), dark theme, font scale 2.0 and the back gesture.

Result: Pixel 6 _not run_ (cold start _ ms; frames over deadline _ %); Samsung _not run_

## 9. The first hosted `android.yml` run

Open the pull request and let the workflow run on GitHub.

```bash
gh run list --workflow android.yml
gh run view <id> --log-failed
```

Expected: every job green: `verify` (lint, unit tests, Roborazzi verify, `assembleRelease`), `emulator` (the device tests on API 34 x86_64), the benchmark gate and the release smoke. The workflow sets `PAFM_ALLOW_DEBUG_SIGNED_RELEASE=1` because the release job is a smoke build signed with the debug key; `verifyReleaseSigning` (R-015) would otherwise fail a CI release with no upload key, and that is intended. The first run is also the check of the workflow file itself (its pinned actions, KVM step and emulator image have never run). If it fails, fix the workflow in its own commit and record what you changed. Note the hosted emulator is API 34 x86_64, not the local API 37 arm64.

Result: _not run_ (run id _, jobs _). The hosted run of the PR that carries these results is recorded in that PR, not here. The first hosted `android.yml` run of the app code is still the owner's to read (`gh run list --workflow android.yml`).

## 10. The iPhone UI-test caveat ("iPhone Duo")

`./scripts/ios-test.sh` picks the first available iPhone simulator. On the machine that wrote this, that is a custom device type "iPhone Duo" (iOS 27.2) on which `AgentsUITests` fails 3 of 3 (the tab bar never appears within 30 s), at the commit before the review fixes as well as after. The same class passes on iPhone 17. The cause is not established. Run the iPhone gates on a standard device:

```bash
xcrun simctl list devices available | grep iPhone
IOS_SIMULATOR_ID=<udid of an iPhone 17> ./scripts/ios-test.sh
```

Expected: 378 Swift Testing unit tests pass and all XCUITests pass except the non-blocking `testAccessibilityAudit*` set (16 failed on iPhone 17 at the last run, identical before and after the review fixes; ADR-094). Record the counts you see.

Result: _not run_

## Emulator run of 2026-11-06: findings and what is left for the owner

Run without the owner's hardware on the Pixel_9_Pro (API 37), Pixel_API28 and Pixel_API33 emulators; raw logs and screenshots are outside the repository. New findings:

| Id | Finding | State |
|---|---|---|
| F-1 | A factory with no company leaves Inbox and Factory on "Loading..." forever (item 1) | fixed: company gate, ADR-150 (emulator re-check: see item 1) |
| F-2 | A new fingerprint does not invalidate the shipping key because it also allows the screen lock (item 2) | fixed: docs corrected, ADR-149, policy pinned by `KeyPolicyTest` |
| F-3 | The key prompt subtitle reads `call paperclip` (item 3) | fixed: platform-supplied reason, ADR-151 |
| F-3b | `UnlockSession` still passes `unlock paperclip` and `CheckCredentials` `check credentials` as the prompt reason | open, same plumbing (ADR-151) |
| F-4 | `android-live-pairing.sh` checks passed vacuously (quoting) | fixed, 305aedb |
| F-5 | `KeystorePerUseDeviceTest` failed on API 28 (no API 30 gate) | fixed, 4e5b03c |
| F-6 | `android-release-smoke.sh live` failed on API 28 (harmless `ClassValue` probe, credential-only unlock screen) | fixed, d02e30b |

What only the owner can still do, and what to send back:

| Item | Needs | Command | Send back |
|---|---|---|---|
| 1 | Tailscale | `ANDROID_SERIAL=<serial> ./scripts/android-live-pairing.sh` with a Tailscale address | the `OK 1` and `OK 2` lines, and whether the factory showed Connected |
| 2 | a real Pixel with a fingerprint | enrol a second fingerprint after pairing with app lock on, then read the factory | that no "Pair again" appears after a new fingerprint (the emulator agrees, F-2 decided) |
| 3 | a real sensor, a second `BiometricPrompt` app | the table in item 3: cancel run, reject approval, decision option, budget raise, budget incident, remove factory | one line per row: prompt shown, cancel left the server unchanged |
| 4 | a real device | the seven steps of item 4 on `1bdb70c` or later | the counts |
| 5 | TalkBack with sound | the walk in item 5 | what was not announced or read in a wrong order |
| 6 | a StrongBox phone | the scratch `KeyInfo` test in item 6 | `securityLevel` |
| 7 | predictive back | API 33 or newer phone: `adb shell settings put global enable_back_animation 1`, back on a pushed screen and a sheet | what the preview showed |
| 8 | Pixel 6 and Samsung | `PAFM_BENCHMARK_ALLOW_EMULATOR=0 ./scripts/run-benchmarks.sh` | cold start ms, frames over deadline % |
| 9 | GitHub | `gh run list --workflow android.yml` | run id and jobs |
| 10 | iPhone 17 simulator | `IOS_SIMULATOR_ID=<udid> ./scripts/ios-test.sh` | counts (not touched in this run) |

## Where the open items are decided

| Open item | Where | Owner decision needed |
|---|---|---|
| Prompts at unlock and per refresh with the cache | item 4, [ADR-145](./architectural-decision-record.md#adr-145-the-board-token-is-held-in-memory-per-factory-for-the-foreground-unlock-session-resolves-adr-144-option-c) | decided (option C, 2026-11-05); only the device counts and the one-prompt-at-unlock follow-up are open |
| A budget raise asks the device owner | item 3, ADR-141 (DEC-1) | decided: kept, confirmed by the owner 2026-11-05 |
| An unvalidated network counts as online | item 1 (Tailscale), ADR-142 (DEC-2) | decided: kept, confirmed by the owner 2026-11-05 |
