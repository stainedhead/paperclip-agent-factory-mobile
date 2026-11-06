# Research: Phase 2a Android review fixes

**Date:** 2026-11-04

**Source PRD:** [phase-2a-android-ui-auto-review-PRD.md](./phase-2a-android-ui-auto-review-PRD.md)

## Research questions

1. Does `setUserAuthenticationParameters(0, STRONG or CREDENTIAL)` with a `CryptoObject` work for per-use authorization on API 30+ and how do keys on API 28 and 29 fall back (R-001)?
2. Does biometric-1.1.0 always call back on cancel or lifecycle death, or must the launcher bound the wait (R-002)?
3. Does `MutatorMutex` cancel the Run viewer's follow-scroll when the user drags (R-005)?
4. Does androidx navigation retain back-stack `ViewModelStore`s when the tab shell is disposed on lock (R-021)?
5. Which Android network capabilities are reported on a Tailscale-only path or a failed-probe VPN (R-012, DEC-2)?

## Industry standards, existing implementations, API documentation, best practices

[TBD] Android Keystore and BiometricPrompt documentation; iPhone counterparts in `iosApp/`.

## Open questions

Verify-first items R-005, R-021, R-008, R-025, R-026 are verifications, not decisions. DEC-1 and DEC-2 are run assumptions.

## References

[phase-2a-android-ui-auto-review-PRD.md](./phase-2a-android-ui-auto-review-PRD.md) · [Android spec research](../261104-phase-2a-android-ui/research.md)
