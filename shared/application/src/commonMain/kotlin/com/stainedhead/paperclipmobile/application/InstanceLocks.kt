package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One lock per factory for the read-modify-write sections that change its configuration and credentials together:
 * editing it, enrolling a key, signing out, removing it and marking it Signed out (ADR-033). Without it an address edit
 * and a key enrollment could each write over the other's change. The composition root creates one instance and hands it
 * to every use case that needs it; nothing is global. Never hold a lock across a server call.
 */
class InstanceLocks {
    private val registry = Mutex()
    private val locks = mutableMapOf<InstanceId, Mutex>()

    suspend fun <T> withLock(
        id: InstanceId,
        block: suspend () -> T,
    ): T {
        val lock = registry.withLock { locks.getOrPut(id) { Mutex() } }
        return lock.withLock { block() }
    }

    /**
     * Changes fields of a factory without writing back a stale copy: under its lock the factory is read again and
     * [change] is applied to that fresh read, so an address edit or sign out that landed meanwhile survives (F-R-1).
     * Returns the stored factory, or null when it is gone or [change] returned null (nothing is written then).
     */
    suspend fun modify(
        repository: InstanceRepository,
        id: InstanceId,
        change: (Instance) -> Instance?,
    ): Instance? =
        withLock(id) {
            val changed = repository.get(id)?.let(change)
            changed?.also { repository.update(it) }
        }
}
