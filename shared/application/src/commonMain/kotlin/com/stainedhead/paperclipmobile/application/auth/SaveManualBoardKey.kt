package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository

private const val BOARD_KEY_PREFIX = "pcp_board_"

/**
 * Stores a board key the person pasted (PRD FR-021). The key is checked with `GET /api/cli-auth/me` first; if that
 * fails, nothing is stored. The caller's copy of the key is wiped when this returns.
 */
class SaveManualBoardKey(
    private val repository: InstanceRepository,
    private val auth: AuthGateway,
    private val enrollment: KeyEnrollment,
) {
    suspend operator fun invoke(
        instanceId: InstanceId,
        key: Secret,
    ): SignInResult {
        try {
            val instance = repository.get(instanceId) ?: return SignInResult.NotFound
            if (!key.startsWith(BOARD_KEY_PREFIX)) return SignInResult.InvalidKey
            return when (val me = auth.meWithKey(instance.baseUri, key)) {
                is DomainResult.Failure -> SignInResult.Failed(me.error)
                is DomainResult.Success -> enrollment.complete(instance, key, AuthMethod.ManualBoardKey, me.value.keyId)
            }
        } finally {
            key.wipe()
        }
    }
}
