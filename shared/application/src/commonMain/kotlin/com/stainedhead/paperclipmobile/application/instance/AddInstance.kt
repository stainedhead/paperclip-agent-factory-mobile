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

    /** The probe was taken for another address than the one being added. Nothing was written. */
    data object ProbeRequired : AddInstanceResult
}

/**
 * Adds a factory after a successful probe of exactly that address (PRD FR-001, FR-003, FR-005): the probe result names
 * the address it was taken for, a result for any other address is refused, and `local_trusted` is refused here, not
 * only in the UI. The same address may be added more than once (ADR-030). It starts Signed out until it is paired.
 */
class AddInstance(
    private val repository: InstanceRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val validateAddress: ValidateInstanceAddress,
) {
    suspend operator fun invoke(
        name: String,
        address: String,
        probed: ProbeResult.Accepted,
    ): AddInstanceResult =
        when (val parsed = validateAddress(address)) {
            is BaseUriResult.Invalid -> {
                AddInstanceResult.InvalidAddress(parsed.error)
            }

            is BaseUriResult.Valid -> {
                if (probed.baseUri != parsed.uri) AddInstanceResult.ProbeRequired else addValid(name, parsed, probed)
            }
        }

    private suspend fun addValid(
        name: String,
        address: BaseUriResult.Valid,
        probed: ProbeResult.Accepted,
    ): AddInstanceResult {
        val existing = repository.observeAll().first()
        val nameIssue = InstanceNames.issueFor(name, existing)
        val refusal = probed.report.refusal()
        if (nameIssue != null) return AddInstanceResult.InvalidName(nameIssue)
        if (refusal != null) return AddInstanceResult.Refused(refusal)
        val nextSortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val instance = build(name.trim(), address, probed.report, nextSortOrder)
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
