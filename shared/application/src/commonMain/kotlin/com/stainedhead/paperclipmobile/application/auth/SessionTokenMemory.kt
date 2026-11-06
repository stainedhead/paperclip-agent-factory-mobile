package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The in-memory part of the session cache (ADR-145): one board token per factory, nothing else, never written out.
 * Every change swaps one immutable [State] in an atomic cell, so [forgetAll] from a lifecycle callback needs no lock.
 * A factory's generation moves each time its entry is dropped, so a read that began before the drop can tell.
 */
internal class SessionTokenMemory {
    private class State(
        val held: Map<InstanceId, Secret> = emptyMap(),
        val generations: Map<InstanceId, Long> = emptyMap(),
        val epoch: Long = 0,
    ) {
        fun generationOf(id: InstanceId): Long = (generations[id] ?: 0L) + epoch
    }

    private val state = MutableStateFlow(State())

    fun generationOf(id: InstanceId): Long = state.value.generationOf(id)

    /**
     * A copy of the held token, or null. The copy is checked against the entry afterwards: a clear wipes the entry only
     * after removing it, so an entry that is still held proves the copy was complete.
     */
    fun copyOf(id: InstanceId): Secret? {
        val held = state.value.held[id] ?: return null
        val copy = held.copy()
        if (state.value.held[id] === held) return copy
        copy.wipe()
        return null
    }

    /** Keeps a copy of [secret] unless [id] was dropped since [generation] was read. Answers whether it was kept. */
    fun keepIfUnchanged(
        id: InstanceId,
        generation: Long,
        secret: Secret,
    ): Boolean {
        val mine = secret.copy()
        var kept = false
        swap {
            kept = it.generationOf(id) == generation
            if (kept) State(it.held + (id to mine), it.generations, it.epoch) else it
        }
        if (!kept) mine.wipe()
        return kept
    }

    fun forget(id: InstanceId) {
        val before =
            swap {
                State(it.held - id, it.generations + (id to ((it.generations[id] ?: 0L) + 1L)), it.epoch)
            }
        before.held[id]?.wipe()
    }

    fun forgetAll() {
        val before = swap { State(emptyMap(), it.generations, it.epoch + 1L) }
        before.held.values.forEach { it.wipe() }
    }

    /** Applies [next] to the current state until the swap wins, and answers the state it replaced. */
    private fun swap(next: (State) -> State): State {
        while (true) {
            val current = state.value
            if (state.compareAndSet(current, next(current))) return current
        }
    }
}
