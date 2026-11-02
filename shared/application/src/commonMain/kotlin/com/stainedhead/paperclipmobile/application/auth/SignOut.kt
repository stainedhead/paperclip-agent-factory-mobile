package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.DeviceAuthGuard
import com.stainedhead.paperclipmobile.application.InstanceLocks
import com.stainedhead.paperclipmobile.application.deleteCredentialsLocally
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
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

    /** The person declined the prompt that guards the key. Nothing was revoked or deleted; try again (F-A-2). */
    data object Declined : SignOutResult
}

/**
 * Revokes the key on the server, then deletes it locally (PRD FR-024). Credentials are deleted locally even when the
 * server cannot be reached, and the result says so; the configuration stays so the person can pair again.
 */
class SignOutInstance(
    private val repository: InstanceRepository,
    private val secrets: SecretStore,
    private val revoker: KeyRevoker,
    private val locks: InstanceLocks,
) {
    suspend operator fun invoke(id: InstanceId): SignOutResult {
        val instance = repository.get(id) ?: return SignOutResult.NotFound
        var outcome = RevokeOutcome.NotAttempted
        try {
            if (instance.status == InstanceStatus.SignedIn) outcome = revoker.revoke(id)
        } finally {
            // A declined prompt means the revoke never ran: the key stays, so nothing is deleted behind the person.
            if (outcome != RevokeOutcome.Declined) deleteSignedOutLocally(repository, secrets, locks, id)
        }
        return if (outcome == RevokeOutcome.Declined) SignOutResult.Declined else SignOutResult.SignedOut(outcome)
    }
}

sealed interface SignOutHereOnlyResult {
    data class SignedOut(
        val serverKey: RevokeOutcome,
    ) : SignOutHereOnlyResult

    /** Device authentication did not succeed. Nothing was changed. */
    data class NotAuthorized(
        val result: BiometricResult,
    ) : SignOutHereOnlyResult

    data object NotFound : SignOutHereOnlyResult
}

/**
 * Signs out on this phone only, for a key whose read stays denied (rereview1b F-R-2): device authentication, then the
 * local key is deleted and the factory is marked signed out. The server is not contacted, so the key stays live there
 * and a factory that was signed in reports [RevokeOutcome.Unconfirmed].
 */
class SignOutInstanceHereOnly(
    private val repository: InstanceRepository,
    private val secrets: SecretStore,
    private val deviceAuth: DeviceAuthGuard,
    private val locks: InstanceLocks,
) {
    suspend operator fun invoke(id: InstanceId): SignOutHereOnlyResult {
        val instance = repository.get(id) ?: return SignOutHereOnlyResult.NotFound
        val auth = deviceAuth(Action.RemoveFactory)
        if (auth != BiometricResult.Approved) return SignOutHereOnlyResult.NotAuthorized(auth)
        deleteSignedOutLocally(repository, secrets, locks, id)
        return SignOutHereOnlyResult.SignedOut(
            if (instance.status == InstanceStatus.SignedIn) RevokeOutcome.Unconfirmed else RevokeOutcome.NotAttempted,
        )
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
            } else if (error != null || result == SignOutResult.Declined) {
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

private suspend fun deleteSignedOutLocally(
    repository: InstanceRepository,
    secrets: SecretStore,
    locks: InstanceLocks,
    id: InstanceId,
) {
    deleteCredentialsLocally(secrets, locks, id) {
        repository.get(id)?.let {
            repository.update(it.copy(boardKeyId = null, keyExpiresAt = null, status = InstanceStatus.SignedOut))
        }
    }
}
