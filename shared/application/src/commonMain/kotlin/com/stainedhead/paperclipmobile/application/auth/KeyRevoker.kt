package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.AuthGateway

/** What happened to a factory's key on the server when the app signed out or removed it. */
enum class RevokeOutcome {
    /** The server confirmed the revoke. */
    Revoked,

    /** The server said the key was already unusable (401 or 404). */
    AlreadyInvalid,

    /** The server could not be reached or refused: the key may still be valid and should be revoked in the web app. */
    Unconfirmed,

    NotAttempted,

    /**
     * The person declined the prompt that guards the key, so nothing was sent and nothing changed. The key is still
     * live on the server and on the phone; the person can try again (review1b F-A-2).
     */
    Declined,
}

/** Best-effort `POST /api/cli-auth/revoke-current`. It is a mutation, so it runs once and is never retried. */
class KeyRevoker(
    private val auth: AuthGateway,
) {
    suspend fun revoke(instanceId: InstanceId): RevokeOutcome =
        when (val result = auth.revokeCurrentKey(instanceId)) {
            is DomainResult.Success -> {
                RevokeOutcome.Revoked
            }

            is DomainResult.Failure -> {
                when (result.error) {
                    DomainError.Unauthorized, DomainError.NotFound -> RevokeOutcome.AlreadyInvalid
                    DomainError.DeviceAuthDeclined -> RevokeOutcome.Declined
                    else -> RevokeOutcome.Unconfirmed
                }
            }
        }
}
