package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore

/**
 * A test-only [SecretStore] for the contract run (never shipped, never used by the app). The adapters wipe the secret
 * they are handed after each call, so every read returns a fresh copy of the stored text.
 */
internal class MemorySecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun put(
        key: SecretKey,
        value: Secret,
        policy: SecretPolicy,
    ): SecretWriteResult {
        values[key.path] = value.reveal()
        return SecretWriteResult.Stored
    }

    override suspend fun get(
        key: SecretKey,
        reason: String,
    ): SecretReadResult = values[key.path]?.let { SecretReadResult.Found(Secret.of(it)) } ?: SecretReadResult.Missing

    override suspend fun delete(key: SecretKey) {
        values.remove(key.path)
    }

    override suspend fun deleteNamespace(instanceId: InstanceId) {
        values.keys.removeAll { it.startsWith(SecretKey.namespacePrefix(instanceId)) }
    }

    override suspend fun namespaces(): Set<InstanceId> = values.keys.map { InstanceId(it.split('/')[1]) }.toSet()
}
