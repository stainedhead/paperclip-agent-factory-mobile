package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.presentation.IssueFilterUi

// How the Work list orders what the shared state says. Nothing here decides what is allowed.

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
