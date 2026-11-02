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

/**
 * A list of rows that each name their company proves whose issue it is; an empty one proves nothing, so the issue is
 * asked once more. A thread that really is empty costs one extra read; an issue of another company is NotFound.
 */
internal suspend fun <T> CompanyCalls.provingOwnership(
    scope: CompanyScope,
    issueId: IssueId,
    rows: DomainResult<List<T>>,
): DomainResult<List<T>> {
    if (rows !is DomainResult.Success || rows.value.isNotEmpty()) return rows
    val owned = requireIssue(scope, issueId)
    return if (owned is DomainResult.Failure) owned else rows
}
