package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.AgentStatus
import com.stainedhead.paperclipmobile.domain.IssuePriority
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.RunStatus

enum class RunStatusUi {
    Queued,
    ScheduledRetry,
    Running,
    Succeeded,
    Interrupted,
    Failed,
    Cancelled,
    TimedOut,
    Unknown,
}

internal fun RunStatus.toUi() = RunStatusUi.valueOf(name)

enum class IssueStatusUi {
    Backlog,
    Todo,
    InProgress,
    InReview,
    Done,
    Blocked,
    Cancelled,
    Unknown,
}

internal fun IssueStatus.toUi() = IssueStatusUi.valueOf(name)

internal fun IssueStatusUi.toDomain() = IssueStatus.valueOf(name)

enum class IssuePriorityUi {
    Critical,
    High,
    Medium,
    Low,
    Unknown,
}

internal fun IssuePriority.toUi() = IssuePriorityUi.valueOf(name)

internal fun IssuePriorityUi.toDomain() = IssuePriority.valueOf(name)

enum class AgentStatusUi {
    Active,
    Paused,
    Idle,
    Running,
    Error,
    PendingApproval,
    Terminated,
    Unknown,
}

internal fun AgentStatus.toUi() = AgentStatusUi.valueOf(name)
