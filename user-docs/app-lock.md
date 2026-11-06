# App lock and security settings

Settings (from the Factories tab) controls this phone.

<img src="images/settings.png" alt="Settings with device name, app lock, lock after and destructive actions" width="240">

- **Lock app with Face ID** locks the app when you leave it for longer than **Lock after** (immediately, or from one minute up to an hour or more). The app switcher shows a blurred screen.
- **Destructive actions** (pause an agent, cancel a run, reject an approval, a destructive decision option, raise a budget, remove a factory) always ask for Face ID, a fingerprint or your passcode first. This cannot be turned off. A lock icon marks these buttons. If you cancel the prompt, nothing is sent.
- **Device name** names the key on new pairings.
- **Paired devices and keys** lists the keys of each factory; **Sign out** keeps the factory in the list, **Sign out everywhere** also revokes the keys.

## On Android

<img src="images/android-settings.png" alt="Android Settings with device name, app lock, lock after, destructive actions and the factories (the device name is demo data)" width="240">

<img src="images/android-lock.png" alt="Android lock screen with an Unlock button, after a cancelled unlock" width="240">

- **Lock app with fingerprint, face or screen lock** is the same switch. The system prompt accepts a fingerprint or face, or your PIN, pattern or password. On Android 9 and 10 the prompt takes weak biometrics or your screen lock; on 11 and later strong biometrics or your screen lock.
- **Lock after** works as described above; the lock screen has one **Unlock** button. Cancelling leaves the app locked and nothing changes.
- **Destructive actions** show the app's confirmation sheet with a lock icon, then the system prompt. If you cancel, fail, are locked out of biometrics for a while, or the phone has no fingerprint, face or screen lock set up, nothing is sent. This includes **raising an agent's monthly budget** (or setting it to zero): the Save button carries the lock icon and its spoken name says it asks you; lowering a budget asks nothing.
- **What it asks, and when.** With the app lock on, the app asks for your fingerprint, face or screen lock once when you unlock it: that same check also opens the key to your current factory, so the first refresh does not ask again. (If you turned the app lock on after pairing that factory, its key is not protected, and you get the plain unlock prompt and nothing more.) If the key to that factory was invalidated, for example by a new fingerprint, you still unlock with the plain prompt and the factory then shows that it needs pairing again. After that it does not ask again for refreshes, opening a screen or the automatic updates while you keep the app open. It asks again the first time after you leave the app (the key is forgotten when the app goes to the background), when you switch to another factory for the first time, and after you pair a factory again. While the app is open, its key to the factory is kept in the phone's memory, never written anywhere; it is dropped as soon as you leave the app. Destructive actions (the list above) always ask, every time, however recently you unlocked ([ADR-145](../docs/architectural-decision-record.md#adr-145-the-board-token-is-held-in-memory-per-factory-for-the-foreground-unlock-session-resolves-adr-144-option-c)). This has been checked in tests; the exact prompts on a real phone have not been recorded yet.
- **Recents.** While the app is locked, and while a pairing page is open, the app's thumbnail in the recent apps list is blank, and screenshots are blocked.
- **No screen lock, no app.** If you remove the phone's screen lock, Android deletes the keys that depend on it. The factory then needs [re-pairing](./key-expiry-and-repairing.md). Adding a new fingerprint does **not** invalidate the key on Android: the key also accepts your screen lock, so the factory keeps working (on an iPhone, changing the enrolled Face ID or Touch ID does require re-pairing). If you delete the key some other way, such as clearing the app's data, the factory also needs re-pairing.
- The **Diagnostics** screen (Settings) shows what the app knows about itself (app version, server compatibility, whether the app's data opened, the last key-storage error class, the last device-prompt result and the last pairing outcome), with any address or key removed. You can copy the report; nothing is ever sent anywhere.
