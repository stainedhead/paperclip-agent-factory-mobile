package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.application.ValidateInstanceAddress
import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.BaseUriError
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.HealthReport
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.ProbeRefusal
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.IdGenerator
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import kotlinx.coroutines.flow.first

sealed interface AddInstanceResult {
    data class Added(
        val instance: Instance,
    ) : AddInstanceResult

    data class InvalidAddress(
        val error: BaseUriError,
    ) : AddInstanceResult

    data class InvalidName(
        val issue: InstanceNameIssue,
    ) : AddInstanceResult

    data class Refused(
        val reason: ProbeRefusal,
    ) : AddInstanceResult
}

/** Adds a factory after a successful probe (PRD FR-001, FR-003, FR-005). It starts Signed out until it is paired. */
class AddInstance(
    private val repository: InstanceRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val validateAddress: ValidateInstanceAddress,
) {
    suspend operator fun invoke(
        name: String,
        address: String,
        probed: HealthReport,
    ): AddInstanceResult =
        when (val parsed = validateAddress(address)) {
            is BaseUriResult.Invalid -> AddInstanceResult.InvalidAddress(parsed.error)
            is BaseUriResult.Valid -> addValid(name, parsed, probed)
        }

    private suspend fun addValid(
        name: String,
        address: BaseUriResult.Valid,
        probed: HealthReport,
    ): AddInstanceResult {
        val existing = repository.observeAll().first()
        val nameIssue = InstanceNames.issueFor(name, existing)
        val refusal = probed.refusal()
        if (nameIssue != null) return AddInstanceResult.InvalidName(nameIssue)
        if (refusal != null) return AddInstanceResult.Refused(refusal)
        val nextSortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val instance = build(name.trim(), address, probed, nextSortOrder)
        repository.add(instance)
        return AddInstanceResult.Added(instance)
    }

    private fun build(
        name: String,
        address: BaseUriResult.Valid,
        probed: HealthReport,
        nextSortOrder: Int,
    ) = Instance(
        id = InstanceId(ids.generate()),
        displayName = name,
        baseUri = address.uri,
        deploymentMode = probed.deploymentMode,
        exposure = probed.exposure,
        authMethod = AuthMethod.PairedBoardKey,
        boardKeyId = null,
        keyExpiresAt = null,
        defaultCompanyId = null,
        lastCompanyId = null,
        pins = emptyList(),
        createdAt = clock.now(),
        lastConnectedAt = null,
        sortOrder = nextSortOrder,
        status = InstanceStatus.SignedOut,
    )
}
