package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.domain.AttentionSourceKind
import com.stainedhead.paperclipmobile.domain.AttentionSubject
import com.stainedhead.paperclipmobile.domain.DecideBy
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.InboxGroup
import com.stainedhead.paperclipmobile.domain.InputIssue
import com.stainedhead.paperclipmobile.domain.ResolutionIssue
import com.stainedhead.paperclipmobile.domain.ResolverAudience
import com.stainedhead.paperclipmobile.domain.ResolverPolicy
import com.stainedhead.paperclipmobile.domain.ResolverPolicySource
import com.stainedhead.paperclipmobile.domain.Severity
import com.stainedhead.paperclipmobile.domain.SnoozePreset
import com.stainedhead.paperclipmobile.domain.SubjectKind
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlin.test.Test

class AttentionUiTest {
    @Test
    fun `every severity group and snooze preset keeps its place`() {
        Severity.entries.map { it.toUi() } shouldBe SeverityUi.entries
        InboxGroup.entries.map { it.toUi() } shouldBe InboxGroupUi.entries
        SnoozePreset.entries.map { it.toUi() } shouldBe SnoozePresetUi.entries
        SnoozePresetUi.entries.map { it.toDomain() } shouldBe SnoozePreset.entries
    }

    @Test
    fun `source kinds map and unknown ones become unsupported`() {
        AttentionSourceKind.known
            .map { it.toUi() }
            .toSet()
            .size shouldBe 11
        AttentionSourceKind.BudgetAlert.toUi() shouldBe SourceKindUi.BudgetAlert
        AttentionSourceKind.Approval.toUi() shouldBe SourceKindUi.Approval
        AttentionSourceKind.Decision.toUi() shouldBe SourceKindUi.Decision
        AttentionSourceKind.IssueThreadInteraction.toUi() shouldBe SourceKindUi.IssueThreadInteraction
        AttentionSourceKind.JoinRequest.toUi() shouldBe SourceKindUi.JoinRequest
        AttentionSourceKind.RecoveryAction.toUi() shouldBe SourceKindUi.RecoveryAction
        AttentionSourceKind.ProductivityReview.toUi() shouldBe SourceKindUi.ProductivityReview
        AttentionSourceKind.BlockerAttention.toUi() shouldBe SourceKindUi.BlockerAttention
        AttentionSourceKind.Review.toUi() shouldBe SourceKindUi.Review
        AttentionSourceKind.FailedRun.toUi() shouldBe SourceKindUi.FailedRun
        AttentionSourceKind.AgentErrorAlert.toUi() shouldBe SourceKindUi.AgentErrorAlert
        AttentionSourceKind.Unsupported("new").toUi() shouldBe SourceKindUi.Unsupported
    }

    @Test
    fun `decide-by keeps its kind and date`() {
        DecideBy.Today.toUi() shouldBe DecideByUi(DecideByKindUi.Today, null)
        DecideBy.ThisWeek.toUi() shouldBe DecideByUi(DecideByKindUi.ThisWeek, null)
        DecideBy.Whenever.toUi() shouldBe DecideByUi(DecideByKindUi.Whenever, null)
        DecideBy.None.toUi() shouldBe DecideByUi(DecideByKindUi.None, null)
        DecideBy.OnDate(LocalDate(2026, 11, 3)).toUi() shouldBe DecideByUi(DecideByKindUi.OnDate, "2026-11-03")
    }

    @Test
    fun `audience facts say whether the card is addressed to another user`() {
        val audience =
            ResolverAudience(
                ResolverPolicy.Anyone,
                ResolverPolicy.HumanOnly,
                ResolverPolicySource.CompanyCap,
                addresseeUserId = "user-2",
                addresseeName = "Sam",
                createdByAgentName = "CEO",
            )

        audience.toUi("user-1") shouldBe AudienceUi(ResolverPolicyUi.HumanOnly, "Sam", true, "CEO")
        audience.toUi("user-2").addresseeIsAnotherUser shouldBe false
        ResolverPolicy.NotCreator.toUi() shouldBe ResolverPolicyUi.NotCreator
    }

    @Test
    fun `the web address is the factory address plus the relative path`() {
        val subject = AttentionSubject(SubjectKind.Approval, "a", href = "/PAP/approvals/a")

        webUrl("https://home.example.com", subject) shouldBe "https://home.example.com/PAP/approvals/a"
        webUrl("https://home.example.com/", subject) shouldBe "https://home.example.com/PAP/approvals/a"
        webUrl("https://home.example.com", subject.copy(href = "PAP/costs")) shouldBe
            "https://home.example.com/PAP/costs"
        webUrl("https://home.example.com", subject.copy(href = null)) shouldBe null
    }
}

class FeedbackUiTest {
    @Test
    fun `a success has no feedback`() {
        MutationResult.Done(1).toFeedback() shouldBe null
    }

    @Test
    fun `each refusal maps to a key`() {
        MutationResult.Denied.toFeedback() shouldBe FeedbackUi(FeedbackKind.NotAllowed)
        MutationResult.NotAuthorized(BiometricResult.LockedOut).toFeedback() shouldBe
            FeedbackUi(FeedbackKind.DeviceAuth, deviceAuth = DeviceAuthIssue.LockedOut)
        MutationResult.Failed(DomainError.Timeout).toFeedback() shouldBe
            FeedbackUi(FeedbackKind.Failed, error = ErrorUi(ErrorKey.Timeout))
        MutationResult.Reconciled(DomainError.Conflict, 1).toFeedback() shouldBe
            FeedbackUi(FeedbackKind.Reconciled, error = ErrorUi(ErrorKey.Conflict))
    }

    @Test
    fun `invalid input keeps its reason and the offending input id`() {
        MutationResult.Invalid(InvalidInput.NoteRequired).toFeedback() shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.NoteRequired)
        MutationResult.Invalid(InvalidInput.Input(InputIssue.Required("why"))).toFeedback() shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.InputRequired, inputId = "why")
        MutationResult.Invalid(InvalidInput.Input(InputIssue.TooLong("note"))).toFeedback() shouldBe
            FeedbackUi(FeedbackKind.Invalid, invalid = InvalidKey.InputTooLong, inputId = "note")
        MutationResult.Invalid(InvalidInput.Amount(ResolutionIssue.MustExceedObserved)).toFeedback()?.invalid shouldBe
            InvalidKey.AmountMustExceedObserved
        MutationResult.Invalid(InvalidInput.Amount(ResolutionIssue.WrongCurrency)).toFeedback()?.invalid shouldBe
            InvalidKey.WrongCurrency
        listOf(
            InvalidInput.BodyRequired to InvalidKey.BodyRequired,
            InvalidInput.WrongStatus to InvalidKey.WrongStatus,
            InvalidInput.UnknownOption to InvalidKey.UnknownOption,
            InvalidInput.SnoozeNotInFuture to InvalidKey.SnoozeNotInFuture,
            InvalidInput.UndoWindowClosed to InvalidKey.UndoWindowClosed,
        ).forEach { (input, key) -> input.toKey() shouldBe key }
    }
}
