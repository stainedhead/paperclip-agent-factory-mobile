package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.application.agent.InvokeHeartbeat
import com.stainedhead.paperclipmobile.application.agent.PauseAgent
import com.stainedhead.paperclipmobile.application.agent.ResumeAgent
import com.stainedhead.paperclipmobile.application.agent.SetAgentBudget
import com.stainedhead.paperclipmobile.application.interaction.RespondToInteraction
import com.stainedhead.paperclipmobile.application.issue.AssignIssue
import com.stainedhead.paperclipmobile.application.issue.ChangeIssueStatus
import com.stainedhead.paperclipmobile.application.issue.CommentOnIssue
import com.stainedhead.paperclipmobile.application.issue.CreateIssue
import com.stainedhead.paperclipmobile.application.run.CancelRun
import com.stainedhead.paperclipmobile.domain.Assignee
import com.stainedhead.paperclipmobile.domain.CommentOptions
import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.InteractionAnswer
import com.stainedhead.paperclipmobile.domain.IssueStatus
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.NewIssue
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.testing.AgentFixtures
import com.stainedhead.paperclipmobile.testing.WorkFixtures
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** One mutation use case run as one role. The result tells whether the role got through to the gateway. */
private class Case(
    val name: String,
    val allowed: Set<Role>,
    val run: suspend (Env, Role) -> MutationResult<*>,
)

private val everyone = setOf(Role.Owner, Role.Operator)
private val ownerOnly = setOf(Role.Owner)

private val cases =
    listOf(
        Case("comment", everyone) { e, r ->
            CommentOnIssue(e.issues)(e.context(r), WorkFixtures.pap1182, "hi", CommentOptions())
        },
        Case("status", everyone) { e, r ->
            ChangeIssueStatus(e.issues)(e.context(r), WorkFixtures.issues().first(), IssueStatus.InReview)
        },
        Case(
            "assign",
            everyone,
        ) { e, r -> AssignIssue(e.issues)(e.context(r), WorkFixtures.issues().first(), Assignee.None) },
        Case("create", everyone) { e, r -> CreateIssue(e.issues)(e.context(r), NewIssue("x")) },
        Case("respond", everyone) { e, r ->
            RespondToInteraction(e.interactions)(
                e.context(r),
                WorkFixtures.pap1182,
                WorkFixtures.interactions().first(),
                InteractionAnswer.Accept,
            )
        },
        Case("pause", everyone) { e, r -> PauseAgent(e.agents, e.deviceAuth)(e.context(r), AgentFixtures.agents()[2]) },
        Case("resume", everyone) { e, r -> ResumeAgent(e.agents)(e.context(r), AgentFixtures.agents()[5]) },
        Case(
            "run now",
            everyone,
        ) { e, r -> InvokeHeartbeat(e.agents, e.runs)(e.context(r), AgentFixtures.agents()[1]) },
        Case("set budget", ownerOnly) { e, r ->
            SetAgentBudget(e.agents)(e.context(r), AgentFixtures.agents()[2], Money.usdCents(1_000))
        },
        Case(
            "cancel run",
            everyone,
        ) { e, r -> CancelRun(e.runs, e.deviceAuth)(e.context(r), AgentFixtures.runs().first()) },
    )

private fun Env.gatewayCalls(): Int =
    listOf(issues.calls, interactions.calls, agents.calls, runs.calls).sumOf { calls -> calls.values.sum() }

class WorkRoleMatrixTest {
    @Test
    fun `acceptance 8 a viewer never reaches a gateway and is never asked to authenticate`() =
        runTest {
            cases.forEach { case ->
                val env = Env()

                case.run(env, Role.Viewer) shouldBe MutationResult.Denied
                (case.name to env.gatewayCalls()) shouldBe (case.name to 0)
                (case.name to env.biometrics.calls) shouldBe (case.name to 0)
            }
        }

    @Test
    fun `acceptance 8 owners and operators differ only on the agent budget`() =
        runTest {
            cases.forEach { case ->
                listOf(Role.Owner, Role.Operator).forEach { role ->
                    val env = Env()

                    val result = case.run(env, role)

                    val reached = result !is MutationResult.Denied
                    (case.name to role to reached) shouldBe (case.name to role to (role in case.allowed))
                }
            }
        }

    @Test
    fun `acceptance 10 a timeout on any mutation reaches the gateway exactly once`() =
        runTest {
            val timeouts =
                mapOf(
                    "comment" to { e: Env -> e.issues.failures["addComment"] = DomainError.Timeout },
                    "status" to { e: Env -> e.issues.failures["setStatus"] = DomainError.Timeout },
                    "assign" to { e: Env -> e.issues.failures["assign"] = DomainError.Timeout },
                    "respond" to { e: Env -> e.interactions.failures["answer"] = DomainError.Timeout },
                    "pause" to { e: Env -> e.agents.failures["pause"] = DomainError.Timeout },
                    "cancel run" to { e: Env -> e.runs.failures["cancel"] = DomainError.Timeout },
                )
            val sent =
                mapOf<String, (Env) -> Int>(
                    "comment" to { it.issues.count("addComment") },
                    "status" to { it.issues.count("setStatus") },
                    "assign" to { it.issues.count("assign") },
                    "respond" to { it.interactions.count("answer") },
                    "pause" to { it.agents.count("pause") },
                    "cancel run" to { it.runs.count("cancel") },
                )
            timeouts.forEach { (name, fail) ->
                val env = Env()
                fail(env)

                val result = cases.first { it.name == name }.run(env, Role.Owner)

                (name to (result is MutationResult.Reconciled<*>)) shouldBe (name to true)
                (name to sent.getValue(name)(env)) shouldBe (name to 1)
            }
        }
}
