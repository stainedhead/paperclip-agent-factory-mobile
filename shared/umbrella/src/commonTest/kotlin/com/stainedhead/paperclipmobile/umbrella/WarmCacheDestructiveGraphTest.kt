package com.stainedhead.paperclipmobile.umbrella

import com.stainedhead.paperclipmobile.application.MutationResult
import com.stainedhead.paperclipmobile.application.agent.PauseAgent
import com.stainedhead.paperclipmobile.application.agent.SetAgentBudget
import com.stainedhead.paperclipmobile.application.approval.RejectApproval
import com.stainedhead.paperclipmobile.application.run.CancelRun
import com.stainedhead.paperclipmobile.domain.CompanyContext
import com.stainedhead.paperclipmobile.domain.CompanyId
import com.stainedhead.paperclipmobile.domain.CompanyScope
import com.stainedhead.paperclipmobile.domain.InstanceId
import com.stainedhead.paperclipmobile.domain.Money
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.domain.SecretKey
import com.stainedhead.paperclipmobile.domain.SecretName
import com.stainedhead.paperclipmobile.domain.SecretReadResult
import com.stainedhead.paperclipmobile.domain.port.AgentGateway
import com.stainedhead.paperclipmobile.domain.port.ApprovalGateway
import com.stainedhead.paperclipmobile.domain.port.BiometricResult
import com.stainedhead.paperclipmobile.domain.port.RunGateway
import com.stainedhead.paperclipmobile.domain.port.SecretStore
import com.stainedhead.paperclipmobile.testing.demo.DemoIds
import com.stainedhead.paperclipmobile.testing.fakes.FakeAgentGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeApprovalGateway
import com.stainedhead.paperclipmobile.testing.fakes.FakeRunGateway
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The proof behind ADR-145 and ADR-146 through the composition root: with the board token held in memory, every
 * destructive use case the graph builds still asks the device owner once per action, and a refusal sends nothing.
 * This fails if `DeviceAuthGuard` were bypassed or the cache stood in for an authentication.
 */
class WarmCacheDestructiveGraphTest {
    private val home = InstanceId(DemoIds.HOME_FACTORY)
    private val context = CompanyContext(CompanyScope(home, CompanyId(DemoIds.ACME)), Role.Owner, "demo-user")

    private suspend fun warmGraph(): PaperclipGraph {
        val graph = PaperclipCore.createDemoGraph()
        val read = graph.resolve<SecretStore>().get(SecretKey(home, SecretName.BoardToken), "warm up")
        read.shouldBeInstanceOf<SecretReadResult.Found>()
        return graph
    }

    @Test
    fun `pause cancel reject and a budget raise each ask the device owner with the token cached`() =
        runTest {
            val graph = warmGraph()
            val controls = graph.demoControls!!
            val agents = graph.resolveRaw<AgentGateway>() as FakeAgentGateway
            val runs = graph.resolveRaw<RunGateway>() as FakeRunGateway
            val approvals = graph.resolveRaw<ApprovalGateway>() as FakeApprovalGateway
            val agent = agents.store.values.first { it.canPause }
            val run = runs.store.values.first { it.canCancel }
            val approval = approvals.store.values.first { it.status.isResolvable }

            controls.biometricPromptCount shouldBe 0
            graph.resolve<PauseAgent>()(context, agent).shouldBeInstanceOf<MutationResult.Done<*>>()
            controls.biometricPromptCount shouldBe 1
            graph.resolve<CancelRun>()(context, run).shouldBeInstanceOf<MutationResult.Done<*>>()
            controls.biometricPromptCount shouldBe 2
            graph
                .resolve<RejectApproval>()(context, approval.id, approval.status, "not now")
                .shouldBeInstanceOf<MutationResult.Done<*>>()
            controls.biometricPromptCount shouldBe 3
            val raised = Money(agent.monthlyBudget.minorUnits + 1, agent.monthlyBudget.currency)
            graph.resolve<SetAgentBudget>()(context, agent, raised).shouldBeInstanceOf<MutationResult.Done<*>>()
            controls.biometricPromptCount shouldBe 4

            graph.close()
        }

    @Test
    fun `a refused prompt sends nothing even though the token is cached`() =
        runTest {
            val graph = warmGraph()
            val controls = graph.demoControls!!
            val agents = graph.resolveRaw<AgentGateway>() as FakeAgentGateway
            val agent = agents.store.values.first { it.canPause }
            controls.scriptBiometrics(BiometricResult.Cancelled)

            graph.resolve<PauseAgent>()(context, agent).shouldBeInstanceOf<MutationResult.NotAuthorized>()

            controls.biometricPromptCount shouldBe 1
            agents.count("pause") shouldBe 0
            graph.close()
        }
}
