package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult

private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/**
 * Server ids are UUID columns. An id that is not one cannot exist, and the server would answer a malformed id with a
 * database error rather than a 404 (research, ADR-057), so such an id is NotFound here and no request is made.
 */
internal fun isServerId(id: String): Boolean = UUID_PATTERN.matches(id)

/**
 * Approvals and decisions are addressed by id with no company in the path. The reply says which company owns the row;
 * a row of another company is NotFound under this scope, and a reply that does not say is Malformed (ADR-057).
 */
internal fun ownershipOf(
    scope: CompanyScope,
    companyId: String?,
): DomainError? =
    when {
        companyId.isNullOrBlank() -> DomainError.Malformed
        companyId != scope.companyId.value -> DomainError.NotFound
        else -> null
    }

/** Fails with [ownershipOf] when the decoded row does not belong to [scope]. */
internal fun <T> DomainResult<T>.ownedBy(
    scope: CompanyScope,
    companyOf: (T) -> String?,
): DomainResult<T> {
    if (this !is DomainResult.Success) return this
    val refusal = ownershipOf(scope, companyOf(value)) ?: return this
    return DomainResult.Failure(refusal)
}
