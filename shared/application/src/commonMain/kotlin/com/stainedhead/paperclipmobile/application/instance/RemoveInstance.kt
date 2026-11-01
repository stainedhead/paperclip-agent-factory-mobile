package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.auth.KeyRevoker
import com.stainedhead.paperclipmobile.application.auth.RevokeOutcome
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
) {
    suspend operator fun invoke(id: InstanceId): RemoveInstanceResult {
        val instance = repository.get(id) ?: return RemoveInstanceResult.NotFound
        val auth = deviceAuth(Action.RemoveFactory)
        if (auth != BiometricResult.Approved) return RemoveInstanceResult.NotAuthorized(auth)
        var outcome = RevokeOutcome.NotAttempted
        try {
            if (instance.status == InstanceStatus.SignedIn) outcome = revoker.revoke(id)
        } finally {
            withContext(NonCancellable) {
                secrets.deleteNamespace(id)
                repository.delete(id)
            }
        }
        return RemoveInstanceResult.Removed(outcome)
    }
}
