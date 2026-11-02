package com.stainedhead.paperclipmobile.domain

import kotlin.time.Instant

/** One configured factory: a Paperclip instance this phone can reach (PRD FR-001). Holds no secrets. */
data class Instance(
    val id: InstanceId,
    val displayName: String,
    val baseUri: BaseUri,
    val deploymentMode: DeploymentMode,
    val exposure: Exposure,
    val authMethod: AuthMethod,
    val boardKeyId: String?,
    val keyExpiresAt: Instant?,
    val defaultCompanyId: CompanyId?,
    val lastCompanyId: CompanyId?,
    val pins: List<SpkiPin>,
    val createdAt: Instant,
    val lastConnectedAt: Instant?,
    val sortOrder: Int,
    val status: InstanceStatus,
) {
    /** The company to open: the last one used, else the default one. */
    val activeCompanyId: CompanyId? get() = lastCompanyId ?: defaultCompanyId

    companion object {
        const val MIN_NAME_LENGTH = 1
        const val MAX_NAME_LENGTH = 40
    }
}

/** A company the signed-in person can open, with their role in it. */
data class Company(
    val id: CompanyId,
    val name: String,
    val role: Role,
)

/** The signed-in person, read-only (PRD FR-017: the app keeps no profile of its own). */
data class UserProfile(
    val userId: String,
    val name: String?,
    val email: String?,
    val isInstanceAdmin: Boolean,
)

data class CompanyMembership(
    val companyId: CompanyId,
    val role: Role,
)

/** The answer of `GET /api/cli-auth/me`, with roles already mapped (ADR-011). */
data class BoardAccess(
    val profile: UserProfile,
    val keyId: String?,
    val memberships: List<CompanyMembership>,
)

/** A company as listed by the server, before it is combined with the caller's role. */
data class CompanyRef(
    val id: CompanyId,
    val name: String,
)

/** A board API key of this account (PRD FR-025). */
data class BoardKey(
    val id: String,
    val name: String,
    val createdAt: Instant?,
    val lastUsedAt: Instant?,
    val expiresAt: Instant?,
    val revokedAt: Instant?,
    val isCurrentDevice: Boolean = false,
)

/** Server health as far as an unauthenticated caller can see it (research: health.ts redacts the body). */
data class HealthReport(
    val status: ServerStatus,
    val deploymentMode: DeploymentMode,
    val exposure: Exposure,
    val serverVersion: String?,
    val bootstrapPending: Boolean,
) {
    /** Why this server cannot be added, or null when it can. Fails closed. */
    fun refusal(): ProbeRefusal? =
        when {
            deploymentMode == DeploymentMode.LocalTrusted -> ProbeRefusal.LocalTrusted
            deploymentMode != DeploymentMode.Authenticated -> ProbeRefusal.UnknownDeploymentMode
            bootstrapPending -> ProbeRefusal.BootstrapPending
            status != ServerStatus.Ok -> ProbeRefusal.NotReady
            else -> null
        }
}

enum class ServerStatus {
    Ok,
    Starting,
    Unhealthy,
}

/** Why a server was refused (PRD FR-005, security rules). */
enum class ProbeRefusal {
    LocalTrusted,
    UnknownDeploymentMode,
    BootstrapPending,
    NotReady,
}

/** Where a pairing request stands on the server. */
enum class ChallengeStatus {
    Pending,
    Approved,
    Cancelled,
    Expired,
}

/**
 * A pairing request created on the server (PRD FR-020). [pendingBoardToken] is usable only after approval;
 * [challengeToken] and [approvalUrl] embed a secret, so all three are [Secret]s.
 */
class PairingChallenge(
    val id: String,
    val challengeToken: Secret,
    val pendingBoardToken: Secret,
    val approvalUrl: Secret,
    val expiresAt: Instant,
    val suggestedPollIntervalMs: Long,
) {
    /** Wipes every secret held by this challenge. */
    fun wipe() {
        challengeToken.wipe()
        pendingBoardToken.wipe()
        approvalUrl.wipe()
    }

    override fun toString(): String = "PairingChallenge(id=$id)"
}
