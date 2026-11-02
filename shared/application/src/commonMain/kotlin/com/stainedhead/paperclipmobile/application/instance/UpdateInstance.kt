package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.application.ValidateInstanceAddress
import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriError
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.SpkiPin
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import kotlinx.coroutines.flow.first

/**
 * The editable fields of a factory (PRD FR-006, FR-016, FR-017). [probed] is the accepted health probe of the NEW
 * address; it is required, and must be for exactly that address, when the address changes.
 */
data class InstanceEdit(
    val displayName: String,
    val address: String,
    val defaultCompanyId: CompanyId?,
    val pins: List<SpkiPin>,
    val probed: ProbeResult.Accepted?,
)

fun Instance.toEdit() = InstanceEdit(displayName, baseUri.value, defaultCompanyId, pins, probed = null)

sealed interface UpdateInstanceResult {
    /** [credentialsCleared] is true when a new address made the stored key unusable (ADR-012). */
    data class Updated(
        val instance: Instance,
        val credentialsCleared: Boolean,
    ) : UpdateInstanceResult

    data object NotFound : UpdateInstanceResult

    /** The address changed but no accepted probe of exactly that address was given. Nothing was changed. */
    data object ProbeRequired : UpdateInstanceResult

    data class InvalidName(
        val issue: InstanceNameIssue,
    ) : UpdateInstanceResult

    data class InvalidAddress(
        val error: BaseUriError,
    ) : UpdateInstanceResult
}

/**
 * Renames or edits a factory. A key must never be sent to a host other than the one that issued it, so a new address
 * deletes the stored credentials and leaves the factory Signed out until it is paired again (ADR-012).
 */
class UpdateInstance(
    private val repository: InstanceRepository,
    private val secrets: SecretStore,
    private val validateAddress: ValidateInstanceAddress,
) {
    suspend operator fun invoke(
        id: InstanceId,
        edit: InstanceEdit,
    ): UpdateInstanceResult {
        if (repository.get(id) == null) return UpdateInstanceResult.NotFound
        val nameIssue = InstanceNames.issueFor(edit.displayName, repository.observeAll().first(), self = id)
        return when (val parsed = validateAddress(edit.address)) {
            is BaseUriResult.Invalid -> {
                UpdateInstanceResult.InvalidAddress(parsed.error)
            }

            is BaseUriResult.Valid -> {
                if (nameIssue != null) UpdateInstanceResult.InvalidName(nameIssue) else apply(id, edit, parsed)
            }
        }
    }

    /** Reads the factory again right before writing, so a key enrollment that finished meanwhile is not reverted. */
    private suspend fun apply(
        id: InstanceId,
        edit: InstanceEdit,
        parsed: BaseUriResult.Valid,
    ): UpdateInstanceResult {
        val current = repository.get(id) ?: return UpdateInstanceResult.NotFound
        val addressChanged = parsed.uri != current.baseUri
        if (addressChanged && !edit.isProbedFor(parsed.uri)) return UpdateInstanceResult.ProbeRequired
        if (addressChanged) secrets.deleteNamespace(current.id)
        val edited =
            current.copy(
                displayName = edit.displayName.trim(),
                baseUri = parsed.uri,
                defaultCompanyId = edit.defaultCompanyId,
                pins = edit.pins,
            )
        val updated =
            if (addressChanged) {
                edited.copy(boardKeyId = null, keyExpiresAt = null, status = InstanceStatus.SignedOut)
            } else {
                edited
            }
        repository.update(updated)
        return UpdateInstanceResult.Updated(updated, credentialsCleared = addressChanged)
    }
}

private fun InstanceEdit.isProbedFor(uri: BaseUri) =
    probed != null && probed.baseUri == uri && probed.report.refusal() == null
