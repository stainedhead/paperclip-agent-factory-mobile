package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.deleteCredentialsLocally
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import kotlinx.coroutines.CancellationException
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
        var outcome = RevokeOutcome.NotAttempted
        try {
            if (instance.status == InstanceStatus.SignedIn) outcome = revoker.revoke(id)
        } finally {
            deleteCredentialsLocally(secrets, id) {
                repository.get(id)?.let {
                    repository.update(
                        it.copy(boardKeyId = null, keyExpiresAt = null, status = InstanceStatus.SignedOut),
                    )
                }
            }
        }
        return SignOutResult.SignedOut(outcome)
    }
}

/**
 * How many factories were signed out, how many keys the server did not confirm revoking, and which factories could
 * not be signed out locally ([failed], for example a secure store error). The others were still signed out.
 */
data class SignOutEverywhereResult(
    val signedOut: Int,
    val unconfirmed: Int,
    val failed: List<InstanceId>,
)

/**
 * Signs out of every configured factory (PRD FR-024). A factory that cannot be signed out (a secure store or
 * repository error) is reported in [SignOutEverywhereResult.failed] and the rest are still signed out. A cancellation
 * is never swallowed; it stops after the factory in progress has finished its own local deletion.
 */
class SignOutEverywhere(
    private val repository: InstanceRepository,
    private val signOut: SignOutInstance,
) {
    suspend operator fun invoke(): SignOutEverywhereResult {
        val failed = mutableListOf<InstanceId>()
        val signedOut = mutableListOf<SignOutResult.SignedOut>()
        for (instance in repository.observeAll().first()) {
            val attempt = runCatching { signOut(instance.id) }
            val error = attempt.exceptionOrNull()
            if (error is CancellationException) throw error
            val result = attempt.getOrNull()
            if (result is SignOutResult.SignedOut) {
                signedOut += result
            } else if (error != null) {
                failed += instance.id
            }
        }
        return SignOutEverywhereResult(
            signedOut.size,
            signedOut.count { it.serverKey == RevokeOutcome.Unconfirmed },
            failed,
        )
    }
}
