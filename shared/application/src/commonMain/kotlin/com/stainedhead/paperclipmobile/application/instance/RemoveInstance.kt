package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.InstanceLocks
import com.stainedhead.paperclipmobile.application.auth.KeyRevoker
import com.stainedhead.paperclipmobile.application.auth.RevokeOutcome
import com.stainedhead.paperclipmobile.application.deleteCredentialsLocally
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore

sealed interface RemoveInstanceResult {
    data class Removed(
        val serverKey: RevokeOutcome,
    ) : RemoveInstanceResult

    /** Device authentication did not succeed. Nothing was changed and the server was not contacted. */
    data class NotAuthorized(
        val result: BiometricResult,
    ) : RemoveInstanceResult

    data object NotFound : RemoveInstanceResult
}

/**
 * Deletes a factory (PRD FR-006): device authentication first, then a best-effort revoke of its key on the server, then
 * every secret under its namespace, then its configuration and cache. Secrets are deleted even if the revoke fails or
 * is cancelled, so no secret of a removed factory is ever left behind.
 */
class RemoveInstance(
    private val repository: InstanceRepository,
    private val secrets: SecretStore,
    private val revoker: KeyRevoker,
    private val deviceAuth: DeviceAuthGuard,
    private val locks: InstanceLocks,
) {
    suspend operator fun invoke(id: InstanceId): RemoveInstanceResult {
        val instance = repository.get(id) ?: return RemoveInstanceResult.NotFound
        val auth = deviceAuth(Action.RemoveFactory)
        if (auth != BiometricResult.Approved) return RemoveInstanceResult.NotAuthorized(auth)
        var outcome = RevokeOutcome.NotAttempted
        try {
            if (instance.status == InstanceStatus.SignedIn) outcome = revoker.revoke(id)
        } finally {
            // A declined key prompt means the revoke never ran: nothing is deleted behind the person (F-A-2).
            if (outcome !=
                RevokeOutcome.Declined
            ) {
                deleteCredentialsLocally(secrets, locks, id) { repository.delete(id) }
            }
        }
        return if (outcome == RevokeOutcome.Declined) {
            RemoveInstanceResult.NotAuthorized(BiometricResult.Cancelled)
        } else {
            RemoveInstanceResult.Removed(outcome)
        }
    }
}
