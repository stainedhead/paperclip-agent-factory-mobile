package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

internal fun meta(
    id: String = "int-1",
    status: InteractionStatus = InteractionStatus.Pending,
    addresseeUserId: String? = null,
    blocked: Boolean = false,
) = InteractionMeta(
    id = InteractionId(id),
    status = status,
    title = null,
    summary = null,
    createdAt = Instant.parse("2026-11-01T08:00:00Z"),
    audience =
        ResolverAudience(
            requestedPolicy = ResolverPolicy.Anyone,
            effectivePolicy = ResolverPolicy.Anyone,
            policySource = ResolverPolicySource.Requested,
            addresseeUserId = addresseeUserId,
        ),
    acceptanceBlocked = blocked,
)

private val tasks =
    SuggestTasks(
        meta(),
        listOf(SuggestedTask("a", "Write tests", null), SuggestedTask("b", "Ship it", null)),
    )
private val confirmation = RequestConfirmation(meta(), "Merge the PR?", rejectRequiresReason = true)
private val checkbox =
    RequestCheckboxConfirmation(
        meta(),
        "Which environments?",
        listOf(InteractionOption("s", "Staging"), InteractionOption("p", "Production")),
        defaultSelected = setOf("s"),
        minSelected = 1,
        maxSelected = 1,
    )
private val questions =
    AskUserQuestions(
        meta(),
        listOf(
            Question(
                "q1",
                "Which region?",
                multi = false,
                required = true,
                allowOther = false,
                options = listOf(InteractionOption("eu", "EU"), InteractionOption("us", "US")),
            ),
            Question("q2", "Anything else?", multi = true, required = false, allowOther = true, options = emptyList()),
        ),
    )
private val verdicts =
    RequestItemVerdicts(
        meta(),
        "Review these",
        listOf(InteractionItem("x", "Item X"), InteractionItem("y", "Item Y")),
        verdicts = setOf(Verdict.Approve, Verdict.Reject),
        requireReasonOn = setOf(Verdict.Reject),
    )

class InteractionKindTest {
    @Test
    fun `FR-063 each supported kind reports its server kind and unsupported ones keep theirs`() {
        tasks.kind shouldBe InteractionKind.SuggestTasks
        confirmation.kind shouldBe InteractionKind.RequestConfirmation
        checkbox.kind shouldBe InteractionKind.RequestCheckboxConfirmation
        questions.kind shouldBe InteractionKind.AskUserQuestions
        verdicts.kind shouldBe InteractionKind.RequestItemVerdicts
        UnsupportedInteraction(meta(), InteractionKind.ConnectionIntent).isSupported shouldBe false
        tasks.isSupported shouldBe true
    }

    @Test
    fun `FR-063 an interaction is open to the user only while pending unblocked and addressed to them`() {
        confirmation.canBeResolvedBy("user-1") shouldBe true
        RequestConfirmation(meta(status = InteractionStatus.Accepted), "p").canBeResolvedBy("user-1") shouldBe false
        RequestConfirmation(meta(blocked = true), "p").canBeResolvedBy("user-1") shouldBe false
        RequestConfirmation(meta(addresseeUserId = "user-2"), "p").canBeResolvedBy("user-1") shouldBe false
        RequestConfirmation(meta(addresseeUserId = "user-1"), "p").canBeResolvedBy("user-1") shouldBe true
        UnsupportedInteraction(meta(), InteractionKind.ConnectionIntent).canBeResolvedBy("user-1") shouldBe false
    }

    @Test
    fun `an unknown kind from a newer server is unsupported with its raw name`() {
        InteractionKind.fromServer("brand_new") shouldBe InteractionKind.Unsupported("brand_new")
        UnsupportedInteraction(meta(), InteractionKind.fromServer("brand_new")).kind.serverName shouldBe "brand_new"
    }
}

class InteractionValidationTest {
    @Test
    fun `FR-063 suggest tasks needs at least one known task selected`() {
        tasks.validate(InteractionAnswer.AcceptTasks(emptySet())) shouldBe AnswerIssue.NothingSelected
        tasks.validate(InteractionAnswer.AcceptTasks(setOf("zzz"))) shouldBe AnswerIssue.UnknownChoice("zzz")
        tasks.validate(InteractionAnswer.AcceptTasks(setOf("a"))) shouldBe null
        tasks.validate(InteractionAnswer.Reject(null)) shouldBe null
    }

    @Test
    fun `FR-063 confirmation accepts and rejects and a required reason must not be blank`() {
        confirmation.validate(InteractionAnswer.Accept) shouldBe null
        confirmation.validate(InteractionAnswer.Reject(null)) shouldBe AnswerIssue.ReasonRequired(null)
        confirmation.validate(InteractionAnswer.Reject("  ")) shouldBe AnswerIssue.ReasonRequired(null)
        confirmation.validate(InteractionAnswer.Reject("not now")) shouldBe null
        RequestConfirmation(meta(), "p").validate(InteractionAnswer.Reject(null)) shouldBe null
    }

