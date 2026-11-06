package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.AttestedReadResult
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

    /** The paths stored with `requireUserPresence`: only these are attested by [readAttested]. */
    private val protectedPaths = mutableSetOf<String>()

    /** How many attested reads were asked for (each stands for one prompt on a protected secret). */
    var attestedReads = 0
        private set

    /** The reason text of every read, in order: what a real store would show on its system prompt. */
    val readReasons = mutableListOf<String>()

    /** When true every read reports [SecretReadResult.Invalidated] (the key was invalidated, FR-015). */
    var invalidated = false

    /** When true every read reports [SecretReadResult.Denied]. */
    var denyReads = false

    /** When true every write fails and stores nothing. */
    var failWrites = false

    /** Factories whose namespace cannot be deleted: `deleteNamespace` throws, as a failing secure store would. */
    val failNamespaceDeletes = mutableSetOf<InstanceId>()

    var putCalls = 0
        private set

    /** The namespaced paths currently stored, for example `instance/id-1/boardToken`. */
    val paths: Set<String> get() = values.keys.toSet()

    /** Stores [value] without going through the port, so demo mode can start with a paired factory. */
    fun seed(
        key: SecretKey,
        value: String,
        protected: Boolean = false,
    ) {
        values[key.path] = value
        if (protected) protectedPaths += key.path else protectedPaths -= key.path
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
        if (policy.requireUserPresence) protectedPaths += key.path else protectedPaths -= key.path
        return SecretWriteResult.Stored
    }

    override suspend fun get(
        key: SecretKey,
        reason: String,
    ): SecretReadResult {
        readReasons += reason
        return read(key)
    }

    private fun read(key: SecretKey): SecretReadResult =
        when {
            invalidated -> SecretReadResult.Invalidated
            denyReads -> SecretReadResult.Denied
            else -> values[key.path]?.let { SecretReadResult.Found(Secret.of(it)) } ?: SecretReadResult.Missing
        }

    override suspend fun readAttested(
        key: SecretKey,
        reason: String,
    ): AttestedReadResult {
        attestedReads += 1
        if (key.path !in protectedPaths) return AttestedReadResult.NotAttested
        return when (val read = get(key, reason)) {
            is SecretReadResult.Found -> AttestedReadResult.Authenticated(read.secret)
            SecretReadResult.Denied -> AttestedReadResult.Denied
            SecretReadResult.Invalidated -> AttestedReadResult.Invalidated
            SecretReadResult.Missing -> AttestedReadResult.NotAttested
        }
    }

    override suspend fun delete(key: SecretKey) {
        values.remove(key.path)
        protectedPaths.remove(key.path)
    }

    override suspend fun namespaces(): Set<InstanceId> = values.keys.map { InstanceId(it.split('/')[1]) }.toSet()

    override suspend fun deleteNamespace(instanceId: InstanceId) {
        check(instanceId !in failNamespaceDeletes) { "the secure store is unavailable" }
        val prefix = SecretKey.namespacePrefix(instanceId)
        values.keys.removeAll { it.startsWith(prefix) }
        protectedPaths.removeAll { it.startsWith(prefix) }
    }
}
