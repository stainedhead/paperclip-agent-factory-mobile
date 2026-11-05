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
    private class State(
        val held: Map<InstanceId, Secret> = emptyMap(),
        val generations: Map<InstanceId, Long> = emptyMap(),
        val epoch: Long = 0,
    ) {
        fun generationOf(id: InstanceId): Long = (generations[id] ?: 0L) + epoch
    }

    /** One per factory: the lock around its device read, how many reads finished, and the last refusal. */
    private class Flight(
        val lock: Mutex = Mutex(),
        val finished: MutableStateFlow<Pair<Long, SecretReadResult?>> = MutableStateFlow(0L to null),
    )

    private val state = MutableStateFlow(State())
    private val flights = MutableStateFlow<Map<InstanceId, Flight>>(emptyMap())

    override suspend fun get(
        key: SecretKey,
        reason: String,
    ): SecretReadResult {
        if (key.name != SecretName.BoardToken) return device.get(key, reason)
        val id = key.instanceId
        hit(id)?.let { return it }
        val flight = flightOf(id)
        val seenFinished = flight.finished.value.first
        return flight.lock.withLock {
            hit(id) ?: sharedRefusal(flight, seenFinished) ?: readThrough(key, reason, flight)
        }
    }

    override suspend fun put(
        key: SecretKey,
        value: Secret,
        policy: SecretPolicy,
    ): SecretWriteResult {
        if (key.name != SecretName.BoardToken) return device.put(key, value, policy)
        forget(key.instanceId)
        return try {
            device.put(key, value, policy)
        } finally {
            forget(key.instanceId)
        }
    }

    override suspend fun delete(key: SecretKey) {
        forget(key.instanceId)
        try {
            device.delete(key)
        } finally {
            forget(key.instanceId)
        }
    }

    override suspend fun deleteNamespace(instanceId: InstanceId) {
        forget(instanceId)
        try {
            device.deleteNamespace(instanceId)
        } finally {
            forget(instanceId)
        }
    }

    override suspend fun namespaces(): Set<InstanceId> = device.namespaces()

    override fun forget(instanceId: InstanceId) {
        val before = state.getAndUpdateState {
            State(
                it.held - instanceId,
                it.generations + (instanceId to ((it.generations[instanceId] ?: 0L) + 1L)),
                it.epoch,
            )
        }
        before.held[instanceId]?.wipe()
    }

    override fun forgetAll() {
        val before = state.getAndUpdateState { State(emptyMap(), it.generations, it.epoch + 1L) }
        before.held.values.forEach { it.wipe() }
    }

    private fun MutableStateFlow<State>.getAndUpdateState(next: (State) -> State): State {
        while (true) {
            val current = value
            if (compareAndSet(current, next(current))) return current
        }
    }

    /**
     * A copy of the held token, or null. The copy is checked against the entry afterwards: a clear wipes the entry only
     * after removing it, so an entry that is still held proves the copy was complete.
     */
    private fun hit(id: InstanceId): SecretReadResult.Found? {
        val held = state.value.held[id] ?: return null
        val copy = held.copy()
        if (state.value.held[id] === held) return SecretReadResult.Found(copy)
        copy.wipe()
        return null
    }

    private fun sharedRefusal(
        flight: Flight,
        seenFinished: Long,
    ): SecretReadResult? = flight.finished.value.takeIf { it.first > seenFinished }?.second

    private suspend fun readThrough(
        key: SecretKey,
        reason: String,
        flight: Flight,
    ): SecretReadResult {
        val id = key.instanceId
        val started = state.value
        val result = device.get(key, reason)
        val generation = started.generationOf(id)
        if (result is SecretReadResult.Found) {
            keepIfUnchanged(id, generation, result.secret)
            flight.finished.update { (it.first + 1L) to null }
        } else {
            flight.finished.update { (it.first + 1L) to result }
        }
        return result
    }

    private fun keepIfUnchanged(
        id: InstanceId,
        generation: Long,
        secret: Secret,
    ) {
        val mine = secret.copy()
        var kept = false
        state.getAndUpdateState {
            kept = it.generationOf(id) == generation
            if (kept) State(it.held + (id to mine), it.generations, it.epoch) else it
        }
        if (!kept) mine.wipe()
    }

    private fun flightOf(id: InstanceId): Flight {
        flights.value[id]?.let { return it }
        val fresh = Flight()
        var winner = fresh
        flights.update { map -> (map[id] ?: fresh).also { winner = it }.let { map + (id to it) } }
        return winner
    }
}
