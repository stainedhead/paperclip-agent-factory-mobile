package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore

/** An in-memory secret store. It keeps its own copy of each value, as a real store would. */
class FakeSecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()

    /** When true every read reports [SecretReadResult.Invalidated] (a new fingerprint was enrolled, FR-015). */
    var invalidated = false

    /** When true every read reports [SecretReadResult.Denied]. */
    var denyReads = false

    /** When true every write fails and stores nothing. */
    var failWrites = false

    var putCalls = 0
        private set

    /** The namespaced paths currently stored, for example `instance/id-1/boardToken`. */
    val paths: Set<String> get() = values.keys.toSet()

    /** Stores [value] without going through the port, so demo mode can start with a paired factory. */
    fun seed(
        key: SecretKey,
        value: String,
    ) {
        values[key.path] = value
    }

    /** The stored value of [key] without the port, so a fake gateway can see which token it would send. */
    fun peek(key: SecretKey): String? = values[key.path]

    fun pathsFor(instanceId: InstanceId): Set<String> =
        paths.filter { it.startsWith(SecretKey.namespacePrefix(instanceId)) }.toSet()

    override suspend fun put(
        key: SecretKey,
        value: Secret,
        policy: SecretPolicy,
    ): SecretWriteResult {
        putCalls += 1
        if (failWrites) return SecretWriteResult.Failed
        values[key.path] = value.reveal()
        return SecretWriteResult.Stored
    }

    override suspend fun get(
        key: SecretKey,
        reason: String,
    ): SecretReadResult =
        when {
            invalidated -> SecretReadResult.Invalidated
            denyReads -> SecretReadResult.Denied
            else -> values[key.path]?.let { SecretReadResult.Found(Secret.of(it)) } ?: SecretReadResult.Missing
        }

    override suspend fun delete(key: SecretKey) {
        values.remove(key.path)
    }

    override suspend fun deleteNamespace(instanceId: InstanceId) {
        val prefix = SecretKey.namespacePrefix(instanceId)
        values.keys.removeAll { it.startsWith(prefix) }
    }
}
