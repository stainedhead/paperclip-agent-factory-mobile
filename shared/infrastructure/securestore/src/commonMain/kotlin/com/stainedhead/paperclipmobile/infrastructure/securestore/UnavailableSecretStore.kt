package com.stainedhead.paperclipmobile.infrastructure.securestore

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore

/**
 * [SecretStore] for platforms with no device secret store bridged yet (Android until its app phase, and the JVM). It
 * refuses every write and holds no state, so a secret can never end up somewhere weaker than the Keychain or Keystore
 * (AD-033, INTENT principle 1). It deliberately does not satisfy the shared contract, which needs a store that stores.
 */
class UnavailableSecretStore : SecretStore {
    override suspend fun put(
        key: SecretKey,
        value: Secret,
        policy: SecretPolicy,
    ): SecretWriteResult = SecretWriteResult.Failed

    override suspend fun get(
        key: SecretKey,
        reason: String,
    ): SecretReadResult = SecretReadResult.Missing

    override suspend fun delete(key: SecretKey) = Unit

    override suspend fun deleteNamespace(instanceId: InstanceId) = Unit
}
