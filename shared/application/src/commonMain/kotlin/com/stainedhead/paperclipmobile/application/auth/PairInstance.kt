package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.port.AppSettingsStore
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.PairingBrowser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

private const val MIN_POLL_INTERVAL_MS = 250L
private const val MAX_POLL_INTERVAL_MS = 10_000L
private val MAX_WAIT = 15.minutes

/**
 * Pairs this phone with a factory (PRD FR-020, ADR-012): creates a challenge, opens the approval page, polls at the
 * server's suggested interval until the person approves, cancels or the challenge expires, and only then writes the
 * board key to the secret store. Every in-memory copy of a secret is wiped on every exit, including cancellation.
 */
class PairInstance(
    private val repository: InstanceRepository,
    private val auth: AuthGateway,
    private val browser: PairingBrowser,
    private val clock: Clock,
    private val settings: AppSettingsStore,
    private val enrollment: KeyEnrollment,
) {
    suspend operator fun invoke(
        instanceId: InstanceId,
        requestedCompanyId: CompanyId?,
    ): SignInResult {
        val instance = repository.get(instanceId) ?: return SignInResult.NotFound
        val clientName = settings.observe().first().deviceName
        val challenge =
            when (val created = auth.createChallenge(instance.baseUri, clientName, requestedCompanyId)) {
                is DomainResult.Failure -> return SignInResult.Failed(created.error)
                is DomainResult.Success -> created.value
            }
        try {
            browser.open(challenge.approvalUrl)
            return waitForApproval(instance, challenge)
        } finally {
            challenge.wipe()
        }
    }

    /**
     * Waits for the person, bounded by the challenge's expiry and never longer than [MAX_WAIT] whatever the server
     * says; the poll interval is clamped too, so a bad value cannot hang the screen.
     */
    private suspend fun waitForApproval(
        instance: Instance,
        challenge: PairingChallenge,
    ): SignInResult {
        val budget = minOf(challenge.expiresAt - clock.now(), MAX_WAIT)
        val ended = withTimeoutOrNull(budget) { pollUntilDecided(instance, challenge) } ?: return SignInResult.Expired
        return when (ended) {
            is Decided -> ended.result
            Approved -> enrollment.complete(instance, challenge.pendingBoardToken, AuthMethod.PairedBoardKey, null)
        }
    }

    private suspend fun pollUntilDecided(
        instance: Instance,
        challenge: PairingChallenge,
    ): PollEnd {
        val interval = challenge.suggestedPollIntervalMs.coerceIn(MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS)
        while (true) {
            delay(interval)
            pollOnce(instance, challenge)?.let { return it }
        }
    }

    /** Returns how the wait ended, or null to keep waiting. */
    private suspend fun pollOnce(
        instance: Instance,
        challenge: PairingChallenge,
    ): PollEnd? =
        when (val poll = auth.pollChallenge(instance.baseUri, challenge.id, challenge.challengeToken)) {
            is DomainResult.Failure -> {
                if (poll.isKeepWaiting()) null else Decided(SignInResult.Failed(poll.error))
            }

            is DomainResult.Success -> {
                when (poll.value) {
                    ChallengeStatus.Pending -> null
                    ChallengeStatus.Cancelled -> Decided(SignInResult.Cancelled)
                    ChallengeStatus.Expired -> Decided(SignInResult.Expired)
                    ChallengeStatus.Approved -> Approved
                }
            }
        }

    /** A flaky network or a rate limit while waiting must not abort a pairing the person is approving. */
    private fun DomainResult.Failure.isKeepWaiting() =
        error == DomainError.Unreachable || error == DomainError.ConnectionLost || error == DomainError.Timeout ||
            error == DomainError.ServerError || error == DomainError.RateLimited
}

/** How the polling loop ended. Approval is stored outside the timed wait so a timeout cannot lose a key. */
private sealed interface PollEnd

private data object Approved : PollEnd

private data class Decided(
    val result: SignInResult,
) : PollEnd
