package com.stainedhead.paperclipmobile.infrastructure.api.dto

import com.stainedhead.paperclipmobile.domain.BaseUri
import com.stainedhead.paperclipmobile.domain.BoardAccess
import com.stainedhead.paperclipmobile.domain.BoardKey
import com.stainedhead.paperclipmobile.domain.ChallengeStatus
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyMembership
import com.stainedhead.paperclipmobile.domain.CompanyRef
import com.stainedhead.paperclipmobile.domain.PairingChallenge
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.Secret
import com.stainedhead.paperclipmobile.domain.UserProfile
import com.stainedhead.paperclipmobile.infrastructure.api.parseInstantOrNull
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

// Shapes from server/src/routes/access.ts and server/src/services/board-auth.ts (research Q2). DTOs that hold a
// credential are plain classes, not data classes, so no generated toString can print one.

/** Body of `POST /api/cli-auth/challenges` (packages/shared/src/validators/access.ts:73). */
@Serializable
internal class CreateChallengeBody(
    val command: String,
    val clientName: String,
    val requestedAccess: String,
    val requestedCompanyId: String?,
)

/** `201` of `POST /api/cli-auth/challenges`. [token], [boardApiToken] and [approvalUrl] are secrets. */
@Serializable
internal class ChallengeCreatedDto(
    val id: String,
    val token: String,
    val boardApiToken: String,
    val approvalUrl: String? = null,
    val expiresAt: String,
    val suggestedPollIntervalMs: Long = DEFAULT_POLL_MS,
)

private const val DEFAULT_POLL_MS = 1_000L

private fun ChallengeCreatedDto.hasBlankField(): Boolean = listOf(id, token, boardApiToken).any { it.isBlank() }

/**
 * The challenge becomes a domain value. The server returns `approvalUrl: null` when it cannot derive its own origin
 * (access.ts:2802); the approval page is then the instance's address plus `/cli-auth/{id}?token={token}`, which is the
 * `approvalPath` the server builds (access.ts:167).
 *
 * The server builds `approvalUrl` from request headers (`x-forwarded-proto`, `x-forwarded-host`, access.ts:73-80), so
 * behind a proxy it can be `http://` or a foreign host. It is used only when it has the instance's own scheme, host and
 * port and no user info; otherwise the URL is built here from [baseUri] (review1b F-A-3, ADR-076).
 */
internal fun ChallengeCreatedDto.toDomain(baseUri: BaseUri): PairingChallenge? {
    val expires = parseInstantOrNull(expiresAt)
    if (expires == null || hasBlankField()) return null
    val approval = approvalUrl?.takeIf { ApprovalOrigin.isOwn(it, baseUri) } ?: ApprovalOrigin.built(baseUri, id, token)
    return PairingChallenge(
        id = id,
        challengeToken = Secret.of(token),
        pendingBoardToken = Secret.of(boardApiToken),
        approvalUrl = Secret.of(approval),
        expiresAt = expires,
        suggestedPollIntervalMs = suggestedPollIntervalMs.coerceAtLeast(1),
    )
}

@Serializable
internal data class ChallengePolledDto(
    val status: String,
)

/** An unknown status is not guessed: it is null, and the caller reports Malformed. */
internal fun ChallengePolledDto.toDomain(): ChallengeStatus? =
    when (status) {
        "pending" -> ChallengeStatus.Pending
        "approved" -> ChallengeStatus.Approved
        "cancelled" -> ChallengeStatus.Cancelled
        "expired" -> ChallengeStatus.Expired
        else -> null
    }

@Serializable
internal data class UserDto(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
)

@Serializable
internal data class MembershipDto(
    val companyId: String,
    val membershipRole: String? = null,
    val status: String? = null,
)

/** `GET /api/cli-auth/me` (access.ts:2889). */
@Serializable
internal data class MeDto(
    val user: UserDto? = null,
    val userId: String,
    val isInstanceAdmin: Boolean = false,
    val memberships: List<MembershipDto> = emptyList(),
    val keyId: String? = null,
)

