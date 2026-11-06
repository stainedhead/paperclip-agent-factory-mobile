package com.stainedhead.paperclipmobile.infrastructure.api

import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainResult

/** The mapped rows, or null when any one row cannot be mapped: a page is never shown with a card missing. */
internal inline fun <D, T> allOrNone(
    rows: List<D>,
    transform: (D) -> T?,
): List<T>? = rows.map(transform).takeIf { mapped -> mapped.none { it == null } }?.filterNotNull()

/** Rows that each name their company: one of another company makes the whole reply NotFound under this scope. */
internal fun <D> DomainResult<List<D>>.ofCompany(
    scope: CompanyScope,
    companyOf: (D) -> String?,
): DomainResult<List<D>> {
    if (this !is DomainResult.Success) return this
    val refusal = value.firstNotNullOfOrNull { ownershipOf(scope, companyOf(it)) }
    return if (refusal == null) this else DomainResult.Failure(refusal)
}
