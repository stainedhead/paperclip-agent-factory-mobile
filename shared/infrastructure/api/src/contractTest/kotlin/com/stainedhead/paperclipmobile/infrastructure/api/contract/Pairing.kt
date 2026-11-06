package com.stainedhead.paperclipmobile.infrastructure.api.contract

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import kotlinx.coroutines.delay

/** What a completed pairing leaves: the challenge (for its id) and the board key, valid after the approval. */
internal class PairedKey(
    val challenge: PairingChallenge,
    val key: String,
)

/**
 * The real pairing flow of the app (PRD FR-020), with the browser's part played by the board session: create a
 * challenge through the adapter, approve it as the signed-in user (`POST /api/cli-auth/challenges/{id}/approve`), and
 * poll through the adapter until the server says approved.
 */
internal object Pairing {
    private const val POLL_ATTEMPTS = 40
    private const val POLL_DELAY_MS = 500L
    private const val OK = 200

    suspend fun pair(
        auth: AuthGateway,
        raw: RawServer,
        session: BoardSession,
        base: BaseUri,
        clientName: String,
    ): PairedKey {
        val challenge = value(auth.createChallenge(base, clientName, null), "createChallenge")
        val approval =
            raw.call(
                "POST",
                "/api/cli-auth/challenges/${challenge.id}/approve",
                """{"token":"${challenge.challengeToken.reveal()}"}""",
                cookie = session.cookie,
            )
        check(approval.status == OK && approval.obj?.get("approved")?.toString() == "true") {
            "Approving the pairing challenge answered ${approval.describe()}"
        }
        repeat(POLL_ATTEMPTS) {
            when (
                val status =
                    value(
                        auth.pollChallenge(base, challenge.id, challenge.challengeToken),
                        "pollChallenge",
                    )
            ) {
                ChallengeStatus.Approved -> return PairedKey(challenge, challenge.pendingBoardToken.reveal())
                ChallengeStatus.Pending -> delay(POLL_DELAY_MS)
                else -> error("The challenge ended as $status")
            }
        }
        error("The challenge was not approved within the poll budget")
    }

    fun <T> value(
        result: DomainResult<T>,
        operation: String,
    ): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> error("$operation failed with ${result.error}")
        }
}
