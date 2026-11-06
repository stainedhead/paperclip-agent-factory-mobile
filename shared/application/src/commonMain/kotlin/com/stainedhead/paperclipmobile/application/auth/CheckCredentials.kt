package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.InstanceLocks
import com.stainedhead.paperclipmobile.domain.Instance
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

    /** The device key was invalidated, for example by changed biometrics on iPhone or a removed screen lock on Android (PRD FR-015, ADR-149). */
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
    private val locks: InstanceLocks,
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
                markNeedsRePairing(instance)
                CredentialState.NeedsRePairing
            }
        }
    }

    /**
     * Applied to a fresh read. A factory whose address or key changed since the check (an edit or a new pairing) no
     * longer holds the key that was found invalid, so it is left as it is.
     */
    private suspend fun markNeedsRePairing(checked: Instance) {
        locks.modify(repository, checked.id) {
            if (it.baseUri == checked.baseUri && it.boardKeyId == checked.boardKeyId) {
                it.copy(status = InstanceStatus.NeedsRePairing)
            } else {
                null
            }
        }
    }
}
