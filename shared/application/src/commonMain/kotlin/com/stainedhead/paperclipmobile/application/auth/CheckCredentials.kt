package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore

enum class CredentialState {
    Usable,
    Missing,

    /** The device key was invalidated, for example by a new fingerprint (PRD FR-015). */
    NeedsRePairing,

    /** The person declined device authentication; nothing changed. */
    Denied,
}

/**
 * Checks that a factory's board key can still be read. An invalidated key moves the factory to "needs re-pairing" and
 * never falls back to unprotected storage (PRD FR-015).
 */
class CheckCredentials(
    private val repository: InstanceRepository,
    private val secrets: SecretStore,
) {
    suspend operator fun invoke(id: InstanceId): CredentialState {
        val instance = repository.get(id) ?: return CredentialState.Missing
        return when (val read = secrets.get(SecretKey(id, SecretName.BoardToken), "check credentials")) {
            is SecretReadResult.Found -> {
                read.secret.wipe()
                CredentialState.Usable
            }

            SecretReadResult.Missing -> {
                CredentialState.Missing
            }

            SecretReadResult.Denied -> {
                CredentialState.Denied
            }

            SecretReadResult.Invalidated -> {
                repository.update(instance.copy(status = InstanceStatus.NeedsRePairing))
                CredentialState.NeedsRePairing
            }
        }
    }
}
