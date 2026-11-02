package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore

/**
 * Finds where a factory lives and the key the device holds for it, for one call. The key is read from the
 * [SecretStore] for this call, handed to [call] and wiped afterwards; nothing here keeps it (AD-001).
 */
internal class StoredCredentials(
    private val instances: InstanceRepository,
    private val secrets: SecretStore,
) {
    /** A factory that is not configured is NotFound; a key that is missing, invalidated or declined is Unauthorized. */
    suspend fun <T> call(
        instanceId: InstanceId,
        block: suspend (Instance, com.stainedhead.paperclipmobile.domain.Secret) -> DomainResult<T>,
    ): DomainResult<T> {
        val instance = instances.get(instanceId) ?: return DomainResult.Failure(DomainError.NotFound)
        val read = secrets.get(SecretKey(instanceId, SecretName.BoardToken), REASON)
        val key = (read as? SecretReadResult.Found)?.secret ?: return DomainResult.Failure(DomainError.Unauthorized)
        return try {
            block(instance, key)
        } finally {
            key.wipe()
        }
    }

    private companion object {
        const val REASON = "call paperclip"
    }
}
