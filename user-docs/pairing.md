# Pairing this phone

Pairing gives this phone its own **board key** for one factory. The key is created after you approve the phone in the Paperclip web app, and it stays in the iPhone Keychain, or in the Android Keystore on Android (this device only, never synced, never backed up). Nothing leaves the phone except requests to that factory.

1. After the address step the app opens an approval page in a secure browser sheet.
2. Sign in to Paperclip if asked and approve "this phone". The sheet closes and the app finishes by itself. Closing the sheet is how you finish approving; it is not a failure.
3. If the sheet cannot be shown, or you cancel, or the pairing runs out of time, the pairing is cancelled, the app closes the sheet so a stale approval cannot be given, and nothing is stored.
4. Pick the company to open on launch, then choose the app lock ([app lock](./app-lock.md)).

The screen that shows the approval is never captured in the documentation, so there is no screenshot here. A factory can have several phones; see Settings, "Paired devices and keys", to review or revoke them.

Status: this is the real flow. In demo mode (Debug builds only) every pairing is approved at once and nothing is stored.

## On Android

The approval page opens in a **Chrome Custom Tab** (the browser's own sign-in and cookies; the app never sees your Paperclip password). The steps are the same:

1. Sign in to Paperclip if asked and approve "this phone".
2. Return to the app (close the tab with the back arrow or the system back gesture, or switch back to Paperclip). The app cannot close the tab itself; once you are back it asks the server whether the pairing was approved and stores the key. Coming back is how you finish, not a failure.
3. If the tab cannot be shown, you cancel, or the pairing runs out of time, nothing is stored and the pairing is cancelled. On the first-run Pair step the system Back button or gesture cancels the pairing too.
4. While the tab is open the app's own window is hidden from Recents (`FLAG_SECURE`), and when the system asks for your fingerprint or screen lock to protect the new key, give it.

The key is wrapped by a non-exportable AES-256 key in the **Android Keystore** (the StrongBox chip where the phone has one) and the encrypted record is kept where Android Backup does not copy it. Reinstalling the app or restoring to another phone leaves no key, so you pair again. On a phone without a PIN, pattern or password the key cannot be stored: set a screen lock first.

The pairing screen, and the browser page, are never captured in these guides. Pairing against a real server from the Android app has not been verified end to end yet (see the [technical details](../docs/technical-details.md#known-limitations-and-gaps) and the [verification checklist](../docs/android-verification-checklist.md)).
