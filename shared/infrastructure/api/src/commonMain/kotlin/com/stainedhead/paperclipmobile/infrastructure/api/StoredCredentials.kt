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
 * [SecretStore] for this call, handed to [call] and wiped afterwards; nothing here keeps it. The [SecretStore] it is
 * given is the graph's session cache over the device store, which returns a fresh copy per read, so wiping is safe and
 * a protected token costs one prompt per unlock session, not one per call (ADR-145; AD-001's "never cached" is
 * superseded there).
 */
internal class StoredCredentials(
    private val instances: InstanceRepository,
    private val secrets: SecretStore,
) {
    /**
     * A factory that is not configured is NotFound. A key that is missing is Unauthorized; one invalidated by a new
     * fingerprint is KeyInvalidated (re-pair); a declined prompt is DeviceAuthDeclined (nothing changes, try again).
     * Only a real server 401 and a missing key may sign the factory out (review1b F-A-2).
     */
    suspend fun <T> call(
        instanceId: InstanceId,
        block: suspend (Instance, com.stainedhead.paperclipmobile.domain.Secret) -> DomainResult<T>,
    ): DomainResult<T> {
        val instance = instances.get(instanceId) ?: return DomainResult.Failure(DomainError.NotFound)
        val read = secrets.get(SecretKey(instanceId, SecretName.BoardToken), REASON)
        val key =
            when (read) {
                is SecretReadResult.Found -> read.secret
                SecretReadResult.Missing -> return DomainResult.Failure(DomainError.Unauthorized)
                SecretReadResult.Invalidated -> return DomainResult.Failure(DomainError.KeyInvalidated)
                SecretReadResult.Denied -> return DomainResult.Failure(DomainError.DeviceAuthDeclined)
            }
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
