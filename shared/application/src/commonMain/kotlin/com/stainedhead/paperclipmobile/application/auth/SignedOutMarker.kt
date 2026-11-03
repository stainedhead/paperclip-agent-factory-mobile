package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.InstanceLocks
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository

/**
 * A 401 marks the factory Signed out and keeps its configuration (PRD FR-023). A key the phone could not use because it
 * was invalidated marks it as needing re-pairing (FR-015). A declined device prompt changes nothing (review1b F-A-2).
 */
class SignedOutMarker(
    private val repository: InstanceRepository,
    private val locks: InstanceLocks,
) {
    suspend fun <T> noteIfUnauthorized(
        id: InstanceId,
        result: DomainResult<T>,
    ) {
        val error = (result as? DomainResult.Failure)?.error
        when (error) {
            DomainError.Unauthorized -> mark(id, InstanceStatus.SignedOut) { it != InstanceStatus.SignedOut }
            DomainError.KeyInvalidated -> mark(id, InstanceStatus.NeedsRePairing) { it.isSignedIn() }
            else -> Unit
        }
    }

    /** Only a factory that is not already in the target state is written, so repeats cost one write (FR-023). */
    private suspend fun mark(
        id: InstanceId,
        status: InstanceStatus,
        applies: (InstanceStatus) -> Boolean,
    ) {
        locks.withLock(id) {
            repository.get(id)?.takeIf { applies(it.status) }?.let {
                repository.update(it.copy(status = status))
            }
        }
    }
}

private fun InstanceStatus.isSignedIn() = this == InstanceStatus.SignedIn
