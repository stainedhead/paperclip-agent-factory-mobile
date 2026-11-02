package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.IssueId
import com.stainedhead.paperclipmobile.infrastructure.api.dto.IssueOwnerDto
import io.ktor.http.HttpMethod

/**
 * Proves that an issue belongs to the scope's company before a call that has no company in its path or reply
 * (`/issues/{id}/runs`, `/issues/{id}/interactions`). An id that is not a uuid is NotFound without a request; an
 * issue of another company is NotFound under this scope (ADR-057, ADR-061).
 */
internal suspend fun CompanyCalls.requireIssue(
    scope: CompanyScope,
    issueId: IssueId,
): DomainResult<Unit> {
    if (!isServerId(issueId.value)) return DomainResult.Failure(DomainError.NotFound)
    return send(scope.instanceId, HttpMethod.Get, listOf("issues", issueId.value))
        .decode<IssueOwnerDto>()
        .ownedBy(scope) { it.companyId }
        .map { }
}
