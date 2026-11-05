package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretPolicy
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.SecretWriteResult
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What ends a cached board token before the next prompt: one factory (a 401, a re-pair) or all of them. */
interface SessionTokens {
    fun forget(instanceId: InstanceId)

    fun forgetAll()
}

/** A [SessionTokens] that holds nothing, for a graph or a test with no cache. */
object NoSessionTokens : SessionTokens {
    override fun forget(instanceId: InstanceId) = Unit

    override fun forgetAll() = Unit
}

/**
 * Keeps each factory's board token in memory for the foreground unlock session so that one refresh asks the device
 * owner once, not once per gateway call (ADR-145, which supersedes the "nothing caches the token" statement of AD-001
 * for this one place). It sits above the device store: the Keystore and Keychain stores stay per-use (ADR-139), and the
 * cache holds only the board token, per factory, only in memory.
 *
 * The entry is dropped by [forget] / [forgetAll] (a 401, the app going to the background) and by [put], [delete] and
 * [deleteNamespace] on that factory. A read that was in flight when an entry was dropped is not kept. Concurrent reads
 * of one factory share one device read; a refusal is shared with the callers that were waiting for it and is never
 * cached. Every caller gets its own copy because callers wipe what they are given. There is no timer: the bound is the
 * background and the app lock.
 */
class SessionCachingSecretStore(
    private val device: SecretStore,
) : SecretStore,
    SessionTokens {
    /** One per factory: the lock around its device read, how many reads finished, and the last refusal. */
    private class Flight {
        val lock = Mutex()
        val finished = MutableStateFlow<Pair<Long, SecretReadResult?>>(0L to null)

        /** The refusal of a read that finished after [seenFinished], for a caller that waited on it. */
        fun refusalSince(seenFinished: Long): SecretReadResult? =
            finished.value.takeIf { it.first > seenFinished }?.second
    }

    private val memory = SessionTokenMemory()
    private val flights = MutableStateFlow<Map<InstanceId, Flight>>(emptyMap())

    override suspend fun get(
        key: SecretKey,
        reason: String,
    ): SecretReadResult {
        if (key.name != SecretName.BoardToken) return device.get(key, reason)
        val id = key.instanceId
        memory.copyOf(id)?.let { return SecretReadResult.Found(it) }
        val flight = flightOf(id)
        val seenFinished = flight.finished.value.first
        return flight.lock.withLock {
            memory.copyOf(id)?.let { SecretReadResult.Found(it) }
                ?: flight.refusalSince(seenFinished)
                ?: readThrough(key, reason, flight)
        }
    }

    override suspend fun put(
        key: SecretKey,
        value: Secret,
        policy: SecretPolicy,
    ): SecretWriteResult {
        if (key.name != SecretName.BoardToken) return device.put(key, value, policy)
        return forgettingAround(key.instanceId) { device.put(key, value, policy) }
    }

    override suspend fun delete(key: SecretKey) = forgettingAround(key.instanceId) { device.delete(key) }

    override suspend fun deleteNamespace(instanceId: InstanceId) =
        forgettingAround(instanceId) { device.deleteNamespace(instanceId) }

    override suspend fun namespaces(): Set<InstanceId> = device.namespaces()

    override fun forget(instanceId: InstanceId) = memory.forget(instanceId)

    override fun forgetAll() = memory.forgetAll()

    /** Drops the entry before and after [change], so a read overlapping the change cannot bring the old token back. */
    private suspend fun <T> forgettingAround(
        id: InstanceId,
        change: suspend () -> T,
    ): T {
        memory.forget(id)
        return try {
            change()
        } finally {
            memory.forget(id)
        }
    }

    private suspend fun readThrough(
        key: SecretKey,
        reason: String,
        flight: Flight,
    ): SecretReadResult {
        val generation = memory.generationOf(key.instanceId)
        val result = device.get(key, reason)
        val refusal = if (result is SecretReadResult.Found) null else result
        if (result is SecretReadResult.Found) memory.keepIfUnchanged(key.instanceId, generation, result.secret)
        flight.finished.update { (it.first + 1L) to refusal }
        return result
    }

    private fun flightOf(id: InstanceId): Flight {
        flights.value[id]?.let { return it }
        val fresh = Flight()
        var winner = fresh
        flights.update { map -> (map[id] ?: fresh).also { winner = it }.let { map + (id to it) } }
        return winner
    }
}
