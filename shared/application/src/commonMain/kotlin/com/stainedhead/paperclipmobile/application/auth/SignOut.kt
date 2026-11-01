package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import kotlinx.coroutines.flow.first

sealed interface SignOutResult {
    /** Local credentials are gone. [serverKey] says whether the server confirmed the revoke (ADR-012). */
    data class SignedOut(
        val serverKey: RevokeOutcome,
    ) : SignOutResult

    data object NotFound : SignOutResult
}

/**
 * Revokes the key on the server, then deletes it locally (PRD FR-024). Credentials are deleted locally even when the
 * server cannot be reached, and the result says so; the configuration stays so the person can pair again.
 */
class SignOutInstance(
    private val repository: InstanceRepository,
    private val secrets: SecretStore,
    private val revoker: KeyRevoker,
) {
    suspend operator fun invoke(id: InstanceId): SignOutResult {
        val instance = repository.get(id) ?: return SignOutResult.NotFound
        val outcome =
            if (instance.status == InstanceStatus.SignedIn) revoker.revoke(id) else RevokeOutcome.NotAttempted
        secrets.deleteNamespace(id)
        repository.update(
            instance.copy(boardKeyId = null, keyExpiresAt = null, status = InstanceStatus.SignedOut),
        )
        return SignOutResult.SignedOut(outcome)
    }
}

/** How many factories were signed out and how many keys the server did not confirm revoking. */
data class SignOutEverywhereResult(
    val signedOut: Int,
    val unconfirmed: Int,
)

/** Signs out of every configured factory (PRD FR-024). */
class SignOutEverywhere(
    private val repository: InstanceRepository,
    private val signOut: SignOutInstance,
) {
    suspend operator fun invoke(): SignOutEverywhereResult {
        val results = repository.observeAll().first().map { signOut(it.id) }
        val signedOut = results.filterIsInstance<SignOutResult.SignedOut>()
        return SignOutEverywhereResult(signedOut.size, signedOut.count { it.serverKey == RevokeOutcome.Unconfirmed })
    }
}
