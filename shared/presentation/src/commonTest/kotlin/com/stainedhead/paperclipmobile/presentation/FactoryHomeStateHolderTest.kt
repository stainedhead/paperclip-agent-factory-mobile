@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.presentation

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import com.stainedhead.paperclipmobile.domain.Role
import com.stainedhead.paperclipmobile.testing.TestData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private fun TestScope.factoryEnv(role: Role = Role.Owner): Env {
    val env = Env(listOf(TestData.instance())) { testScheduler.currentTime }
    env.auth.meResult = DomainResult.Success(TestData.access(role))
    return env
}

private fun TestScope.factoryHolder(env: Env) =
    FactoryHomeStateHolder(
        env.factory,
        env.connectivity,
        env.clock,
        CoroutineScope(StandardTestDispatcher(testScheduler)),
        CompanyTarget("inst-1", TestData.acme.value),
    )

class FactoryHomeStateHolderTest {
    @Test
    fun `FR-078 the tiles show agents tasks month spend approvals and incidents`() =
        runTest {
            val env = factoryEnv()
            val holder = factoryHolder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.phase shouldBe DetailPhase.Content
            val tiles = state.tiles!!
            tiles.agentsRunning shouldBe 1
            tiles.agentsPaused shouldBe 2
            tiles.agentsError shouldBe 1
            tiles.tasksOpen shouldBe 5
            tiles.tasksBlocked shouldBe 1
            tiles.monthSpend shouldBe MoneyUi(312_000, "USD")
            tiles.monthBudget shouldBe MoneyUi(500_000, "USD")
            tiles.monthPercentUsed shouldBe 62
            tiles.pendingApprovals shouldBe 2
            tiles.budgetIncidents shouldBe 1
            state.updatedAtEpochMillis shouldBe env.clock.now().toEpochMilliseconds()
        }

    @Test
    fun `FR-078 runs in progress and rolling spend windows are listed`() =
        runTest {
            val env = factoryEnv()
            val holder = factoryHolder(env)
            advanceUntilIdle()

            val state = holder.state.value
            state.liveRuns.map { it.runId } shouldBe listOf("run-live")
            state.liveRuns.single().agentName shouldBe "Engineer 1"
            state.liveRuns.single().status shouldBe RunStatusUi.Running
            state.liveRuns.single().issueIdentifier shouldBe "PAP-1182"
            state.liveRuns
                .single()
                .issueTitle
                .isNullOrEmpty() shouldBe false
            state.spend.map { it.window } shouldBe
                listOf(SpendWindowUi.Last5Hours, SpendWindowUi.Last24Hours, SpendWindowUi.Last7Days)
            state.spend.map { it.cost.minorUnits } shouldBe listOf(1_200L, 9_000L, 61_000L)
            state.refreshIntervalSeconds shouldBe 15
        }

    @Test
    fun `a company with no budget has no percent`() =
        runTest {
            val env = factoryEnv()
            env.dashboard.summary =
                env.dashboard.summary.copy(
                    monthBudget =
                        com.stainedhead.paperclipmobile.domain.Money
                            .usdCents(0),
                )
            val holder = factoryHolder(env)
            advanceUntilIdle()

            holder.state.value.tiles!!
                .hasBudget shouldBe false
            holder.state.value.tiles!!
                .monthPercentUsed shouldBe 0
        }

    @Test
    fun `a failed spend read keeps the tiles and says the spend is unavailable`() =
        runTest {
            val env = factoryEnv()
            env.dashboard.failures["windowSpend"] = DomainError.ServerError
            val holder = factoryHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Content
            holder.state.value.spendUnavailable shouldBe true
        }

    @Test
    fun `a failed dashboard is a failed screen with an error key and no tiles`() =
        runTest {
            val env = factoryEnv()
            env.dashboard.failures["dashboard"] = DomainError.Forbidden
            val holder = factoryHolder(env)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Failed
            holder.state.value.error shouldBe ErrorUi(ErrorKey.NoPermission)
            holder.state.value.tiles shouldBe null
        }

    @Test
    fun `acceptance 11 offline with nothing loaded makes no call and offline after loading keeps the content`() =
        runTest {
            val offline = factoryEnv()
            offline.connectivity.setOnline(false)
            val first = factoryHolder(offline)
            advanceUntilIdle()
            first.state.value.phase shouldBe DetailPhase.Failed
            first.state.value.isOffline shouldBe true
            offline.dashboard.count("dashboard") shouldBe 0

            val env = factoryEnv()
            val holder = factoryHolder(env)
            advanceUntilIdle()
            env.connectivity.setOnline(false)
            advanceUntilIdle()
            holder.onIntent(FactoryHomeIntent.Refresh)
            advanceUntilIdle()

            holder.state.value.phase shouldBe DetailPhase.Content
            holder.state.value.isOffline shouldBe true
            holder.state.value.updatedAtEpochMillis shouldBe env.clock.now().toEpochMilliseconds()
            env.dashboard.count("dashboard") shouldBe 1
        }

    @Test
    fun `refresh reads again and a second refresh while loading is ignored`() =
        runTest {
            val env = factoryEnv()
            val holder = factoryHolder(env)
            advanceUntilIdle()

            holder.onIntent(FactoryHomeIntent.Refresh)
            holder.onIntent(FactoryHomeIntent.Refresh)
            advanceUntilIdle()

            env.dashboard.count("dashboard") shouldBe 2
        }
}
