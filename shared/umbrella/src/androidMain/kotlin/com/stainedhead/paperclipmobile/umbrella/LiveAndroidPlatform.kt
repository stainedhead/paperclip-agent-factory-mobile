package com.stainedhead.paperclipmobile.umbrella

/**
 * The Android app has no live platform yet: its database needs a `Context` and its secrets need the Keystore, both of
 * which arrive with the Android app phase. Until then building a live graph is refused rather than run on a pretend
 * secret store (ADR-069, INTENT principle 1).
 */
internal actual fun defaultLivePlatform(): LivePlatform =
    error("The live graph is not available on Android until the Android app phase supplies a Context and the Keystore")
