package com.stainedhead.paperclipmobile.infrastructure.persistence

import com.stainedhead.paperclipmobile.domain.AuthMethod
import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.DeploymentMode
import com.stainedhead.paperclipmobile.domain.Exposure
import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.InstanceStatus
import com.stainedhead.paperclipmobile.domain.SpkiPin
import com.stainedhead.paperclipmobile.infrastructure.persistence.db.Instance_config
import kotlin.time.Instant

private val whitespace = Regex("\\s+")

/**
 * Turns a stored row into an [Instance], or null when the row cannot be read (a damaged value). A row that cannot be
 * read is skipped rather than failing the whole list. Unknown enum names, which another app version may have written,
 * read as the safe value: a factory stays listed but is not trusted as signed in.
 */
internal fun Instance_config.toInstanceOrNull(): Instance? =
    try {
        // A stored address was validated when it was added; debug builds may have stored http://localhost.
        val uri = (BaseUri.parse(base_uri, allowInsecureLocal = true) as? BaseUriResult.Valid)?.uri
        uri?.let { toInstance(it) }
    } catch (_: IllegalArgumentException) {
        null
    }

private fun Instance_config.toInstance(uri: BaseUri) =
    Instance(
        id = InstanceId(id),
        displayName = display_name,
        baseUri = uri,
        deploymentMode = enumOrDefault(deployment_mode, DeploymentMode.Unknown),
        exposure = enumOrDefault(exposure, Exposure.Unknown),
        authMethod = enumOrDefault(auth_method, AuthMethod.ManualBoardKey),
        boardKeyId = board_key_id,
        keyExpiresAt = key_expires_at?.let { Instant.parse(it) },
        defaultCompanyId = default_company_id?.let { CompanyId(it) },
        lastCompanyId = last_company_id?.let { CompanyId(it) },
        pins = pins.split(whitespace).mapNotNull { SpkiPin.parse(it) },
        createdAt = Instant.parse(created_at),
        lastConnectedAt = last_connected_at?.let { Instant.parse(it) },
        sortOrder = sort_order.toInt(),
        status = enumOrDefault(status, InstanceStatus.SignedOut),
    )

internal inline fun <reified E : Enum<E>> enumOrDefault(
    name: String,
    default: E,
): E = enumValues<E>().firstOrNull { it.name == name } ?: default

internal fun List<SpkiPin>.toStoredPins(): String = joinToString(" ") { it.value }