/** Roles map fail closed (ADR-011); a membership that is not active is dropped. */
internal fun MeDto.toDomain(): BoardAccess =
    BoardAccess(
        profile = UserProfile(userId, user?.name, user?.email, isInstanceAdmin),
        keyId = keyId,
        memberships =
            memberships
                .filter { it.status == null || it.status == "active" }
                .map {
                    CompanyMembership(
                        CompanyId(it.companyId),
                        Role.fromServer(it.membershipRole, isInstanceAdmin),
                    )
                },
    )

/** One company of `GET /api/companies`; the app reads only the id and name. */
@Serializable
internal data class CompanyDto(
    val id: String,
    val name: String,
)

internal fun CompanyDto.toDomain(): CompanyRef = CompanyRef(CompanyId(id), name)

/** One key of `GET /api/board-api-keys` (board-auth.ts:262). Timestamps stay strings until mapped. */
@Serializable
internal data class BoardKeyDto(
    val id: String,
    val name: String = "",
    val createdAt: String? = null,
    val lastUsedAt: String? = null,
    val revokedAt: String? = null,
    val expiresAt: String? = null,
)

/** [currentKeyId] is the key this device holds, when the caller knows it. */
internal fun BoardKeyDto.toDomain(currentKeyId: String?): BoardKey =
    BoardKey(
        id = id,
        name = name,
        createdAt = parseInstantOrNull(createdAt),
        lastUsedAt = parseInstantOrNull(lastUsedAt),
        expiresAt = parseInstantOrNull(expiresAt),
        revokedAt = parseInstantOrNull(revokedAt),
        isCurrentDevice = currentKeyId != null && id == currentKeyId,
    )

/** The acknowledgement of a revoke: `{ok, keyId}` or `{revoked, keyId}`. Only that it is a JSON object matters. */
@Serializable
internal data class AckDto(
    val ok: Boolean? = null,
    val revoked: Boolean? = null,
)

/** Decides whether a server-supplied approval address is the instance's own page, and builds the local one. */
internal object ApprovalOrigin {
    private const val HTTPS = "https"
    private const val HTTPS_PORT = "443"
    private const val HTTP_PORT = "80"
    private val AUTHORITY_END = charArrayOf('/', '?', '#')
    private val FORBIDDEN = charArrayOf('\\', '@', ' ')

    fun built(
        baseUri: BaseUri,
        id: String,
        token: String,
    ): String = "${baseUri.value}/cli-auth/${id.encodeURLPathPart()}?token=${token.encodeURLParameter()}"

    /** True when [url] has exactly the scheme, host and port of [baseUri] and carries no credentials or odd bytes. */
    fun isOwn(
        url: String,
        baseUri: BaseUri,
    ): Boolean {
        val own = originOf(baseUri.value) ?: return false
        val candidate = originOf(url) ?: return false
        return candidate == own && url.none { it.isISOControl() }
    }

    /** `scheme://host:port` in lower case with the default port made explicit, or null for anything unusual. */
    private fun originOf(text: String): String? {
        val schemeEnd = text.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = text.substring(0, schemeEnd).lowercase()
        val rest = text.substring(schemeEnd + 3)
        val authority = rest.substring(0, rest.indexOfAny(AUTHORITY_END).let { if (it < 0) rest.length else it })
        if (authority.isEmpty() || authority.any { it in FORBIDDEN }) return null
        val hostAndPort = authority.lowercase()
        val hasPort = hostAndPort.substringAfterLast(':', "").let { it.isNotEmpty() && it.all(Char::isDigit) }
        val explicit =
            when {
                hasPort -> hostAndPort
                scheme == HTTPS -> "$hostAndPort:$HTTPS_PORT"
                else -> "$hostAndPort:$HTTP_PORT"
            }
        return "$scheme://$explicit"
    }
}
