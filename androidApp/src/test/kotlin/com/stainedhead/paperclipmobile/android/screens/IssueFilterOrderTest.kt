package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.presentation.IssueFilterUi
import io.kotest.matchers.shouldBe
import org.junit.Test

/** The lookups of the Work list and Issue: colours and order, never what is allowed. */
class IssueFilterOrderTest {
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
