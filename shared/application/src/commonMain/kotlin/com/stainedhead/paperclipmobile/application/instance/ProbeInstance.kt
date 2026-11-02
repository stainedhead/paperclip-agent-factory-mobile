package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.domain.port.HealthGateway

/** What a probe found. Every result names the [baseUri] it was taken for, so a late one is never misapplied. */
sealed interface ProbeResult {
    val baseUri: BaseUri

    data class Accepted(
        override val baseUri: BaseUri,
        val report: HealthReport,
    ) : ProbeResult

    /** The server is reachable but must not be added. */
    data class Refused(
        override val baseUri: BaseUri,
        val reason: ProbeRefusal,
        val report: HealthReport,
    ) : ProbeResult

    data class Failed(
        override val baseUri: BaseUri,
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
                ProbeResult.Failed(baseUri, result.error)
            }

            is DomainResult.Success -> {
                val refusal = result.value.refusal()
                if (refusal == null) {
                    ProbeResult.Accepted(baseUri, result.value)
                } else {
                    ProbeResult.Refused(baseUri, refusal, result.value)
                }
            }
        }
}
