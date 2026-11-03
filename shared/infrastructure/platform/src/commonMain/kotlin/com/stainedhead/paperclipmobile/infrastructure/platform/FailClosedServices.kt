package com.stainedhead.paperclipmobile.infrastructure.platform

import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics
import com.stainedhead.paperclipmobile.domain.port.Connectivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * [Biometrics] for platforms that have no bridge yet (Android until its app phase, and the JVM). It never approves, so
 * every destructive action stays blocked rather than running unguarded (AD-033, INTENT principle 1).
 */
class UnavailableBiometrics : Biometrics {
    override suspend fun authenticate(reason: AuthReason): BiometricResult = BiometricResult.Unavailable
}

/** [Connectivity] for platforms without a network monitor: always online, so requests are tried and fail alone. */
class OptimisticConnectivity : Connectivity {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
}
