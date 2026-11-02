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

internal fun RunStatus.toUi(): RunStatusUi =
    when (this) {
        RunStatus.Queued -> RunStatusUi.Queued
        RunStatus.ScheduledRetry -> RunStatusUi.ScheduledRetry
        RunStatus.Running -> RunStatusUi.Running
        RunStatus.Succeeded -> RunStatusUi.Succeeded
        RunStatus.Interrupted -> RunStatusUi.Interrupted
        RunStatus.Failed -> RunStatusUi.Failed
        RunStatus.Cancelled -> RunStatusUi.Cancelled
        RunStatus.TimedOut -> RunStatusUi.TimedOut
        RunStatus.Unknown -> RunStatusUi.Unknown
    }

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

internal fun IssueStatus.toUi(): IssueStatusUi =
    when (this) {
        IssueStatus.Backlog -> IssueStatusUi.Backlog
        IssueStatus.Todo -> IssueStatusUi.Todo
        IssueStatus.InProgress -> IssueStatusUi.InProgress
        IssueStatus.InReview -> IssueStatusUi.InReview
        IssueStatus.Done -> IssueStatusUi.Done
        IssueStatus.Blocked -> IssueStatusUi.Blocked
        IssueStatus.Cancelled -> IssueStatusUi.Cancelled
        IssueStatus.Unknown -> IssueStatusUi.Unknown
    }

internal fun IssueStatusUi.toDomain(): IssueStatus =
    when (this) {
        IssueStatusUi.Backlog -> IssueStatus.Backlog
        IssueStatusUi.Todo -> IssueStatus.Todo
        IssueStatusUi.InProgress -> IssueStatus.InProgress
        IssueStatusUi.InReview -> IssueStatus.InReview
        IssueStatusUi.Done -> IssueStatus.Done
        IssueStatusUi.Blocked -> IssueStatus.Blocked
        IssueStatusUi.Cancelled -> IssueStatus.Cancelled
        IssueStatusUi.Unknown -> IssueStatus.Unknown
    }

enum class IssuePriorityUi {
    Critical,
    High,
    Medium,
    Low,
    Unknown,
}

internal fun IssuePriority.toUi(): IssuePriorityUi =
    when (this) {
        IssuePriority.Critical -> IssuePriorityUi.Critical
        IssuePriority.High -> IssuePriorityUi.High
        IssuePriority.Medium -> IssuePriorityUi.Medium
        IssuePriority.Low -> IssuePriorityUi.Low
        IssuePriority.Unknown -> IssuePriorityUi.Unknown
    }

internal fun IssuePriorityUi.toDomain(): IssuePriority =
    when (this) {
        IssuePriorityUi.Critical -> IssuePriority.Critical
        IssuePriorityUi.High -> IssuePriority.High
        IssuePriorityUi.Medium -> IssuePriority.Medium
        IssuePriorityUi.Low -> IssuePriority.Low
        IssuePriorityUi.Unknown -> IssuePriority.Unknown
    }

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

internal fun AgentStatus.toUi(): AgentStatusUi =
    when (this) {
        AgentStatus.Active -> AgentStatusUi.Active
        AgentStatus.Paused -> AgentStatusUi.Paused
        AgentStatus.Idle -> AgentStatusUi.Idle
        AgentStatus.Running -> AgentStatusUi.Running
        AgentStatus.Error -> AgentStatusUi.Error
        AgentStatus.PendingApproval -> AgentStatusUi.PendingApproval
        AgentStatus.Terminated -> AgentStatusUi.Terminated
        AgentStatus.Unknown -> AgentStatusUi.Unknown
    }
