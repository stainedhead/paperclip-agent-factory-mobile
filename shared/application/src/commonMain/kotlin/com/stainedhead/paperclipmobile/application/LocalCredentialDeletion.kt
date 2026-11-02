package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Deletes every secret of [id] and then runs [afterwards] (the configuration change that goes with it), shielded from
 * cancellation. A caller whose screen or scope goes away while it awaits a server call must not leave a credential
 * behind (PRD FR-006, FR-024), so remove and sign out both finish their local work here.
 */
internal suspend fun deleteCredentialsLocally(
    secrets: SecretStore,
    id: InstanceId,
    afterwards: suspend () -> Unit,
) {
    withContext(NonCancellable) {
        secrets.deleteNamespace(id)
        afterwards()
    }
}
