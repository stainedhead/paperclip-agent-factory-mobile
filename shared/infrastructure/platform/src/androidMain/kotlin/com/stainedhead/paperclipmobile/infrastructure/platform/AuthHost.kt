package com.stainedhead.paperclipmobile.infrastructure.platform

import androidx.fragment.app.FragmentActivity

/**
 * Where the biometric prompt can be shown. `BiometricPrompt` needs a resumed [FragmentActivity] but the `Biometrics`
 * port is called from a use case, so the app's main activity registers itself here while it is resumed and clears
 * itself when it pauses. [current] is `null` whenever the app is not in the foreground; then no prompt can be shown and
 * the result is `Unavailable`, which blocks (never approves).
 */
fun interface AuthHost {
    suspend fun current(): FragmentActivity?
}
