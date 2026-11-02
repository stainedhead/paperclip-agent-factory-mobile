package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.Action
import com.stainedhead.paperclipmobile.domain.Permissions
import com.stainedhead.paperclipmobile.domain.Role

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

/** The one rule behind every `can...` flag: a connection and a role the matrix allows (ADR-019). */
internal fun mayAct(
    online: Boolean,
    role: Role?,
    action: Action,
): Boolean = online && role != null && Permissions.allows(role, action)