    @Test
    fun `FR-063 checkbox confirmation enforces the selection range and known options`() {
        checkbox.validate(InteractionAnswer.AcceptOptions(emptySet())) shouldBe AnswerIssue.TooFewSelected(1)
        checkbox.validate(InteractionAnswer.AcceptOptions(setOf("s", "p"))) shouldBe AnswerIssue.TooManySelected(1)
        checkbox.validate(InteractionAnswer.AcceptOptions(setOf("q"))) shouldBe AnswerIssue.UnknownChoice("q")
        checkbox.validate(InteractionAnswer.AcceptOptions(setOf("p"))) shouldBe null
        checkbox.validate(InteractionAnswer.Reject("no")) shouldBe null
    }

    @Test
    fun `FR-063 questions need every required answer and a single choice has one option`() {
        questions.validate(InteractionAnswer.Respond(emptyList())) shouldBe AnswerIssue.QuestionRequired("q1")
        questions.validate(InteractionAnswer.Respond(listOf(QuestionAnswer("q1", listOf("eu", "us"))))) shouldBe
            AnswerIssue.TooManySelected(1)
        questions.validate(InteractionAnswer.Respond(listOf(QuestionAnswer("q1", listOf("mars"))))) shouldBe
            AnswerIssue.UnknownChoice("mars")
        questions.validate(InteractionAnswer.Respond(listOf(QuestionAnswer("nope", listOf("eu"))))) shouldBe
            AnswerIssue.UnknownChoice("nope")
        questions.validate(InteractionAnswer.Respond(listOf(QuestionAnswer("q1", listOf("eu"))))) shouldBe null
    }

    @Test
    fun `FR-063 a free text answer counts as an answer when the question allows other`() {
        val other = QuestionAnswer("q2", emptyList(), otherText = "later")
        questions.validate(
            InteractionAnswer.Respond(listOf(QuestionAnswer("q1", listOf("us")), other)),
        ) shouldBe null
        val bad = QuestionAnswer("q1", emptyList(), otherText = "x")
        questions.validate(InteractionAnswer.Respond(listOf(bad))) shouldBe AnswerIssue.QuestionRequired("q1")
    }

    @Test
    fun `FR-063 item verdicts need a known item an enabled verdict and a reason where required`() {
        verdicts.validate(InteractionAnswer.SubmitVerdicts(emptyList())) shouldBe AnswerIssue.NothingSelected
        verdicts.validate(InteractionAnswer.SubmitVerdicts(listOf(ItemVerdict("q", Verdict.Approve)))) shouldBe
            AnswerIssue.UnknownChoice("q")
        verdicts.validate(InteractionAnswer.SubmitVerdicts(listOf(ItemVerdict("x", Verdict.Defer)))) shouldBe
            AnswerIssue.VerdictNotEnabled("x")
        verdicts.validate(InteractionAnswer.SubmitVerdicts(listOf(ItemVerdict("x", Verdict.Reject)))) shouldBe
            AnswerIssue.ReasonRequired("x")
        verdicts.validate(
            InteractionAnswer.SubmitVerdicts(
                listOf(ItemVerdict("x", Verdict.Reject, "wrong"), ItemVerdict("y", Verdict.Approve)),
            ),
        ) shouldBe null
    }

    @Test
    fun `an answer for the wrong kind and any answer to an unsupported or closed interaction is refused`() {
        tasks.validate(InteractionAnswer.Respond(emptyList())) shouldBe AnswerIssue.WrongKind
        confirmation.validate(InteractionAnswer.SubmitVerdicts(emptyList())) shouldBe AnswerIssue.WrongKind
        verdicts.validate(InteractionAnswer.Accept) shouldBe AnswerIssue.WrongKind
        questions.validate(InteractionAnswer.Reject("x")) shouldBe AnswerIssue.WrongKind
        UnsupportedInteraction(meta(), InteractionKind.ConnectionIntent).validate(InteractionAnswer.Accept) shouldBe
            AnswerIssue.Unsupported
        RequestConfirmation(meta(status = InteractionStatus.Accepted), "p").validate(InteractionAnswer.Accept) shouldBe
            AnswerIssue.NotPending
        RequestConfirmation(meta(blocked = true), "p").validate(InteractionAnswer.Accept) shouldBe AnswerIssue.Blocked
    }

    @Test
    fun `skip is allowed for any pending supported interaction`() {
        listOf(tasks, confirmation, checkbox, questions, verdicts).forEach {
            it.validate(InteractionAnswer.Skip(null)) shouldBe null
        }
    }
}
