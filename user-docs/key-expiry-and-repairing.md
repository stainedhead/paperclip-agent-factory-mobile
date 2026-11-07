# Key expiry and re-pairing

A board key expires after a set time; the Factories tab shows "Board key expires in 23 days" and, once it has expired, "Key expired" with a **Pair again** button. A factory that rejected the key shows "Signed out".

1. Tap **Pair again** on the factory ([pairing](./pairing.md)).
2. Approve the phone in the browser sheet. The old key is replaced.

Until then the factory keeps its last data, marked with its time, and every action is disabled. If the phone's secure store invalidates the key (on an iPhone, for example after a change of Face ID or Touch ID; on Android, after the screen lock is removed) the factory also needs re-pairing. Other factories are not affected.

## On Android

Android adds one reason for a factory to need re-pairing, besides expiry: you removed the phone's screen lock. Android then deletes the key that wrapped the board key, and the app reports the factory as needing re-pairing rather than failing in a loop. The steps are the same: **Pair again** on the factory, approve in the Custom Tab ([pairing](./pairing.md)). Because the pairing is new, the device name in Settings names the new key, and you may want to revoke the old one under **Paired devices and keys** (Settings). Restoring the phone or reinstalling the app also leaves no key. Enrolling a new fingerprint does **not** need re-pairing on Android: the key also accepts your PIN, pattern or password, so a new fingerprint leaves it valid (an emulator run confirmed it; the decision is ADR-149). An iPhone is different: changing the enrolled Face ID or Touch ID invalidates the key. Tests cover the screen-lock case with a fake Keystore and on a real emulator keystore.
