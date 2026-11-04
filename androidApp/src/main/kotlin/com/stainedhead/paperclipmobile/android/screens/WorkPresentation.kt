package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.presentation.IssueFilterUi
import com.stainedhead.paperclipmobile.presentation.IssuePriorityUi
import com.stainedhead.paperclipmobile.presentation.IssueStatusUi

// How the Work list and Issue colour and order what the shared state says. Nothing here decides what is allowed.

fun IssueStatusUi.tone(): StatusTone =
    when (this) {
        IssueStatusUi.Blocked -> StatusTone.Critical

        IssueStatusUi.Done -> StatusTone.Success

        IssueStatusUi.InReview -> StatusTone.Warn

        IssueStatusUi.Backlog,
        IssueStatusUi.Todo,
        IssueStatusUi.InProgress,
        IssueStatusUi.Cancelled,
        IssueStatusUi.Unknown,
        -> StatusTone.Neutral
    }

fun IssuePriorityUi.tone(): StatusTone =
    when (this) {
        IssuePriorityUi.Critical -> StatusTone.Critical
        IssuePriorityUi.High -> StatusTone.Warn
        IssuePriorityUi.Medium, IssuePriorityUi.Low, IssuePriorityUi.Unknown -> StatusTone.Neutral
    }

/** The saved filters in the order of the chips (the mocks'). */
object IssueFilterOrder {
    val chips: List<IssueFilterUi> =
        listOf(
            IssueFilterUi.AllOpen,
            IssueFilterUi.Blocked,
            IssueFilterUi.CriticalAndHigh,
            IssueFilterUi.InReview,
            IssueFilterUi.AssignedToMe,
        )
}
