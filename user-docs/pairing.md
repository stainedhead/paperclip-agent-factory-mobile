# Pairing this phone

Pairing gives this phone its own **board key** for one factory. The key is created after you approve the phone in the Paperclip web app, and it stays in the iPhone Keychain (this device only, never synced, never backed up). Nothing leaves the phone except requests to that factory.

1. After the address step the app opens an approval page in a secure browser sheet.
2. Sign in to Paperclip if asked and approve "this phone". The sheet closes and the app finishes by itself. Closing the sheet is how you finish approving; it is not a failure.
3. If the sheet cannot be shown, or you cancel, the pairing is cancelled and nothing is stored.
4. Pick the company to open on launch, then choose the app lock ([app lock](./app-lock.md)).

The screen that shows the approval is never captured in the documentation, so there is no screenshot here. A factory can have several phones; see Settings, "Paired devices and keys", to review or revoke them.

Status: the real server flow arrives with Phase 1b. In demo mode every pairing is approved at once.
