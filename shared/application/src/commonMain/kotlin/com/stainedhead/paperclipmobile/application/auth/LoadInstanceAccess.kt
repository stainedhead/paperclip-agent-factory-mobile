package com.stainedhead.paperclipmobile.application.auth

import com.stainedhead.paperclipmobile.application.retryingRead
import com.stainedhead.paperclipmobile.domain.Company
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.UserProfile
import com.stainedhead.paperclipmobile.domain.port.AuthGateway
import com.stainedhead.paperclipmobile.domain.port.Clock
import com.stainedhead.paperclipmobile.domain.port.InstanceRepository

/** Who the key belongs to and which companies it can open, each with the person's role (PRD FR-022, ADR-011). */
data class InstanceAccess(
    val profile: UserProfile,
    val companies: List<Company>,
)

/**
 * Reads `GET /api/cli-auth/me` and the company list. A success records the connection time; a 401 marks the factory
 * Signed out and keeps its configuration (PRD FR-023). Reads are retried (FR-096).
 */
class LoadInstanceAccess(
    private val repository: InstanceRepository,
    private val auth: AuthGateway,
    private val signedOut: SignedOutMarker,
    private val clock: Clock,
) {
    suspend operator fun invoke(instanceId: InstanceId): DomainResult<InstanceAccess> {
        val me = retryingRead { auth.me(instanceId) }
        signedOut.noteIfUnauthorized(instanceId, me)
        if (me is DomainResult.Failure) return me
        val companies = retryingRead { auth.listCompanies(instanceId) }
        signedOut.noteIfUnauthorized(instanceId, companies)
        if (companies is DomainResult.Failure) return companies
        val access = (me as DomainResult.Success).value
        val roles = access.memberships.associate { it.companyId to it.role }
        val fallback = if (access.profile.isInstanceAdmin) Role.Owner else Role.Viewer
        repository.get(instanceId)?.let { repository.update(it.copy(lastConnectedAt = clock.now())) }
        return DomainResult.Success(
            InstanceAccess(
                profile = access.profile,
                companies =
                    (companies as DomainResult.Success).value.map {
                        Company(
                            it.id,
                            it.name,
                            roles[it.id] ?: fallback,
                        )
                    },
            ),
        )
    }
}
