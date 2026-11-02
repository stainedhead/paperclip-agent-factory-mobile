package com.stainedhead.paperclipmobile.domain

enum class IssueStatus(
    val serverName: String,
) {
    Backlog("backlog"),
    Todo("todo"),
    InProgress("in_progress"),
    InReview("in_review"),
    Done("done"),
    Blocked("blocked"),
    Cancelled("cancelled"),
    Unknown("unknown"),
    ;

    /** Not finished: anything except done and cancelled (server dashboard `tasks.open`, research Q-W1). */
    val isOpen: Boolean get() = this != Done && this != Cancelled && this != Unknown

    companion object {
        fun fromServer(raw: String): IssueStatus = entries.firstOrNull { it.serverName == raw } ?: Unknown
    }
}

enum class IssuePriority(
    val serverName: String,
) {
    Critical("critical"),
    High("high"),
    Medium("medium"),
    Low("low"),
    Unknown("unknown"),
    ;

    val isUrgent: Boolean get() = this == Critical || this == High

    companion object {
        fun fromServer(raw: String): IssuePriority = entries.firstOrNull { it.serverName == raw } ?: Unknown
    }
}

enum class AgentStatus {
    Active,
    Paused,
    Idle,
    Running,
    Error,
    PendingApproval,
    Terminated,
    Unknown,
    ;

    companion object {
        fun fromServer(raw: String): AgentStatus =
            when (raw) {
                "active" -> Active
                "paused" -> Paused
                "idle" -> Idle
                "running" -> Running
                "error" -> Error
                "pending_approval" -> PendingApproval
                "terminated" -> Terminated
                else -> Unknown
            }
    }
}

enum class RunStatus {
    Queued,
    ScheduledRetry,
    Running,
    Succeeded,
    Interrupted,
    Failed,
    Cancelled,
    TimedOut,
    Unknown,
    ;

    /** Queued, waiting to retry or running: the run can still change and can be cancelled. */
    val isActive: Boolean get() = this == Queued || this == ScheduledRetry || this == Running
    val canCancel: Boolean get() = isActive

    companion object {
        fun fromServer(raw: String): RunStatus =
            when (raw) {
                "queued" -> Queued
                "scheduled_retry" -> ScheduledRetry
                "running" -> Running
                "succeeded" -> Succeeded
                "interrupted" -> Interrupted
                "failed" -> Failed
                "cancelled" -> Cancelled
                "timed_out" -> TimedOut
                else -> Unknown
            }
    }
}

enum class InteractionStatus {
    Pending,
    Accepted,
    Rejected,
    Answered,
    Cancelled,
    Expired,
    Failed,
    Unknown,
    ;

    val isOpen: Boolean get() = this == Pending

    companion object {
        fun fromServer(raw: String): InteractionStatus =
            when (raw) {
                "pending" -> Pending
                "accepted" -> Accepted
                "rejected" -> Rejected
                "answered" -> Answered
                "cancelled" -> Cancelled
                "expired" -> Expired
                "failed" -> Failed
                else -> Unknown
            }
    }
}
