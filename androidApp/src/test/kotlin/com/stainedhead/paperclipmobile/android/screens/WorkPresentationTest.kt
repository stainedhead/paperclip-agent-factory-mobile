package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.android.designsystem.StatusTone
import com.stainedhead.paperclipmobile.presentation.IssueFilterUi
import com.stainedhead.paperclipmobile.presentation.IssuePriorityUi
import com.stainedhead.paperclipmobile.presentation.IssueStatusUi
import io.kotest.matchers.shouldBe
import org.junit.Test

/** The lookups of the Work list and Issue: colours and order, never what is allowed. */
class WorkPresentationTest {
    @Test
    fun aBlockedIssueIsCriticalADoneOneIsSuccessfulAndOneInReviewIsAWarning() {
        IssueStatusUi.Blocked.tone() shouldBe StatusTone.Critical
        IssueStatusUi.Done.tone() shouldBe StatusTone.Success
        IssueStatusUi.InReview.tone() shouldBe StatusTone.Warn
        for (neutral in listOf(IssueStatusUi.Backlog, IssueStatusUi.Todo, IssueStatusUi.InProgress)) {
            neutral.tone() shouldBe StatusTone.Neutral
        }
        IssueStatusUi.Cancelled.tone() shouldBe StatusTone.Neutral
        IssueStatusUi.Unknown.tone() shouldBe StatusTone.Neutral
    }

    @Test
    fun criticalAndHighPrioritiesStandOutAndTheRestDoNot() {
        IssuePriorityUi.Critical.tone() shouldBe StatusTone.Critical
        IssuePriorityUi.High.tone() shouldBe StatusTone.Warn
        IssuePriorityUi.Medium.tone() shouldBe StatusTone.Neutral
        IssuePriorityUi.Low.tone() shouldBe StatusTone.Neutral
        IssuePriorityUi.Unknown.tone() shouldBe StatusTone.Neutral
    }

    @Test
    fun theFilterChipsKeepTheMocksOrderAndOfferEveryFilter() {
        IssueFilterOrder.chips shouldBe
            listOf(
                IssueFilterUi.AllOpen,
                IssueFilterUi.Blocked,
                IssueFilterUi.CriticalAndHigh,
                IssueFilterUi.InReview,
                IssueFilterUi.AssignedToMe,
            )
        IssueFilterOrder.chips.toSet() shouldBe IssueFilterUi.entries.toSet()
    }
}
