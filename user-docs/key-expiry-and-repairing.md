# Key expiry and re-pairing

A board key expires after a set time; the Factories tab shows "Board key expires in 23 days" and, once it has expired, "Key expired" with a **Pair again** button. A factory that rejected the key shows "Signed out".

1. Tap **Pair again** on the factory ([pairing](./pairing.md)).
2. Approve the phone in the browser sheet. The old key is replaced.

Until then the factory keeps its last data, marked with its time, and every action is disabled. If the phone's secure store invalidates the key (for example after a biometrics change) the factory also needs re-pairing. Other factories are not affected.

## On Android

Android adds two reasons for a factory to need re-pairing, besides expiry: you removed the phone's screen lock, or (on some phones) enrolled a new fingerprint. Android then deletes the key that wrapped the board key, and the app reports the factory as needing re-pairing rather than failing in a loop. The steps are the same: **Pair again** on the factory, approve in the Custom Tab ([pairing](./pairing.md)). Because the pairing is new, the device name in Settings names the new key, and you may want to revoke the old one under **Paired devices and keys** (Settings). Restoring the phone or reinstalling the app also leaves no key. Tests cover each case with a fake Keystore and some on a real emulator keystore; a real new-fingerprint enrolment has not been tried.
