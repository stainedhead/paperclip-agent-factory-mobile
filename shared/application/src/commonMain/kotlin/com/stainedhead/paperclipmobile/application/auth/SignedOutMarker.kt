package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository

/** A 401 marks the factory Signed out and keeps its configuration (PRD FR-023). */
class SignedOutMarker(
    private val repository: InstanceRepository,
) {
    suspend fun <T> noteIfUnauthorized(
        id: InstanceId,
        result: DomainResult<T>,
    ) {
        if (result is DomainResult.Failure && result.error == DomainError.Unauthorized) {
            // Only a factory still marked Signed in is written, so repeated 401s cost one status write (FR-023).
            repository.get(id)?.takeIf { it.status != InstanceStatus.SignedOut }?.let {
                repository.update(it.copy(status = InstanceStatus.SignedOut))
            }
        }
    }
}
