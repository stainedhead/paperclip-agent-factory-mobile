package com.stainedhead.paperclipmobile.application.interaction

import com.stainedhead.paperclipmobile.application.Env
import com.stainedhead.paperclipmobile.application.InvalidInput
import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.domain.AnswerIssue
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Interaction
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.InteractionKind
import com.stainedhead.paperclipmobile.domain.InteractionStatus
import com.stainedhead.paperclipmobile.domain.RequestConfirmation
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.UnsupportedInteraction
import com.stainedhead.paperclipmobile.testing.WorkFixtures
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class LoadInteractionsTest {
    @Test
    fun `FR-063 the interactions of an issue are loaded and a transient failure is retried`() =
        runTest {
            val env = Env()

            val loaded = LoadInteractions(env.interactions)(env.scope, WorkFixtures.pap1182)
            (loaded as DomainResult.Success).value.size shouldBe 2

            env.interactions.failures["list"] = DomainError.Unreachable
            LoadInteractions(env.interactions)(env.scope, WorkFixtures.pap1182)
            env.interactions.count("list") shouldBe 5
        }
}

class RespondToInteractionTest {
    private val issue = WorkFixtures.pap1182

    private fun Env.pending(): Interaction = WorkFixtures.interactions().first { it.meta.id.value == "int-confirm" }

    private fun Env.respond() = RespondToInteraction(interactions)

    @Test
    fun `FR-063 an operator accepts a confirmation with one call and no device authentication`() =
        runTest {
            val env = Env()

            val result = env.respond()(env.context(Role.Operator), issue, env.pending(), InteractionAnswer.Accept)

            (result as MutationResult.Done).value.meta.status shouldBe InteractionStatus.Accepted
            env.interactions.count("answer") shouldBe 1
            env.biometrics.calls shouldBe 0
        }

    @Test
    fun `a viewer is denied before anything else and the gateway is untouched`() =
        runTest {
            val env = Env()

            val result = env.respond()(env.context(Role.Viewer), issue, env.pending(), InteractionAnswer.Accept)

            result shouldBe MutationResult.Denied
            env.interactions.count("answer") shouldBe 0
        }

    @Test
    fun `FR-063 an interaction addressed to someone else is read only`() =
        runTest {
            val env = Env()
            val other = RequestConfirmation(WorkFixtures.meta("int-x", addresseeUserId = "user-2"), "Merge?")

            val result = env.respond()(env.context(), issue, other, InteractionAnswer.Accept)

            result shouldBe MutationResult.Invalid(InvalidInput.NotAddressedToYou)
            env.interactions.count("answer") shouldBe 0
        }

    @Test
    fun `a required reason that is missing is refused with no call`() =
        runTest {
            val env = Env()

            val result = env.respond()(env.context(), issue, env.pending(), InteractionAnswer.Reject(null))

            result shouldBe MutationResult.Invalid(InvalidInput.Answer(AnswerIssue.ReasonRequired(null)))
            env.interactions.count("answer") shouldBe 0
        }

    @Test
    fun `an unsupported interaction is never sent`() =
        runTest {
            val env = Env()
            val unsupported = UnsupportedInteraction(WorkFixtures.meta("int-ci"), InteractionKind.ConnectionIntent)

            val result = env.respond()(env.context(), issue, unsupported, InteractionAnswer.Accept)

            result shouldBe MutationResult.Invalid(InvalidInput.Answer(AnswerIssue.Unsupported))
            env.interactions.count("answer") shouldBe 0
        }

    @Test
    fun `an interaction that is no longer pending is refused`() =
        runTest {
            val env = Env()
            val done = RequestConfirmation(WorkFixtures.meta("int-d", InteractionStatus.Accepted), "Merge?")

            env.respond()(env.context(), issue, done, InteractionAnswer.Accept) shouldBe
                MutationResult.Invalid(InvalidInput.Answer(AnswerIssue.NotPending))
        }

    @Test
    fun `FR-096 a timeout is sent once and the interaction is read again`() =
        runTest {
            val env = Env()
            env.interactions.failures["answer"] = DomainError.Timeout

            val result = env.respond()(env.context(), issue, env.pending(), InteractionAnswer.Accept)

            (result as MutationResult.Reconciled).error shouldBe DomainError.Timeout
            result.current.meta.id.value shouldBe "int-confirm"
            env.interactions.count("answer") shouldBe 1
            env.interactions.count("list") shouldBe 1
        }

    @Test
    fun `an interaction resolved elsewhere is a conflict that shows its final state`() =
        runTest {
            val env = Env()
            val pending = env.pending()
            env.interactions.answer(env.scope, issue, pending.meta.id, InteractionAnswer.Reject("later"))

            val result = env.respond()(env.context(), issue, pending, InteractionAnswer.Accept)

            (result as MutationResult.Reconciled).error shouldBe DomainError.Conflict
            result.current.meta.status shouldBe InteractionStatus.Rejected
        }

    @Test
    fun `a server refusal other than timeout or conflict is reported without a re-read`() =
        runTest {
            val env = Env()
            env.interactions.failures["answer"] = DomainError.Forbidden

            env.respond()(env.context(), issue, env.pending(), InteractionAnswer.Accept) shouldBe
                MutationResult.Failed(DomainError.Forbidden)
            env.interactions.count("list") shouldBe 0
        }
}
