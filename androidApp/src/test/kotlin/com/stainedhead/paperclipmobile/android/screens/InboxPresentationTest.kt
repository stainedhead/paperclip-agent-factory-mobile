package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.android.shell.AppRoute
import com.stainedhead.paperclipmobile.android.shell.AppSheet
import com.stainedhead.paperclipmobile.presentation.DecideByKindUi
import com.stainedhead.paperclipmobile.presentation.DecideByUi
import com.stainedhead.paperclipmobile.presentation.InboxItemUi
import com.stainedhead.paperclipmobile.presentation.InlineActionUi
import com.stainedhead.paperclipmobile.presentation.OpenKindUi
import com.stainedhead.paperclipmobile.presentation.OpenTargetUi
import com.stainedhead.paperclipmobile.presentation.SeverityUi
import com.stainedhead.paperclipmobile.presentation.SourceKindUi
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** AND-030: where a card goes, which buttons it offers and what the web address rule lets through. */
class InboxPresentationTest {
    private fun item(
        kind: SourceKindUi = SourceKindUi.Decision,
        open: OpenTargetUi = OpenTargetUi(OpenKindUi.Decision, "dec-1", null),
        inline: List<InlineActionUi> = emptyList(),
        webUrl: String? = null,
        decideBy: DecideByUi = DecideByUi(DecideByKindUi.Today, null),
    ) = InboxItemUi(
        id = "decision:dec-1",
        kind = kind,
        rawKind = null,
        severity = SeverityUi.Critical,
        title = "Ship it?",
        identifier = "PAP-1",
        whyNow = "CTO proposes 3 options",
        originAgentName = "CTO",
        activityAtEpochMillis = 0L,
        decideBy = decideBy,
        inlineActions = inline,
        open = open,
        webUrl = webUrl,
        isReadOnly = false,
        audience = null,
        dismissalKey = "attention:decision:dec-1",
    )

    @Test
    fun `a decision card opens the decision with what the card knew`() {
        inboxDestination(item()) shouldBe
            InboxDestination.Route(AppRoute.Decision("dec-1", "CTO", "today", "attention:decision:dec-1", "PAP-1"))
    }

    @Test
    fun `decide by is written back in the words the shared parser reads`() {
        DecideByUi(DecideByKindUi.Today, null).hintText() shouldBe "today"
        DecideByUi(DecideByKindUi.ThisWeek, null).hintText() shouldBe "this_week"
        DecideByUi(DecideByKindUi.Whenever, null).hintText() shouldBe "whenever"
        DecideByUi(DecideByKindUi.OnDate, "2026-11-20").hintText() shouldBe "2026-11-20"
        DecideByUi(DecideByKindUi.None, null).hintText() shouldBe null
    }

    @Test
    fun `an approval opens the approval and a budget alert opens the budget sheet`() {
        inboxDestination(item(open = OpenTargetUi(OpenKindUi.Approval, "ap-1", null))) shouldBe
            InboxDestination.Route(AppRoute.Approval("ap-1"))
        inboxDestination(item(open = OpenTargetUi(OpenKindUi.BudgetIncident, "inc-1", null))) shouldBe
            InboxDestination.Sheet(AppSheet.BudgetIncident("inc-1"))
    }

    @Test
    fun `an interaction and an issue open the issue, preferring the issue id`() {
        inboxDestination(item(open = OpenTargetUi(OpenKindUi.Interaction, "int-1", "pap-9"))) shouldBe
            InboxDestination.Route(AppRoute.Issue("pap-9"))
        inboxDestination(item(open = OpenTargetUi(OpenKindUi.Issue, "pap-3", null))) shouldBe
            InboxDestination.Route(AppRoute.Issue("pap-3"))
    }

    @Test
    fun `a run and an agent open their screens`() {
        inboxDestination(item(open = OpenTargetUi(OpenKindUi.Run, "run-1", null))) shouldBe
            InboxDestination.Route(AppRoute.Run("run-1"))
        inboxDestination(item(open = OpenTargetUi(OpenKindUi.Agent, "ag-1", null))) shouldBe
            InboxDestination.Route(AppRoute.Agent("ag-1"))
    }

    @Test
    fun `a web only card opens its address only when it is https`() {
        val web = OpenTargetUi(OpenKindUi.WebOnly, "x", null)

        inboxDestination(item(open = web, webUrl = "https://paperclip.example/x")) shouldBe
            InboxDestination.Web("https://paperclip.example/x")
        inboxDestination(item(open = web, webUrl = "http://paperclip.example/x")) shouldBe InboxDestination.None
        inboxDestination(item(open = web, webUrl = "intent://x")) shouldBe InboxDestination.None
        inboxDestination(item(open = web, webUrl = null)) shouldBe InboxDestination.None
    }

    @Test
    fun `a decision offers review options, an approval details and an unknown kind the web app`() {
        val web = OpenTargetUi(OpenKindUi.WebOnly, "x", null)
        val run = OpenTargetUi(OpenKindUi.Run, "r", null)

        InboxCardLink.of(item()) shouldBe InboxCardLink.ReviewOptions
        InboxCardLink.of(item(kind = SourceKindUi.Approval)) shouldBe InboxCardLink.Details
        InboxCardLink.of(item(kind = SourceKindUi.Unsupported, open = web)) shouldBe InboxCardLink.OpenInWeb
        InboxCardLink.of(item(kind = SourceKindUi.FailedRun, open = run)) shouldBe InboxCardLink.Chevron
    }

    @Test
    fun `the agent is added to the context only when the reason does not name it already`() {
        cardContext("CTO proposes 3 options", "CTO") shouldBe "CTO proposes 3 options"
        cardContext("Requested by the board", "CEO") shouldBe "Requested by the board · CEO"
        cardContext("Hard stop", null) shouldBe "Hard stop"
        cardContext("Hard stop", "") shouldBe "Hard stop"
    }

    @Test
    fun `the kind line joins the kind and the identifier`() {
        kindLine("Decision", "PAP-1182") shouldBe "Decision · PAP-1182"
        kindLine("Approval", null) shouldBe "Approval"
    }
}
