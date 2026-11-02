package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/** How a pairing or a manual key entry ended. */
sealed interface SignInResult {
    data class SignedIn(
        val keyId: String?,
        val expiresAt: Instant?,
    ) : SignInResult

    data object NotFound : SignInResult

    /** The text does not look like a board key (`pcp_board_...`). Nothing was sent anywhere. */
    data object InvalidKey : SignInResult

    data object StorageFailed : SignInResult

    data object Cancelled : SignInResult

    data object Expired : SignInResult

    data class Failed(
        val error: DomainError,
    ) : SignInResult
}

/**
 * Stores a validated board key and records its metadata on the factory. Shared by pairing and manual entry so a key is
 * written the same way in both (PRD FR-020, FR-021, FR-022).
 */
class KeyEnrollment(
    private val auth: AuthGateway,
    private val secrets: SecretStore,
    private val repository: InstanceRepository,
    private val clock: Clock,
    private val settings: AppSettingsStore,
) {
    /**
     * [knownKeyId] is given when the key was already validated against [instance]'s address. Otherwise the key is
     * validated here, and in both cases every request goes to the snapshot's own address, so a pending key never
     * reaches a host that did not issue it (ADR-012). If the address was edited while the key was in flight, nothing is
     * kept and the result is `Failed(Conflict)`.
     */
    suspend fun complete(
        instance: Instance,
        token: Secret,
        method: AuthMethod,
        knownKeyId: String?,
    ): SignInResult {
        val keyId =
            knownKeyId ?: when (val me = auth.meWithKey(instance.baseUri, token)) {
                is DomainResult.Success -> me.value.keyId
                is DomainResult.Failure -> return SignInResult.Failed(me.error)
            }
        val expiresAt =
            auth.listBoardKeysWithKey(instance.baseUri, token).let { keys ->
                (keys as? DomainResult.Success)?.value?.firstOrNull { it.id == keyId }?.expiresAt
            }
        return store(instance, token, method, keyId, expiresAt)
    }

    /**
     * Writes the key and then the status. Neither a cancellation nor a failing repository may leave the two out of
     * agreement, so this runs shielded from cancellation and, unless the status was committed, removes the new key and
     * marks the factory Signed out.
     */
    private suspend fun store(
        instance: Instance,
        token: Secret,
        method: AuthMethod,
        keyId: String?,
        expiresAt: Instant?,
    ): SignInResult =
        withContext(NonCancellable) {
            val policy = SecretPolicy(requireUserPresence = settings.observe().first().appLockEnabled)
            val key = SecretKey(instance.id, SecretName.BoardToken)
            if (secrets.put(key, token, policy) !=
                SecretWriteResult.Stored
            ) {
                return@withContext SignInResult.StorageFailed
            }
            var committed = false
            try {
                val fresh = repository.get(instance.id)
                if (fresh == null || fresh.baseUri != instance.baseUri) {
                    return@withContext SignInResult.Failed(DomainError.Conflict)
                }
                repository.update(
                    fresh.copy(
                        authMethod = method,
                        boardKeyId = keyId,
                        keyExpiresAt = expiresAt,
                        status = InstanceStatus.SignedIn,
                        lastConnectedAt = clock.now(),
                    ),
                )
                committed = true
                SignInResult.SignedIn(keyId, expiresAt)
            } finally {
                if (!committed) discard(instance, key)
            }
        }

    /** Removes an uncommitted key, and marks the factory Signed out if it is still at its address. */
    private suspend fun discard(
        instance: Instance,
        key: SecretKey,
    ) {
        secrets.delete(key)
        val current = repository.get(instance.id)
        if (current != null && current.baseUri == instance.baseUri && current.status == InstanceStatus.SignedIn) {
            repository.update(current.copy(boardKeyId = null, keyExpiresAt = null, status = InstanceStatus.SignedOut))
        }
    }
}
