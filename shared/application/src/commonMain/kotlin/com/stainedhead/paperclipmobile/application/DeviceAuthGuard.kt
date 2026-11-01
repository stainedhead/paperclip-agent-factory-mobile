package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.DeviceAuthPolicy
import com.stainedhead.paperclipmobile.domain.port.AuthReason
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.Biometrics

/**
 * Asks the device owner to confirm [Action]s that [DeviceAuthPolicy] lists (PRD FR-014, ADR-013). A use case calls this
 * first and makes no further call unless the answer is [BiometricResult.Approved].
 */
class DeviceAuthGuard(
    private val biometrics: Biometrics,
) {
    suspend operator fun invoke(action: Action): BiometricResult =
        if (DeviceAuthPolicy.requires(action)) {
            biometrics.authenticate(AuthReason.ConfirmDestructiveAction)
        } else {
            BiometricResult.Approved
        }
}
