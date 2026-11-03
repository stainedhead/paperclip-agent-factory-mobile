# Key expiry and re-pairing

A board key expires after a set time; the Factories tab shows "Board key expires in 23 days" and, once it has expired, "Key expired" with a **Pair again** button. A factory that rejected the key shows "Signed out".

1. Tap **Pair again** on the factory ([pairing](./pairing.md)).
2. Approve the phone in the browser sheet. The old key is replaced.

Until then the factory keeps its last data, marked with its time, and every action is disabled. If the phone's secure store invalidates the key (for example after a biometrics change) the factory also needs re-pairing. Other factories are not affected.
