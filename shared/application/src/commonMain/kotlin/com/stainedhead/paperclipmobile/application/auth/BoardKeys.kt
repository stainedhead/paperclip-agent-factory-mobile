package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.retryingRead
import com.stainedhead.paperclipmobile.domain.BoardKey
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository

/** The account's active board keys, with this phone's key marked (PRD FR-025). A read, so it is retried. */
class ListBoardKeys(
    private val repository: InstanceRepository,
    private val auth: AuthGateway,
    private val signedOut: SignedOutMarker,
) {
    suspend operator fun invoke(instanceId: InstanceId): DomainResult<List<BoardKey>> {
        val instance = repository.get(instanceId) ?: return DomainResult.Failure(DomainError.NotFound)
        val result = retryingRead { auth.listBoardKeys(instanceId) }
        signedOut.noteIfUnauthorized(instanceId, result)
        return when (result) {
            is DomainResult.Failure -> {
                result
            }

            is DomainResult.Success -> {
                DomainResult.Success(
                    result.value
                        .filter { it.revokedAt == null }
                        .map { it.copy(isCurrentDevice = it.id == instance.boardKeyId) },
                )
            }
        }
    }
}

sealed interface RevokeKeyResult {
    data object Revoked : RevokeKeyResult

    /** The key was this phone's own, so the phone was signed out as well. */
    data class SignedOutThisDevice(
        val serverKey: RevokeOutcome,
    ) : RevokeKeyResult

    /**
     * This phone does not know which server key is its own, so it cannot tell "revoke another device" from "revoke
     * myself". Nothing was sent: revoking blind could lock this phone out while the factory still shows Signed in.
     */
    data object CurrentKeyUnknown : RevokeKeyResult

    data class Failed(
        val error: DomainError,
    ) : RevokeKeyResult
}

/** Revokes one of the account's board keys (PRD FR-025). A mutation: attempted once. */
class RevokeBoardKey(
    private val repository: InstanceRepository,
    private val auth: AuthGateway,
    private val signOut: SignOutInstance,
    private val signedOut: SignedOutMarker,
) {
    suspend operator fun invoke(
        instanceId: InstanceId,
        keyId: String,
    ): RevokeKeyResult {
        val instance = repository.get(instanceId) ?: return RevokeKeyResult.Failed(DomainError.NotFound)
        if (instance.boardKeyId == null) return RevokeKeyResult.CurrentKeyUnknown
        if (instance.boardKeyId == keyId) {
            val result = signOut(instanceId) as? SignOutResult.SignedOut
            return RevokeKeyResult.SignedOutThisDevice(result?.serverKey ?: RevokeOutcome.NotAttempted)
        }
        val result = auth.revokeBoardKey(instanceId, keyId)
        signedOut.noteIfUnauthorized(instanceId, result)
        return when (result) {
            is DomainResult.Success -> RevokeKeyResult.Revoked
            is DomainResult.Failure -> RevokeKeyResult.Failed(result.error)
        }
    }
}
