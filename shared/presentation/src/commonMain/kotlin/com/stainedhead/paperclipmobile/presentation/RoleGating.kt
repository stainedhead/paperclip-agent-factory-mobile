package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.ResolveCompanyContext
import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Why [action] is unavailable, as a key: no connection first, then the role. Null while it is available (F-B-5). */
internal fun disabledReason(
    online: Boolean,
    role: Role?,
    action: Action,
): ActionsDisabledReason? =
    when {
        !online -> ActionsDisabledReason.Offline
        role == null || Permissions.allows(role, action) -> null
        role == Role.Viewer -> ActionsDisabledReason.ViewerRole
        else -> ActionsDisabledReason.OwnerRole
    }

/** A 403 on a mutation: the role on the server is no longer the one the screen resolved (F-D-2). */
internal fun MutationResult<*>.wasForbidden() = this is MutationResult.Failed && error == DomainError.Forbidden

/** The one rule behind every `can...` flag: a connection and a role the matrix allows (ADR-019). */
internal fun mayAct(
    online: Boolean,
    role: Role?,
    action: Action,
): Boolean = online && role != null && Permissions.allows(role, action)

/** A read that answers 403 means the same: the role is read once more, and only for that error (F-D-2). */
internal suspend fun ContextSource.roleAfter(error: DomainError): Role? =
    if (error == DomainError.Forbidden) reResolve()?.role else null

/** Resolves the role again after a 403, keeping the old one if that read fails (F-D-2). One read, no repeat. */
internal suspend fun <M> MutableStateFlow<M>.refreshContext(
    resolve: ResolveCompanyContext,
    scope: CompanyScope,
    withContext: M.(CompanyContext) -> M,
) {
    (resolve(scope) as? DomainResult.Success)?.let { fresh -> update { it.withContext(fresh.value) } }
}
