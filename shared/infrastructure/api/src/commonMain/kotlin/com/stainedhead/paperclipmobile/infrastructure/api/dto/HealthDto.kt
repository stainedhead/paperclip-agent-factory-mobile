package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.ServerStatus
import kotlinx.serialization.Serializable

/** `GET /api/health` (server/src/routes/health.ts). Every field is optional: the redacted body omits several. */
@Serializable
internal data class HealthDto(
    val status: String? = null,
    val deploymentMode: String? = null,
    val deploymentExposure: String? = null,
    val bootstrapStatus: String? = null,
    val version: String? = null,
    val serverVersion: String? = null,
)

internal fun HealthDto.toDomain(): HealthReport =
    HealthReport(
        status =
            when (status) {
                "ok" -> ServerStatus.Ok
                "starting" -> ServerStatus.Starting
                else -> ServerStatus.Unhealthy
            },
        deploymentMode = DeploymentMode.fromServer(deploymentMode),
        exposure = Exposure.fromServer(deploymentExposure),
        serverVersion = (serverVersion ?: version)?.takeIf { it.isNotBlank() },
        bootstrapPending = bootstrapStatus == "bootstrap_pending",
    )
