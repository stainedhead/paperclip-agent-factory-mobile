package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.domain.port.HealthGateway

sealed interface ProbeResult {
    data class Accepted(
        val report: HealthReport,
    ) : ProbeResult

    /** The server is reachable but must not be added. */
    data class Refused(
        val reason: ProbeRefusal,
        val report: HealthReport,
    ) : ProbeResult

    data class Failed(
        val error: DomainError,
    ) : ProbeResult
}

/**
 * Reads `GET /api/health` and decides whether this server may be added (PRD FR-005). `local_trusted` and anything
 * unrecognised are refused. This is an interactive read, so it is not retried: the person taps retry.
 */
class ProbeInstance(
    private val gateway: HealthGateway,
) {
    suspend operator fun invoke(baseUri: BaseUri): ProbeResult =
        when (val result = gateway.probe(baseUri)) {
            is DomainResult.Failure -> {
                ProbeResult.Failed(result.error)
            }

            is DomainResult.Success -> {
                val refusal = result.value.refusal()
                if (refusal == null) ProbeResult.Accepted(result.value) else ProbeResult.Refused(refusal, result.value)
            }
        }
}
