package com.stainedhead.paperclipmobile.application.instance

import com.stainedhead.paperclipmobile.domain.Instance
import com.stainedhead.paperclipmobile.domain.InstanceId

/** Why a factory name cannot be used (PRD FR-001). */
enum class InstanceNameIssue {
    Empty,
    TooLong,
    Taken,
}

internal object InstanceNames {
    /** Names are trimmed and unique ignoring case. [self] is the factory being edited, which may keep its own name. */
    fun issueFor(
        name: String,
        existing: List<Instance>,
        self: InstanceId? = null,
    ): InstanceNameIssue? {
        val trimmed = name.trim()
        return when {
            trimmed.length < Instance.MIN_NAME_LENGTH -> {
                InstanceNameIssue.Empty
            }

            trimmed.length > Instance.MAX_NAME_LENGTH -> {
                InstanceNameIssue.TooLong
            }

            existing.any { it.id != self && it.displayName.trim().equals(trimmed, ignoreCase = true) } -> {
                InstanceNameIssue.Taken
            }

            else -> {
                null
            }
        }
    }
}
