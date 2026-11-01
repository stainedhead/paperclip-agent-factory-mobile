package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretKey
import com.stainedhead.paperclipmobile.domain.port.SecretName
import com.stainedhead.paperclipmobile.domain.port.SecretPolicy
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.domain.port.SecretWriteResult
import kotlinx.coroutines.flow.first
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
    /** [knownKeyId] is given when the key was already validated; otherwise it is read with the stored key. */
    suspend fun complete(
        instance: Instance,
        token: Secret,
        method: AuthMethod,
        knownKeyId: String?,
    ): SignInResult {
        val policy = SecretPolicy(requireUserPresence = settings.observe().first().appLockEnabled)
        val key = SecretKey(instance.id, SecretName.BoardToken)
        if (secrets.put(key, token, policy) != SecretWriteResult.Stored) return SignInResult.StorageFailed
        val keyId =
            if (knownKeyId != null) {
                knownKeyId
            } else {
                when (val me = auth.me(instance.id)) {
                    is DomainResult.Success -> {
                        me.value.keyId
                    }

                    is DomainResult.Failure -> {
                        secrets.delete(key)
                        return SignInResult.Failed(me.error)
                    }
                }
            }
        val expiresAt =
            auth.listBoardKeys(instance.id).let { keys ->
                (keys as? DomainResult.Success)?.value?.firstOrNull { it.id == keyId }?.expiresAt
            }
        repository.update(
            instance.copy(
                authMethod = method,
                boardKeyId = keyId,
                keyExpiresAt = expiresAt,
                status = InstanceStatus.SignedIn,
                lastConnectedAt = clock.now(),
            ),
        )
        return SignInResult.SignedIn(keyId, expiresAt)
    }
}
