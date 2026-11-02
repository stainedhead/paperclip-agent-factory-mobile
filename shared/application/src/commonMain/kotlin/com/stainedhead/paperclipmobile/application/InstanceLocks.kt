package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.InstanceId
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
}
