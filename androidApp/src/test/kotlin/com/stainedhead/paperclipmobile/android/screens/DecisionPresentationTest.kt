package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.presentation.EffectKindUi
import com.stainedhead.paperclipmobile.presentation.EffectSummaryUi
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.TimeZone

/** AND-040: the small lookups of the Decision screen; which options exist and what is enabled is the shared state's. */
class DecisionPresentationTest {
    @Test
    fun `a server word reads with its underscores taken out`() {
        prettify("in_progress") shouldBe "in progress"
        prettify("blocked") shouldBe "blocked"
    }

    @Test
    fun `only a status is a server word, any other detail is shown as written`() {
        effectDetail(EffectSummaryUi(EffectKindUi.UpdateIssueStatus, "i", "in_progress", false)) shouldBe "in progress"
        effectDetail(EffectSummaryUi(EffectKindUi.CommentOnIssue, "i", "Rollout_plan: 5%", false)) shouldBe
            "Rollout_plan: 5%"
        effectDetail(EffectSummaryUi(EffectKindUi.AssignIssue, "i", null, false)) shouldBe ""
    }

    @Test
    fun `a result title raises the first letter of the effect type`() {
        resultTitle("update_issue_status") shouldBe "Update issue status"
        resultTitle("x") shouldBe "X"
    }

    @Test
    fun `a changed target has its own sentence and any other reason is the server's word`() {
        ResultReason.of("target_changed") shouldBe ResultReason.TargetChanged
        ResultReason.of("stale_state") shouldBe ResultReason.Other("stale state")
        ResultReason.of(null) shouldBe ResultReason.None
        ResultReason.of("") shouldBe ResultReason.None
    }

    @Test
    fun `an expiry reads as a date and a time in the phone's zone`() {
        val epoch = 1_800_000_000_000L // 2027-01-15T08:00:00Z

        val text = expiryDateTime(epoch, Locale.US, TimeZone.getTimeZone("UTC"))

        text shouldContain "2027"
        text shouldMatch Regex(".*Jan.*15.*8:00.*")
    }

    @Test
    fun `a decide by date reads as a date, and a text that is not a date is shown as it is`() {
        decideByDate("2026-11-20", Locale.US) shouldBe "Nov 20, 2026"
        decideByDate("soon", Locale.US) shouldBe "soon"
    }

    @Test
    fun `the counter under a limited input is the length typed and the limit, none for an unlimited one`() {
        inputCounter("abc", 40) shouldBe CounterText("3", "40")
        inputCounter("abc", null) shouldBe null
    }
}
