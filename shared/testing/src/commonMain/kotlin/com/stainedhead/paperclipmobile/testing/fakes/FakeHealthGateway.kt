package com.stainedhead.paperclipmobile.testing.fakes

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.ServerStatus
import com.stainedhead.paperclipmobile.domain.port.HealthGateway

/**
 * A scripted health endpoint: [response] answers every address, and [responses] answers a specific address (the
 * contract suite needs several situations from one fake).
 */
class FakeHealthGateway(
    var response: DomainResult<HealthReport> = DomainResult.Success(healthy()),
) : HealthGateway {
    val responses = mutableMapOf<BaseUri, DomainResult<HealthReport>>()

    var probeCalls = 0
        private set

    var lastProbed: BaseUri? = null
        private set

    override suspend fun probe(baseUri: BaseUri): DomainResult<HealthReport> {
        probeCalls += 1
        lastProbed = baseUri
        return responses[baseUri] ?: response
    }

    companion object {
        fun healthy(
            mode: DeploymentMode = DeploymentMode.Authenticated,
            exposure: Exposure = Exposure.Private,
        ) = HealthReport(
            status = ServerStatus.Ok,
            deploymentMode = mode,
            exposure = exposure,
            serverVersion = null,
            bootstrapPending = false,
        )
    }
}
